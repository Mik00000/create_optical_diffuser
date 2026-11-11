package com.mik.optical_diffuser.block.entity;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.block.SpeakerBlock;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SpeakerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private BlockPos linkedDiffuserPos = null;
    public ScrollValueBehaviour volumeSettings;

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
            sendData();
        }
    }

    public boolean isLinked() {
        return this.linkedDiffuserPos != null;
    }

    public int getVolume() {
        return volumeSettings != null ? volumeSettings.getValue() : 100;
    }

    public void setVolume(int vol) {
        if (volumeSettings != null) {
            volumeSettings.setValue(Math.max(0, Math.min(150, vol)));
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        volumeSettings = new ScrollValueBehaviour(
                Component.translatable("gui.create_optical_diffuser.speaker.volume"),
                this,
                new CenteredSideValueBoxTransform((state, face) -> {
                    Direction facing = state.getValue(SpeakerBlock.FACING);
                    return face != facing && face != facing.getOpposite();
                })
        );
        volumeSettings.between(0, 150);
        volumeSettings.value = 100;
        volumeSettings.requiresWrench();
        volumeSettings.withFormatter(v -> v + "%");
        volumeSettings.withCallback(v -> {
            this.setChanged();
            this.sendData();
        });
        behaviours.add(volumeSettings);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            com.mik.optical_diffuser.client.media.ClientSpeakerRegistry.register(this);
        }
    }

    @Override
    public void remove() {
        super.remove();
        com.mik.optical_diffuser.client.media.ClientSpeakerRegistry.unregister(this);
    }

    @Override
    public void destroy() {
        super.destroy();
        com.mik.optical_diffuser.client.media.ClientSpeakerRegistry.unregister(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        com.mik.optical_diffuser.client.media.ClientSpeakerRegistry.unregister(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        com.mik.optical_diffuser.client.media.ClientSpeakerRegistry.unregister(this);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.linkedDiffuserPos != null) {
            tag.putInt("DiffuserX", this.linkedDiffuserPos.getX());
            tag.putInt("DiffuserY", this.linkedDiffuserPos.getY());
            tag.putInt("DiffuserZ", this.linkedDiffuserPos.getZ());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("DiffuserX") && tag.contains("DiffuserY") && tag.contains("DiffuserZ")) {
            this.linkedDiffuserPos = new BlockPos(tag.getInt("DiffuserX"), tag.getInt("DiffuserY"), tag.getInt("DiffuserZ"));
        } else {
            this.linkedDiffuserPos = null;
        }
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

        int vol = getVolume();
        tooltip.add(Component.literal("  Volume: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(vol + "%").withStyle(vol > 100 ? ChatFormatting.LIGHT_PURPLE : (vol == 0 ? ChatFormatting.RED : ChatFormatting.YELLOW)))
                .append(Component.literal(" (Wrench to tune)").withStyle(ChatFormatting.DARK_GRAY)));

        tooltip.add(Component.literal("  Coverage: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("9x9 Zone Ahead").withStyle(ChatFormatting.YELLOW)));
        return true;
    }
}
