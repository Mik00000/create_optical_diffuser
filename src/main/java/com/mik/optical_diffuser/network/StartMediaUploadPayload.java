package com.mik.optical_diffuser.network;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.server.media.ServerUploadManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

public record StartMediaUploadPayload(
        String fileName,
        long fileSize,
        Optional<BlockPos> pos,
        Optional<InteractionHand> hand,
        String clientHost
) implements CustomPacketPayload {

    public static final Type<StartMediaUploadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "start_media_upload")
    );

    public static final StreamCodec<ByteBuf, StartMediaUploadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, StartMediaUploadPayload::fileName,
            ByteBufCodecs.VAR_LONG, StartMediaUploadPayload::fileSize,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), StartMediaUploadPayload::pos,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT.map(i -> InteractionHand.values()[i], InteractionHand::ordinal)), StartMediaUploadPayload::hand,
            ByteBufCodecs.STRING_UTF8, StartMediaUploadPayload::clientHost,
            StartMediaUploadPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StartMediaUploadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                try {
                    UUID uploadId = ServerUploadManager.startSession(
                            player,
                            payload.fileName(),
                            payload.fileSize(),
                            payload.pos(),
                            payload.hand(),
                            payload.clientHost()
                    );
                    PacketDistributor.sendToPlayer(player, new MediaUploadStatusPayload(
                            uploadId, 0, 0, "Approved"
                    ));
                } catch (Throwable t) {
                    OpticalDiffuserMod.LOGGER.warn("Upload start rejected for {}: {}", player.getName().getString(), t.getMessage());
                    PacketDistributor.sendToPlayer(player, new MediaUploadStatusPayload(
                            new UUID(0, 0), 3, 0, t.getMessage() != null ? t.getMessage() : "Upload rejected"
                    ));
                }
            }
        });
    }
}
