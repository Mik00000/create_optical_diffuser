package com.mik.optical_diffuser.block.entity;

import com.mik.optical_diffuser.ModRegistry;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class FilmReelBlockEntity extends KineticBlockEntity {

    private String videoUrl = "";
    private long currentTimeMs = 0;
    private long totalDurationMs = 0;
    private boolean isPlaying = false;
    private float visualAngle = 0.0f;
    private int serverSyncTicks = 0;
    private float lastServerSpeed = 0.0f;

    public FilmReelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public FilmReelBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.FILM_REEL_BE.get(), pos, state);
    }

    public boolean isPlaying() {
        return this.isPlaying;
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
    }

    public OpticalDiffuserBlockEntity getConnectedDiffuser() {
        if (level != null) {
            net.minecraft.core.Direction facing = getBlockState().getValue(com.mik.optical_diffuser.block.FilmReelBlock.FACING);
            net.minecraft.core.Direction left = com.mik.optical_diffuser.block.OpticalDiffuserBlock.getLeftDirection(facing);
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(getBlockPos().relative(left));
            if (be instanceof OpticalDiffuserBlockEntity diffuser) {
                return diffuser;
            }
        }
        return null;
    }

    private static final java.util.Map<String, Long> GLOBAL_TIME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> GLOBAL_DURATION_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static long getLastKnownTime(String url) {
        if (url == null || url.isEmpty()) return 0;
        return GLOBAL_TIME_CACHE.getOrDefault(url, 0L);
    }

    public static long getLastKnownDuration(String url) {
        if (url == null || url.isEmpty()) return 0;
        return GLOBAL_DURATION_CACHE.getOrDefault(url, 0L);
    }

    public String getUrl() {
        return this.videoUrl;
    }

    public void setUrl(String url) {
        this.videoUrl = url != null ? url.trim() : "";
        if (!this.videoUrl.isEmpty()) {
            if (this.currentTimeMs == 0) {
                long cached = getLastKnownTime(this.videoUrl);
                if (cached > 0) {
                    this.currentTimeMs = cached;
                }
            }
            if (this.totalDurationMs == 0) {
                long cachedDur = getLastKnownDuration(this.videoUrl);
                if (cachedDur > 0) {
                    this.totalDurationMs = cachedDur;
                }
            }
        }
        notifyUpdate();
    }

    public long getCurrentTimeMs() {
        return this.currentTimeMs;
    }

    public void setCurrentTimeMs(long ms) {
        this.currentTimeMs = Math.max(0, ms);
        if (this.currentTimeMs > 0 && this.videoUrl != null && !this.videoUrl.isEmpty()) {
            GLOBAL_TIME_CACHE.put(this.videoUrl, this.currentTimeMs);
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public long getTotalDurationMs() {
        return this.totalDurationMs;
    }

    public void setTotalDurationMs(long ms) {
        this.totalDurationMs = Math.max(0, ms);
        if (this.totalDurationMs > 0 && this.videoUrl != null && !this.videoUrl.isEmpty()) {
            GLOBAL_DURATION_CACHE.put(this.videoUrl, this.totalDurationMs);
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public int getComparatorOutput() {
        if (this.videoUrl.isEmpty()) {
            return 0;
        }
        if (this.totalDurationMs > 0) {
            double fraction = (double) this.currentTimeMs / (double) this.totalDurationMs;
            fraction = Math.max(0.0, Math.min(1.0, fraction));
            return Math.min(15, Math.max(1, (int) Math.ceil(fraction * 15.0)));
        }
        return this.isPlaying ? 15 : (this.currentTimeMs > 0 ? 1 : 0);
    }

    public float getEffectiveSpeed() {
        if (Math.abs(getSpeed()) > 0.001f) {
            return getSpeed();
        }
        OpticalDiffuserBlockEntity diffuser = getConnectedDiffuser();
        if (diffuser != null) {
            return diffuser.getSpeed();
        }
        return 0.0f;
    }

    public float getVisualAngle() {
        return this.visualAngle;
    }

    @Override
    public void tick() {
        super.tick();
        float speed = getEffectiveSpeed();
        if (level != null) {
            if (level.isClientSide) {
                if (speed != 0.0f) {
                    this.visualAngle += (speed * 6.0f / 20.0f);
                }
            } else {
                OpticalDiffuserBlockEntity diffuser = getConnectedDiffuser();
                boolean isConnectedToDiffuser = (diffuser != null);

                // If connected to a diffuser, time only advances when media is loaded and actively playing
                boolean canAdvance = !isConnectedToDiffuser || (this.isPlaying && diffuser.isLightEnabled());

                if (Math.abs(speed) > 0.1f && canAdvance && (!isConnectedToDiffuser || !this.videoUrl.isEmpty())) {
                    long deltaMs = (long) ((speed / 16.0f) * 50); // 50ms per tick at 16 RPM
                    this.currentTimeMs = Math.max(0, this.currentTimeMs + deltaMs);
                    serverSyncTicks++;
                    // Periodically sync time to tracking clients (every 2 seconds / 40 ticks, or on speed change)
                    if (serverSyncTicks >= 40 || Math.abs(speed - lastServerSpeed) > 1.0f) {
                        serverSyncTicks = 0;
                        lastServerSpeed = speed;
                        sendData();
                        setChanged();
                    }
                } else if (serverSyncTicks > 0 || Math.abs(lastServerSpeed) > 0.1f) {
                    serverSyncTicks = 0;
                    lastServerSpeed = 0.0f;
                    sendData();
                    setChanged();
                }
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("VideoUrl", this.videoUrl);
        tag.putLong("CurrentTimeMs", this.currentTimeMs);
        tag.putLong("TotalDurationMs", this.totalDurationMs);
        tag.putBoolean("IsPlaying", this.isPlaying);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.videoUrl = tag.getString("VideoUrl");
        this.currentTimeMs = tag.getLong("CurrentTimeMs");
        if (tag.contains("TotalDurationMs")) {
            this.totalDurationMs = tag.getLong("TotalDurationMs");
        }
        this.isPlaying = tag.getBoolean("IsPlaying");
        if (this.currentTimeMs == 0 && !this.videoUrl.isEmpty()) {
            long cached = getLastKnownTime(this.videoUrl);
            if (cached > 0) {
                this.currentTimeMs = cached;
            }
        }
        if (this.totalDurationMs == 0 && !this.videoUrl.isEmpty()) {
            long cachedDur = getLastKnownDuration(this.videoUrl);
            if (cachedDur > 0) {
                this.totalDurationMs = cachedDur;
            }
        }
    }

    public static String getDisplayFilename(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        try {
            String clean = url.trim();
            if (clean.contains("?")) clean = clean.substring(0, clean.indexOf("?"));
            if (clean.contains("#")) clean = clean.substring(0, clean.indexOf("#"));
            if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
            int lastSlash = clean.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < clean.length() - 1) {
                String name = java.net.URLDecoder.decode(clean.substring(lastSlash + 1), java.nio.charset.StandardCharsets.UTF_8);
                if (!name.isEmpty()) {
                    return name.length() > 30 ? name.substring(0, 27) + "..." : name;
                }
            }
        } catch (Throwable ignored) {}
        return url.length() > 30 ? url.substring(0, 27) + "..." : url;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Film Reel:").withStyle(ChatFormatting.GOLD));

        if (!this.videoUrl.isEmpty()) {
            String displayUrl = getDisplayFilename(this.videoUrl);
            tooltip.add(Component.literal("  Source: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(displayUrl).withStyle(ChatFormatting.YELLOW)));

            long seconds = (this.currentTimeMs / 1000) % 60;
            long minutes = (this.currentTimeMs / (1000 * 60)) % 60;
            long hours = this.currentTimeMs / (1000 * 60 * 60);

            String timeStr = hours > 0
                    ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    : String.format("%02d:%02d", minutes, seconds);

            tooltip.add(Component.literal("  Playback Time: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(timeStr).withStyle(ChatFormatting.AQUA)));
        } else {
            tooltip.add(Component.literal("  Source: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Empty (Right-Click to edit)").withStyle(ChatFormatting.DARK_GRAY)));
        }

        return true;
    }
}
