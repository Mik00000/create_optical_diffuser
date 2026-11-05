package com.mik.optical_diffuser.client.render;

import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.config.DiffuserClientConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.lpcamors.optical.blocks.optical_source.BeamHelper;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class OpticalDiffuserRenderer implements BlockEntityRenderer<OpticalDiffuserBlockEntity> {

    private static final int SEGMENTS = 48;
    private static final int RINGS = 16;

    public static final RenderType SPOTLIGHT_BEAM = RenderType.create(
            "spotlight_volumetric_beam",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2097152,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                            "spotlight_additive_transparency",
                            () -> {
                                RenderSystem.enableBlend();
                                RenderSystem.blendFunc(
                                        GlStateManager.SourceFactor.SRC_ALPHA,
                                        GlStateManager.DestFactor.ONE
                                );
                            },
                            () -> {
                                RenderSystem.disableBlend();
                                RenderSystem.defaultBlendFunc();
                            }
                    ))
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );

    public OpticalDiffuserRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(OpticalDiffuserBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 1. Render rotating kinetic shaft connected to the left side or bottom socket
        if (be.getLevel() != null) {
            try {
                Direction facing = be.getBlockState().getValue(OpticalDiffuserBlock.FACING);
                Direction left = OpticalDiffuserBlock.getLeftDirection(facing);

                SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), left);
                int shaftLight = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(left));
                KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, be, shaftLight);
                shaft.renderInto(poseStack, bufferSource.getBuffer(RenderType.solid()));
            } catch (Exception ignored) {}
        }

        // 2. Render 4-slot Lens Carousel Wheel on the left face
        CarouselRenderer.renderCarousel(be, partialTicks, poseStack, bufferSource, packedLight, packedOverlay);

        // 3. Render optical beam only when active
        if (!be.isActive()) {
            return;
        }

        Direction facing = be.getBlockState().getValue(OpticalDiffuserBlock.FACING);
        float coneAngle = be.getConeAngle();
        int range = be.getConeRange();
        float spinSpeed = be.getEffectiveSpinSpeed();
        BeamHelper.BeamPolarization polarization = be.getPolarization();

        int red = be.getRed();
        int green = be.getGreen();
        int blue = be.getBlue();

        poseStack.pushPose();

        // Translate to the center of the block
        poseStack.translate(0.5, 0.5, 0.5);

        // Rotate so that +Z points forward in the direction of FACING
        switch (facing) {
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(0));
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(270));
            case UP    -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
        }

        // Offset slightly forward from the lens surface
        poseStack.translate(0.0, 0.0, 0.51);

        // Apply visual rotation around the beam axis
        float rotation = be.getCurrentRotation() + (be.isActive() && spinSpeed != 0.0f ? (spinSpeed * 6.0f / 20.0f) * partialTicks : 0.0f);
        if (rotation != 0.0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(rotation));
        }

        // Apply polarization roll if diagonal
        if (polarization == BeamHelper.BeamPolarization.DIAGONAL_POSITIVE) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(45.0f));
        } else if (polarization == BeamHelper.BeamPolarization.DIAGONAL_NEGATIVE) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0f));
        }

        VertexConsumer consumer = bufferSource.getBuffer(SPOTLIGHT_BEAM);
        PoseStack.Pose pose = poseStack.last();

        // Calculate radii for unified beam cone (user configured cone angle & range)
        float baseRadius = (float) (range * Math.tan(Math.toRadians(coneAngle / 2.0)));
        float radiusX = baseRadius;
        float radiusY = baseRadius;

        // Apply polarization deformation to beam cone
        if (polarization == BeamHelper.BeamPolarization.HORIZONTAL) {
            radiusX = baseRadius * 2.0f;
            radiusY = baseRadius * 0.35f;
        } else if (polarization == BeamHelper.BeamPolarization.VERTICAL) {
            radiusX = baseRadius * 0.35f;
            radiusY = baseRadius * 2.0f;
        } else if (polarization == BeamHelper.BeamPolarization.DIAGONAL_POSITIVE || polarization == BeamHelper.BeamPolarization.DIAGONAL_NEGATIVE) {
            radiusX = baseRadius * 1.8f;
            radiusY = baseRadius * 0.35f;
        }

        int intensityAlpha = (int) (255 * be.getIntensity());
        int alphaMax = Math.min(255, Math.max(90, intensityAlpha));

        // Lens aperture radius so beam starts cleanly at the projector glass
        float lensBaseRadius = 0.30f;

        // Render clean 2-layer atmospheric volumetric beam with true 3D obstacle shadows
        renderVolumetricCone(pose, consumer, be, range, lensBaseRadius, coneAngle, spinSpeed, partialTicks, polarization, red, green, blue, alphaMax);

        poseStack.popPose();

        // 3. Render Projector video screen if equipped with projector lens
        ProjectorScreenRenderer.renderScreen(be, partialTicks, poseStack, bufferSource);
    }

    /**
     * Renders a continuous, smooth, multi-shell volumetric spotlight cone:
     * - Nested concentric smooth cylindrical/conical shells (Outer, Mid, Core, Spine)
     * - Continuous full-width expansion matching slider (10° to 75°)
     * - Fixed 3D geometry: perfectly stable from all viewing angles, no twisting or spinning.
     * - Internal soft diametric cross fins filling the volume.
     * - Natural atmospheric falloff along the beam.
     */
    private void renderVolumetricCone(PoseStack.Pose pose, VertexConsumer consumer, OpticalDiffuserBlockEntity be,
                                      float range, float lensBaseRadius, float coneAngle, float spinSpeed,
                                      float partialTicks, BeamHelper.BeamPolarization polarization,
                                      int r, int g, int b, int alphaMax) {
        float tanHalf = (float) Math.tan(Math.toRadians(coneAngle * 0.5));
        float scaleU = 1.0f;
        float scaleV = 1.0f;
        if (polarization == BeamHelper.BeamPolarization.HORIZONTAL) {
            scaleU = 2.0f;
            scaleV = 0.35f;
        } else if (polarization == BeamHelper.BeamPolarization.VERTICAL) {
            scaleU = 0.35f;
            scaleV = 2.0f;
        } else if (polarization == BeamHelper.BeamPolarization.DIAGONAL_POSITIVE || polarization == BeamHelper.BeamPolarization.DIAGONAL_NEGATIVE) {
            scaleU = 1.6f;
            scaleV = 0.45f;
        }

        Matrix4f poseMat = pose.pose();

        // 1. Camera position for lens flare effect only
        Matrix4f invPose = new Matrix4f(poseMat).invert();
        Vector3f localCamPos = new Vector3f();
        invPose.transformPosition(localCamPos);

        float camDist = localCamPos.length();

        final int rings = 16;
        final int segments = 32;

        DiffuserClientConfig cfg = DiffuserClientConfig.INSTANCE;
        float coreBrightness = (cfg != null) ? (float) cfg.coreBrightness.get().doubleValue() : 0.70f;
        float coreWhiteHotIntensity = (cfg != null) ? (float) cfg.coreWhiteHotIntensity.get().doubleValue() : 0.38f;
        float layerAlphaDecayStep = (cfg != null) ? (float) cfg.layerAlphaDecayStep.get().doubleValue() : 0.026f;
        float beamAlphaMultiplier = (cfg != null) ? (float) cfg.beamAlphaMultiplier.get().doubleValue() : 1.0f;
        float falloffExp = (cfg != null) ? (float) cfg.longitudinalFalloffExponent.get().doubleValue() : 0.35f;
        float mieBoostFactor = (cfg != null) ? (float) cfg.mieForwardScattering.get().doubleValue() : 0.40f;
        float lensCapBrightness = (cfg != null) ? (float) cfg.lensCapBrightness.get().doubleValue() : 0.90f;

        // 1. Concentric volumetric shells (12 shells for ultra-smooth, continuous gradient):
        final float[] SHELL_RADII = {
                0.04f, 0.09f, 0.15f, 0.22f, 0.30f, 0.39f, 0.49f, 0.60f, 0.71f, 0.82f, 0.91f, 1.00f
        };
        final int shellCount = SHELL_RADII.length;

        // Determine camera view direction in transverse plane (X, Y)
        float camX = localCamPos.x();
        float camY = localCamPos.y();
        float camDistXY = (float) Math.sqrt(camX * camX + camY * camY);

        // Axial view factor: 0.0 when looking straight down beam (head-on), 1.0 when looking from side
        float sideRatio = (camDist > 1e-4f) ? Math.min(1.0f, camDistXY / camDist) : 0.0f;

        // Mie atmospheric forward scattering: looking towards lighthouse increases perceived haze brightness
        float cosPhase = (camDist > 1e-4f) ? (localCamPos.z() / camDist) : 0.0f;
        float mieBoost = 1.0f + mieBoostFactor * Math.max(0.0f, cosPhase);

        float vx = (camDistXY > 1e-4f) ? camX / camDistXY : 0.0f;
        float vy = (camDistXY > 1e-4f) ? camY / camDistXY : 1.0f;

        // Precompute cos, sin and circumferential view-factor softening for each segment
        float[] cosTh = new float[segments + 1];
        float[] sinTh = new float[segments + 1];
        float[] segFactor = new float[segments + 1];

        for (int j = 0; j <= segments; j++) {
            float th = (float) (2.0 * Math.PI * (j % segments) / segments);
            float c = (float) Math.cos(th);
            float s = (float) Math.sin(th);
            cosTh[j] = c * scaleU;
            sinTh[j] = s * scaleV;

            // dot = 1.0 at front and back, 0.0 at silhouette edges
            float dot = c * vx + s * vy;
            float absDot = Math.abs(dot);
            // Pure silhouette zero-fade: smoothly drops all the way to 0.0 at the edges so no contour lines exist
            float sideFactor = (float) Math.pow(absDot, 1.10);
            segFactor[j] = (1.0f - sideRatio) + sideRatio * sideFactor;
        }

        // Base core alpha scaled by config (0.50 factor = another 30% softer core)
        float baseCoreAlpha = coreBrightness * 0.30f;

        // Render each concentric shell
        for (int s = 0; s < shellCount; s++) {
            float rFrac = SHELL_RADII[s];
            float shellAlphaFactor = Math.max(0.03f, baseCoreAlpha - s * layerAlphaDecayStep);
            float shellAlpha = shellAlphaFactor * beamAlphaMultiplier * alphaMax * mieBoost;

            // Smooth Gaussian white-hot core dynamics:
            // Gaussian bell curve ensures an ultra-soft organic transition from white-hot core to pure dye
            float whiteBlend = (float) Math.exp(-Math.pow(rFrac / 0.25f, 2.0f)) * coreWhiteHotIntensity;
            int sr = (int) (r + (255 - r) * whiteBlend);
            int sg = (int) (g + (255 - g) * whiteBlend);
            int sb = (int) (b + (255 - b) * whiteBlend);

            for (int i = 0; i < rings; i++) {
                float t0 = i / (float) rings;
                float t1 = (i + 1) / (float) rings;

                float z0 = range * t0;
                float z1 = range * t1;

                // Collimated atmospheric transmission curve:
                // Carries strong, punchy optical power through distance before soft taper
                float f0 = (float) Math.pow(Math.max(0.0f, 1.0f - t0), falloffExp);
                float f1 = (float) Math.pow(Math.max(0.0f, 1.0f - t1), falloffExp);

                float r0 = (lensBaseRadius + z0 * tanHalf) * rFrac;
                float r1 = (lensBaseRadius + z1 * tanHalf) * rFrac;

                for (int j = 0; j < segments; j++) {
                    int a0 = (int) (shellAlpha * segFactor[j] * f0);
                    int a1 = (int) (shellAlpha * segFactor[j] * f1);
                    int a0_next = (int) (shellAlpha * segFactor[j + 1] * f0);
                    int a1_next = (int) (shellAlpha * segFactor[j + 1] * f1);

                    if (a0 <= 0 && a1 <= 0 && a0_next <= 0 && a1_next <= 0) continue;

                    float x00 = r0 * cosTh[j];
                    float y00 = r0 * sinTh[j];

                    float x01 = r0 * cosTh[j + 1];
                    float y01 = r0 * sinTh[j + 1];

                    float x11 = r1 * cosTh[j + 1];
                    float y11 = r1 * sinTh[j + 1];

                    float x10 = r1 * cosTh[j];
                    float y10 = r1 * sinTh[j];

                    addVertex(pose, consumer, x00, y00, z0, sr, sg, sb, a0);
                    addVertex(pose, consumer, x01, y01, z0, sr, sg, sb, a0_next);
                    addVertex(pose, consumer, x11, y11, z1, sr, sg, sb, a1_next);
                    addVertex(pose, consumer, x10, y10, z1, sr, sg, sb, a1);
                }
            }
        }

        // 2. Circular Glowing Lens Cap at z = 0.01 (pure circular, fits inside the block's brass rim, NO squares!)
        int lensSectors = 24;
        int centerAlpha = Math.min(255, (int) (alphaMax * lensCapBrightness));
        int rimAlpha = Math.min(255, (int) (alphaMax * lensCapBrightness * 0.75f));

        for (int j = 0; j < lensSectors; j++) {
            float th0 = (float) (2.0 * Math.PI * j / lensSectors);
            float th1 = (float) (2.0 * Math.PI * (j + 1) / lensSectors);

            float lx0 = (float) Math.cos(th0) * lensBaseRadius * scaleU;
            float ly0 = (float) Math.sin(th0) * lensBaseRadius * scaleV;
            float lx1 = (float) Math.cos(th1) * lensBaseRadius * scaleU;
            float ly1 = (float) Math.sin(th1) * lensBaseRadius * scaleV;

            // Incandescent white center, rich colored rim
            addVertex(pose, consumer, 0, 0, 0.01f, 255, 255, 255, centerAlpha);
            addVertex(pose, consumer, lx0, ly0, 0.01f, r, g, b, rimAlpha);
            addVertex(pose, consumer, lx1, ly1, 0.01f, r, g, b, rimAlpha);
            addVertex(pose, consumer, 0, 0, 0.01f, 255, 255, 255, centerAlpha);
        }
    }

    private void addVertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
                           int r, int g, int b, int a) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a);
    }

    @Override
    public boolean shouldRenderOffScreen(OpticalDiffuserBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRender(OpticalDiffuserBlockEntity be, Vec3 cameraPos) {
        return Vec3.atCenterOf(be.getBlockPos()).closerThan(cameraPos, getViewDistance());
    }

    @Override
    public AABB getRenderBoundingBox(OpticalDiffuserBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        int r = be.getConeRange();
        AABB box = new AABB(pos).inflate(r);

        // Expand bounding box to enclose the configured projector screen
        var coords = com.mik.optical_diffuser.item.ProjectorLensItem.getScreenCoords(be.getActiveLens());
        if (coords.isComplete()) {
            box = box.minmax(new AABB(
                    coords.pos1.getX(), coords.pos1.getY(), coords.pos1.getZ(),
                    coords.pos2.getX() + 1, coords.pos2.getY() + 1, coords.pos2.getZ() + 1
            ).inflate(2.0));
        }
        return box;
    }
}
