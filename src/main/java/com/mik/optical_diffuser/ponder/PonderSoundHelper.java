package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import net.minecraft.client.Minecraft;

public class PonderSoundHelper {
    private static PonderMediaSoundInstance activeSound = null;

    public static void startCaramelldansen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        stopCaramelldansen();

        try {
            activeSound = new PonderMediaSoundInstance();
            activeSound.setSpeakerActive(false);
            mc.getSoundManager().play(activeSound);
            OpticalDiffuserMod.LOGGER.info("[OpticalDiffuser] Started Ponder Caramelldansen full track");
        } catch (Throwable t) {
            OpticalDiffuserMod.LOGGER.error("[OpticalDiffuser] Failed to start Ponder Caramelldansen track", t);
        }
    }

    public static void boostToSpeaker() {
        if (activeSound != null) {
            activeSound.setSpeakerActive(true);
            OpticalDiffuserMod.LOGGER.info("[OpticalDiffuser] Boosted Caramelldansen volume to Speaker");
        }
    }

    public static void fadeOutAndStop() {
        if (activeSound != null) {
            activeSound.startFadeOut();
        }
    }

    public static void stopCaramelldansen() {
        if (activeSound != null) {
            try {
                activeSound.forceStop();
            } catch (Throwable ignored) {
            }
            activeSound = null;
        }
    }

    public static void stopMuffledCaramelldansen() {
        stopCaramelldansen();
    }
}
