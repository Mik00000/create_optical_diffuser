package com.mik.optical_diffuser.ponder;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class PonderMediaSoundInstance extends AbstractTickableSoundInstance {

    public PonderMediaSoundInstance(SoundEvent sound, float volume, boolean looping) {
        super(sound, SoundSource.MASTER, RandomSource.create());
        this.volume = volume;
        this.pitch = 1.0f;
        this.looping = looping;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
    }

    @Override
    public void tick() {
    }
}
