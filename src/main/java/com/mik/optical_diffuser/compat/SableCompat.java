package com.mik.optical_diffuser.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public class SableCompat {

    public record RayContext(Level level, Vec3 start, Vec3 direction, Vec3 up, Vec3 right) {}

    public static RayContext getRayContext(Level level, BlockPos pos, Direction facing) {
        Vec3 localStart = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.50));
        Vec3 localDir = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 localUp = switch (facing) {
            case UP -> new Vec3(0, 0, -1);
            case DOWN -> new Vec3(0, 0, 1);
            default -> new Vec3(0, 1, 0);
        };
        Vec3 localRight = localUp.cross(localDir).normalize();

        if (level != null && ModList.get().isLoaded("sable")) {
            try {
                return projectFromSable(level, pos, localStart, localDir, localUp, localRight);
            } catch (Throwable ignored) {
            }
        }
        return new RayContext(level, localStart, localDir, localUp, localRight);
    }

    public static RayContext getBlockTransform(Level level, BlockPos pos, Direction facing) {
        Vec3 localCenter = Vec3.atCenterOf(pos);
        Vec3 localDir = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 localUp = switch (facing) {
            case UP -> new Vec3(0, 0, -1);
            case DOWN -> new Vec3(0, 0, 1);
            default -> new Vec3(0, 1, 0);
        };
        Vec3 localRight = localUp.cross(localDir).normalize();

        if (level != null && ModList.get().isLoaded("sable")) {
            try {
                return projectFromSable(level, pos, localCenter, localDir, localUp, localRight);
            } catch (Throwable ignored) {
            }
        }
        return new RayContext(level, localCenter, localDir, localUp, localRight);
    }

    public static Vec3 getWorldPos(Level level, BlockPos pos) {
        if (pos == null) return Vec3.ZERO;
        Vec3 localCenter = Vec3.atCenterOf(pos);
        if (level != null && ModList.get().isLoaded("sable")) {
            try {
                dev.ryanhcode.sable.sublevel.SubLevel subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos);
                if (subLevel != null) {
                    return dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, localCenter);
                }
            } catch (Throwable ignored) {
            }
        }
        return localCenter;
    }

    public static boolean isInsideSpeakerZone(Level level, BlockPos speakerPos, Direction facing, Vec3 playerPos) {
        if (speakerPos == null || facing == null || playerPos == null) return false;
        RayContext ctx = getBlockTransform(level, speakerPos, facing);
        Vec3 rel = playerPos.subtract(ctx.start());
        double forwardDist = rel.dot(ctx.direction());
        if (forwardDist < -0.5 || forwardDist > 9.5) {
            return false;
        }
        double lateral = Math.abs(rel.dot(ctx.right()));
        double vertical = Math.abs(rel.dot(ctx.up()));
        return lateral <= 4.5 && vertical <= 4.5;
    }

    private static RayContext projectFromSable(Level level, BlockPos pos, Vec3 localStart, Vec3 localDir, Vec3 localUp, Vec3 localRight) {
        dev.ryanhcode.sable.sublevel.SubLevel subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos);
        if (subLevel != null) {
            Level parentLevel = subLevel.getLevel();
            Vec3 worldStart = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, localStart);
            Vec3 worldAhead = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, localStart.add(localDir));
            Vec3 worldUpAhead = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, localStart.add(localUp));
            
            Vec3 worldDir = worldAhead.subtract(worldStart).normalize();
            Vec3 rawUp = worldUpAhead.subtract(worldStart).normalize();
            Vec3 worldRight = rawUp.cross(worldDir).normalize();
            Vec3 worldUp = worldDir.cross(worldRight).normalize();

            return new RayContext(parentLevel != null ? parentLevel : level, worldStart, worldDir, worldUp, worldRight);
        }
        return new RayContext(level, localStart, localDir, localUp, localRight);
    }
}
