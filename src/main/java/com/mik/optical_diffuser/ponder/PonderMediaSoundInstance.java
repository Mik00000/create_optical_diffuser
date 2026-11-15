package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class PonderMediaSoundInstance extends AbstractTickableSoundInstance {
    private boolean speakerActive = false;
    private boolean fadingOut = false;

    public PonderMediaSoundInstance() {
        super(ModRegistry.CARAMELLDANSEN_FULL_SOUND.get(), SoundSource.MASTER, RandomSource.create());
        this.volume = 0.35f;
        this.pitch = 1.0f;
        this.looping = false;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
    }

    public void setSpeakerActive(boolean active) {
        this.speakerActive = active;
        // Instant volume transition without latency
        this.volume = active ? 1.0f : 0.35f;
    }

    public void startFadeOut() {
        this.fadingOut = true;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            forceStop();
            return;
        }

        // If user closed the ponder UI or switched to another scene, terminate instantly:
        if (mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ponderUI) {
            net.createmod.ponder.foundation.PonderScene scene = ponderUI.getActiveScene();
            if (scene == null || scene.getId() == null || !scene.getId().getPath().contains("sound_projector")) {
                forceStop();
                return;
            }
        } else {
            forceStop();
            return;
        }

        if (fadingOut) {
            this.volume = Math.max(0.0f, this.volume - 0.05f);
            if (this.volume <= 0.01f) {
                forceStop();
            }
        }
    }

    public void forceStop() {
        stop();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(this);
        }
    }
}
