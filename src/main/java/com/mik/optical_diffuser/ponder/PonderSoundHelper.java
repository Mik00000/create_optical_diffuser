package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

public class PonderSoundHelper {
    private static SimpleSoundInstance activeCaramellSound = null;

    public static void playDistantCaramelldansen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        stopCaramelldansen();

        try {
            activeCaramellSound = SimpleSoundInstance.forUI(
                    ModRegistry.CARAMELLDANSEN_DISTANT_SOUND.get(),
                    1.0f, // pitch
                    0.65f // volume (clearly audible but distant/muffled)
            );
            mc.getSoundManager().play(activeCaramellSound);
            OpticalDiffuserMod.LOGGER.info("[OpticalDiffuser] Playing distant Caramelldansen audio");
        } catch (Throwable t) {
            OpticalDiffuserMod.LOGGER.error("[OpticalDiffuser] Failed to play distant Caramelldansen audio", t);
        }
    }

    public static void playSpeakerCaramelldansen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        stopCaramelldansen();

        try {
            activeCaramellSound = SimpleSoundInstance.forUI(
                    ModRegistry.CARAMELLDANSEN_SPEAKER_SOUND.get(),
                    1.0f, // pitch
                    1.0f  // full volume, crisp and close
            );
            mc.getSoundManager().play(activeCaramellSound);
            OpticalDiffuserMod.LOGGER.info("[OpticalDiffuser] Playing speaker loud Caramelldansen audio");
        } catch (Throwable t) {
            OpticalDiffuserMod.LOGGER.error("[OpticalDiffuser] Failed to play speaker Caramelldansen audio", t);
        }
    }

    public static void stopCaramelldansen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        if (activeCaramellSound != null) {
            try {
                mc.getSoundManager().stop(activeCaramellSound);
            } catch (Throwable ignored) {
            }
            activeCaramellSound = null;
        }
    }

    public static void stopMuffledCaramelldansen() {
        stopCaramelldansen();
    }
}
