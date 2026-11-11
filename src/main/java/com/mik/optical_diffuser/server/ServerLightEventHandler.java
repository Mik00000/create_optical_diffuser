package com.mik.optical_diffuser.server;

import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.compat.SableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

public class ServerLightEventHandler {

    @SubscribeEvent
    public static void onCheckSpawn(MobSpawnEvent.PositionCheck event) {
        try {
            if (event.getLevel() == null || OpticalDiffuserBlockEntity.ACTIVE_SERVER_DIFFUSERS.isEmpty()) {
                return;
            }

            double ex = event.getX();
            double ey = event.getY();
            double ez = event.getZ();
            Vec3 entityPos = new Vec3(ex, ey, ez);

            for (java.util.Iterator<OpticalDiffuserBlockEntity> it = OpticalDiffuserBlockEntity.ACTIVE_SERVER_DIFFUSERS.iterator(); it.hasNext(); ) {
                OpticalDiffuserBlockEntity be = it.next();
                if (be == null || be.isRemoved() || be.getLevel() == null) {
                    it.remove();
                    continue;
                }
                if (!be.isActive()) {
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

                if (rayCtx.level() != event.getLevel().getLevel() && be.getLevel() != event.getLevel().getLevel()) {
                    continue;
                }

                Vec3 start = rayCtx.start();
                Vec3 dir = rayCtx.direction();

                Vec3 toEntity = entityPos.subtract(start);
                double distAlongCenter = toEntity.dot(dir);

                if (distAlongCenter > 0.0 && distAlongCenter <= be.getConeRange()) {
                    Vec3 proj = dir.scale(distAlongCenter);
                    double radialDist = toEntity.subtract(proj).length();
                    double tanHalf = Math.tan(Math.toRadians(be.getConeAngle() * 0.5));
                    double allowedRadius = Math.max(0.30, distAlongCenter * tanHalf);

                    if (radialDist <= allowedRadius) {
                        event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
