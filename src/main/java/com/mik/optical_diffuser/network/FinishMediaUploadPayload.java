package com.mik.optical_diffuser.network;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.server.media.ServerUploadManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record FinishMediaUploadPayload(UUID uploadId) implements CustomPacketPayload {

    public static final Type<FinishMediaUploadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "finish_media_upload")
    );

    public static final StreamCodec<ByteBuf, FinishMediaUploadPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, FinishMediaUploadPayload::uploadId,
            FinishMediaUploadPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FinishMediaUploadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                try {
                    String finalUrl = ServerUploadManager.finishSession(player, payload.uploadId());
                    PacketDistributor.sendToPlayer(player, new MediaUploadStatusPayload(
                            payload.uploadId(), 2, 100, finalUrl
                    ));
                } catch (Throwable t) {
                    OpticalDiffuserMod.LOGGER.error("Failed finishing media upload for {}: {}", player.getName().getString(), t.getMessage());
                    PacketDistributor.sendToPlayer(player, new MediaUploadStatusPayload(
                            payload.uploadId(), 3, 0, t.getMessage() != null ? t.getMessage() : "Error finishing upload"
                    ));
                }
            }
        });
    }
}
