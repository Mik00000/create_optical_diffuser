package com.mik.optical_diffuser.client.render;

import com.mik.optical_diffuser.block.FilmReelBlock;
import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import com.mik.optical_diffuser.block.entity.FilmReelBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class FilmReelRenderer implements BlockEntityRenderer<FilmReelBlockEntity> {

    public static final ResourceLocation REEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            com.mik.optical_diffuser.OpticalDiffuserMod.MOD_ID,
            "textures/item/film_reel.png"
    );

    public FilmReelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FilmReelBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;

        Direction facing = be.getBlockState().getValue(FilmReelBlock.FACING);
        Direction outerFace = OpticalDiffuserBlock.getRightDirection(facing);

        // 1. Render rotating kinetic shaft half connected on the outer face
        try {
            SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), outerFace);
            int shaftLight = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(outerFace));
            KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, be, shaftLight);
            shaft.renderInto(poseStack, bufferSource.getBuffer(RenderType.solid()));
        } catch (Exception ignored) {}

        // 2. Render spinning film spool
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        switch (outerFace) {
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(0));
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(270));
            case UP    -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
        }

        // Slight offset from center towards outer face
        poseStack.translate(0.0, 0.0, 0.1);

        float speed = be.getSpeed();
        float angle = be.getVisualAngle() + (speed != 0.0f ? (speed * 6.0f / 20.0f) * partialTicks : 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-angle));

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(REEL_TEXTURE));
        Matrix4f mat = poseStack.last().pose();

        float s = 0.45f;
        // Front face of reel
        consumer.addVertex(mat, -s, -s, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(mat,  s, -s, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(mat,  s,  s, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(mat, -s,  s, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, 1.0f);

        // Back face of reel
        consumer.addVertex(mat, -s,  s, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
        consumer.addVertex(mat,  s,  s, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
        consumer.addVertex(mat,  s, -s, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
        consumer.addVertex(mat, -s, -s, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);

        poseStack.popPose();
    }
}

