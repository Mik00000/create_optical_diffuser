package com.mik.optical_diffuser.network;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.client.gui.FilmReelScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record MediaUploadStatusPayload(
        UUID uploadId,
        int statusCode,
        int progressPercent,
        String messageOrUrl
) implements CustomPacketPayload {

    public static final Type<MediaUploadStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "media_upload_status")
    );

    public static final StreamCodec<ByteBuf, MediaUploadStatusPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, MediaUploadStatusPayload::uploadId,
            ByteBufCodecs.VAR_INT, MediaUploadStatusPayload::statusCode,
            ByteBufCodecs.VAR_INT, MediaUploadStatusPayload::progressPercent,
            ByteBufCodecs.STRING_UTF8, MediaUploadStatusPayload::messageOrUrl,
            MediaUploadStatusPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MediaUploadStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            FilmReelScreen.handleUploadStatus(
                    payload.uploadId(),
                    payload.statusCode(),
                    payload.progressPercent(),
                    payload.messageOrUrl()
            );
        });
    }
}

