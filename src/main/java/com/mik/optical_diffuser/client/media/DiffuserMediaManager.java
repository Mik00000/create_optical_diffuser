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
    private boolean isMuted = false;

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
            boolean isFastForward = speedFactor > 2.0f;
            boolean isRewinding = speedFactor < -0.05f;

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

                // Snap to 1.0x if near 16 RPM (13.5 to 18.5 RPM) for perfect natural pitch
                float targetSpeed = (speedFactor >= 0.85f && speedFactor <= 1.15f) ? 1.0f : speedFactor;
                float clampedSpeed = Math.max(0.25f, Math.min(4.0f, targetSpeed));
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

                // Mechanical projector reel click sound while rewinding
                if (rewindTickCounter % 3 == 0 && diffuser.getLevel() != null) {
                    var pos = diffuser.getBlockPos();
                    diffuser.getLevel().playLocalSound(
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                            net.minecraft.sounds.SoundSource.BLOCKS,
                            0.20f,
                            1.6f + (float) (Math.random() * 0.4f),
                            false
                    );
                }
            }

            // Direct Audio Zone Check (Projector 12-block radius OR linked Speaker 9x9 zones)
            if (sound) {
                Minecraft mc = Minecraft.getInstance();
                boolean audible = false;

                // Mute during reverse playback (digital audio codecs cannot play backwards)
                // and during fast-forward > 2.0x (to prevent high-pitched screeching)
                if (!isRewinding && !isFastForward && mc.player != null) {
                    net.minecraft.world.phys.Vec3 playerPos = mc.player.position();
                    net.minecraft.world.phys.Vec3 diffuserCenter = net.minecraft.world.phys.Vec3.atCenterOf(diffuser.getBlockPos());

                    // 1. Direct audio within 12 blocks of projector
                    if (playerPos.distanceTo(diffuserCenter) <= 12.0) {
                        audible = true;
                    } else {
                        // 2. Check all linked speakers
                        java.util.List<com.mik.optical_diffuser.block.entity.SpeakerBlockEntity> speakers =
                                ClientSpeakerRegistry.getSpeakersForDiffuser(diffuser.getBlockPos());
                        for (com.mik.optical_diffuser.block.entity.SpeakerBlockEntity s : speakers) {
                            if (com.mik.optical_diffuser.block.SpeakerBlock.isInsideSpeakerZone(
                                    s.getBlockPos(),
                                    s.getBlockState().getValue(com.mik.optical_diffuser.block.SpeakerBlock.FACING),
                                    playerPos)) {
                                audible = true;
                                break;
                            }
                        }
                    }
                }

                // If player is in projector zone or any speaker zone, unmute direct flat audio!
                // Since this is a single media stream, sound NEVER overlaps or echoes!
                if (audible) {
                    if (this.isMuted) {
                        player.mute(false);
                        this.isMuted = false;
                    }
                    player.volume(100);
                } else {
                    if (!this.isMuted) {
                        player.mute(true);
                        this.isMuted = true;
                    }
                    player.volume(0);
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
            com.mik.optical_diffuser.util.DiffuserLogger.info("Starting media player: url='{}', sound={}, resumeTime={}ms", url, sound, resumeTimeMs);
            var mrl = PlayerAPI.mrl(url);
            Minecraft mc = Minecraft.getInstance();
            Thread renderThread = Thread.currentThread();

            // Use 32 buffers without spatial distortion to prevent OpenAL underrun / clock jitter,
            // and disable the experimental persistent PBO ring in GLEngine (ringCapable = false)
            // which causes the Cr chroma plane desync and green flicker on Linux/NVIDIA.
            this.player = PlayerAPI.player(
                    mrl,
                    () -> {
                        org.watermedia.api.player.GLEngine engine = PlayerAPI.glEngine(renderThread, mc);
                        try {
                            java.lang.reflect.Field field = org.watermedia.api.player.GLEngine.class.getDeclaredField("ringCapable");
                            field.setAccessible(true);
                            field.set(engine, Boolean.FALSE);
                            com.mik.optical_diffuser.util.DiffuserLogger.info("Disabled persistent PBO ring in GLEngine (forcing stable uploadClient)");
                        } catch (Throwable t) {
                            com.mik.optical_diffuser.util.DiffuserLogger.error("Failed to disable ringCapable in GLEngine", t);
                        }
                        return engine;
                    },
                    sound ? () -> PlayerAPI.alEngine(32, false) : () -> null
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
            com.mik.optical_diffuser.util.DiffuserLogger.error("Failed to start media player for url=" + url, t);
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
        if (texId <= 0) {
            return null;
        }

        if (this.dynamicTexture == null) {
            var pos = diffuser.getBlockPos();
            this.dynamicLocation = ResourceLocation.fromNamespaceAndPath(
                    com.mik.optical_diffuser.OpticalDiffuserMod.MOD_ID,
                    "screen_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ()
            );
            this.dynamicTexture = new DynamicMediaTexture(this.dynamicLocation);
            Minecraft.getInstance().getTextureManager().register(this.dynamicLocation, this.dynamicTexture);
            com.mik.optical_diffuser.util.DiffuserLogger.info("Registered dynamic media texture: {}", this.dynamicLocation);
        }

        this.dynamicTexture.setTextureId(texId);
        return this.dynamicLocation;
    }

    public synchronized DynamicMediaTexture getDynamicTexture() {
        return this.dynamicTexture;
    }

    public synchronized MediaPlayer getPlayer() {
        return player;
    }

    public synchronized void release() {
        if (player != null) {
            try {
                com.mik.optical_diffuser.util.DiffuserLogger.info("Releasing media player for url='{}'", currentUrl);
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
