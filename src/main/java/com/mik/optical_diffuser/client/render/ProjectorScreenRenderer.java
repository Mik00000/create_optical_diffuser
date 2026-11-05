package com.mik.optical_diffuser.client.render;

import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.client.media.DiffuserMediaManager;
import com.mik.optical_diffuser.item.ProjectorLensItem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

public class ProjectorScreenRenderer {

    private static final Random RANDOM = new Random();

    public static void renderScreen(OpticalDiffuserBlockEntity be, float partialTicks, PoseStack poseStack) {
        OpticalDiffuserBlockEntity.DiffuserModule mod = be.getActiveModule();
        boolean isProjector = (mod == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SILENT
                || mod == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SOUND);

        if (!isProjector || !be.isActive()) {
            return;
        }

        ProjectorLensItem.ScreenCoords coords = ProjectorLensItem.getScreenCoords(be.getInstalledModuleItem());
        if (!coords.isComplete()) {
            return;
        }

        DiffuserMediaManager media = DiffuserMediaManager.get(be);
        media.tick();
        int textureId = media.getTexture();
        if (textureId <= 0) {
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

        // Projector lamp flicker effect (vintage shutter flicker ~18-24 FPS)
        long gameTime = be.getLevel().getGameTime();
        float flicker = 0.94f + (float) Math.sin(gameTime * 1.8f) * 0.06f + (RANDOM.nextFloat() - 0.5f) * 0.04f;

        boolean isSilent = (mod == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SILENT);
        float r = isSilent ? 0.92f * flicker : 1.0f * flicker;
        float g = isSilent ? 0.92f * flicker : 0.98f * flicker;
        float b = isSilent ? 0.88f * flicker : 0.92f * flicker;
        float a = 1.0f;

        // Render textured quad directly to OpenGL buffer
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, textureId);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        Matrix4f mat = poseStack.last().pose();

        buffer.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).setUv(0.0f, 1.0f).setColor(r, g, b, a);
        buffer.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).setUv(1.0f, 1.0f).setColor(r, g, b, a);
        buffer.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).setUv(1.0f, 0.0f).setColor(r, g, b, a);
        buffer.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).setUv(0.0f, 0.0f).setColor(r, g, b, a);

        // Also render back-face so screen is double-sided or seen from both sides
        buffer.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).setUv(0.0f, 0.0f).setColor(r, g, b, a);
        buffer.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).setUv(1.0f, 0.0f).setColor(r, g, b, a);
        buffer.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).setUv(1.0f, 1.0f).setColor(r, g, b, a);
        buffer.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).setUv(0.0f, 1.0f).setColor(r, g, b, a);

        com.mojang.blaze3d.vertex.MeshData mesh = buffer.build();
        if (mesh != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }

        // Render subtle dust / film grain overlay
        renderFilmGrainOverlay(mat, v0, v1, v2, v3, gameTime);

        RenderSystem.disableBlend();
    }

    private static void renderFilmGrainOverlay(Matrix4f mat, Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, long gameTime) {
        if (RANDOM.nextFloat() > 0.4f) return; // random grain pulses

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float grainAlpha = 0.05f + RANDOM.nextFloat() * 0.06f;

        buffer.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).setColor(1.0f, 1.0f, 1.0f, grainAlpha);
        buffer.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).setColor(1.0f, 1.0f, 1.0f, grainAlpha);
        buffer.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).setColor(1.0f, 1.0f, 1.0f, grainAlpha);
        buffer.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).setColor(1.0f, 1.0f, 1.0f, grainAlpha);

        com.mojang.blaze3d.vertex.MeshData mesh = buffer.build();
        if (mesh != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.defaultBlendFunc();
    }
}
