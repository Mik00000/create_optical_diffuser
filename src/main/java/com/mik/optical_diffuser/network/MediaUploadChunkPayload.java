package com.mik.optical_diffuser.network;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.server.media.ServerUploadManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record MediaUploadChunkPayload(
        UUID uploadId,
        int chunkIndex,
        byte[] data
) implements CustomPacketPayload {

    public static final Type<MediaUploadChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "media_upload_chunk")
    );

    public static final StreamCodec<ByteBuf, MediaUploadChunkPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, MediaUploadChunkPayload::uploadId,
            ByteBufCodecs.VAR_INT, MediaUploadChunkPayload::chunkIndex,
            ByteBufCodecs.BYTE_ARRAY, MediaUploadChunkPayload::data,
            MediaUploadChunkPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MediaUploadChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                try {
                    ServerUploadManager.writeChunk(player, payload.uploadId(), payload.chunkIndex(), payload.data());
                } catch (Throwable t) {
                    OpticalDiffuserMod.LOGGER.error("Failed writing upload chunk for {}: {}", player.getName().getString(), t.getMessage());
                    PacketDistributor.sendToPlayer(player, new MediaUploadStatusPayload(
                            payload.uploadId(), 3, 0, t.getMessage() != null ? t.getMessage() : "Error writing chunk"
                    ));
                }
            }
        });
    }
}

