package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class PonderMediaSoundInstance extends AbstractTickableSoundInstance {
    private boolean speakerActive = false;
    private int elapsedTicks = 0;

    public PonderMediaSoundInstance() {
        super(ModRegistry.CARAMELLDANSEN_FULL_SOUND.get(), SoundSource.MASTER, RandomSource.create());
        this.volume = 0.35f;
        this.pitch = 1.0f;
        this.looping = true; // Looped indefinitely while in Ponder screen
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
        // Keeping active for post-scene continuous playback
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            forceStop();
            return;
        }

        // If user closed the ponder UI or switched to another scene, terminate instantly (0 ms):
        if (mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ponderUI) {
            net.createmod.ponder.foundation.PonderScene scene = ponderUI.getActiveScene();
            if (scene == null || scene.getId() == null || !scene.getId().getPath().contains("sound_projector")) {
                forceStop();
                return;
            }

            elapsedTicks++;
            if (elapsedTicks % 20 == 0) {
                updatePonderDisplays(scene);
            }
        } else {
            forceStop();
            return;
        }
    }

    private void updatePonderDisplays(net.createmod.ponder.foundation.PonderScene scene) {
        if (scene == null || scene.getWorld() == null) return;
        net.createmod.ponder.api.level.PonderLevel level = scene.getWorld();

        int totalSec = 44 + (elapsedTicks / 20);
        int min = totalSec / 60;
        int sec = totalSec % 60;
        String timeStr = String.format("%02d:%02d", min, sec);
        String minStr = String.format("%02d", min);
        String secStr = String.format("%02d", sec);
        String fullDigits = minStr + secStr;
        String json = "{\"text\":\"" + fullDigits + "\"}";

        // Board controller is at (3, 2, 2)
        net.minecraft.core.BlockPos boardController = new net.minecraft.core.BlockPos(3, 2, 2);
        net.minecraft.world.level.block.entity.BlockEntity beBoard = level.getBlockEntity(boardController);
        if (beBoard instanceof com.simibubi.create.content.trains.display.FlapDisplayBlockEntity flap) {
            flap.applyTextManually(0, net.minecraft.network.chat.Component.literal("CARAMELLDANSEN"));
            flap.applyTextManually(1, net.minecraft.network.chat.Component.literal(" " + timeStr + " [PLAY] "));
        }

        // Nixie tubes at (3, 1, 1) [left, index 0] and (2, 1, 1) [right, index 1]
        net.minecraft.core.BlockPos nixieLeft = new net.minecraft.core.BlockPos(3, 1, 1);
        net.minecraft.core.BlockPos nixieRight = new net.minecraft.core.BlockPos(2, 1, 1);

        net.minecraft.world.level.block.entity.BlockEntity beNixieLeft = level.getBlockEntity(nixieLeft);
        if (beNixieLeft instanceof com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity n1) {
            n1.displayCustomText(json, 0);
            n1.updateDisplayedStrings();
        }

        net.minecraft.world.level.block.entity.BlockEntity beNixieRight = level.getBlockEntity(nixieRight);
        if (beNixieRight instanceof com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity n2) {
            n2.displayCustomText(json, 1);
            n2.updateDisplayedStrings();
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
