package com.mik.optical_diffuser.client.media;

import com.mik.optical_diffuser.block.entity.FilmReelBlockEntity;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.item.FilmReelBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mik.optical_diffuser.network.UpdateFilmReelTimePayload;
import org.watermedia.api.player.MediaPlayer;
import org.watermedia.api.player.PlayerAPI;

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
    private long pendingSeekMs = -1;
    private int seekWaitTicks = 0;
    private int rewindCooldown = 0;
    private boolean wasRewinding = false;
    private boolean isMuted = false;
    private float currentMasterVolume = 0.0f;
    private float currentReverseVolume = 0.0f;

    private final ProjectorAudioEngine projectorAudioEngine = new ProjectorAudioEngine();
    private float accumulatedReelAngle = 0.0f;
    private long lastServerSyncTimeMs = -1;
    private int syncTickCounter = 0;

    private void syncTimeToReel(FilmReelBlockEntity filmReel, long timeMs) {
        if (filmReel == null) return;
        filmReel.setCurrentTimeMs(timeMs);
        syncTickCounter++;
        if (syncTickCounter >= 20 || Math.abs(timeMs - lastServerSyncTimeMs) >= 1000) {
            syncTickCounter = 0;
            lastServerSyncTimeMs = timeMs;
            try {
                PacketDistributor.sendToServer(new UpdateFilmReelTimePayload(filmReel.getBlockPos(), timeMs));
            } catch (Throwable ignored) {}
        }
    }

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

        // Video playback speed is driven strictly by shaft rotation connected to the film reel
        if (filmReel != null && Math.abs(filmReel.getEffectiveSpeed()) > 0.1f) {
            rpm = filmReel.getEffectiveSpeed();
        }

        if (filmReel != null) {
            url = filmReel.getUrl();
            savedTime = filmReel.getCurrentTimeMs();
        } else {
            ItemStack reel = diffuser.getInstalledFilmReelItem();
            if (!reel.isEmpty()) {
                url = FilmReelBlockItem.getUrl(reel);
                savedTime = FilmReelBlockItem.getCurrentTimeMs(reel);
            }
        }

        boolean sound = (module == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SOUND);
        boolean canPlay = isProjector && !url.isEmpty() && diffuser.isLightEnabled();

        if (!canPlay) {
            if (player != null && player.playing()) {
                player.pause(true);
            }
            projectorAudioEngine.stop();
            return;
        }

        // Initialize media if URL changed or sound mode toggled
        if (player == null || !url.equals(currentUrl) || sound != soundEnabled) {
            startMedia(url, sound, savedTime);
        }

        if (player != null) {
            if (this.pendingSeekMs >= 0) {
                this.seekWaitTicks++;
                if (player.canSeek()) {
                    player.seek(this.pendingSeekMs);
                    this.lastRecordedTimeMs = this.pendingSeekMs;
                    this.pendingSeekMs = -1;
                    this.seekWaitTicks = 0;
                    syncTimeToReel(filmReel, this.lastRecordedTimeMs);
                } else if (this.seekWaitTicks > 200) {
                    this.pendingSeekMs = -1;
                    this.seekWaitTicks = 0;
                }
            }

            float absRpm = Math.abs(rpm);
            float speedFactor = rpm / 16.0f;
            boolean isRewinding = rpm < -0.1f;

            if (absRpm < 0.1f) {
                // 0 RPM: Pause
                this.projectorAudioEngine.stop();
                if (player.playing()) {
                    player.pause(true);
                    syncTimeToReel(filmReel, this.lastRecordedTimeMs);
                }
                OpenALPitchFixer.release(player);
            } else if (speedFactor > 0) {
                // Forward playback
                if (wasRewinding) {
                    this.projectorAudioEngine.stop();
                    player.seek(this.lastRecordedTimeMs);
                    wasRewinding = false;
                }

                if (player.paused()) {
                    player.resume();
                }

                // 4 Ergonomic Speed Zones:
                // Zone 1: Hi-Fi Snap Window (14.0 - 18.0 RPM) -> pristine 1.0x bit-perfect
                // Zone 2: Intelligible Accelerated Range (18.0 - 36.0 RPM / 1.125x - 2.25x)
                // Zone 3: High-Speed Shuttle (36.0+ RPM up to 128+ RPM) -> cap decoder at 4.0x, duck audio
                // Zone 4: Cinematic Slow-Mo (< 14.0 RPM / 0.25x - 0.875x) -> deep baritone, no robotic comb filter
                float targetSpeed;
                if (rpm >= 14.0f && rpm <= 18.0f) {
                    targetSpeed = 1.0f;
                } else if (rpm < 14.0f) {
                    targetSpeed = Math.max(0.25f, speedFactor);
                } else if (rpm <= 36.0f) {
                    targetSpeed = speedFactor;
                } else {
                    targetSpeed = Math.min(4.0f, speedFactor);
                }

                if (Math.abs(targetSpeed - lastSpeed) > 0.03f) {
                    player.speed(targetSpeed);
                    lastSpeed = targetSpeed;
                }

                // Apply OpenAL hardware EFX pitch shifter and de-esser
                OpenALPitchFixer.applyPitchCompensation(player, targetSpeed);

                // Slow-Mo Geneva drive tick for Zone 4:
                if (rpm < 14.0f && diffuser.getLevel() != null) {
                    accumulatedReelAngle += (absRpm * 6.0f) / 20.0f;
                    if (accumulatedReelAngle >= 90.0f) {
                        accumulatedReelAngle -= 90.0f;
                        var pos = diffuser.getBlockPos();
                        diffuser.getLevel().playLocalSound(
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                net.minecraft.sounds.SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                                net.minecraft.sounds.SoundSource.BLOCKS,
                                0.20f,
                                0.85f,
                                false
                        );
                    }
                }

                if (this.pendingSeekMs < 0) {
                    long curTime = player.time();
                    if (curTime > 0) {
                        this.lastRecordedTimeMs = curTime;
                        syncTimeToReel(filmReel, curTime);
                    }
                }
            } else {
                // Negative RPM: Paced Keyframe Shuttle Rewind (Keep player active so lifecycle thread renders frames)
                wasRewinding = true;
                if (player.paused()) {
                    player.resume();
                }
                if (Math.abs(lastSpeed - 0.1f) > 0.01f) {
                    player.speed(0.1f);
                    lastSpeed = 0.1f;
                }
                OpenALPitchFixer.release(player);

                long stepMs = (long) (Math.abs(speedFactor) * 50); // 50ms per game tick = 1x real-time at 16 RPM
                this.lastRecordedTimeMs = Math.max(0, this.lastRecordedTimeMs - stepMs);
                syncTimeToReel(filmReel, this.lastRecordedTimeMs);

                // Paced rapid keyframe seek: 8-10 keyframe updates/sec without decoder stall
                if (rewindCooldown <= 0 && player.canSeek()) {
                    player.seekQuick(this.lastRecordedTimeMs);
                    rewindCooldown = (Math.abs(speedFactor) > 2.0f) ? 1 : 2;
                } else if (rewindCooldown > 0) {
                    rewindCooldown--;
                }

                // Play authentic cine-mechanical spool flutter and Geneva clicks
                if (sound) {
                    this.projectorAudioEngine.tickRewind(Math.abs(speedFactor), this.currentReverseVolume, diffuser.getBlockPos());
                }
            }

            // Direct Audio Zone Check with Smooth Volume Ramping & High-Speed Ducking
            if (sound) {
                Minecraft mc = Minecraft.getInstance();
                boolean inZone = false;

                if (mc.player != null) {
                    net.minecraft.world.phys.Vec3 playerPos = mc.player.position();
                    net.minecraft.world.phys.Vec3 diffuserCenter = net.minecraft.world.phys.Vec3.atCenterOf(diffuser.getBlockPos());

                    // 1. Direct audio within 12 blocks of projector
                    if (playerPos.distanceTo(diffuserCenter) <= 12.0) {
                        inZone = true;
                    } else {
                        // 2. Check all linked speakers
                        java.util.List<com.mik.optical_diffuser.block.entity.SpeakerBlockEntity> speakers =
                                ClientSpeakerRegistry.getSpeakersForDiffuser(diffuser.getBlockPos());
                        for (com.mik.optical_diffuser.block.entity.SpeakerBlockEntity s : speakers) {
                            if (com.mik.optical_diffuser.block.SpeakerBlock.isInsideSpeakerZone(
                                     s.getBlockPos(),
                                     s.getBlockState().getValue(com.mik.optical_diffuser.block.SpeakerBlock.FACING),
                                     playerPos)) {
                                inZone = true;
                                break;
                            }
                        }
                    }
                }

                // Forward player volume:
                // - During rewind: video audio track is muted (0.0f) because projector audio engine plays
                // - At 0 RPM (pause): 0.0f
                // - In Zone 3 (high-speed shuttle > 36 RPM): smoothly duck from 1.0 down to 0.35
                float speedVolumeFactor = 1.0f;
                if (isRewinding || absRpm < 0.1f) {
                    speedVolumeFactor = 0.0f;
                } else if (absRpm > 36.0f) {
                    speedVolumeFactor = Math.max(0.35f, 1.0f - (absRpm - 36.0f) / 45.0f);
                }

                float forwardTargetVolume = inZone ? speedVolumeFactor : 0.0f;

                // Smooth exponential volume ramping (3-4 ticks ramp) to eliminate popping/clicking:
                this.currentMasterVolume += (forwardTargetVolume - this.currentMasterVolume) * 0.30f;
                if (this.currentMasterVolume < 0.02f) {
                    this.currentMasterVolume = 0.0f;
                }

                // Reverse audio player volume:
                float reverseTargetVolume = (inZone && isRewinding) ? 1.0f : 0.0f;
                this.currentReverseVolume += (reverseTargetVolume - this.currentReverseVolume) * 0.30f;
                if (this.currentReverseVolume < 0.02f) {
                    this.currentReverseVolume = 0.0f;
                }

                int intVol = Math.round(this.currentMasterVolume * 100.0f);
                if (intVol > 0) {
                    if (this.isMuted) {
                        player.mute(false);
                        this.isMuted = false;
                    }
                    player.volume(intVol);
                } else {
                    if (!this.isMuted) {
                        player.mute(true);
                        this.isMuted = true;
                    }
                    player.volume(0);
                }
            } else {
                this.projectorAudioEngine.stop();
            }
        }
    }

    private void startMedia(String url, boolean sound, long resumeTimeMs) {
        long effectiveTime = (url.equals(this.currentUrl) && this.lastRecordedTimeMs > 0)
                ? this.lastRecordedTimeMs
                : (resumeTimeMs > 0 ? resumeTimeMs : this.lastRecordedTimeMs);
        release();
        this.currentUrl = url;
        this.soundEnabled = sound;
        this.lastRecordedTimeMs = effectiveTime;
        this.pendingSeekMs = effectiveTime;
        this.seekWaitTicks = 0;

        try {
            com.mik.optical_diffuser.util.DiffuserLogger.info("Starting media player: url='{}', sound={}, resumeTime={}ms", url, sound, effectiveTime);
            var mrl = PlayerAPI.mrl(url);
            Minecraft mc = Minecraft.getInstance();
            Thread renderThread = Thread.currentThread();

            // Disable persistent PBO ring in GLEngine (ringCapable = false) to prevent chroma desync
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
            this.dynamicTexture.setTextureId(texId);
            Minecraft.getInstance().getTextureManager().register(this.dynamicLocation, this.dynamicTexture);
            com.mik.optical_diffuser.util.DiffuserLogger.info("Registered dynamic media texture: {}", this.dynamicLocation);
        } else {
            this.dynamicTexture.setTextureId(texId);
        }

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
            OpenALPitchFixer.release(player);
            FilmReelBlockEntity filmReel = diffuser.getConnectedFilmReel();
            if (filmReel != null && this.lastRecordedTimeMs > 0) {
                syncTimeToReel(filmReel, this.lastRecordedTimeMs);
            }
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
        this.projectorAudioEngine.release();
        currentUrl = "";
        currentMasterVolume = 0.0f;
        currentReverseVolume = 0.0f;
        accumulatedReelAngle = 0.0f;
        rewindCooldown = 0;
        wasRewinding = false;
    }
}
