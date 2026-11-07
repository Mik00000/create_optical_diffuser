package com.mik.optical_diffuser.network;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateDiffuserPayload(
        BlockPos pos,
        float coneAngle,
        float spinSpeed,
        int redstoneMode
) implements CustomPacketPayload {

    public static final Type<UpdateDiffuserPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "update_diffuser")
    );

    public static final StreamCodec<ByteBuf, UpdateDiffuserPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateDiffuserPayload::pos,
            ByteBufCodecs.FLOAT, UpdateDiffuserPayload::coneAngle,
            ByteBufCodecs.FLOAT, UpdateDiffuserPayload::spinSpeed,
            ByteBufCodecs.VAR_INT, UpdateDiffuserPayload::redstoneMode,
            UpdateDiffuserPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateDiffuserPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            Level level = player.level();
            if (level.isLoaded(payload.pos()) && player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) < 64.0) {
                BlockEntity be = level.getBlockEntity(payload.pos());
                if (be instanceof OpticalDiffuserBlockEntity diffuser) {
                    float angle = Math.max(5.0f, Math.min(90.0f, payload.coneAngle()));
                    float speed = Math.max(-256.0f, Math.min(256.0f, payload.spinSpeed()));
                    int mode = Math.max(0, Math.min(2, payload.redstoneMode()));
                    diffuser.updateSettings(
                            angle,
                            speed,
                            mode
                    );
                }
            }
        });
    }
}
