package com.mik.optical_diffuser.item;

import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class HologramLensItem extends Item {

    private static final int[] DISTANCE_PRESETS = {2, 4, 6, 8, 12, 16, 24, 32};
    private static final float[] SCALE_PRESETS = {0.5f, 1.0f, 1.5f, 2.0f, 3.0f};

    public HologramLensItem(Properties properties) {
        super(properties);
    }

    public static int getFocalDistance(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("FocalDistance")) {
                return Math.max(2, Math.min(32, tag.getInt("FocalDistance")));
            }
        }
        return 6;
    }

    public static void setFocalDistance(ItemStack stack, int distance) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt("FocalDistance", Math.max(2, Math.min(32, distance)));
        });
    }

    public static float getHoloScale(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("HoloScale")) {
                return Math.max(0.25f, Math.min(4.0f, tag.getFloat("HoloScale")));
            }
        }
        return 1.0f;
    }

    public static void setHoloScale(ItemStack stack, float scale) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putFloat("HoloScale", Math.max(0.25f, Math.min(4.0f, scale)));
        });
    }

    public static ItemStack getHoloTarget(ItemStack stack, HolderLookup.Provider registries) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("HoloTarget")) {
                CompoundTag targetTag = tag.getCompound("HoloTarget");
                return ItemStack.parseOptional(registries, targetTag);
            }
        }
        return ItemStack.EMPTY;
    }

    public static void setHoloTarget(ItemStack stack, ItemStack target, HolderLookup.Provider registries) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (target != null && !target.isEmpty()) {
                tag.put("HoloTarget", target.save(registries));
            } else {
                tag.remove("HoloTarget");
            }
        });
    }

    public static boolean hasHoloTarget(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            return customData.copyTag().contains("HoloTarget");
        }
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasHoloTarget(stack) || super.isFoil(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = level.getBlockState(clickedPos);

        // Do not intercept if clicking an optical diffuser block (let block handle lens insertion)
        if (state.getBlock() instanceof OpticalDiffuserBlock) {
            return InteractionResult.PASS;
        }

        // 1. Offhand item priority: if player has any item in offhand, imprint that item!
        if (player != null && context.getHand() == InteractionHand.MAIN_HAND) {
            ItemStack offhand = player.getOffhandItem();
            if (!offhand.isEmpty()) {
                if (!level.isClientSide) {
                    setHoloTarget(stack, offhand.copy(), level.registryAccess());
                    level.playSound(null, clickedPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.4f);
                    player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.hologram_imprinted",
                            offhand.getHoverName().getString()).withStyle(ChatFormatting.AQUA), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // 2. Offhand empty: clicking a block imprints the clicked block
        if (player != null && !state.isAir()) {
            if (!level.isClientSide) {
                ItemStack target = new ItemStack(state.getBlock().asItem());
                if (target.isEmpty()) {
                    target = new ItemStack(state.getBlock());
                }
                setHoloTarget(stack, target, level.registryAccess());

                level.playSound(null, clickedPos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
                player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.hologram_imprinted",
                        state.getBlock().getName().getString()).withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            ItemStack offhand = player.getOffhandItem();
            if (hand == InteractionHand.MAIN_HAND && !offhand.isEmpty()) {
                // Imprint offhand item / schematic into hologram target
                if (!level.isClientSide) {
                    setHoloTarget(stack, offhand.copy(), level.registryAccess());
                    level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.4f);
                    player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.hologram_imprinted",
                            offhand.getHoverName().getString()).withStyle(ChatFormatting.AQUA), true);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            } else if (hasHoloTarget(stack)) {
                // Clear target -> revert to Navigational Gyroscope
                if (!level.isClientSide) {
                    setHoloTarget(stack, ItemStack.EMPTY, level.registryAccess());
                    level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
                    player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.hologram_cleared").withStyle(ChatFormatting.GOLD), true);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            } else {
                // Cycle distance
                int curDist = getFocalDistance(stack);
                int nextDist = DISTANCE_PRESETS[0];
                for (int i = 0; i < DISTANCE_PRESETS.length; i++) {
                    if (DISTANCE_PRESETS[i] == curDist) {
                        nextDist = DISTANCE_PRESETS[(i + 1) % DISTANCE_PRESETS.length];
                        break;
                    }
                }
                if (!level.isClientSide) {
                    setFocalDistance(stack, nextDist);
                    level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.3f);
                    player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.hologram_distance", nextDist).withStyle(ChatFormatting.YELLOW), true);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        } else {
            // Cycle scale
            float curScale = getHoloScale(stack);
            float nextScale = SCALE_PRESETS[1]; // default 1.0
            for (int i = 0; i < SCALE_PRESETS.length; i++) {
                if (Math.abs(SCALE_PRESETS[i] - curScale) < 0.05f) {
                    nextScale = SCALE_PRESETS[(i + 1) % SCALE_PRESETS.length];
                    break;
                }
            }
            if (!level.isClientSide) {
                setHoloScale(stack, nextScale);
                level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.5f);
                player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.hologram_scale", String.format("%.1f", nextScale)).withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.hologram_lens.desc").withStyle(ChatFormatting.LIGHT_PURPLE));

        HolderLookup.Provider registries = context.registries();
        if (hasHoloTarget(stack)) {
            ItemStack target = registries != null ? getHoloTarget(stack, registries) : ItemStack.EMPTY;
            String name = !target.isEmpty() ? target.getHoverName().getString() : "Custom Model";
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.hologram_lens.target", name).withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.hologram_lens.target_default").withStyle(ChatFormatting.AQUA));
        }

        int dist = getFocalDistance(stack);
        float scale = getHoloScale(stack);
        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.hologram_lens.distance", dist).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.hologram_lens.scale", String.format("%.1f", scale)).withStyle(ChatFormatting.YELLOW));

        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.hologram_lens.use_block").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.hologram_lens.use_offhand").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.hologram_lens.use_cycle").withStyle(ChatFormatting.DARK_GRAY));
    }
}
