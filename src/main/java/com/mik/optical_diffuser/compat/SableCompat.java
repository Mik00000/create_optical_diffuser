package com.mik.optical_diffuser.compat;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.foundation.utility.CreatePaths;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicLevelExtension;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.StructureTemplateExtension;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class SableCompat {

    public record RayContext(Level level, Vec3 start, Vec3 direction, Vec3 up, Vec3 right) {}

    public record SableBlockHit(
            dev.ryanhcode.sable.sublevel.SubLevel subLevel,
            BlockPos plotPos,
            BlockState state,
            Vec3 worldHitPos,
            Direction worldFace,
            double worldDistance
    ) {}

    public static SableBlockHit raycastSubLevels(Level level, Vec3 start, Vec3 end, boolean stopOnLiquid) {
        return raycastSubLevels(level, start, end, stopOnLiquid, net.minecraft.world.level.ClipContext.Block.COLLIDER);
    }

    public static SableBlockHit raycastSubLevels(
            Level level, Vec3 start, Vec3 end, boolean stopOnLiquid, net.minecraft.world.level.ClipContext.Block blockClip
    ) {
        if (level == null || !ModList.get().isLoaded("sable")) return null;
        try {
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return null;

            SableBlockHit closest = null;
            double closestDist = Double.MAX_VALUE;

            AABB rayAABB = new AABB(start, end).inflate(1.0);

            for (dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
                if (sub == null || sub.isRemoved() || sub.logicalPose() == null || sub.getLevel() == null) continue;

                if (sub.boundingBox() != null) {
                    AABB shipBox = sub.boundingBox().toMojang();
                    if (!shipBox.intersects(rayAABB) && !shipBox.clip(start, end).isPresent()) {
                        continue;
                    }
                }

                Vec3 plotStart = sub.logicalPose().transformPositionInverse(start);
                Vec3 plotEnd = sub.logicalPose().transformPositionInverse(end);

                net.minecraft.world.level.ClipContext clipCtx = new net.minecraft.world.level.ClipContext(
                        plotStart, plotEnd,
                        blockClip,
                        stopOnLiquid ? net.minecraft.world.level.ClipContext.Fluid.ANY : net.minecraft.world.level.ClipContext.Fluid.NONE,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()
                );

                if (clipCtx instanceof dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension ext) {
                    ext.sable$setDoNotProject(true);
                }

                BlockHitResult hit = sub.getLevel().clip(clipCtx);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    Vec3 worldHitPos = sub.logicalPose().transformPosition(hit.getLocation());
                    double d = start.distanceTo(worldHitPos);
                    if (d < closestDist) {
                        BlockState st = sub.getLevel().getBlockState(hit.getBlockPos());
                        Vec3 plotNormal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                        Vec3 worldNormal = sub.logicalPose().transformNormal(plotNormal).normalize();
                        Direction worldFace = Direction.getNearest(worldNormal.x, worldNormal.y, worldNormal.z);

                        closest = new SableBlockHit(sub, hit.getBlockPos(), st, worldHitPos, worldFace, d);
                        closestDist = d;
                    }
                }
            }
            return closest;
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<dev.ryanhcode.sable.sublevel.SubLevel> getIntersectingSubLevels(Level level, AABB aabb) {
        if (level == null || aabb == null || !ModList.get().isLoaded("sable")) return List.of();
        try {
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return List.of();

            List<dev.ryanhcode.sable.sublevel.SubLevel> result = new ArrayList<>();
            for (dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
                if (sub == null || sub.isRemoved() || sub.logicalPose() == null || sub.getLevel() == null) continue;
                if (sub.boundingBox() != null && !sub.boundingBox().toMojang().intersects(aabb)) continue;
                result.add(sub);
            }
            return result;
        } catch (Throwable t) {
            return List.of();
        }
    }

    public static boolean processThermalAt(
            List<dev.ryanhcode.sable.sublevel.SubLevel> candidateSubLevels,
            Vec3 worldPt,
            Vec3 rayDir,
            float rpm,
            RandomSource random,
            Set<BlockPos> visitedDepots
    ) {
        if (candidateSubLevels == null || candidateSubLevels.isEmpty()) return false;
        try {
            for (dev.ryanhcode.sable.sublevel.SubLevel sub : candidateSubLevels) {
                if (sub == null || sub.isRemoved() || sub.logicalPose() == null || sub.getLevel() == null) continue;

                if (sub.boundingBox() != null) {
                    AABB box = sub.boundingBox().toMojang().inflate(0.5);
                    if (!box.contains(worldPt)) continue;
                }

                Vec3 plotPt = sub.logicalPose().transformPositionInverse(worldPt);
                BlockPos plotPos = BlockPos.containing(plotPt);

                Level subLevel = sub.getLevel();
                BlockState st = subLevel.getBlockState(plotPos);
                if (st.isAir() || st.is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                    continue;
                }

                // Process Create Depots and Belts on the ship (only if block has a block entity!)
                if (visitedDepots != null) {
                    BlockPos[] depotCandidates = new BlockPos[]{plotPos, plotPos.below()};
                    for (BlockPos dPos : depotCandidates) {
                        if (visitedDepots.add(dPos)) {
                            BlockState dState = subLevel.getBlockState(dPos);
                            if (dState.hasBlockEntity()) {
                                com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour handler =
                                        com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(
                                                subLevel, dPos, com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TYPE
                                        );
                                if (handler != null) {
                                    handler.handleProcessingOnAllItems(transported ->
                                            com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity.processTransportedCooking(transported, subLevel, rpm)
                                    );
                                }
                            }
                        }
                    }
                }

                Vec3 plotRayDir = sub.logicalPose().transformNormalInverse(rayDir).normalize();
                Direction plotHitFace = Direction.getNearest(-plotRayDir.x, -plotRayDir.y, -plotRayDir.z);

                return com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity.processThermalBlock(
                        subLevel, plotPos, plotHitFace, rpm, random
                );
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isLeadGlassAt(Level level, Vec3 worldPt) {
        if (level == null || !ModList.get().isLoaded("sable")) return false;
        try {
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return false;

            for (dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
                if (sub == null || sub.isRemoved() || sub.logicalPose() == null || sub.getLevel() == null) continue;

                if (sub.boundingBox() != null) {
                    if (!sub.boundingBox().toMojang().inflate(0.5).contains(worldPt)) continue;
                }

                Vec3 plotPt = sub.logicalPose().transformPositionInverse(worldPt);
                BlockPos plotPos = BlockPos.containing(plotPt);
                BlockState st = sub.getLevel().getBlockState(plotPos);
                if (st.is(com.mik.optical_diffuser.ModRegistry.LEAD_GLASS.get())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void meltAt(Level level, Vec3 worldPt) {
        if (level == null || !ModList.get().isLoaded("sable")) return;
        try {
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return;

            for (dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
                if (sub == null || sub.isRemoved() || sub.logicalPose() == null || sub.getLevel() == null) continue;

                if (sub.boundingBox() != null) {
                    if (!sub.boundingBox().toMojang().inflate(0.5).contains(worldPt)) continue;
                }

                Vec3 plotPt = sub.logicalPose().transformPositionInverse(worldPt);
                BlockPos plotPos = BlockPos.containing(plotPt);
                com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity.meltAt(sub.getLevel(), plotPos);
            }
        } catch (Throwable ignored) {}
    }

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
                    return subLevel.logicalPose().transformPosition(localCenter);
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

    private static final java.util.Map<java.util.UUID, dev.ryanhcode.sable.companion.math.Pose3dc> SUBLEVEL_INITIAL_POSES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, dev.ryanhcode.sable.companion.math.Pose3dc> SUBLEVEL_LAST_KNOWN_POSES = new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearCache() {
        SUBLEVEL_INITIAL_POSES.clear();
        SUBLEVEL_LAST_KNOWN_POSES.clear();
    }

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
                        if (sub != null && sub.getUniqueId() != null && sub.logicalPose() != null) {
                            SUBLEVEL_LAST_KNOWN_POSES.put(sub.getUniqueId(), new dev.ryanhcode.sable.companion.math.Pose3d(sub.logicalPose()));
                        }
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    public static void recordSubLevelAssembly(dev.ryanhcode.sable.sublevel.SubLevel sub) {
        if (sub != null && sub.getUniqueId() != null && sub.logicalPose() != null) {
            dev.ryanhcode.sable.companion.math.Pose3d copy = new dev.ryanhcode.sable.companion.math.Pose3d(sub.logicalPose());
            SUBLEVEL_INITIAL_POSES.put(sub.getUniqueId(), copy);
            SUBLEVEL_LAST_KNOWN_POSES.put(sub.getUniqueId(), copy);
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
                    dev.ryanhcode.sable.companion.math.Pose3dc lastPose = SUBLEVEL_LAST_KNOWN_POSES.get(screenSubId);
                    if (lastPose == null && sub != null && sub.logicalPose() != null) {
                        lastPose = sub.logicalPose();
                    }
                    if (lastPose == null) {
                        double[] saved = com.mik.optical_diffuser.item.ProjectorLensItem.getSavedSubLevelPose(lens);
                        if (saved != null) {
                            lastPose = new dev.ryanhcode.sable.companion.math.Pose3d(
                                    new org.joml.Vector3d(saved[0], saved[1], saved[2]),
                                    new org.joml.Quaterniond(saved[3], saved[4], saved[5], saved[6]),
                                    new org.joml.Vector3d(0, 0, 0),
                                    new org.joml.Vector3d(1, 1, 1)
                            );
                        }
                    }

                    if (lastPose != null) {
                        Vec3 w1 = lastPose.transformPosition(Vec3.atCenterOf(coords.pos1));
                        Vec3 w2 = lastPose.transformPosition(Vec3.atCenterOf(coords.pos2));
                        com.mik.optical_diffuser.item.ProjectorLensItem.setPos1(lens, BlockPos.containing(w1));
                        com.mik.optical_diffuser.item.ProjectorLensItem.setPos2(lens, BlockPos.containing(w2));
                    } else {
                        // Fallback: relative offset if diffuser & screen shared the same contraption
                        int[] rel = com.mik.optical_diffuser.item.ProjectorLensItem.getSavedRelativeOffset(lens);
                        if (rel != null) {
                            com.mik.optical_diffuser.item.ProjectorLensItem.setPos1(lens, diffuser.getBlockPos().offset(rel[0], rel[1], rel[2]));
                            com.mik.optical_diffuser.item.ProjectorLensItem.setPos2(lens, diffuser.getBlockPos().offset(rel[3], rel[4], rel[5]));
                        }
                    }
                    com.mik.optical_diffuser.item.ProjectorLensItem.setScreenSubLevelId(lens, null);
                    diffuser.setChanged();
                    diffuser.syncToClient();
                    return true;
                }

                // SubLevel is currently active: track pose & relative offsets
                if (sub.logicalPose() != null) {
                    dev.ryanhcode.sable.companion.math.Pose3d poseCopy = new dev.ryanhcode.sable.companion.math.Pose3d(sub.logicalPose());
                    SUBLEVEL_LAST_KNOWN_POSES.put(screenSubId, poseCopy);
                    org.joml.Vector3dc pos = poseCopy.position();
                    org.joml.Quaterniondc rot = poseCopy.orientation();
                    com.mik.optical_diffuser.item.ProjectorLensItem.saveSubLevelPose(lens,
                            pos.x(), pos.y(), pos.z(),
                            rot.x(), rot.y(), rot.z(), rot.w()
                    );
                }

                dev.ryanhcode.sable.sublevel.SubLevel diffuserSub = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, diffuser.getBlockPos());
                if (diffuserSub != null && diffuserSub.getUniqueId().equals(screenSubId)) {
                    BlockPos dbp = diffuser.getBlockPos();
                    com.mik.optical_diffuser.item.ProjectorLensItem.saveRelativeOffset(lens,
                            coords.pos1.getX() - dbp.getX(),
                            coords.pos1.getY() - dbp.getY(),
                            coords.pos1.getZ() - dbp.getZ(),
                            coords.pos2.getX() - dbp.getX(),
                            coords.pos2.getY() - dbp.getY(),
                            coords.pos2.getZ() - dbp.getZ()
                    );
                }
                return false;
            }

            // Scenario 2: Lens has NO SubLevel UUID (configured on world blocks before assembly)
            Vec3 p1Center = Vec3.atCenterOf(coords.pos1);
            Vec3 p2Center = Vec3.atCenterOf(coords.pos2);

            for (dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
                if (sub == null || sub.isRemoved()) continue;

                dev.ryanhcode.sable.companion.math.Pose3dc testPose = SUBLEVEL_INITIAL_POSES.get(sub.getUniqueId());
                if (testPose == null) testPose = sub.logicalPose();
                if (testPose == null) continue;

                Vec3 plotPos1 = testPose.transformPositionInverse(p1Center);
                Vec3 plotPos2 = testPose.transformPositionInverse(p2Center);

                if (sub.getPlot() != null && sub.getPlot().getBoundingBox() != null) {
                    dev.ryanhcode.sable.companion.math.BoundingBox3ic box = sub.getPlot().getBoundingBox();
                    boolean contains = (plotPos1.x >= box.minX() - 2.5 && plotPos1.x <= box.maxX() + 3.5 &&
                                        plotPos1.y >= box.minY() - 2.5 && plotPos1.y <= box.maxY() + 3.5 &&
                                        plotPos1.z >= box.minZ() - 2.5 && plotPos1.z <= box.maxZ() + 3.5);

                    if (contains) {
                        com.mik.optical_diffuser.item.ProjectorLensItem.setPos1(lens, BlockPos.containing(plotPos1));
                        com.mik.optical_diffuser.item.ProjectorLensItem.setPos2(lens, BlockPos.containing(plotPos2));
                        com.mik.optical_diffuser.item.ProjectorLensItem.setScreenSubLevelId(lens, sub.getUniqueId());
                        if (sub.logicalPose() != null) {
                            dev.ryanhcode.sable.companion.math.Pose3d poseCopy = new dev.ryanhcode.sable.companion.math.Pose3d(sub.logicalPose());
                            SUBLEVEL_LAST_KNOWN_POSES.put(sub.getUniqueId(), poseCopy);
                            org.joml.Vector3dc pos = poseCopy.position();
                            org.joml.Quaterniondc rot = poseCopy.orientation();
                            com.mik.optical_diffuser.item.ProjectorLensItem.saveSubLevelPose(lens,
                                    pos.x(), pos.y(), pos.z(),
                                    rot.x(), rot.y(), rot.z(), rot.w()
                            );
                        }
                        diffuser.setChanged();
                        diffuser.syncToClient();
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

            if (screenSub != null && screenSub.logicalPose() != null) {
                SUBLEVEL_LAST_KNOWN_POSES.put(screenSub.getUniqueId(), new dev.ryanhcode.sable.companion.math.Pose3d(screenSub.logicalPose()));
            }

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

            return localScreenPoint;
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
            Vec3 worldStart = subLevel.logicalPose().transformPosition(localStart);
            Vec3 worldAhead = subLevel.logicalPose().transformPosition(localStart.add(localDir));
            Vec3 worldUpAhead = subLevel.logicalPose().transformPosition(localStart.add(localUp));

            Vec3 worldDir = worldAhead.subtract(worldStart).normalize();
            Vec3 rawUp = worldUpAhead.subtract(worldStart).normalize();
            Vec3 worldRight = rawUp.cross(worldDir).normalize();
            Vec3 worldUp = worldDir.cross(worldRight).normalize();

            return new RayContext(parentLevel != null ? parentLevel : level, worldStart, worldDir, worldUp, worldRight);
        }
        return new RayContext(level, localStart, localDir, localUp, localRight);
    }

    public static void populateSchematicSubLevels(
            ItemStack schematicStack,
            StructureTemplate template,
            SchematicLevel schematicLevel,
            Level level
    ) {
        if (!ModList.get().isLoaded("sable")) return;
        try {
            if (!(template instanceof StructureTemplateExtension templateExt) ||
                !(schematicLevel instanceof SchematicLevelExtension levelExt)) {
                return;
            }

            List<StructureTemplateExtension.SubLevelTemplate> subTemplates = templateExt.sable$getSubLevels();

            // If templateExt sub-levels are empty, attempt reading directly from schematic file
            if ((subTemplates == null || subTemplates.isEmpty()) && schematicStack != null && !schematicStack.isEmpty()) {
                subTemplates = loadSubLevelsFromFile(schematicStack, level);
            }

            if (subTemplates == null || subTemplates.isEmpty()) return;

            for (StructureTemplateExtension.SubLevelTemplate subLevelTemplate : subTemplates) {
                SchematicLevel subLevel = new SchematicLevel(BlockPos.ZERO, level);
                StructurePlaceSettings subSettings = new StructurePlaceSettings();
                subLevelTemplate.template().placeInWorld(subLevel, BlockPos.ZERO, BlockPos.ZERO, subSettings, level.getRandom(), 2);

                // Initialize Copycat materials in sub-level
                CompoundTag subTag = subLevelTemplate.template().save(new CompoundTag());
                ListTag subBlocks = subTag.getList("blocks", Tag.TAG_COMPOUND);
                for (int j = 0; j < subBlocks.size(); j++) {
                    CompoundTag bEntry = subBlocks.getCompound(j);
                    if (bEntry.contains("nbt")) {
                        CompoundTag bNbt = bEntry.getCompound("nbt");
                        if (bNbt.contains("Material")) {
                            BlockPos rawPos = parsePos(bEntry);
                            BlockEntity beAt = subLevel.getBlockEntity(rawPos);
                            if (beAt instanceof CopycatBlockEntity copycat) {
                                if (!copycat.hasCustomMaterial()) {
                                    BlockState mat = NbtUtils.readBlockState(
                                            level.holderLookup(Registries.BLOCK),
                                            bNbt.getCompound("Material")
                                    );
                                    if (mat != null && !mat.isAir()) {
                                        copycat.setMaterial(mat);
                                    }
                                }
                            }
                        }
                    }
                }

                // Extract contraptions inside sub-level if any
                ListTag entitiesTag = subTag.getList("entities", Tag.TAG_COMPOUND);
                for (int j = 0; j < entitiesTag.size(); j++) {
                    CompoundTag entityEntry = entitiesTag.getCompound(j);
                    CompoundTag nbt = entityEntry.getCompound("nbt");
                    String id = nbt.getString("id");
                    if (id.contains("contraption")) {
                        CompoundTag contraptionTag = nbt.getCompound("Contraption");
                        if (!contraptionTag.isEmpty()) {
                            try {
                                Contraption contraption = Contraption.fromNBT(level, contraptionTag, false);
                                if (contraption != null) {
                                    BlockPos basePos = parsePos(entityEntry);
                                    for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry : contraption.getBlocks().entrySet()) {
                                        BlockPos placedPos = basePos.offset(entry.getKey());
                                        subLevel.setBlock(placedPos, entry.getValue().state(), 2);
                                        if (entry.getValue().nbt() != null) {
                                            BlockEntity cbe = subLevel.getBlockEntity(placedPos);
                                            if (cbe != null) {
                                                cbe.loadWithComponents(entry.getValue().nbt(), level.registryAccess());
                                            }
                                        }
                                    }
                                    for (var actorPair : contraption.getActors()) {
                                        if (actorPair != null && actorPair.left != null) {
                                            StructureTemplate.StructureBlockInfo info = actorPair.left;
                                            BlockPos placedPos = basePos.offset(info.pos());
                                            subLevel.setBlock(placedPos, info.state(), 2);
                                            if (info.nbt() != null) {
                                                BlockEntity abe = subLevel.getBlockEntity(placedPos);
                                                if (abe != null) {
                                                    abe.loadWithComponents(info.nbt(), level.registryAccess());
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }

                levelExt.sable$getSubLevels().add(new SchematicLevelExtension.SchematicSubLevel(
                        subLevelTemplate.uuid(),
                        subLevelTemplate.position(),
                        subLevelTemplate.orientation(),
                        subLevel
                ));
            }
        } catch (Throwable ignored) {}
    }

    private static List<StructureTemplateExtension.SubLevelTemplate> loadSubLevelsFromFile(ItemStack stack, Level level) {
        List<StructureTemplateExtension.SubLevelTemplate> result = new ArrayList<>();
        try {
            String file = stack.get(AllDataComponents.SCHEMATIC_FILE);
            String owner = stack.get(AllDataComponents.SCHEMATIC_OWNER);
            if (file == null || !file.endsWith(".nbt")) return result;

            Path path = null;
            if (level.isClientSide) {
                path = CreatePaths.SCHEMATICS_DIR.resolve(file);
            } else if (owner != null) {
                path = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(owner).resolve(file);
            }
            if (path == null || !Files.exists(path)) {
                Path fallback = CreatePaths.SCHEMATICS_DIR.resolve(file);
                if (Files.exists(fallback)) {
                    path = fallback;
                }
            }
            if (path == null || !Files.exists(path)) return result;

            CompoundTag rootTag;
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path, StandardOpenOption.READ))))) {
                rootTag = NbtIo.read(dis, NbtAccounter.unlimitedHeap());
            } catch (Throwable e) {
                try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ)))) {
                    rootTag = NbtIo.read(dis, NbtAccounter.unlimitedHeap());
                }
            }
            if (rootTag == null || !rootTag.contains("sub_levels", Tag.TAG_LIST)) return result;

            ListTag subList = rootTag.getList("sub_levels", Tag.TAG_COMPOUND);
            for (int i = 0; i < subList.size(); i++) {
                CompoundTag subTag = subList.getCompound(i);
                StructureTemplate subTemplate = new StructureTemplate();
                subTemplate.load(level.holderLookup(Registries.BLOCK), subTag);
                UUID uuid = subTag.contains("uuid") ? subTag.getUUID("uuid") : UUID.randomUUID();
                Vector3d pos = SableNBTUtils.readVector3d(subTag.getCompound("position"));
                Quaterniond rot = SableNBTUtils.readQuaternion(subTag.getCompound("orientation"));
                result.add(new StructureTemplateExtension.SubLevelTemplate(uuid, pos, rot, subTemplate));
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static BlockPos parsePos(CompoundTag entry) {
        if (entry.contains("pos", Tag.TAG_LIST)) {
            ListTag list = entry.getList("pos", Tag.TAG_INT);
            if (list.size() >= 3) {
                return new BlockPos(list.getInt(0), list.getInt(1), list.getInt(2));
            }
            ListTag listD = entry.getList("pos", Tag.TAG_DOUBLE);
            if (listD.size() >= 3) {
                return BlockPos.containing(listD.getDouble(0), listD.getDouble(1), listD.getDouble(2));
            }
        }
        if (entry.contains("pos", Tag.TAG_INT_ARRAY)) {
            int[] arr = entry.getIntArray("pos");
            if (arr.length >= 3) {
                return new BlockPos(arr[0], arr[1], arr[2]);
            }
        }
        if (entry.contains("blockPos", Tag.TAG_LIST)) {
            ListTag list = entry.getList("blockPos", Tag.TAG_INT);
            if (list.size() >= 3) {
                return new BlockPos(list.getInt(0), list.getInt(1), list.getInt(2));
            }
        }
        if (entry.contains("blockPos", Tag.TAG_INT_ARRAY)) {
            int[] arr = entry.getIntArray("blockPos");
            if (arr.length >= 3) {
                return new BlockPos(arr[0], arr[1], arr[2]);
            }
        }
        return BlockPos.ZERO;
    }

    public static double[] computeSchematicBounds(
            SchematicLevel schematicLevel,
            BoundingBox rootBounds
    ) {
        if (rootBounds == null) return null;

        int minX = rootBounds.minX();
        int minY = rootBounds.minY();
        int minZ = rootBounds.minZ();
        int maxX = rootBounds.maxX();
        int maxY = rootBounds.maxY();
        int maxZ = rootBounds.maxZ();

        if (ModList.get().isLoaded("sable")) {
            try {
                if (schematicLevel instanceof SchematicLevelExtension ext) {
                    List<SchematicLevelExtension.SchematicSubLevel> subLevels = ext.sable$getSubLevels();
                    if (subLevels != null) {
                        for (SchematicLevelExtension.SchematicSubLevel sub : subLevels) {
                            BoundingBox sb = sub.level().getBounds();
                            if (sb != null) {
                                Vector3d spos = sub.position();
                                Quaterniond sorient = sub.orientation();
                                int[] xs = {sb.minX(), sb.maxX() + 1};
                                int[] ys = {sb.minY(), sb.maxY() + 1};
                                int[] zs = {sb.minZ(), sb.maxZ() + 1};
                                for (int x : xs) {
                                    for (int y : ys) {
                                        for (int z : zs) {
                                            Vector3d corner = new Vector3d(x, y, z);
                                            if (sorient != null) sorient.transform(corner);
                                            if (spos != null) corner.add(spos);
                                            minX = Math.min(minX, (int) Math.floor(corner.x));
                                            minY = Math.min(minY, (int) Math.floor(corner.y));
                                            minZ = Math.min(minZ, (int) Math.floor(corner.z));
                                            maxX = Math.max(maxX, (int) Math.ceil(corner.x) - 1);
                                            maxY = Math.max(maxY, (int) Math.ceil(corner.y) - 1);
                                            maxZ = Math.max(maxZ, (int) Math.ceil(corner.z) - 1);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        double cx = (minX + maxX + 1) * 0.5;
        double cy = (minY + maxY + 1) * 0.5;
        double cz = (minZ + maxZ + 1) * 0.5;
        double maxDim = Math.max(maxX - minX + 1, Math.max(maxY - minY + 1, maxZ - minZ + 1));

        return new double[] {cx, cy, cz, maxDim};
    }
}
