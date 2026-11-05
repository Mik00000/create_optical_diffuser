package com.mik.optical_diffuser.client.media;

import com.mik.optical_diffuser.block.entity.FilmReelBlockEntity;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.item.FilmReelBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
    private long lastRecordedTimeMs = 0;
    private int audioUpdateCooldown = 0;
    private int rewindTickCounter = 0;
    private boolean wasRewinding = false;

    private DynamicMediaTexture dynamicTexture = null;
    private ResourceLocation dynamicLocation = null;

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

        FilmReelBlockEntity filmReel = diffuser.getConnectedFilmReel();
        String url = "";
        float rpm = 0.0f;
        long savedTime = 0;

        if (filmReel != null) {
            url = filmReel.getUrl();
            rpm = filmReel.getSpeed();
            savedTime = filmReel.getCurrentTimeMs();
        } else {
            ItemStack reel = diffuser.getInstalledFilmReelItem();
            if (!reel.isEmpty()) {
                url = FilmReelBlockItem.getUrl(reel);
                savedTime = FilmReelBlockItem.getCurrentTimeMs(reel);
                rpm = 0.0f;
            }
        }

        boolean sound = (module == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SOUND);
        boolean canPlay = isProjector && !url.isEmpty() && diffuser.isLightEnabled();

        if (!canPlay) {
            if (player != null && player.playing()) {
                player.pause(true);
            }
            return;
        }

        // Initialize media if URL changed or sound mode toggled
        if (player == null || !url.equals(currentUrl) || sound != soundEnabled) {
            startMedia(url, sound, savedTime);
        }

        if (player != null) {
            float speedFactor = rpm / 16.0f;

            if (Math.abs(rpm) < 0.1f) {
                // 0 RPM: Pause
                if (player.playing()) {
                    player.pause(true);
                }
                wasRewinding = false;
            } else if (speedFactor > 0) {
                // Forward playback
                if (wasRewinding) {
                    player.seek(this.lastRecordedTimeMs);
                    wasRewinding = false;
                }

                if (player.paused()) {
                    player.resume();
                }

                float clampedSpeed = Math.max(0.1f, Math.min(4.0f, speedFactor));
                if (Math.abs(clampedSpeed - lastSpeed) > 0.05f) {
                    player.speed(clampedSpeed);
                    lastSpeed = clampedSpeed;
                }

                long curTime = player.time();
                if (curTime > 0) {
                    this.lastRecordedTimeMs = curTime;
                    if (filmReel != null) {
                        filmReel.setCurrentTimeMs(curTime);
                    }
                }
            } else {
                // Negative RPM: Rewind playback
                wasRewinding = true;
                if (player.playing()) {
                    player.pause(true);
                }

                long stepMs = (long) (Math.abs(speedFactor) * 50); // 50ms per game tick = 1x real-time at 16 RPM
                this.lastRecordedTimeMs = Math.max(0, this.lastRecordedTimeMs - stepMs);

                if (filmReel != null) {
                    filmReel.setCurrentTimeMs(this.lastRecordedTimeMs);
                }

                rewindTickCounter++;
                if (Math.abs(speedFactor) <= 1.25f) {
                    player.previousFrame();
                } else if (rewindTickCounter % 3 == 0) {
                    player.seekQuick(this.lastRecordedTimeMs);
                }
            }

            // Throttled Spatial 3D Audio update (every 5 ticks = 4 Hz)
            if (sound && player.spatial()) {
                audioUpdateCooldown++;
                if (audioUpdateCooldown >= 5) {
                    audioUpdateCooldown = 0;
                    double px = diffuser.getBlockPos().getX() + 0.5;
                    double py = diffuser.getBlockPos().getY() + 0.5;
                    double pz = diffuser.getBlockPos().getZ() + 0.5;
                    player.spatialAudio(new SFXEngine.SpatialAudio(
                            px, py, pz,
                            4.0f,
                            48.0f,
                            1.0f,
                            false,
                            null
                    ));
                }
            }
        }
    }

    private void startMedia(String url, boolean sound, long resumeTimeMs) {
        release();
        this.currentUrl = url;
        this.soundEnabled = sound;
        this.lastRecordedTimeMs = resumeTimeMs;

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
                if (resumeTimeMs > 0) {
                    this.player.seek(resumeTimeMs);
                }
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

    public synchronized ResourceLocation getTextureLocation() {
        int texId = getTexture();
        if (texId <= 0) return null;

        if (this.dynamicTexture == null) {
            var pos = diffuser.getBlockPos();
            this.dynamicLocation = ResourceLocation.fromNamespaceAndPath(
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
        lastRecordedTimeMs = 0;
    }
}
