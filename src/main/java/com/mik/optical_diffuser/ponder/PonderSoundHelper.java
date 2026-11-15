package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;

public class PonderSoundHelper {
    private static SimpleSoundInstance activeCaramellSound = null;

    public static void playMuffledCaramelldansen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        stopMuffledCaramelldansen();

        activeCaramellSound = new SimpleSoundInstance(
                ModRegistry.CARAMELLDANSEN_SOUND.get().getLocation(),
                SoundSource.RECORDS,
                0.35f, // volume
                1.0f,  // pitch
                net.minecraft.util.RandomSource.create(),
                false, // looping
                0,     // delay
                SimpleSoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0,
                true   // relative (global UI/screen sound)
        );
        mc.getSoundManager().play(activeCaramellSound);
    }

    public static void stopMuffledCaramelldansen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        if (activeCaramellSound != null) {
            mc.getSoundManager().stop(activeCaramellSound);
            activeCaramellSound = null;
        }
    }
}
