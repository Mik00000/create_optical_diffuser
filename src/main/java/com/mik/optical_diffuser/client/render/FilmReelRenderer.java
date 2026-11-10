package com.mik.optical_diffuser.client.render;

import com.mik.optical_diffuser.block.FilmReelBlock;
import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import com.mik.optical_diffuser.block.entity.FilmReelBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class FilmReelRenderer implements BlockEntityRenderer<FilmReelBlockEntity> {

    public FilmReelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FilmReelBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;

        Direction facing = be.getBlockState().getValue(FilmReelBlock.FACING);
        Direction outerFace = OpticalDiffuserBlock.getRightDirection(facing);
        Direction.Axis axis = outerFace.getAxis();

        // 1. Render rotating continuous kinetic shaft through entire block into diffuser
        try {
            BlockState shaftState = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
            SuperByteBuffer shaft = CachedBuffers.block(shaftState);
            KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, be, packedLight);
            shaft.renderInto(poseStack, bufferSource.getBuffer(RenderType.solid()));
        } catch (Exception ignored) {}

        // 2. Render spinning 3D Film Reel Spool model pre-aligned to block facing and locked to kinetic shaft
        try {
            float yRot = switch (facing) {
                case NORTH -> 0f;
                case EAST -> 270f;
                case SOUTH -> 180f;
                case WEST -> 90f;
                default -> 0f;
            };
            SuperByteBuffer spool = CachedBuffers.partialDirectional(
                    ModPartialModels.FILM_REEL_SPOOL,
                    be.getBlockState(),
                    facing,
                    () -> {
                        PoseStack stack = new PoseStack();
                        if (yRot != 0f) {
                            TransformStack.of(stack).center().rotateYDegrees(yRot).uncenter();
                        }
                        return stack;
                    }
            );
            KineticBlockEntityRenderer.standardKineticRotationTransform(spool, be, packedLight);
            spool.renderInto(poseStack, bufferSource.getBuffer(RenderType.cutoutMipped()));
        } catch (Exception ignored) {}
    }
}
