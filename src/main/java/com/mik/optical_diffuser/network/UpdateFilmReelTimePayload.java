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

public record UpdateFilmReelTimePayload(BlockPos pos, long timeMs) implements CustomPacketPayload {

    public static final Type<UpdateFilmReelTimePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "update_film_reel_time"));

    public static final StreamCodec<ByteBuf, UpdateFilmReelTimePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            UpdateFilmReelTimePayload::pos,
            ByteBufCodecs.VAR_LONG,
            UpdateFilmReelTimePayload::timeMs,
            UpdateFilmReelTimePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateFilmReelTimePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null && player.level().isLoaded(payload.pos())
                    && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.pos())) < 64.0) {
                BlockEntity be = player.level().getBlockEntity(payload.pos());
                if (be instanceof FilmReelBlockEntity reelBe) {
                    if (payload.timeMs() >= 0 && payload.timeMs() < 86400000L) { // max 24 hours
                        reelBe.setCurrentTimeMs(payload.timeMs());
                    }
                }
            }
        });
    }
}

