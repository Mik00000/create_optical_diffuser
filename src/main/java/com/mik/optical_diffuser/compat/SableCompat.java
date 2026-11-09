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

    public static float getSpeakerZoneGain(Level level, BlockPos speakerPos, Direction facing, Vec3 playerPos) {
        if (speakerPos == null || facing == null || playerPos == null) return 0.0f;
        RayContext ctx = getBlockTransform(level, speakerPos, facing);
        Vec3 rel = playerPos.subtract(ctx.start());
        double forwardDist = rel.dot(ctx.direction());
        if (forwardDist < -1.0 || forwardDist > 28.0) {
            return 0.0f;
        }

        double lateral = Math.abs(rel.dot(ctx.right()));
        double vertical = Math.abs(rel.dot(ctx.up()));
        if (lateral > 12.0 || vertical > 10.0) {
            return 0.0f;
        }

        // Cinema-scale acoustic coverage:
        // Forward: 0..20m pristine, 20..28m smooth quadratic falloff
        float forwardGain = 1.0f;
        if (forwardDist < 0.5) {
            forwardGain = (float) (0.50 + 0.50 * ((forwardDist + 1.0) / 1.5));
        } else if (forwardDist > 20.0) {
            double t = (28.0 - forwardDist) / 8.0;
            forwardGain = (float) (t * t);
        }

        // Lateral (24m total width): 0..8m full, 8..12m smooth roll-off
        float lateralGain = 1.0f;
        if (lateral > 8.0) {
            double t = (12.0 - lateral) / 4.0;
            lateralGain = (float) (t * t);
        }

        // Vertical (20m total height for balconies/amphitheaters): 0..6m full, 6..10m smooth roll-off
        float verticalGain = 1.0f;
        if (vertical > 6.0) {
            double t = (10.0 - vertical) / 4.0;
            verticalGain = (float) (t * t);
        }

        return forwardGain * lateralGain * verticalGain;
    }

    public static Vec3 transformScreenPointToDiffuserLocal(
            com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity diffuser,
            net.minecraft.world.item.ItemStack lens,
            Vec3 localScreenPoint
    ) {
        if (diffuser == null || diffuser.getLevel() == null || localScreenPoint == null) {
            return Vec3.ZERO;
        }

        Level level = diffuser.getLevel();
        Vec3 worldPoint = localScreenPoint;

        if (ModList.get().isLoaded("sable")) {
            try {
                worldPoint = resolveScreenWorldPos(level, lens, localScreenPoint);
            } catch (Throwable ignored) {}
        }

        // Transform worldPoint into diffuser's local rendering coordinate space
        if (ModList.get().isLoaded("sable")) {
            try {
                dev.ryanhcode.sable.sublevel.SubLevel diffuserSub = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, diffuser.getBlockPos());
                if (diffuserSub != null && !diffuserSub.isRemoved()) {
                    Vec3 subLocal = diffuserSub.logicalPose().transformPositionInverse(worldPoint);
                    return subLocal.subtract(Vec3.atLowerCornerOf(diffuser.getBlockPos()));
                }
            } catch (Throwable ignored) {}
        }

        return worldPoint.subtract(Vec3.atLowerCornerOf(diffuser.getBlockPos()));
    }

    public static Vec3 resolveScreenWorldPos(Level level, net.minecraft.world.item.ItemStack lens, Vec3 localScreenPoint) {
        if (level == null || localScreenPoint == null) return Vec3.ZERO;
        if (!ModList.get().isLoaded("sable")) {
            return localScreenPoint;
        }

        try {
            BlockPos basePos = BlockPos.containing(localScreenPoint);

            // 1. Check if lens explicitly specifies a SubLevel UUID
            java.util.UUID subLevelId = com.mik.optical_diffuser.item.ProjectorLensItem.getScreenSubLevelId(lens);
            if (subLevelId != null) {
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
                if (container != null) {
                    dev.ryanhcode.sable.sublevel.SubLevel sub = container.getSubLevel(subLevelId);
                    if (sub != null && !sub.isRemoved()) {
                        return sub.logicalPose().transformPosition(localScreenPoint);
                    }
                }
            }

            // 2. Check if localScreenPoint is currently inside any SubLevel (e.g. assembled ship)
            dev.ryanhcode.sable.sublevel.SubLevel containing = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, basePos);
            if (containing != null && !containing.isRemoved()) {
                return containing.logicalPose().transformPosition(localScreenPoint);
            }

            // 3. Fallback to direct projection via Sable Helper
            return dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, localScreenPoint);
        } catch (Throwable t) {
            return localScreenPoint;
        }
    }

    public static boolean isInsideSpeakerZone(Level level, BlockPos speakerPos, Direction facing, Vec3 playerPos) {
        return getSpeakerZoneGain(level, speakerPos, facing, playerPos) > 0.01f;
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
