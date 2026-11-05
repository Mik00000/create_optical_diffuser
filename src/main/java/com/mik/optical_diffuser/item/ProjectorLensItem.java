package com.mik.optical_diffuser.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public class ProjectorLensItem extends Item {

    private final boolean soundEnabled;

    public ProjectorLensItem(Properties properties, boolean soundEnabled) {
        super(properties);
        this.soundEnabled = soundEnabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public static class ScreenCoords {
        public final BlockPos pos1;
        public final BlockPos pos2;

        public ScreenCoords(BlockPos pos1, BlockPos pos2) {
            this.pos1 = pos1;
            this.pos2 = pos2;
        }

        public boolean isComplete() {
            return pos1 != null && pos2 != null;
        }
    }

    public static ScreenCoords getScreenCoords(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            BlockPos p1 = tag.contains("Pos1X") ? new BlockPos(tag.getInt("Pos1X"), tag.getInt("Pos1Y"), tag.getInt("Pos1Z")) : null;
            BlockPos p2 = tag.contains("Pos2X") ? new BlockPos(tag.getInt("Pos2X"), tag.getInt("Pos2Y"), tag.getInt("Pos2Z")) : null;
            return new ScreenCoords(p1, p2);
        }
        return new ScreenCoords(null, null);
    }

    public static void setPos1(ItemStack stack, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt("Pos1X", pos.getX());
            tag.putInt("Pos1Y", pos.getY());
            tag.putInt("Pos1Z", pos.getZ());
        });
    }

    public static void setPos2(ItemStack stack, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt("Pos2X", pos.getX());
            tag.putInt("Pos2Y", pos.getY());
            tag.putInt("Pos2Z", pos.getZ());
        });
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Enchantment glint appears when both coordinates are selected
        return getScreenCoords(stack).isComplete() || super.isFoil(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        var player = context.getPlayer();

        // Do not intercept if clicking an optical diffuser block (let block handle lens insertion)
        if (level.getBlockState(clickedPos).getBlock() instanceof com.mik.optical_diffuser.block.OpticalDiffuserBlock) {
            return InteractionResult.PASS;
        }

        if (player != null) {
            if (player.isShiftKeyDown()) {
                // Shift + Right-Click: select corner 2
                if (!level.isClientSide) {
                    setPos2(stack, clickedPos);
                    ScreenCoords coords = getScreenCoords(stack);
                    player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.corner2_set",
                            clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()).withStyle(ChatFormatting.AQUA), true);

                    if (coords.isComplete()) {
                        int dx = Math.abs(coords.pos1.getX() - coords.pos2.getX()) + 1;
                        int dy = Math.abs(coords.pos1.getY() - coords.pos2.getY()) + 1;
                        int dz = Math.abs(coords.pos1.getZ() - coords.pos2.getZ()) + 1;
                        int w = Math.max(dx, dz);
                        int h = dy;
                        player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.screen_configured", w, h).withStyle(ChatFormatting.GREEN), false);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else {
                // Right-Click: select corner 1
                if (!level.isClientSide) {
                    setPos1(stack, clickedPos);
                    player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.corner1_set",
                            clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()).withStyle(ChatFormatting.GOLD), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.projector_lens.use1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.projector_lens.use2").withStyle(ChatFormatting.AQUA));

        ScreenCoords coords = getScreenCoords(stack);
        if (coords.isComplete()) {
            int dx = Math.abs(coords.pos1.getX() - coords.pos2.getX()) + 1;
            int dy = Math.abs(coords.pos1.getY() - coords.pos2.getY()) + 1;
            int dz = Math.abs(coords.pos1.getZ() - coords.pos2.getZ()) + 1;
            int w = Math.max(dx, dz);
            int h = dy;
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.projector_lens.screen_configured",
                    coords.pos1.getX(), coords.pos1.getY(), coords.pos1.getZ(),
                    coords.pos2.getX(), coords.pos2.getY(), coords.pos2.getZ(),
                    w, h).withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.projector_lens.screen_not_configured").withStyle(ChatFormatting.RED));
        }
    }
}
