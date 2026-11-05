package com.mik.optical_diffuser.network;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.item.FilmReelBlockItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateFilmReelPayload(InteractionHand hand, String url) implements CustomPacketPayload {

    public static final Type<UpdateFilmReelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "update_film_reel"));

    public static final StreamCodec<ByteBuf, UpdateFilmReelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> InteractionHand.values()[i], InteractionHand::ordinal),
            UpdateFilmReelPayload::hand,
            ByteBufCodecs.STRING_UTF8,
            UpdateFilmReelPayload::url,
            UpdateFilmReelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateFilmReelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                ItemStack stack = player.getItemInHand(payload.hand());
                if (stack.getItem() instanceof FilmReelBlockItem) {
                    FilmReelBlockItem.setUrl(stack, payload.url());
                }
            }
        });
    }
}
