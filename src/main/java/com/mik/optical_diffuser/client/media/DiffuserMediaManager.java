package com.mik.optical_diffuser.client.media;

import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.item.FilmReelItem;
import com.mik.optical_diffuser.item.ProjectorLensItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.watermedia.api.player.ALEngine;
import org.watermedia.api.player.GLEngine;
import org.watermedia.api.player.MediaPlayer;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.SFXEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiffuserMediaManager {

    private static final Map<OpticalDiffuserBlockEntity, DiffuserMediaManager> INSTANCES = new ConcurrentHashMap<>();

    private final OpticalDiffuserBlockEntity diffuser;
    private MediaPlayer player = null;
    private String currentUrl = "";
    private boolean soundEnabled = false;
    private float lastSpeed = 1.0f;
    private long lastTimeMs = 0;

    private DynamicMediaTexture dynamicTexture = null;
    private net.minecraft.resources.ResourceLocation dynamicLocation = null;

    public DiffuserMediaManager(OpticalDiffuserBlockEntity diffuser) {
        this.diffuser = diffuser;
    }

    public static DiffuserMediaManager get(OpticalDiffuserBlockEntity diffuser) {
        return INSTANCES.computeIfAbsent(diffuser, DiffuserMediaManager::new);
    }

    public static void remove(OpticalDiffuserBlockEntity diffuser) {
        DiffuserMediaManager mgr = INSTANCES.remove(diffuser);
        if (mgr != null) {
            mgr.release();
        }
    }

    public synchronized void tick() {
        if (diffuser.isRemoved() || diffuser.getLevel() == null) {
            release();
            return;
        }

        OpticalDiffuserBlockEntity.DiffuserModule module = diffuser.getActiveModule();
        boolean isProjector = (module == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SILENT
                || module == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SOUND);

        ItemStack reel = diffuser.getInstalledFilmReelItem();
        String url = FilmReelItem.getUrl(reel);
        boolean sound = (module == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SOUND);

        // Check if conditions permit playback
        boolean canPlay = isProjector && !url.isEmpty() && diffuser.isLightEnabled();

        if (!canPlay) {
            if (player != null && player.playing()) {
                player.pause(true);
            }
            return;
        }

        // URL changed or player not initialized
        if (player == null || !url.equals(currentUrl) || sound != soundEnabled) {
            startMedia(url, sound);
        }

        if (player != null) {
            // Kinetic speed control: Base 1.0x = 16 RPM (Hand Crank)
            float rpm = diffuser.getSpeed();
            float speedFactor = rpm / 16.0f;

            if (Math.abs(rpm) < 0.1f) {
                // Stopped / 0 RPM
                if (player.playing()) {
                    player.pause(true);
                }
            } else if (speedFactor > 0) {
                // Forward playback
                if (player.paused()) {
                    player.resume();
                }
                float clampedSpeed = Math.max(0.1f, Math.min(4.0f, speedFactor));
                if (Math.abs(clampedSpeed - lastSpeed) > 0.05f) {
                    player.speed(clampedSpeed);
                    lastSpeed = clampedSpeed;
                }
            } else {
                // Negative RPM: Rewind effect
                if (player.playing()) {
                    player.pause(true);
                }
                long cur = player.time();
                long stepMs = (long) (Math.abs(speedFactor) * 50); // Rewind per tick
                long targetTime = Math.max(0, cur - stepMs);
                player.seekQuick(targetTime);
            }

            // Spatial 3D Audio update if sound is enabled
            if (sound && player.spatial()) {
                double px = diffuser.getBlockPos().getX() + 0.5;
                double py = diffuser.getBlockPos().getY() + 0.5;
                double pz = diffuser.getBlockPos().getZ() + 0.5;
                player.spatialAudio(new SFXEngine.SpatialAudio(
                        px, py, pz,
                        4.0f,  // Reference distance (full volume)
                        48.0f, // Max audibility distance
                        1.0f,  // Rolloff factor
                        false, // Aux only
                        null   // Default environment
                ));
            }
        }
    }

    private void startMedia(String url, boolean sound) {
        release();
        this.currentUrl = url;
        this.soundEnabled = sound;

        try {
            var mrl = PlayerAPI.mrl(url);
            Minecraft mc = Minecraft.getInstance();

            this.player = PlayerAPI.player(
                    mrl,
                    () -> PlayerAPI.glEngine(Thread.currentThread(), mc),
                    sound ? PlayerAPI::alEngine : () -> null
            );

            if (this.player != null) {
                this.player.repeat(true);
                this.player.start();
                this.lastSpeed = 1.0f;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public synchronized int getTexture() {
        if (player != null && player.video()) {
            long tex = player.texture();
            if (tex != MediaPlayer.NO_TEXTURE && tex > 0) {
                return (int) tex;
            }
        }
        return -1;
    }

    public synchronized net.minecraft.resources.ResourceLocation getTextureLocation() {
        int texId = getTexture();
        if (texId <= 0) return null;

        if (this.dynamicTexture == null) {
            var pos = diffuser.getBlockPos();
            this.dynamicLocation = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.mik.optical_diffuser.OpticalDiffuserMod.MOD_ID,
                    "screen_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ()
            );
            this.dynamicTexture = new DynamicMediaTexture(this.dynamicLocation);
            Minecraft.getInstance().getTextureManager().register(this.dynamicLocation, this.dynamicTexture);
        }

        this.dynamicTexture.updateTextureId(texId);
        return this.dynamicLocation;
    }

    public synchronized MediaPlayer getPlayer() {
        return player;
    }

    public synchronized void release() {
        if (player != null) {
            try {
                player.release();
            } catch (Throwable ignored) {}
            player = null;
        }
        if (dynamicLocation != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(dynamicLocation);
            } catch (Throwable ignored) {}
            dynamicLocation = null;
            dynamicTexture = null;
        }
        currentUrl = "";
    }
}
