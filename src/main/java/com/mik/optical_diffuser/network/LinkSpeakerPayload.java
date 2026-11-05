package com.mik.optical_diffuser.network;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.block.entity.SpeakerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record LinkSpeakerPayload(BlockPos speakerPos, Optional<BlockPos> diffuserPos) implements CustomPacketPayload {

    public static final Type<LinkSpeakerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "link_speaker"));

    public static final StreamCodec<ByteBuf, LinkSpeakerPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            LinkSpeakerPayload::speakerPos,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
            LinkSpeakerPayload::diffuserPos,
            LinkSpeakerPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LinkSpeakerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null && player.level().isLoaded(payload.speakerPos())) {
                BlockEntity be = player.level().getBlockEntity(payload.speakerPos());
                if (be instanceof SpeakerBlockEntity speaker) {
                    speaker.setLinkedDiffuserPos(payload.diffuserPos().orElse(null));
                }
            }
        });
    }
}
