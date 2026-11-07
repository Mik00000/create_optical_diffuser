package com.mik.optical_diffuser.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class FilmReelBlockItem extends BlockItem {

    public FilmReelBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static String getUrl(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("VideoUrl")) {
                return tag.getString("VideoUrl");
            }
        }
        return "";
    }

    public static long getCurrentTimeMs(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("CurrentTimeMs")) {
                return tag.getLong("CurrentTimeMs");
            }
        }
        return 0;
    }

    public static void setData(ItemStack stack, String url, long currentTimeMs) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (url == null || url.trim().isEmpty()) {
                tag.remove("VideoUrl");
            } else {
                tag.putString("VideoUrl", url.trim());
            }
            if (currentTimeMs > 0) {
                tag.putLong("CurrentTimeMs", currentTimeMs);
            } else {
                tag.remove("CurrentTimeMs");
            }
        });
    }

    public static void setUrl(ItemStack stack, String url) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (url == null || url.trim().isEmpty()) {
                tag.remove("VideoUrl");
            } else {
                tag.putString("VideoUrl", url.trim());
            }
        });
    }

    public static void setCurrentTimeMs(ItemStack stack, long currentTimeMs) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (currentTimeMs > 0) {
                tag.putLong("CurrentTimeMs", currentTimeMs);
            } else {
                tag.remove("CurrentTimeMs");
            }
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.mik.optical_diffuser.client.gui.FilmReelScreen.openHand(hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String url = getUrl(stack);
        long timeMs = getCurrentTimeMs(stack);

        if (!url.isEmpty()) {
            String displayUrl = url.length() > 36 ? url.substring(0, 33) + "..." : url;
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.film_reel.url", displayUrl).withStyle(ChatFormatting.GOLD));

            if (timeMs > 0) {
                long seconds = (timeMs / 1000) % 60;
                long minutes = (timeMs / (1000 * 60)) % 60;
                long hours = timeMs / (1000 * 60 * 60);
                String timeStr = hours > 0
                        ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                        : String.format("%02d:%02d", minutes, seconds);
                tooltip.add(Component.literal("Saved Time: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(timeStr).withStyle(ChatFormatting.AQUA)));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.film_reel.empty").withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.literal("Placement: ").withStyle(ChatFormatting.DARK_GREEN)
                .append(Component.literal("Place on right side of Optical Diffuser").withStyle(ChatFormatting.WHITE)));
        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.film_reel.speed_info").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.film_reel.base_speed").withStyle(ChatFormatting.YELLOW));
    }
}

