package com.mik.optical_diffuser.client.render;

import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.client.media.DiffuserMediaManager;
import com.mik.optical_diffuser.item.ProjectorLensItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

public class ProjectorScreenRenderer {

    private static final Random RANDOM = new Random();

    public static void renderScreen(OpticalDiffuserBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource) {
        OpticalDiffuserBlockEntity.DiffuserModule mod = be.getActiveModule();
        boolean isProjector = (mod == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SILENT
                || mod == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SOUND);

        if (!isProjector || !be.isActive()) {
            return;
        }

        ProjectorLensItem.ScreenCoords coords = ProjectorLensItem.getScreenCoords(be.getActiveLens());
        if (!coords.isComplete()) {
            return;
        }

        DiffuserMediaManager media = DiffuserMediaManager.get(be);
        net.minecraft.resources.ResourceLocation texLoc = media.getTextureLocation();
        if (texLoc == null) {
            return;
        }

        BlockPos p1 = coords.pos1;
        BlockPos p2 = coords.pos2;

        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX()) + 1;
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY()) + 1;
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ()) + 1;

        // Determine orientation plane
        boolean isXPlane = (maxX - minX == 1);
        boolean isZPlane = (maxZ - minZ == 1);
        boolean isYPlane = (maxY - minY == 1);

        BlockPos diffuserPos = be.getBlockPos();

        // 4 corner vertices in local block coordinates (relative to diffuser)
        Vec3 v0, v1, v2, v3;

        if (isXPlane) {
            // Facing along X axis
            double planeX = minX;
            if (diffuserPos.getX() > planeX) {
                planeX = maxX + 0.02; // screen facing +X
                v0 = new Vec3(planeX, minY, maxZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v1 = new Vec3(planeX, minY, minZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v2 = new Vec3(planeX, maxY, minZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v3 = new Vec3(planeX, maxY, maxZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
            } else {
                planeX = minX - 0.02; // screen facing -X
                v0 = new Vec3(planeX, minY, minZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v1 = new Vec3(planeX, minY, maxZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v2 = new Vec3(planeX, maxY, maxZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v3 = new Vec3(planeX, maxY, minZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
            }
        } else if (isZPlane) {
            // Facing along Z axis
            double planeZ = minZ;
            if (diffuserPos.getZ() > planeZ) {
                planeZ = maxZ + 0.02; // screen facing +Z
                v0 = new Vec3(minX, minY, planeZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v1 = new Vec3(maxX, minY, planeZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v2 = new Vec3(maxX, maxY, planeZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v3 = new Vec3(minX, maxY, planeZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
            } else {
                planeZ = minZ - 0.02; // screen facing -Z
                v0 = new Vec3(maxX, minY, planeZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v1 = new Vec3(minX, minY, planeZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v2 = new Vec3(minX, maxY, planeZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v3 = new Vec3(maxX, maxY, planeZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
            }
        } else {
            // Horizontal floor/ceiling screen
            double planeY = minY;
            if (diffuserPos.getY() > planeY) {
                planeY = maxY + 0.02;
                v0 = new Vec3(minX, planeY, minZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v1 = new Vec3(maxX, planeY, minZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v2 = new Vec3(maxX, planeY, maxZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v3 = new Vec3(minX, planeY, maxZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
            } else {
                planeY = minY - 0.02;
                v0 = new Vec3(minX, planeY, maxZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v1 = new Vec3(maxX, planeY, maxZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v2 = new Vec3(maxX, planeY, minZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
                v3 = new Vec3(minX, planeY, minZ).subtract(Vec3.atLowerCornerOf(diffuserPos));
            }
        }

        boolean isSilent = (mod == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SILENT);

        // Authentic vintage cinema shutter flicker (gentle 0.95..1.0 range, warm vintage tone)
        long gameTime = be.getLevel().getGameTime();
        float flicker = 0.96f + (float) Math.sin(gameTime * 1.8f) * 0.03f + (RANDOM.nextFloat() - 0.5f) * 0.02f;

        float r = isSilent ? 0.94f * flicker : 1.0f * flicker;
        float g = isSilent ? 0.94f * flicker : 0.98f * flicker;
        float b = isSilent ? 0.90f * flicker : 0.95f * flicker;
        float a = 1.0f;

        boolean isIrisActive = isShaderPackActive();
        RenderType renderType;
        if (isSilent) {
            renderType = isIrisActive ? RenderType.entityTranslucentCull(texLoc) : ModShaders.getProjectorMonochrome(texLoc);
        } else {
            // Raw translucent entity - no custom shaders or color filters
            renderType = RenderType.entityTranslucentCull(texLoc);
        }
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

        Matrix4f mat = poseStack.last().pose();

        vertexConsumer.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z)
                .setColor(r, g, b, a)
                .setUv(0.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880) // full bright
                .setNormal(0.0f, 1.0f, 0.0f);

        vertexConsumer.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z)
                .setColor(r, g, b, a)
                .setUv(1.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0.0f, 1.0f, 0.0f);

        vertexConsumer.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z)
                .setColor(r, g, b, a)
                .setUv(1.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0.0f, 1.0f, 0.0f);

        vertexConsumer.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z)
                .setColor(r, g, b, a)
                .setUv(0.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0.0f, 1.0f, 0.0f);

        // Also render reverse face so screen is visible from both sides
        vertexConsumer.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z)
                .setColor(r, g, b, a)
                .setUv(0.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0.0f, -1.0f, 0.0f);

        vertexConsumer.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z)
                .setColor(r, g, b, a)
                .setUv(1.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0.0f, -1.0f, 0.0f);

        vertexConsumer.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z)
                .setColor(r, g, b, a)
                .setUv(1.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0.0f, -1.0f, 0.0f);

        vertexConsumer.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z)
                .setColor(r, g, b, a)
                .setUv(0.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0.0f, -1.0f, 0.0f);
    }

    public static boolean isShaderPackActive() {
        try {
            Class<?> clazz = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = clazz.getMethod("getInstance").invoke(null);
            return (boolean) clazz.getMethod("isShaderPackInUse").invoke(instance);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
