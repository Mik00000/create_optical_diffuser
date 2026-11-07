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
    private float visualAngle = 0.0f;
    private int serverSyncTicks = 0;
    private float lastServerSpeed = 0.0f;

    public FilmReelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public FilmReelBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.FILM_REEL_BE.get(), pos, state);
    }

    public String getUrl() {
        return this.videoUrl;
    }

    public void setUrl(String url) {
        this.videoUrl = url != null ? url.trim() : "";
        notifyUpdate();
    }

    public long getCurrentTimeMs() {
        return this.currentTimeMs;
    }

    public void setCurrentTimeMs(long ms) {
        this.currentTimeMs = Math.max(0, ms);
        setChanged();
    }

    public float getEffectiveSpeed() {
        if (Math.abs(getSpeed()) > 0.001f) {
            return getSpeed();
        }
        if (level != null) {
            net.minecraft.core.Direction facing = getBlockState().getValue(com.mik.optical_diffuser.block.FilmReelBlock.FACING);
            net.minecraft.core.Direction left = com.mik.optical_diffuser.block.OpticalDiffuserBlock.getLeftDirection(facing);
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(getBlockPos().relative(left));
            if (be instanceof OpticalDiffuserBlockEntity diffuser) {
                return diffuser.getSpeed();
            }
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
                // Authoritative server-side playback time tracking based on Create shaft RPM
                if (Math.abs(speed) > 0.1f) {
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
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.videoUrl = tag.getString("VideoUrl");
        this.currentTimeMs = tag.getLong("CurrentTimeMs");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Film Reel:").withStyle(ChatFormatting.GOLD));

        if (!this.videoUrl.isEmpty()) {
            String displayUrl = this.videoUrl.length() > 32 ? this.videoUrl.substring(0, 29) + "..." : this.videoUrl;
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
