package com.mik.optical_diffuser.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class FilmReelItem extends Item {

    public FilmReelItem(Properties properties) {
        super(properties);
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

    public static void setUrl(ItemStack stack, String url) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (url == null || url.trim().isEmpty()) {
                tag.remove("VideoUrl");
            } else {
                tag.putString("VideoUrl", url.trim());
            }
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.mik.optical_diffuser.client.gui.FilmReelScreen.open(hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String url = getUrl(stack);
        if (!url.isEmpty()) {
            String displayUrl = url.length() > 36 ? url.substring(0, 33) + "..." : url;
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.film_reel.url", displayUrl).withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.create_optical_diffuser.film_reel.empty").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.film_reel.speed_info").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.create_optical_diffuser.film_reel.base_speed").withStyle(ChatFormatting.YELLOW));
    }
}
