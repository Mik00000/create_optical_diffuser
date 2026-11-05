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

        if (ModList.get().isLoaded("sable")) {
            try {
                return projectFromSable(level, pos, localStart, localDir, localUp, localRight);
            } catch (Throwable ignored) {
            }
        }
        return new RayContext(level, localStart, localDir, localUp, localRight);
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
