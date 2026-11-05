package com.mik.optical_diffuser.network;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.block.entity.FilmReelBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateFilmReelBlockPayload(BlockPos pos, String url) implements CustomPacketPayload {

    public static final Type<UpdateFilmReelBlockPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "update_film_reel_block"));

    public static final StreamCodec<ByteBuf, UpdateFilmReelBlockPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            UpdateFilmReelBlockPayload::pos,
            ByteBufCodecs.STRING_UTF8,
            UpdateFilmReelBlockPayload::url,
            UpdateFilmReelBlockPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateFilmReelBlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null && player.level().isLoaded(payload.pos())) {
                BlockEntity be = player.level().getBlockEntity(payload.pos());
                if (be instanceof FilmReelBlockEntity reelBe) {
                    reelBe.setUrl(payload.url());
                }
            }
        });
    }
}
