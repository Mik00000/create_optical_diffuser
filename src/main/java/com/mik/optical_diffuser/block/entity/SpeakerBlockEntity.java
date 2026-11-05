package com.mik.optical_diffuser.block.entity;

import com.mik.optical_diffuser.ModRegistry;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SpeakerBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    private BlockPos linkedDiffuserPos = null;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SPEAKER_BE.get(), pos, state);
    }

    public BlockPos getLinkedDiffuserPos() {
        return this.linkedDiffuserPos;
    }

    public void setLinkedDiffuserPos(BlockPos pos) {
        this.linkedDiffuserPos = pos;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isLinked() {
        return this.linkedDiffuserPos != null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            com.mik.optical_diffuser.client.media.ClientSpeakerRegistry.register(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            com.mik.optical_diffuser.client.media.ClientSpeakerRegistry.unregister(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.linkedDiffuserPos != null) {
            tag.putInt("DiffuserX", this.linkedDiffuserPos.getX());
            tag.putInt("DiffuserY", this.linkedDiffuserPos.getY());
            tag.putInt("DiffuserZ", this.linkedDiffuserPos.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("DiffuserX") && tag.contains("DiffuserY") && tag.contains("DiffuserZ")) {
            this.linkedDiffuserPos = new BlockPos(tag.getInt("DiffuserX"), tag.getInt("DiffuserY"), tag.getInt("DiffuserZ"));
        } else {
            this.linkedDiffuserPos = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Loudspeaker:").withStyle(ChatFormatting.GOLD));

        if (this.linkedDiffuserPos != null) {
            double dist = Math.sqrt(this.worldPosition.distSqr(this.linkedDiffuserPos));
            tooltip.add(Component.literal("  Status: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Linked").withStyle(ChatFormatting.GREEN)));
            tooltip.add(Component.literal("  Diffuser: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("[%d, %d, %d] (%.1fm)",
                            this.linkedDiffuserPos.getX(), this.linkedDiffuserPos.getY(), this.linkedDiffuserPos.getZ(), dist))
                            .withStyle(ChatFormatting.AQUA)));
        } else {
            tooltip.add(Component.literal("  Status: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Unlinked (Right-Click to connect)").withStyle(ChatFormatting.RED)));
        }

        tooltip.add(Component.literal("  Coverage: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("9x9 Zone Ahead").withStyle(ChatFormatting.YELLOW)));
        return true;
    }
}

