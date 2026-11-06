package com.mik.optical_diffuser.client.render;

import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public class CarouselRenderer {

    public static final ResourceLocation CAROUSEL_WHEEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            com.mik.optical_diffuser.OpticalDiffuserMod.MOD_ID,
            "textures/block/carousel_wheel.png"
    );

    public static final ResourceLocation ACTIVE_FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            com.mik.optical_diffuser.OpticalDiffuserMod.MOD_ID,
            "textures/misc/active_frame.png"
    );

    public static void renderCarousel(OpticalDiffuserBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;

        Direction facing = be.getBlockState().getValue(OpticalDiffuserBlock.FACING);
        Direction left = OpticalDiffuserBlock.getLeftDirection(facing);

        poseStack.pushPose();

        // Translate to the center of the block
        poseStack.translate(0.5, 0.5, 0.5);

        // Align local coordinate system so that +Z points outward from the left face
        switch (left) {
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(0));
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(270));
            case UP    -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
        }

        // Move to the outer surface of the left face
        poseStack.translate(0.0, 0.0, 0.505);

        // Carousel wheel angle: rotated smoothly by shaft on left face or in 90-degree steps via Wrench
        float shaftSpeed = be.getSpeed();
        float angle = be.getCarouselAngle() + (shaftSpeed != 0.0f ? (shaftSpeed * 6.0f / 20.0f) * partialTicks : 0.0f);

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(-angle));

        // 1. Render Carousel Wheel Base Plate (radius 0.44 blocks)
        VertexConsumer wheelConsumer = bufferSource.getBuffer(RenderType.entityCutout(CAROUSEL_WHEEL_TEXTURE));
        Matrix4f wheelMat = poseStack.last().pose();
        float r = 0.44f;

        wheelConsumer.addVertex(wheelMat, -r, -r, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        wheelConsumer.addVertex(wheelMat,  r, -r, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        wheelConsumer.addVertex(wheelMat,  r,  r, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        wheelConsumer.addVertex(wheelMat, -r,  r, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);

        // 2. Render 4 Lens items mounted in the 4 carousel slots
        ItemStack[] lenses = be.getCarouselLenses();
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        float socketRadius = 0.26f;

        // Slot 0: Top (0 deg)
        // Slot 1: Front (90 deg)
        // Slot 2: Bottom (180 deg)
        // Slot 3: Back (270 deg)
        float[] slotAngles = new float[]{0.0f, 90.0f, 180.0f, 270.0f};

        for (int i = 0; i < 4; i++) {
            ItemStack lens = lenses[i];
            if (lens.isEmpty()) continue;

            poseStack.pushPose();
            float th = (float) Math.toRadians(slotAngles[i]);
            float sx = (float) Math.sin(th) * socketRadius;
            float sy = (float) Math.cos(th) * socketRadius;

            poseStack.translate(sx, sy, 0.015);
            poseStack.scale(0.24f, 0.24f, 0.24f);

            itemRenderer.renderStatic(
                    lens,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    bufferSource,
                    be.getLevel(),
                    0
            );
            poseStack.popPose();
        }

        poseStack.popPose(); // Restore from wheel rotation

        // 3. Render glowing active indicator frame at the Front position (+X in local wheel space = towards FACING)
        // The front position is fixed at (x = +socketRadius, y = 0) relative to the left face!
        VertexConsumer frameConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(ACTIVE_FRAME_TEXTURE));
        Matrix4f frameMat = poseStack.last().pose();

        float fx = socketRadius;
        float fy = 0.0f;
        float fr = 0.09f;

        frameConsumer.addVertex(frameMat, fx - fr, fy - fr, 0.025f).setColor(255, 255, 255, 240).setUv(0.0f, 1.0f).setOverlay(packedOverlay).setLight(15728880).setNormal(0, 0, 1);
        frameConsumer.addVertex(frameMat, fx + fr, fy - fr, 0.025f).setColor(255, 255, 255, 240).setUv(1.0f, 1.0f).setOverlay(packedOverlay).setLight(15728880).setNormal(0, 0, 1);
        frameConsumer.addVertex(frameMat, fx + fr, fy + fr, 0.025f).setColor(255, 255, 255, 240).setUv(1.0f, 0.0f).setOverlay(packedOverlay).setLight(15728880).setNormal(0, 0, 1);
        frameConsumer.addVertex(frameMat, fx - fr, fy + fr, 0.025f).setColor(255, 255, 255, 240).setUv(0.0f, 0.0f).setOverlay(packedOverlay).setLight(15728880).setNormal(0, 0, 1);

        poseStack.popPose();
    }
}

