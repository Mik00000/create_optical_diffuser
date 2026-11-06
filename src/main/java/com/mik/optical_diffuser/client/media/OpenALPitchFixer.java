package com.mik.optical_diffuser.client.media;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.EXTEfx;
import org.watermedia.api.player.MediaPlayer;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OpenALPitchFixer {

    private static boolean efxChecked = false;
    private static boolean efxAvailable = false;

    private static Field sfxField = null;
    private static Field sourceIdsField = null;

    private static final Map<Integer, EffectSlotPair> SOURCE_EFFECTS = new ConcurrentHashMap<>();

    private record EffectSlotPair(int effectId, int slotId, int dryFilterId, int sendFilterId) {}

    public static boolean checkEfxSupport() {
        if (!efxChecked) {
            efxChecked = true;
            try {
                efxAvailable = AL.getCapabilities().ALC_EXT_EFX;
            } catch (Throwable t1) {
                try {
                    efxAvailable = ALC.getCapabilities().ALC_EXT_EFX;
                } catch (Throwable t2) {
                    efxAvailable = false;
                }
            }
        }
        return efxAvailable;
    }

    public static int[] getSourceIds(MediaPlayer player) {
        if (player == null) return new int[0];
        try {
            if (sfxField == null) {
                sfxField = MediaPlayer.class.getDeclaredField("sfx");
                sfxField.setAccessible(true);
            }
            Object sfxEngine = sfxField.get(player);
            if (sfxEngine == null) return new int[0];

            if (sourceIdsField == null) {
                try {
                    sourceIdsField = sfxEngine.getClass().getDeclaredField("sourceIds");
                    sourceIdsField.setAccessible(true);
                } catch (NoSuchFieldException ignored) {}
            }
            if (sourceIdsField != null) {
                int[] ids = (int[]) sourceIdsField.get(sfxEngine);
                if (ids != null && ids.length > 0) return ids;
            }
            int primary = player.audioSource();
            if (primary > 0) return new int[]{primary};
        } catch (Throwable ignored) {}
        return new int[0];
    }

    /**
     * Applies ergonomic pitch compensation and high-frequency de-essing.
     *
     * @param player       WaterMedia player instance
     * @param currentSpeed Playback speed ratio (1.0 = 16 RPM)
     */
    public static void applyPitchCompensation(MediaPlayer player, float currentSpeed) {
        if (player == null || currentSpeed <= 0.05f) return;
        if (!checkEfxSupport()) return;

        try {
            int[] sourceIds = getSourceIds(player);
            if (sourceIds == null || sourceIds.length == 0) return;

            // ZONE 1 & ZONE 4 (Hi-Fi 1.0x OR Cinematic Slow-Mo < 1.0x):
            // - At 1.0x: 100% clean bit-perfect direct output without DSP overhead.
            // - At < 1.0x: Slow-mo pitch drops naturally with speed (deep, weighty cinematic tone).
            //   We explicitly avoid shifting UP with delay-line pitch shifter, which causes robotic vocoder artifacts.
            if (currentSpeed <= 1.05f) {
                for (int sourceId : sourceIds) {
                    if (sourceId > 0) detachSource(sourceId);
                }
                return;
            }

            // ZONE 2 & ZONE 3 (Accelerated > 1.05x up to high-speed scrub):
            // Compensate pitch downward by -12.0 * log2(speed) semitones.
            float semitones = (float) (-12.0 * (Math.log(currentSpeed) / Math.log(2.0)));
            int coarseTune = Math.max(-12, Math.min(0, Math.round(semitones))); // clamped to max -12 semitones
            float cents = (semitones - coarseTune) * 100.0f;
            int fineTune = Math.max(-50, Math.min(0, Math.round(cents)));

            // High-frequency roll-off (De-esser):
            // When speeding up audio, high sibilants ("s", "sh") double in frequency and sound shrill.
            // Apply gentle lowpass damping on the wet send: 0.75 at 1.5x down to 0.40 at 3.0x+
            float gainHF = Math.max(0.35f, Math.min(0.85f, 1.0f - (currentSpeed - 1.0f) * 0.30f));

            for (int sourceId : sourceIds) {
                if (sourceId <= 0) continue;

                EffectSlotPair pair = SOURCE_EFFECTS.computeIfAbsent(sourceId, id -> {
                    try {
                        // 1. Dry Mute Filter
                        int dryFilter = EXTEfx.alGenFilters();
                        EXTEfx.alFilteri(dryFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                        EXTEfx.alFilterf(dryFilter, EXTEfx.AL_LOWPASS_GAIN, 0.0f);
                        EXTEfx.alFilterf(dryFilter, EXTEfx.AL_LOWPASS_GAINHF, 0.0f);

                        // 2. Wet De-essing Send Filter
                        int sendFilter = EXTEfx.alGenFilters();
                        EXTEfx.alFilteri(sendFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                        EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAIN, 1.0f);
                        EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAINHF, 0.75f);

                        // 3. Pitch Shifter Effect
                        int effect = EXTEfx.alGenEffects();
                        EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_PITCH_SHIFTER);

                        // 4. Auxiliary Effect Slot
                        int slot = EXTEfx.alGenAuxiliaryEffectSlots();
                        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
                        EXTEfx.alAuxiliaryEffectSlotf(slot, EXTEfx.AL_EFFECTSLOT_GAIN, 1.0f);

                        return new EffectSlotPair(effect, slot, dryFilter, sendFilter);
                    } catch (Throwable t) {
                        return null;
                    }
                });

                if (pair != null) {
                    EXTEfx.alEffecti(pair.effectId(), EXTEfx.AL_PITCH_SHIFTER_COARSE_TUNE, coarseTune);
                    EXTEfx.alEffecti(pair.effectId(), EXTEfx.AL_PITCH_SHIFTER_FINE_TUNE, fineTune);
                    EXTEfx.alAuxiliaryEffectSloti(pair.slotId(), EXTEfx.AL_EFFECTSLOT_EFFECT, pair.effectId());

                    // Update high frequency damping on the send filter
                    EXTEfx.alFilterf(pair.sendFilterId(), EXTEfx.AL_LOWPASS_GAINHF, gainHF);

                    // Mute dry direct signal, route wet signal via pitch shifter + de-esser
                    AL10.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, pair.dryFilterId());
                    AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, pair.slotId(), 0, pair.sendFilterId());
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void detachSource(int sourceId) {
        EffectSlotPair pair = SOURCE_EFFECTS.remove(sourceId);
        if (pair != null) {
            try {
                AL10.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
                EXTEfx.alAuxiliaryEffectSloti(pair.slotId(), EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECT_NULL);
                EXTEfx.alDeleteAuxiliaryEffectSlots(pair.slotId());
                EXTEfx.alDeleteEffects(pair.effectId());
                EXTEfx.alDeleteFilters(pair.sendFilterId());
                EXTEfx.alDeleteFilters(pair.dryFilterId());
            } catch (Throwable ignored) {}
        }
    }

    public static void release(MediaPlayer player) {
        if (player == null) return;
        int[] sourceIds = getSourceIds(player);
        for (int sourceId : sourceIds) {
            if (sourceId > 0) detachSource(sourceId);
        }
    }

    public static void releaseAll() {
        for (var entry : SOURCE_EFFECTS.entrySet()) {
            int sourceId = entry.getKey();
            EffectSlotPair pair = entry.getValue();
            try {
                AL10.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
                EXTEfx.alAuxiliaryEffectSloti(pair.slotId(), EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECT_NULL);
                EXTEfx.alDeleteAuxiliaryEffectSlots(pair.slotId());
                EXTEfx.alDeleteEffects(pair.effectId());
                EXTEfx.alDeleteFilters(pair.sendFilterId());
                EXTEfx.alDeleteFilters(pair.dryFilterId());
            } catch (Throwable ignored) {}
        }
        SOURCE_EFFECTS.clear();
    }
}
