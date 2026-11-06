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

    private record EffectSlotPair(int effectId, int slotId, int filterId) {}

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

    public static void applyPitchCompensation(MediaPlayer player, float currentSpeed) {
        if (player == null || currentSpeed <= 0.05f) return;
        if (!checkEfxSupport()) return;

        try {
            int[] sourceIds = getSourceIds(player);
            if (sourceIds == null || sourceIds.length == 0) return;

            // Target coarse tune in semitones:
            // S = 2.0 -> semitones = -12.0 * log2(2.0) = -12 semitones (1 octave lower)
            // S = 0.5 -> semitones = -12.0 * log2(0.5) = +12 semitones (1 octave higher)
            float semitones = (float) (-12.0 * (Math.log(currentSpeed) / Math.log(2.0)));
            int coarseTune = Math.max(-12, Math.min(12, Math.round(semitones)));
            float cents = (semitones - coarseTune) * 100.0f;
            int fineTune = Math.max(-50, Math.min(50, Math.round(cents)));

            for (int sourceId : sourceIds) {
                if (sourceId <= 0) continue;

                if (Math.abs(currentSpeed - 1.0f) < 0.05f) {
                    // Normal speed: detach pitch shifter and restore standard direct playback
                    detachSource(sourceId);
                    continue;
                }

                // Speed != 1.0x: update or create pitch shifter effect + mute dry signal
                EffectSlotPair pair = SOURCE_EFFECTS.computeIfAbsent(sourceId, id -> {
                    try {
                        int filter = EXTEfx.alGenFilters();
                        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, 0.0f);
                        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, 0.0f);

                        int effect = EXTEfx.alGenEffects();
                        EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_PITCH_SHIFTER);

                        int slot = EXTEfx.alGenAuxiliaryEffectSlots();
                        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
                        EXTEfx.alAuxiliaryEffectSlotf(slot, EXTEfx.AL_EFFECTSLOT_GAIN, 1.0f);

                        return new EffectSlotPair(effect, slot, filter);
                    } catch (Throwable t) {
                        return null;
                    }
                });

                if (pair != null) {
                    EXTEfx.alEffecti(pair.effectId(), EXTEfx.AL_PITCH_SHIFTER_COARSE_TUNE, coarseTune);
                    EXTEfx.alEffecti(pair.effectId(), EXTEfx.AL_PITCH_SHIFTER_FINE_TUNE, fineTune);
                    EXTEfx.alAuxiliaryEffectSloti(pair.slotId(), EXTEfx.AL_EFFECTSLOT_EFFECT, pair.effectId());

                    // Mute dry direct signal, route wet output via pitch shifter slot
                    AL10.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, pair.filterId());
                    AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, pair.slotId(), 0, EXTEfx.AL_FILTER_NULL);
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
                EXTEfx.alDeleteFilters(pair.filterId());
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
                EXTEfx.alDeleteFilters(pair.filterId());
            } catch (Throwable ignored) {}
        }
        SOURCE_EFFECTS.clear();
    }
}
