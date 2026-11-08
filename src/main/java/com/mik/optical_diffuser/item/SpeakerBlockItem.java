package com.mik.optical_diffuser.item;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SpeakerBlockItem extends BlockItem {

    public SpeakerBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static BlockPos getLinkedDiffuserPos(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("DiffuserX") && tag.contains("DiffuserY") && tag.contains("DiffuserZ")) {
                return new BlockPos(tag.getInt("DiffuserX"), tag.getInt("DiffuserY"), tag.getInt("DiffuserZ"));
            }
        }
        return null;
    }

    public static void setLinkedDiffuserPos(ItemStack stack, BlockPos pos) {
        if (stack == null || stack.isEmpty()) return;
        if (pos == null) {
            clearLinkedDiffuserPos(stack);
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt("DiffuserX", pos.getX());
            tag.putInt("DiffuserY", pos.getY());
            tag.putInt("DiffuserZ", pos.getZ());
        });
    }

    public static void clearLinkedDiffuserPos(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove("DiffuserX");
            tag.remove("DiffuserY");
            tag.remove("DiffuserZ");
            tag.remove("DiffuserDim");
            tag.remove("Volume");
        });
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        }
    }

    public static int getVolume(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 100;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().contains("Volume")) {
            return customData.copyTag().getInt("Volume");
        }
        return 100;
    }

    public static void setVolume(ItemStack stack, int volume) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("Volume", Math.max(0, Math.min(150, volume))));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getLinkedDiffuserPos(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlockPos linkedPos = getLinkedDiffuserPos(stack);
        if (linkedPos != null) {
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.speaker.linked",
                    linkedPos.getX(), linkedPos.getY(), linkedPos.getZ()).withStyle(ChatFormatting.AQUA));
            int vol = getVolume(stack);
            if (vol != 100) {
                tooltip.add(Component.translatable("tooltip.create_optical_diffuser.speaker.volume_fmt", vol)
                        .withStyle(ChatFormatting.GOLD));
            }
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.speaker.coverage")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.speaker.unlinked")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState state = level.getBlockState(clickedPos);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (state.getBlock() instanceof OpticalDiffuserBlock) {
            if (!level.isClientSide) {
                if (stack.getCount() == 1) {
                    setLinkedDiffuserPos(stack, clickedPos);
                } else {
                    stack.shrink(1);
                    ItemStack paired = new ItemStack(this);
                    setLinkedDiffuserPos(paired, clickedPos);
                    if (player != null && !player.getInventory().add(paired)) {
                        player.drop(paired, false);
                    }
                }
                level.playSound(null, clickedPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0f, 1.4f);
                if (player != null) {
                    player.displayClientMessage(
                            Component.translatable("chat.create_optical_diffuser.speaker_linked",
                                    clickedPos.getX(), clickedPos.getY(), clickedPos.getZ())
                                    .withStyle(ChatFormatting.GREEN),
                            true
                    );
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useOn(context);
    }
}
