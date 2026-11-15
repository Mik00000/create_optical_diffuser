package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import net.minecraft.client.Minecraft;

public class PonderSoundHelper {
    private static PonderMediaSoundInstance activeDistantSound = null;
    private static PonderMediaSoundInstance activeSpeakerSound = null;

    public static boolean isPlaying() {
        return activeDistantSound != null || activeSpeakerSound != null;
    }

    public static void startCaramelldansen() {
        stopCaramelldansen();

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        try {
            // Muffled bandpass projector track (0.85 volume, looping)
            activeDistantSound = new PonderMediaSoundInstance(
                    ModRegistry.CARAMELLDANSEN_DISTANT_SOUND.get(),
                    0.85f,
                    true
            );
            mc.getSoundManager().play(activeDistantSound);
            OpticalDiffuserMod.LOGGER.info("[OpticalDiffuser] Started Ponder Caramelldansen muffled distant sound");
        } catch (Throwable t) {
            OpticalDiffuserMod.LOGGER.error("[OpticalDiffuser] Failed to start distant sound", t);
        }
    }

    public static void boostToSpeaker() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        try {
            // Instantly stop the muffled projector track
            if (activeDistantSound != null) {
                mc.getSoundManager().stop(activeDistantSound);
                activeDistantSound = null;
            }

            // Instantly stop any previous speaker track
            if (activeSpeakerSound != null) {
                mc.getSoundManager().stop(activeSpeakerSound);
                activeSpeakerSound = null;
            }

            // Play crisp full-range speaker track (1.0 volume, looping seamlessly every 22s)
            activeSpeakerSound = new PonderMediaSoundInstance(
                    ModRegistry.CARAMELLDANSEN_SPEAKER_SOUND.get(),
                    1.0f,
                    true
            );
            mc.getSoundManager().play(activeSpeakerSound);
            OpticalDiffuserMod.LOGGER.info("[OpticalDiffuser] Boosted to Speaker: crisp full track started");
        } catch (Throwable t) {
            OpticalDiffuserMod.LOGGER.error("[OpticalDiffuser] Failed to boost to speaker", t);
        }
    }

    public static void stopCaramelldansen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            if (activeDistantSound != null) {
                try {
                    mc.getSoundManager().stop(activeDistantSound);
                } catch (Throwable ignored) {}
                activeDistantSound = null;
            }
            if (activeSpeakerSound != null) {
                try {
                    mc.getSoundManager().stop(activeSpeakerSound);
                } catch (Throwable ignored) {}
                activeSpeakerSound = null;
            }
        }
    }

    public static void fadeOutAndStop() {
        stopCaramelldansen();
    }

    public static void stopMuffledCaramelldansen() {
        stopCaramelldansen();
    }
}
