package com.mik.optical_diffuser.client;

import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.compat.SableCompat;
import net.lpcamors.optical.blocks.optical_source.BeamHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.level.LevelEvent;
import com.mik.optical_diffuser.client.media.DiffuserMediaManager;

public class DiffuserClientEffects {

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAllClientCaches();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() != null && event.getLevel().isClientSide()) {
            clearAllClientCaches();
        }
    }

    private static void clearAllClientCaches() {
        DiffuserMediaManager.releaseAll();
        com.mik.optical_diffuser.client.media.ClientSpeakerRegistry.clear();
        com.mik.optical_diffuser.client.render.HologramRenderer.clearCache();
        com.mik.optical_diffuser.compat.SableCompat.clearCache();
        DiffuserSelectionTracker.clear();
        com.mik.optical_diffuser.ponder.PonderSoundHelper.stopMuffledCaramelldansen();
    }

    @SubscribeEvent
    public void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Level level = mc.level;
        net.minecraft.util.RandomSource rnd = level.random;

        for (OpticalDiffuserBlockEntity be : OpticalDiffuserBlockEntity.CLIENT_INSTANCES) {
            if (be == null || be.isRemoved() || !be.isActive() || be.getLevel() != level) continue;

            OpticalDiffuserBlockEntity.DiffuserModule mod = be.getActiveModule();

            if (mod == OpticalDiffuserBlockEntity.DiffuserModule.HOLOGRAM) {
                if (!be.isLightEnabled()) continue;
                Direction facing = be.getBlockState().getValue(OpticalDiffuserBlock.FACING);
                SableCompat.RayContext rayCtx = SableCompat.getRayContext(level, be.getBlockPos(), facing);
                int distance = com.mik.optical_diffuser.item.HologramLensItem.getFocalDistance(be.getActiveLens());
                Vec3 holoPos = rayCtx.start().add(rayCtx.direction().normalize().scale(distance));

                // Spawn cyan/amethyst holographic particles around the hologram focus
                if (rnd.nextInt(2) == 0) {
                    double theta = rnd.nextDouble() * Math.PI * 2.0;
                    double radius = 0.4 + rnd.nextDouble() * 0.6;
                    double px = holoPos.x + Math.cos(theta) * radius;
                    double py = holoPos.y + (rnd.nextDouble() - 0.5) * 0.8;
                    double pz = holoPos.z + Math.sin(theta) * radius;

                    double vx = -Math.sin(theta) * 0.03;
                    double vy = 0.01 + rnd.nextDouble() * 0.02;
                    double vz = Math.cos(theta) * 0.03;

                    level.addParticle(
                            net.minecraft.core.particles.ParticleTypes.PORTAL,
                            px, py, pz,
                            vx, vy, vz
                    );
                }
                if (rnd.nextInt(5) == 0) {
                    level.addParticle(
                            net.minecraft.core.particles.ParticleTypes.END_ROD,
                            holoPos.x + (rnd.nextDouble() - 0.5) * 0.5,
                            holoPos.y + (rnd.nextDouble() - 0.5) * 0.5,
                            holoPos.z + (rnd.nextDouble() - 0.5) * 0.5,
                            0, 0.01, 0
                    );
                }
                continue;
            }

            if (mod != OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SILENT && mod != OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SOUND) {
                continue;
            }

            com.mik.optical_diffuser.item.ProjectorLensItem.ScreenCoords coords =
                    com.mik.optical_diffuser.item.ProjectorLensItem.getScreenCoords(be.getActiveLens());
            if (!coords.isComplete()) continue;

            SableCompat.checkAndMigrateScreenCoords(be);
            coords = com.mik.optical_diffuser.item.ProjectorLensItem.getScreenCoords(be.getActiveLens());

            Direction facing = be.getBlockState().getValue(OpticalDiffuserBlock.FACING);
            com.mik.optical_diffuser.item.ProjectorLensItem.PlacementCheck placement =
                    com.mik.optical_diffuser.item.ProjectorLensItem.checkPlacement(level, be.getBlockPos(), facing, be.getActiveLens());

            if (!placement.isValid()) {
                if (be.isLightEnabled() && rnd.nextInt(3) == 0) {
                    SableCompat.RayContext rayCtx = SableCompat.getRayContext(level, be.getBlockPos(), facing);
                    Vec3 start = rayCtx.start();
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                            start.x + (rnd.nextDouble() - 0.5) * 0.2,
                            start.y + (rnd.nextDouble() - 0.5) * 0.2,
                            start.z + (rnd.nextDouble() - 0.5) * 0.2,
                            0, 0.02, 0);
                }
                if (be.isLightEnabled() && level.getGameTime() % 40 == 0) {
                    level.playLocalSound(be.getBlockPos(),
                            net.minecraft.sounds.SoundEvents.DISPENSER_FAIL,
                            net.minecraft.sounds.SoundSource.BLOCKS,
                            0.30f, 1.4f, false);
                }
                continue;
            }

            SableCompat.RayContext rayCtx = SableCompat.getRayContext(level, be.getBlockPos(), facing);
            Vec3 start = rayCtx.start();

            Vec3 screenCenter = new Vec3(
                    (coords.pos1.getX() + coords.pos2.getX()) * 0.5 + 0.5,
                    (coords.pos1.getY() + coords.pos2.getY()) * 0.5 + 0.5,
                    (coords.pos1.getZ() + coords.pos2.getZ()) * 0.5 + 0.5
            );
            Vec3 worldScreenCenter = SableCompat.resolveScreenWorldPos(level, be.getActiveLens(), screenCenter);

            Vec3 beamVec = worldScreenCenter.subtract(start);
            double beamDist = beamVec.length();
            if (beamDist < 1.0) continue;

            Vec3 right = rayCtx.right();
            Vec3 up = rayCtx.up();

            // Spawn 2-3 atmospheric cinema dust particles along the beam path
            int count = 2 + rnd.nextInt(2);
            for (int i = 0; i < count; i++) {
                double t = rnd.nextDouble();
                Vec3 pCenter = start.add(beamVec.scale(t));

                double maxRadius = 0.15 + t * 0.65;
                double angle = rnd.nextDouble() * Math.PI * 2.0;
                double r = rnd.nextDouble() * maxRadius;

                double offsetX = Math.cos(angle) * r;
                double offsetY = Math.sin(angle) * r;

                Vec3 particlePos = pCenter.add(right.scale(offsetX)).add(up.scale(offsetY));

                double vx = (rnd.nextDouble() - 0.5) * 0.006;
                double vy = 0.003 + rnd.nextDouble() * 0.006;
                double vz = (rnd.nextDouble() - 0.5) * 0.006;

                level.addParticle(
                        net.minecraft.core.particles.ParticleTypes.WHITE_ASH,
                        particlePos.x, particlePos.y, particlePos.z,
                        vx, vy, vz
                );
            }
        }
    }

    private static float currentTintR = 1.0f;
    private static float currentTintG = 1.0f;
    private static float currentTintB = 1.0f;
    private static float currentTintAlpha = 0.0f;

    @SubscribeEvent
    public void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        updateTint();
        if (currentTintAlpha > 0.01f) {
            float blend = currentTintAlpha * 0.55f;
            event.setRed(Mth.lerp(blend, event.getRed(), currentTintR));
            event.setGreen(Mth.lerp(blend, event.getGreen(), currentTintG));
            event.setBlue(Mth.lerp(blend, event.getBlue(), currentTintB));
        }
    }

    @SubscribeEvent
    public void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.CAMERA_OVERLAYS.equals(event.getName())) {
            return;
        }

        updateTint();
        if (currentTintAlpha > 0.01f) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            int width = guiGraphics.guiWidth();
            int height = guiGraphics.guiHeight();

            // Screen tint for volumetric immersion (up to ~45% opacity, preserving HUD readability)
            int a = (int) (currentTintAlpha * 115.0f);
            int r = (int) (currentTintR * 255.0f);
            int g = (int) (currentTintG * 255.0f);
            int b = (int) (currentTintB * 255.0f);
            int argb = (a << 24) | (r << 16) | (g << 8) | b;

            guiGraphics.fill(0, 0, width, height, argb);
        }
    }

    private static void updateTint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null || mc.level == null) {
            currentTintAlpha = Mth.lerp(0.15f, currentTintAlpha, 0.0f);
            return;
        }

        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Level clientLevel = mc.level;

        float targetAlpha = 0.0f;
        float targetR = 1.0f;
        float targetG = 1.0f;
        float targetB = 1.0f;

        for (OpticalDiffuserBlockEntity be : OpticalDiffuserBlockEntity.CLIENT_INSTANCES) {
            if (be == null || be.isRemoved() || !be.isActive() || be.getLevel() == null) continue;

            OpticalDiffuserBlockEntity.DiffuserModule mod = be.getActiveModule();
            if (mod == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SILENT || mod == OpticalDiffuserBlockEntity.DiffuserModule.PROJECTOR_SOUND) {
                continue;
            }

            Direction facing;
            try {
                facing = be.getBlockState().getValue(OpticalDiffuserBlock.FACING);
            } catch (Exception e) {
                continue;
            }

            BlockPos pos = be.getBlockPos();
            SableCompat.RayContext rayCtx = SableCompat.getRayContext(be.getLevel(), pos, facing);
            if (clientLevel != rayCtx.level() && clientLevel != be.getLevel()) {
                continue;
            }

            Vec3 start = rayCtx.start();
            Vec3 dir = rayCtx.direction();
            Vec3 up = rayCtx.up();
            Vec3 right = rayCtx.right();

            Vec3 toCam = camPos.subtract(start);
            double distAlongCenter = toCam.dot(dir);

            if (distAlongCenter >= 0.0 && distAlongCenter <= be.getConeRange()) {
                // Line-of-sight check: if an opaque obstacle sits between diffuser lens and camera, no immersion
                BlockHitResult hit = clientLevel.clip(new ClipContext(
                        start, camPos,
                        ClipContext.Block.VISUAL,
                        ClipContext.Fluid.NONE,
                        CollisionContext.empty()
                ));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    continue;
                }

                // Orthogonal components relative to beam frame
                Vec3 perp = toCam.subtract(dir.scale(distAlongCenter));
                double perpX = perp.dot(right);
                double perpY = perp.dot(up);

                // Beam rotation & polarization roll
                float roll = be.getCurrentRotation();
                BeamHelper.BeamPolarization pol = be.getPolarization();
                if (pol == BeamHelper.BeamPolarization.DIAGONAL_POSITIVE) {
                    roll += 45.0f;
                } else if (pol == BeamHelper.BeamPolarization.DIAGONAL_NEGATIVE) {
                    roll -= 45.0f;
                }

                double rad = -Math.toRadians(roll);
                double localX = perpX * Math.cos(rad) - perpY * Math.sin(rad);
                double localY = perpX * Math.sin(rad) + perpY * Math.cos(rad);

                double scaleX = 1.0;
                double scaleY = 1.0;
                if (pol == BeamHelper.BeamPolarization.HORIZONTAL) {
                    scaleX = 2.0;
                    scaleY = 0.35;
                } else if (pol == BeamHelper.BeamPolarization.VERTICAL) {
                    scaleX = 0.35;
                    scaleY = 2.0;
                }

                double tanHalf = Math.tan(Math.toRadians(be.getConeAngle() * 0.5));
                double rx = Math.max(0.15, distAlongCenter * tanHalf * scaleX);
                double ry = Math.max(0.15, distAlongCenter * tanHalf * scaleY);

                double normDistSq = (localX * localX) / (rx * rx) + (localY * localY) / (ry * ry);
                if (normDistSq <= 1.0) {
                    // Check if camera is situated in the shadow behind an obstacle in its angular sector
                    double angleCam = Math.atan2(localY, localX);
                    if (angleCam < 0) angleCam += 2.0 * Math.PI;
                    int sCam = (int) (angleCam / (2.0 * Math.PI / OpticalDiffuserBlockEntity.NUM_SECTORS)) % OpticalDiffuserBlockEntity.NUM_SECTORS;
                    if (distAlongCenter > be.getSectorObstacleDistance(sCam)) {
                        continue;
                    }

                    double alphaRadial = 1.0 - Math.sqrt(normDistSq);
                    double alphaAxial = 1.0 - (distAlongCenter / (double) be.getConeRange());
                    double immersion = alphaRadial * alphaAxial;

                    // Directional glare when looking towards the lens
                    Vec3 toLight = start.subtract(camPos).normalize();
                    Vec3 look = mc.player.getViewVector(1.0F);
                    double facingLight = Math.max(0.0, look.dot(toLight));
                    double glare = Math.pow(facingLight, 3.0) * 0.40;
                    immersion = Math.min(1.0, immersion + glare * immersion);

                    float factor = (float) Math.min(1.0, Math.max(0.0, immersion));
                    if (factor > targetAlpha) {
                        targetAlpha = factor;
                        targetR = be.getRed() / 255.0f;
                        targetG = be.getGreen() / 255.0f;
                        targetB = be.getBlue() / 255.0f;
                    }
                }
            }
        }

        currentTintAlpha = Mth.lerp(0.2f, currentTintAlpha, targetAlpha);
        currentTintR = Mth.lerp(0.2f, currentTintR, targetR);
        currentTintG = Mth.lerp(0.2f, currentTintG, targetG);
        currentTintB = Mth.lerp(0.2f, currentTintB, targetB);
    }
}
