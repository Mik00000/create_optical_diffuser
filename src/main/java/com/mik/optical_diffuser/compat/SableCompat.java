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

    private static final java.util.Map<java.util.UUID, dev.ryanhcode.sable.companion.math.Pose3d> SUBLEVEL_INITIAL_POSES = new java.util.concurrent.ConcurrentHashMap<>();

    public static void init() {
        if (ModList.get().isLoaded("sable")) {
            try {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SableCompat::onContainerReady);
            } catch (Throwable ignored) {}
        }
    }

    private static void onContainerReady(dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent event) {
        if (event != null && event.getContainer() != null) {
            try {
                event.getContainer().addObserver(new dev.ryanhcode.sable.api.sublevel.SubLevelObserver() {
                    @Override
                    public void onSubLevelAdded(dev.ryanhcode.sable.sublevel.SubLevel sub) {
                        recordSubLevelAssembly(sub);
                    }

                    @Override
                    public void onSubLevelRemoved(dev.ryanhcode.sable.sublevel.SubLevel sub, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason reason) {
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    public static void recordSubLevelAssembly(dev.ryanhcode.sable.sublevel.SubLevel sub) {
        if (sub != null && sub.getUniqueId() != null && sub.logicalPose() != null) {
            SUBLEVEL_INITIAL_POSES.put(sub.getUniqueId(), new dev.ryanhcode.sable.companion.math.Pose3d(sub.logicalPose()));
        }
    }

    public static boolean checkAndMigrateScreenCoords(com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity diffuser) {
        if (diffuser == null || diffuser.getLevel() == null || !ModList.get().isLoaded("sable")) {
            return false;
        }

        try {
            net.minecraft.world.item.ItemStack lens = diffuser.getActiveLens();
            if (lens == null || lens.isEmpty()) return false;

            com.mik.optical_diffuser.item.ProjectorLensItem.ScreenCoords coords =
                    com.mik.optical_diffuser.item.ProjectorLensItem.getScreenCoords(lens);
            if (!coords.isComplete()) return false;

            Level level = diffuser.getLevel();
            java.util.UUID screenSubId = com.mik.optical_diffuser.item.ProjectorLensItem.getScreenSubLevelId(lens);
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return false;

            // Scenario 1: Lens is currently bound to a SubLevel UUID
            if (screenSubId != null) {
                dev.ryanhcode.sable.sublevel.SubLevel sub = container.getSubLevel(screenSubId);
                // If the SubLevel was removed / disassembled:
                if (sub == null || sub.isRemoved()) {
                    dev.ryanhcode.sable.companion.math.Pose3dc lastPose = (sub != null) ? sub.logicalPose() : null;
                    if (lastPose != null) {
                        Vec3 w1 = lastPose.transformPosition(Vec3.atCenterOf(coords.pos1));
                        Vec3 w2 = lastPose.transformPosition(Vec3.atCenterOf(coords.pos2));
                        com.mik.optical_diffuser.item.ProjectorLensItem.setPos1(lens, BlockPos.containing(w1));
                        com.mik.optical_diffuser.item.ProjectorLensItem.setPos2(lens, BlockPos.containing(w2));
                    }
                    com.mik.optical_diffuser.item.ProjectorLensItem.setScreenSubLevelId(lens, null);
                    diffuser.setChanged();
                    return true;
                }
                return false;
            }

            // Scenario 2: Lens has NO SubLevel UUID (configured on world blocks before assembly)
            // Check if pos1 / pos2 blocks were assembled into any active SubLevel!
            Vec3 p1Center = Vec3.atCenterOf(coords.pos1);
            Vec3 p2Center = Vec3.atCenterOf(coords.pos2);

            for (dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
                if (sub == null || sub.isRemoved()) continue;

                // Check with initial assembly pose if known, otherwise current logical pose
                dev.ryanhcode.sable.companion.math.Pose3dc testPose = SUBLEVEL_INITIAL_POSES.get(sub.getUniqueId());
                if (testPose == null) testPose = sub.logicalPose();
                if (testPose == null) continue;

                Vec3 plotPos1 = testPose.transformPositionInverse(p1Center);
                Vec3 plotPos2 = testPose.transformPositionInverse(p2Center);

                if (sub.getPlot() != null && sub.getPlot().getBoundingBox() != null) {
                    dev.ryanhcode.sable.companion.math.BoundingBox3ic box = sub.getPlot().getBoundingBox();
                    // Match if plot position falls within or directly bordering the contraption's plot bounds
                    boolean contains = (plotPos1.x >= box.minX() - 2.5 && plotPos1.x <= box.maxX() + 3.5 &&
                                        plotPos1.y >= box.minY() - 2.5 && plotPos1.y <= box.maxY() + 3.5 &&
                                        plotPos1.z >= box.minZ() - 2.5 && plotPos1.z <= box.maxZ() + 3.5);

                    if (contains) {
                        // Match confirmed! Screen was assembled into this SubLevel
                        com.mik.optical_diffuser.item.ProjectorLensItem.setPos1(lens, BlockPos.containing(plotPos1));
                        com.mik.optical_diffuser.item.ProjectorLensItem.setPos2(lens, BlockPos.containing(plotPos2));
                        com.mik.optical_diffuser.item.ProjectorLensItem.setScreenSubLevelId(lens, sub.getUniqueId());
                        diffuser.setChanged();
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
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
        if (!ModList.get().isLoaded("sable")) {
            return localScreenPoint.subtract(Vec3.atLowerCornerOf(diffuser.getBlockPos()));
        }

        try {
            // 1. Check if diffuser is on a SubLevel
            dev.ryanhcode.sable.sublevel.SubLevel diffuserSub = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, diffuser.getBlockPos());

            // 2. Check if screen is on a SubLevel
            java.util.UUID screenSubId = com.mik.optical_diffuser.item.ProjectorLensItem.getScreenSubLevelId(lens);
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            dev.ryanhcode.sable.sublevel.SubLevel screenSub = (container != null && screenSubId != null) ? container.getSubLevel(screenSubId) : null;
            if (screenSub != null && screenSub.isRemoved()) screenSub = null;

            // CASE 1: BOTH on the SAME SubLevel (Same ship contraption!)
            if (diffuserSub != null && screenSub != null && diffuserSub.getUniqueId().equals(screenSub.getUniqueId())) {
                // In local plot coordinates, relative directly to diffuser block pos:
                return localScreenPoint.subtract(Vec3.atLowerCornerOf(diffuser.getBlockPos()));
            }

            // Screen world position:
            Vec3 worldScreenPoint;
            if (screenSub != null) {
                worldScreenPoint = screenSub.logicalPose().transformPosition(localScreenPoint);
            } else {
                worldScreenPoint = localScreenPoint;
            }

            // CASE 2: Diffuser on SubLevel, Screen in World (or on different ship)
            if (diffuserSub != null && !diffuserSub.isRemoved()) {
                Vec3 localToPlot = diffuserSub.logicalPose().transformPositionInverse(worldScreenPoint);
                return localToPlot.subtract(Vec3.atLowerCornerOf(diffuser.getBlockPos()));
            }

            // CASE 3: Diffuser in World, Screen on SubLevel (or both in World)
            return worldScreenPoint.subtract(Vec3.atLowerCornerOf(diffuser.getBlockPos()));
        } catch (Throwable t) {
            return localScreenPoint.subtract(Vec3.atLowerCornerOf(diffuser.getBlockPos()));
        }
    }

    public static Vec3 resolveScreenWorldPos(Level level, net.minecraft.world.item.ItemStack lens, Vec3 localScreenPoint) {
        if (level == null || localScreenPoint == null) return Vec3.ZERO;
        if (!ModList.get().isLoaded("sable")) {
            return localScreenPoint;
        }

        try {
            java.util.UUID screenSubId = com.mik.optical_diffuser.item.ProjectorLensItem.getScreenSubLevelId(lens);
            if (screenSubId != null) {
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
                if (container != null) {
                    dev.ryanhcode.sable.sublevel.SubLevel sub = container.getSubLevel(screenSubId);
                    if (sub != null && !sub.isRemoved()) {
                        return sub.logicalPose().transformPosition(localScreenPoint);
                    }
                }
            }

            // Fallback: check if localScreenPoint is inside a SubLevel
            BlockPos basePos = BlockPos.containing(localScreenPoint);
            dev.ryanhcode.sable.sublevel.SubLevel containing = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, basePos);
            if (containing != null && !containing.isRemoved()) {
                return containing.logicalPose().transformPosition(localScreenPoint);
            }

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
