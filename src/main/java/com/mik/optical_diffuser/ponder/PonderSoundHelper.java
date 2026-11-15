package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

public class PonderSoundHelper {
    private static SimpleSoundInstance activeCaramellSound = null;

    public static void playMuffledCaramelldansen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        stopMuffledCaramelldansen();

        try {
            activeCaramellSound = SimpleSoundInstance.forUI(
                    ModRegistry.CARAMELLDANSEN_SOUND.get(),
                    1.0f, // pitch
                    0.9f  // volume
            );
            mc.getSoundManager().play(activeCaramellSound);
            OpticalDiffuserMod.LOGGER.info("[OpticalDiffuser] Playing Ponder Caramelldansen audio");
        } catch (Throwable t) {
            OpticalDiffuserMod.LOGGER.error("[OpticalDiffuser] Failed to play Ponder Caramelldansen audio", t);
        }
    }

    public static void stopMuffledCaramelldansen() {
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
}
