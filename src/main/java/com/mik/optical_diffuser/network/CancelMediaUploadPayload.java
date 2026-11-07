package com.mik.optical_diffuser.network;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.server.media.ServerUploadManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record CancelMediaUploadPayload(UUID uploadId) implements CustomPacketPayload {

    public static final Type<CancelMediaUploadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "cancel_media_upload")
    );

    public static final StreamCodec<ByteBuf, CancelMediaUploadPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CancelMediaUploadPayload::uploadId,
            CancelMediaUploadPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CancelMediaUploadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerUploadManager.cancelSession(payload.uploadId());
        });
    }
}

