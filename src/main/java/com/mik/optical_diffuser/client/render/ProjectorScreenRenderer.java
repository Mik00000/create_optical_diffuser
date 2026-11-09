package com.mik.optical_diffuser.client.render;

import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.client.media.DiffuserMediaManager;
import com.mik.optical_diffuser.compat.SableCompat;
import com.mik.optical_diffuser.item.ProjectorLensItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
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

        ItemStack activeLens = be.getActiveLens();

        // 4 corner vertices in local screen plane
        Vec3 corner0, corner1, corner2, corner3;

        if (isXPlane) {
            // Facing along X axis
            double planeX = minX;
            double midY = (minY + maxY) * 0.5;
            double midZ = (minZ + maxZ) * 0.5;
            Vec3 testP = SableCompat.transformScreenPointToDiffuserLocal(be, activeLens, new Vec3(planeX, midY, midZ));
            if (testP.x < 0) {
                // Diffuser is in front of the screen facing +X
                planeX = maxX + 0.02;
                corner0 = new Vec3(planeX, minY, maxZ);
                corner1 = new Vec3(planeX, minY, minZ);
                corner2 = new Vec3(planeX, maxY, minZ);
                corner3 = new Vec3(planeX, maxY, maxZ);
            } else {
                // Diffuser is in front of the screen facing -X
                planeX = minX - 0.02;
                corner0 = new Vec3(planeX, minY, minZ);
                corner1 = new Vec3(planeX, minY, maxZ);
                corner2 = new Vec3(planeX, maxY, maxZ);
                corner3 = new Vec3(planeX, maxY, minZ);
            }
        } else if (isZPlane) {
            // Facing along Z axis
            double planeZ = minZ;
            double midX = (minX + maxX) * 0.5;
            double midY = (minY + maxY) * 0.5;
            Vec3 testP = SableCompat.transformScreenPointToDiffuserLocal(be, activeLens, new Vec3(midX, midY, planeZ));
            if (testP.z < 0) {
                planeZ = maxZ + 0.02; // screen facing +Z
                corner0 = new Vec3(minX, minY, planeZ);
                corner1 = new Vec3(maxX, minY, planeZ);
                corner2 = new Vec3(maxX, maxY, planeZ);
                corner3 = new Vec3(minX, maxY, planeZ);
            } else {
                planeZ = minZ - 0.02; // screen facing -Z
                corner0 = new Vec3(maxX, minY, planeZ);
                corner1 = new Vec3(minX, minY, planeZ);
                corner2 = new Vec3(minX, maxY, planeZ);
                corner3 = new Vec3(maxX, maxY, planeZ);
            }
        } else {
            // Horizontal floor/ceiling screen
            double planeY = minY;
            double midX = (minX + maxX) * 0.5;
            double midZ = (minZ + maxZ) * 0.5;
            Vec3 testP = SableCompat.transformScreenPointToDiffuserLocal(be, activeLens, new Vec3(midX, planeY, midZ));
            if (testP.y < 0) {
                planeY = maxY + 0.02;
                corner0 = new Vec3(minX, planeY, minZ);
                corner1 = new Vec3(maxX, planeY, minZ);
                corner2 = new Vec3(maxX, planeY, maxZ);
                corner3 = new Vec3(minX, planeY, maxZ);
            } else {
                planeY = minY - 0.02;
                corner0 = new Vec3(minX, planeY, maxZ);
                corner1 = new Vec3(maxX, planeY, maxZ);
                corner2 = new Vec3(maxX, planeY, minZ);
                corner3 = new Vec3(minX, planeY, minZ);
            }
        }

        // Project every corner vertex through Sable 3D space into the local pose of the diffuser renderer:
        Vec3 v0 = SableCompat.transformScreenPointToDiffuserLocal(be, activeLens, corner0);
        Vec3 v1 = SableCompat.transformScreenPointToDiffuserLocal(be, activeLens, corner1);
        Vec3 v2 = SableCompat.transformScreenPointToDiffuserLocal(be, activeLens, corner2);
        Vec3 v3 = SableCompat.transformScreenPointToDiffuserLocal(be, activeLens, corner3);

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

        // Video aspect ratio calculation & Letterboxing / Pillarboxing:
        float videoAspect = 16.0f / 9.0f; // Default standard aspect ratio
        if (media != null && media.getPlayer() != null) {
            org.watermedia.api.player.MediaPlayer mp = media.getPlayer();
            int vw = mp.width();
            int vh = mp.height();
            if (vw > 0 && vh > 0) {
                videoAspect = (float) vw / (float) vh;
            }
        }

        Vec3 horizVec = v1.subtract(v0);
        Vec3 vertVec = v3.subtract(v0);
        double fullW = horizVec.length();
        double fullH = vertVec.length();
        double screenAspect = fullW / Math.max(0.001, fullH);

        double renderW = fullW;
        double renderH = fullH;
        double marginW = 0.0;
        double marginH = 0.0;

        if (screenAspect > videoAspect) {
            // Screen is wider than video -> Pillarbox (bars on left & right)
            renderW = fullH * videoAspect;
            marginW = (fullW - renderW) / 2.0;
        } else if (screenAspect < videoAspect) {
            // Screen is taller than video -> Letterbox (bars on top & bottom)
            renderH = fullW / videoAspect;
            marginH = (fullH - renderH) / 2.0;
        }

        Vec3 uH = horizVec.normalize();
        Vec3 uV = vertVec.normalize();
        Vec3 norm = uH.cross(uV).normalize();

        // Render black cinema matte on the full background quad if letterboxed/pillarboxed:
        if (marginW > 0.02 || marginH > 0.02) {
            // Front matte:
            vertexConsumer.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z)
                    .setColor(0.04f, 0.04f, 0.04f, 0.95f)
                    .setUv(0.0f, 0.0f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal((float) norm.x, (float) norm.y, (float) norm.z);

            vertexConsumer.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z)
                    .setColor(0.04f, 0.04f, 0.04f, 0.95f)
                    .setUv(0.0f, 0.0f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal((float) norm.x, (float) norm.y, (float) norm.z);

            vertexConsumer.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z)
                    .setColor(0.04f, 0.04f, 0.04f, 0.95f)
                    .setUv(0.0f, 0.0f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal((float) norm.x, (float) norm.y, (float) norm.z);

            vertexConsumer.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z)
                    .setColor(0.04f, 0.04f, 0.04f, 0.95f)
                    .setUv(0.0f, 0.0f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal((float) norm.x, (float) norm.y, (float) norm.z);

            // Back matte:
            vertexConsumer.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z)
                    .setColor(0.04f, 0.04f, 0.04f, 0.95f)
                    .setUv(0.0f, 0.0f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal((float) -norm.x, (float) -norm.y, (float) -norm.z);

            vertexConsumer.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z)
                    .setColor(0.04f, 0.04f, 0.04f, 0.95f)
                    .setUv(0.0f, 0.0f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal((float) -norm.x, (float) -norm.y, (float) -norm.z);

            vertexConsumer.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z)
                    .setColor(0.04f, 0.04f, 0.04f, 0.95f)
                    .setUv(0.0f, 0.0f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal((float) -norm.x, (float) -norm.y, (float) -norm.z);

            vertexConsumer.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z)
                    .setColor(0.04f, 0.04f, 0.04f, 0.95f)
                    .setUv(0.0f, 0.0f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal((float) -norm.x, (float) -norm.y, (float) -norm.z);
        }

        // Active video quad, centered with preserved aspect ratio
        Vec3 fwdOffset = norm.scale(0.001);
        Vec3 vidV0 = v0.add(uH.scale(marginW)).add(uV.scale(marginH)).add(fwdOffset);
        Vec3 vidV1 = vidV0.add(uH.scale(renderW));
        Vec3 vidV2 = vidV1.add(uV.scale(renderH));
        Vec3 vidV3 = vidV0.add(uV.scale(renderH));

        vertexConsumer.addVertex(mat, (float) vidV0.x, (float) vidV0.y, (float) vidV0.z)
                .setColor(r, g, b, a)
                .setUv(0.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880) // full bright
                .setNormal((float) norm.x, (float) norm.y, (float) norm.z);

        vertexConsumer.addVertex(mat, (float) vidV1.x, (float) vidV1.y, (float) vidV1.z)
                .setColor(r, g, b, a)
                .setUv(1.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal((float) norm.x, (float) norm.y, (float) norm.z);

        vertexConsumer.addVertex(mat, (float) vidV2.x, (float) vidV2.y, (float) vidV2.z)
                .setColor(r, g, b, a)
                .setUv(1.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal((float) norm.x, (float) norm.y, (float) norm.z);

        vertexConsumer.addVertex(mat, (float) vidV3.x, (float) vidV3.y, (float) vidV3.z)
                .setColor(r, g, b, a)
                .setUv(0.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal((float) norm.x, (float) norm.y, (float) norm.z);

        // Also render reverse face so screen is visible from both sides
        Vec3 revOffset = norm.scale(-0.002);
        Vec3 rVidV0 = vidV0.add(revOffset);
        Vec3 rVidV1 = vidV1.add(revOffset);
        Vec3 rVidV2 = vidV2.add(revOffset);
        Vec3 rVidV3 = vidV3.add(revOffset);

        vertexConsumer.addVertex(mat, (float) rVidV3.x, (float) rVidV3.y, (float) rVidV3.z)
                .setColor(r, g, b, a)
                .setUv(0.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal((float) -norm.x, (float) -norm.y, (float) -norm.z);

        vertexConsumer.addVertex(mat, (float) rVidV2.x, (float) rVidV2.y, (float) rVidV2.z)
                .setColor(r, g, b, a)
                .setUv(1.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal((float) -norm.x, (float) -norm.y, (float) -norm.z);

        vertexConsumer.addVertex(mat, (float) rVidV1.x, (float) rVidV1.y, (float) rVidV1.z)
                .setColor(r, g, b, a)
                .setUv(1.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal((float) -norm.x, (float) -norm.y, (float) -norm.z);

        vertexConsumer.addVertex(mat, (float) rVidV0.x, (float) rVidV0.y, (float) rVidV0.z)
                .setColor(r, g, b, a)
                .setUv(0.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal((float) -norm.x, (float) -norm.y, (float) -norm.z);
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
