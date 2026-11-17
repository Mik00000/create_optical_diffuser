package com.mik.optical_diffuser.client.render;

import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.compat.SableCompat;
import com.mik.optical_diffuser.item.HologramLensItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.client.SchematicRenderer;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HologramRenderer {

    private record CachedSchematic(
            String key,
            SchematicRenderer renderer,
            double cx,
            double cy,
            double cz,
            double maxDim,
            long lastAccess
    ) {}

    private static final Map<BlockPos, CachedSchematic> SCHEMATIC_CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        SCHEMATIC_CACHE.clear();
    }

    public static void renderHologram(OpticalDiffuserBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (be == null || be.getLevel() == null || !be.isActive()) return;
        if (be.getActiveModule() != OpticalDiffuserBlockEntity.DiffuserModule.HOLOGRAM) return;

        ItemStack activeLens = be.getActiveLens();
        if (activeLens.isEmpty()) return;

        int distance = HologramLensItem.getFocalDistance(activeLens);
        float scale = HologramLensItem.getHoloScale(activeLens);

        ItemStack target = HologramLensItem.getHoloTarget(activeLens, be.getLevel().registryAccess());
        if (target.isEmpty()) {
            ItemStack reelSlot = be.getInstalledFilmReelItem();
            if (!reelSlot.isEmpty() && !reelSlot.is(com.mik.optical_diffuser.ModRegistry.FILM_REEL.get())) {
                target = reelSlot;
            }
        }

        Direction facing = be.getBlockState().getValue(OpticalDiffuserBlock.FACING);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 localHolo = new Vec3(0.5, 0.5, 0.5).add(normal.scale(distance + 0.52));

        float renderTime = AnimationTickHolder.getRenderTime(be.getLevel());
        float bob = (float) Math.sin((renderTime % 78.53982f) * 0.08f) * 0.06f;

        // Hologram rotation is driven by the bottom shaft (beam-rotation drive), not the left shaft (lens carousel).
        // currentRotation is accumulated in tick() from getEffectiveSpinSpeed() and synced to the client via NBT.
        // We interpolate it with the partial-tick spin speed for butter-smooth rendering.
        float spinSpeed = be.getEffectiveSpinSpeed();
        // 1 RPM → 6 deg/sec → 6/20 = 0.3 deg/tick
        float angle = (be.getCurrentRotation() + spinSpeed * 0.3f * (AnimationTickHolder.getPartialTicks(be.getLevel()))) % 360.0f;
        if (angle < 0.0f) angle += 360.0f;

        float lr = be.getRed() / 255.0f;
        float lg = be.getGreen() / 255.0f;
        float lb = be.getBlue() / 255.0f;

        // 1. Render Projector Focal Guide Lines from lens to hologram center
        renderFocalGuides(poseStack, bufferSource, facing, localHolo, bob, lr, lg, lb);

        // 2. Render Hologram Content
        if (!target.isEmpty() && isCreateSchematic(target)) {
            renderSchematic(be, target, localHolo, bob, angle, scale, poseStack);
        } else if (!target.isEmpty() && target.getItem() instanceof BlockItem blockItem) {
            renderBlock(blockItem.getBlock().defaultBlockState(), localHolo, bob, angle, scale, poseStack, bufferSource);
        } else if (!target.isEmpty()) {
            renderItem(target, localHolo, bob, angle, scale, poseStack, bufferSource, be.getLevel());
        } else {
            renderNavigationalGyro(be, partialTicks, poseStack, bufferSource, localHolo, bob, angle, scale, renderTime, lr, lg, lb);
        }

        // 3. Render Holographic Scanline Disk
        renderScanlineRing(poseStack, bufferSource, localHolo, bob, scale, renderTime, lr, lg, lb);
    }

    private static boolean isCreateSchematic(ItemStack stack) {
        if (stack.getItem() instanceof SchematicItem) {
            return true;
        }
        String name = stack.getItem().toString().toLowerCase();
        return name.contains("schematic");
    }

    private static String getSchematicKey(ItemStack stack) {
        if (stack.isEmpty()) return "";
        return stack.getHoverName().getString() + "_" + stack.getComponentsPatch().hashCode();
    }

    private static BlockPos parseBlockPos(CompoundTag entry) {
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
        return BlockPos.ZERO;
    }

    private static void fixControllerBlockEntities(SchematicLevel schematicLevel) {
        for (BlockEntity be : schematicLevel.getBlockEntities()) {
            if (be instanceof IMultiBlockEntityContainer container) {
                BlockPos lastKnown = container.getLastKnownPos();
                BlockPos current = be.getBlockPos();
                if (lastKnown != null && current != null && !container.isController() && !lastKnown.equals(current)) {
                    BlockPos newController = container.getController().offset(current.subtract(lastKnown));
                    if (container instanceof SmartBlockEntity smart) {
                        smart.markVirtual();
                    }
                    container.setController(newController);
                }
            }
        }
    }

    private static CachedSchematic buildSchematic(
            OpticalDiffuserBlockEntity be,
            ItemStack schematicStack,
            String key,
            long now
    ) {
        Level level = be.getLevel();
        if (level == null) return null;

        try {
            StructureTemplate template = SchematicItem.loadSchematic(level, schematicStack);
            if (template == null || template.getSize().equals(Vec3i.ZERO)) {
                try {
                    var opt = Minecraft.getInstance().getResourceManager().getResource(
                            ResourceLocation.fromNamespaceAndPath("create_optical_diffuser", "structures/demo_structure.nbt")
                    );
                    if (opt.isPresent()) {
                        try (var is = opt.get().open()) {
                            CompoundTag tag = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
                            template = new StructureTemplate();
                            template.load(level.holderLookup(Registries.BLOCK), tag);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (template == null || template.getSize().equals(Vec3i.ZERO)) return null;

            // Anchor at BlockPos.ZERO ensures relative coordinates in SchematicRenderer match blockEntities!
            SchematicLevel schematicLevel = new SchematicLevel(BlockPos.ZERO, level);
            StructurePlaceSettings settings = SchematicItem.getSettings(schematicStack);

            // 1. Place static blocks from template
            template.placeInWorld(schematicLevel, BlockPos.ZERO, BlockPos.ZERO, settings, level.getRandom(), 2);

            // 2. Extract and place contraptions (e.g. Mechanical Bearing Turrets, CBC Cannons)
            CompoundTag templateTag = template.save(new CompoundTag());
            ListTag entitiesTag = templateTag.getList("entities", Tag.TAG_COMPOUND);

            StructureTransform transform = new StructureTransform(
                    settings.getRotationPivot(),
                    Direction.Axis.Y,
                    settings.getRotation(),
                    settings.getMirror()
            );

            for (int i = 0; i < entitiesTag.size(); i++) {
                CompoundTag entityEntry = entitiesTag.getCompound(i);
                CompoundTag nbt = entityEntry.getCompound("nbt");
                String id = nbt.getString("id");
                if (id.contains("contraption")) {
                    CompoundTag contraptionTag = nbt.getCompound("Contraption");
                    if (!contraptionTag.isEmpty()) {
                        try {
                            Contraption contraption = Contraption.fromNBT(level, contraptionTag, false);
                            if (contraption != null) {
                                BlockPos basePos = parseBlockPos(entityEntry);

                                // 2a. Add contraption blocks (e.g. turret blocks, deployers, barrels)
                                for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry : contraption.getBlocks().entrySet()) {
                                    BlockPos relPos = entry.getKey();
                                    StructureTemplate.StructureBlockInfo info = entry.getValue();
                                    BlockPos placedPos = transform.apply(basePos.offset(relPos));
                                    BlockState placedState = transform.apply(info.state());
                                    schematicLevel.setBlock(placedPos, placedState, 2);
                                    if (info.nbt() != null) {
                                        BlockEntity cbe = schematicLevel.getBlockEntity(placedPos);
                                        if (cbe != null) {
                                            cbe.loadWithComponents(info.nbt(), level.registryAccess());
                                        }
                                    }
                                }

                                // 2b. Add contraption actors (e.g. CBC cannon barrels, mounts, drills)
                                for (var actorPair : contraption.getActors()) {
                                    if (actorPair != null && actorPair.left != null) {
                                        StructureTemplate.StructureBlockInfo info = actorPair.left;
                                        BlockPos placedPos = transform.apply(basePos.offset(info.pos()));
                                        BlockState placedState = transform.apply(info.state());
                                        schematicLevel.setBlock(placedPos, placedState, 2);
                                        if (info.nbt() != null) {
                                            BlockEntity abe = schematicLevel.getBlockEntity(placedPos);
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

            // 3. Ensure all Copycat blocks have their materials initialized for ModelData
            ListTag blocksTag = templateTag.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag bEntry = blocksTag.getCompound(i);
                if (bEntry.contains("nbt")) {
                    CompoundTag bNbt = bEntry.getCompound("nbt");
                    if (bNbt.contains("Material")) {
                        BlockPos rawPos = parseBlockPos(bEntry);
                        BlockPos placedPos = transform.apply(rawPos);
                        BlockEntity beAt = schematicLevel.getBlockEntity(placedPos);
                        if (beAt instanceof CopycatBlockEntity copycat) {
                            if (!copycat.hasCustomMaterial()) {
                                BlockState mat = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), bNbt.getCompound("Material"));
                                if (mat != null && !mat.isAir()) {
                                    copycat.setMaterial(mat);
                                }
                            }
                        }
                    }
                }
            }

            // 4. Apply structure transforms and multi-block fixes to all rendered block entities
            for (BlockEntity renderedBE : schematicLevel.getRenderedBlockEntities()) {
                transform.apply(renderedBE);
            }
            fixControllerBlockEntities(schematicLevel);

            // 5. Populate Sable sub-levels (e.g. ship hulls, tank chassis, tracks, diesel engines)
            SableCompat.populateSchematicSubLevels(schematicStack, template, schematicLevel, level);

            BoundingBox bounds = schematicLevel.getBounds();
            if (bounds == null) return null;

            double[] boundData = SableCompat.computeSchematicBounds(schematicLevel, bounds);
            double cx = boundData != null ? boundData[0] : (bounds.minX() + bounds.maxX() + 1) * 0.5;
            double cy = boundData != null ? boundData[1] : (bounds.minY() + bounds.maxY() + 1) * 0.5;
            double cz = boundData != null ? boundData[2] : (bounds.minZ() + bounds.maxZ() + 1) * 0.5;
            double maxDim = boundData != null ? boundData[3] : Math.max(bounds.getXSpan(), Math.max(bounds.getYSpan(), bounds.getZSpan()));

            SchematicRenderer renderer = new SchematicRenderer(schematicLevel);

            return new CachedSchematic(key, renderer, cx, cy, cz, maxDim, now);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void renderSchematic(
            OpticalDiffuserBlockEntity be,
            ItemStack schematicStack,
            Vec3 localHolo,
            float bob,
            float angle,
            float scale,
            PoseStack poseStack
    ) {
        if (be == null || be.getLevel() == null) return;
        BlockPos bePos = be.getBlockPos();
        String key = getSchematicKey(schematicStack);

        CachedSchematic cached = SCHEMATIC_CACHE.get(bePos);
        long now = System.currentTimeMillis();

        if (cached == null || !cached.key.equals(key)) {
            cached = buildSchematic(be, schematicStack, key, now);
            if (cached != null) {
                SCHEMATIC_CACHE.put(bePos, cached);
            }
        }

        if (cached == null) return;

        // Auto-cleanup stale entries periodically
        if (SCHEMATIC_CACHE.size() > 16 && (now & 0xFF) == 0) {
            SCHEMATIC_CACHE.entrySet().removeIf(entry -> now - entry.getValue().lastAccess > 30000L);
        }

        float autoScale = (float) (cached.maxDim > 3.0 ? (3.0 / cached.maxDim) * scale : scale);

        poseStack.pushPose();
        poseStack.translate(localHolo.x, localHolo.y + bob, localHolo.z);
        poseStack.scale(autoScale, autoScale, autoScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.translate(-cached.cx, -cached.cy, -cached.cz);

        cached.renderer.render(poseStack, DefaultSuperRenderTypeBuffer.getInstance());
        DefaultSuperRenderTypeBuffer.getInstance().draw();

        poseStack.popPose();
    }

    private static void renderBlock(
            BlockState state,
            Vec3 localHolo,
            float bob,
            float angle,
            float scale,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        poseStack.pushPose();
        poseStack.translate(localHolo.x, localHolo.y + bob, localHolo.z);
        float s = scale * 1.35f;
        poseStack.scale(s, s, s);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.translate(-0.5, -0.5, -0.5);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state,
                poseStack,
                bufferSource,
                15728880,
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                null
        );

        poseStack.popPose();
    }

    private static void renderItem(
            ItemStack target,
            Vec3 localHolo,
            float bob,
            float angle,
            float scale,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            net.minecraft.world.level.Level level
    ) {
        poseStack.pushPose();
        poseStack.translate(localHolo.x, localHolo.y + bob, localHolo.z);
        float s = scale * 1.5f;
        poseStack.scale(s, s, s);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));

        Minecraft.getInstance().getItemRenderer().renderStatic(
                target,
                ItemDisplayContext.FIXED,
                15728880,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                level,
                0
        );

        poseStack.popPose();
    }

    private static void renderNavigationalGyro(
            OpticalDiffuserBlockEntity be,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 localHolo,
            float bob,
            float angle,
            float scale,
            float time,
            float r,
            float g,
            float b
    ) {
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentCull(
                ResourceLocation.fromNamespaceAndPath("create_optical_diffuser", "textures/block/lead_glass.png")
        ));

        poseStack.pushPose();
        poseStack.translate(localHolo.x, localHolo.y + bob, localHolo.z);
        float s = scale * 1.2f;
        poseStack.scale(s, s, s);

        float spinSpeed = be.getEffectiveSpinSpeed();
        // If kinetic drive connected, rotate with shaft; otherwise gentle idle rotor spin so it never looks frozen
        float ringSpin = (Math.abs(spinSpeed) > 0.01f) ? angle : (time * 1.2f) % 360.0f;

        // Query Sable ship orientation (if mounted on a ship contraption)
        Quaternionf shipRot = SableCompat.getShipRotation(be, partialTicks);
        // Inverse quaternion transforms from world orientation to ship local orientation
        Quaternionf qLocal = (shipRot != null) ? new Quaternionf(shipRot).invert() : new Quaternionf();

        // 1. Outer gimbal ring:
        // Mount ring in the local plane of the diffuser/ship, spins with rotor axis
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(ringSpin));
        renderWireRing(poseStack.last().pose(), vc, 0.95f, 0.03f, r, g, b, 0.85f);
        poseStack.popPose();

        // 2. Middle gimbal ring (attitude / horizon gimbal):
        // Stabilized in pitch & roll, staying parallel to world horizon, rotating with gimbal bearings
        poseStack.pushPose();
        poseStack.mulPose(qLocal);
        poseStack.mulPose(Axis.YP.rotationDegrees(-ringSpin * 1.3f));
        renderWireRing(poseStack.last().pose(), vc, 0.75f, 0.03f, r * 0.9f, g * 0.95f, b, 0.80f);
        renderCardinalTicks(poseStack.last().pose(), vc, 0.75f, r, g, b);
        poseStack.popPose();

        // 3. Inner 3D cardinal compass needle:
        // Fully rotates across ALL axes (pitch, roll, yaw) to continuously point to True World North!
        poseStack.pushPose();
        poseStack.mulPose(qLocal);
        // Subtle natural magnetic compass needle oscillation (around needle's own vertical axis)
        float compassWobble = (float) Math.sin((time % 125.6637f) * 0.05f) * 1.2f;
        poseStack.mulPose(Axis.YP.rotationDegrees(compassWobble));
        renderCompassNeedle(poseStack.last().pose(), vc, r, g, b);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void renderCardinalTicks(Matrix4f mat, VertexConsumer vc, float radius, float r, float g, float b) {
        float tLen = 0.08f;
        float tWidth = 0.02f;

        // North tick (Crimson, points -Z)
        vc.addVertex(mat, -tWidth, 0.01f, -radius).setColor(1.0f, 0.25f, 0.25f, 0.95f).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, tWidth, 0.01f, -radius).setColor(1.0f, 0.25f, 0.25f, 0.95f).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, tWidth, 0.01f, -(radius - tLen)).setColor(1.0f, 0.25f, 0.25f, 0.95f).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, -tWidth, 0.01f, -(radius - tLen)).setColor(1.0f, 0.25f, 0.25f, 0.95f).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);

        // South tick (+Z)
        vc.addVertex(mat, -tWidth, 0.01f, radius - tLen).setColor(r * 0.7f, g * 0.7f, b * 0.9f, 0.85f).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, tWidth, 0.01f, radius - tLen).setColor(r * 0.7f, g * 0.7f, b * 0.9f, 0.85f).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, tWidth, 0.01f, radius).setColor(r * 0.7f, g * 0.7f, b * 0.9f, 0.85f).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, -tWidth, 0.01f, radius).setColor(r * 0.7f, g * 0.7f, b * 0.9f, 0.85f).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);

        // East tick (+X)
        vc.addVertex(mat, radius - tLen, 0.01f, -tWidth).setColor(r * 0.8f, g * 0.85f, b, 0.85f).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, radius, 0.01f, -tWidth).setColor(r * 0.8f, g * 0.85f, b, 0.85f).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, radius, 0.01f, tWidth).setColor(r * 0.8f, g * 0.85f, b, 0.85f).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, radius - tLen, 0.01f, tWidth).setColor(r * 0.8f, g * 0.85f, b, 0.85f).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);

        // West tick (-X)
        vc.addVertex(mat, -radius, 0.01f, -tWidth).setColor(r * 0.8f, g * 0.85f, b, 0.85f).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, -(radius - tLen), 0.01f, -tWidth).setColor(r * 0.8f, g * 0.85f, b, 0.85f).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, -(radius - tLen), 0.01f, tWidth).setColor(r * 0.8f, g * 0.85f, b, 0.85f).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, -radius, 0.01f, tWidth).setColor(r * 0.8f, g * 0.85f, b, 0.85f).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
    }

    private static void renderWireRing(Matrix4f mat, VertexConsumer vc, float radius, float thickness, float r, float g, float b, float a) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            double a1 = (i / (double) segments) * Math.PI * 2.0;
            double a2 = ((i + 1) / (double) segments) * Math.PI * 2.0;

            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            float x2 = (float) Math.cos(a2) * radius;
            float z2 = (float) Math.sin(a2) * radius;

            vc.addVertex(mat, x1, -thickness, z1).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
            vc.addVertex(mat, x2, -thickness, z2).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
            vc.addVertex(mat, x2, thickness, z2).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
            vc.addVertex(mat, x1, thickness, z1).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        }
    }

    private static void renderCompassNeedle(Matrix4f mat, VertexConsumer vc, float r, float g, float b) {
        // True North pointer arrow (golden-crimson red tip pointing along -Z)
        float tipZ = -0.55f;
        float baseZ = 0.45f;
        float w = 0.12f;
        float h = 0.035f; // 3D ridge height

        // --- North Tip: Vibrant Crimson / Red (Points True North) ---
        // 1. Top-left face
        vc.addVertex(mat, 0, h, 0).setColor(1.0f, 0.28f, 0.28f, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, 0, 0, tipZ).setColor(1.0f, 0.18f, 0.18f, 0.98f).setUv(0.5f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, -w, 0, 0).setColor(0.9f, 0.22f, 0.22f, 0.90f).setUv(0.0f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, 0, h, 0).setColor(1.0f, 0.28f, 0.28f, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);

        // 2. Top-right face
        vc.addVertex(mat, 0, h, 0).setColor(1.0f, 0.28f, 0.28f, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, w, 0, 0).setColor(0.9f, 0.22f, 0.22f, 0.90f).setUv(1.0f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, 0, 0, tipZ).setColor(1.0f, 0.18f, 0.18f, 0.98f).setUv(0.5f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, 0, h, 0).setColor(1.0f, 0.28f, 0.28f, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);

        // 3. Bottom-left face
        vc.addVertex(mat, 0, -h, 0).setColor(0.85f, 0.15f, 0.15f, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, -w, 0, 0).setColor(0.80f, 0.15f, 0.15f, 0.90f).setUv(0.0f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, 0, 0, tipZ).setColor(0.85f, 0.15f, 0.15f, 0.98f).setUv(0.5f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, 0, -h, 0).setColor(0.85f, 0.15f, 0.15f, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);

        // 4. Bottom-right face
        vc.addVertex(mat, 0, -h, 0).setColor(0.85f, 0.15f, 0.15f, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, 0, 0, tipZ).setColor(0.85f, 0.15f, 0.15f, 0.98f).setUv(0.5f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, w, 0, 0).setColor(0.80f, 0.15f, 0.15f, 0.90f).setUv(1.0f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, 0, -h, 0).setColor(0.85f, 0.15f, 0.15f, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);

        // --- South Tail: Cyan / Beam Tint (Points South) ---
        // 5. Top-left face
        vc.addVertex(mat, 0, h, 0).setColor(r, g, b, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, -w, 0, 0).setColor(r * 0.8f, g * 0.8f, b * 0.9f, 0.85f).setUv(0.0f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, 0, 0, baseZ).setColor(r * 0.6f, g * 0.6f, b * 0.8f, 0.85f).setUv(0.5f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, 0, h, 0).setColor(r, g, b, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);

        // 6. Top-right face
        vc.addVertex(mat, 0, h, 0).setColor(r, g, b, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, 0, 0, baseZ).setColor(r * 0.6f, g * 0.6f, b * 0.8f, 0.85f).setUv(0.5f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, w, 0, 0).setColor(r * 0.8f, g * 0.8f, b * 0.9f, 0.85f).setUv(1.0f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, 0, h, 0).setColor(r, g, b, 0.95f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);

        // 7. Bottom-left face
        vc.addVertex(mat, 0, -h, 0).setColor(r * 0.7f, g * 0.7f, b * 0.8f, 0.90f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, 0, 0, baseZ).setColor(r * 0.5f, g * 0.5f, b * 0.7f, 0.80f).setUv(0.5f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, -w, 0, 0).setColor(r * 0.6f, g * 0.6f, b * 0.7f, 0.80f).setUv(0.0f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, 0, -h, 0).setColor(r * 0.7f, g * 0.7f, b * 0.8f, 0.90f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);

        // 8. Bottom-right face
        vc.addVertex(mat, 0, -h, 0).setColor(r * 0.7f, g * 0.7f, b * 0.8f, 0.90f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, w, 0, 0).setColor(r * 0.6f, g * 0.6f, b * 0.7f, 0.80f).setUv(1.0f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, 0, 0, baseZ).setColor(r * 0.5f, g * 0.5f, b * 0.7f, 0.80f).setUv(0.5f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, 0, -h, 0).setColor(r * 0.7f, g * 0.7f, b * 0.8f, 0.90f).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);

        // Center pivot jewel (faceted octahedron)
        float jw = 0.05f;
        float jh = 0.05f;
        // Top cap of jewel
        vc.addVertex(mat, 0, jh, 0).setColor(1.0f, 0.95f, 0.7f, 0.95f).setUv(0.5f, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, -jw, 0, 0).setColor(1.0f, 0.85f, 0.5f, 0.90f).setUv(0, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, 0, 0, jw).setColor(1.0f, 0.85f, 0.5f, 0.90f).setUv(0.5f, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        vc.addVertex(mat, jw, 0, 0).setColor(1.0f, 0.85f, 0.5f, 0.90f).setUv(1, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);

        // Bottom cap of jewel
        vc.addVertex(mat, 0, -jh, 0).setColor(1.0f, 0.95f, 0.7f, 0.95f).setUv(0.5f, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, jw, 0, 0).setColor(1.0f, 0.85f, 0.5f, 0.90f).setUv(1, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, 0, 0, jw).setColor(1.0f, 0.85f, 0.5f, 0.90f).setUv(0.5f, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
        vc.addVertex(mat, -jw, 0, 0).setColor(1.0f, 0.85f, 0.5f, 0.90f).setUv(0, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, -1, 0);
    }

    private static void renderFocalGuides(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Direction facing,
            Vec3 localHolo,
            float bob,
            float r,
            float g,
            float b
    ) {
        VertexConsumer vc = bufferSource.getBuffer(OpticalDiffuserRenderer.SPOTLIGHT_BEAM);
        Matrix4f mat = poseStack.last().pose();

        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 apertureCenter = new Vec3(0.5, 0.5, 0.5).add(normal.scale(0.52));

        Vec3 up = switch (facing) {
            case UP -> new Vec3(0, 0, -1);
            case DOWN -> new Vec3(0, 0, 1);
            default -> new Vec3(0, 1, 0);
        };
        Vec3 right = up.cross(normal).normalize();

        float rAperture = 0.28f;
        Vec3[] apCorners = new Vec3[] {
                apertureCenter.add(right.scale(rAperture)).add(up.scale(rAperture)),
                apertureCenter.add(right.scale(-rAperture)).add(up.scale(rAperture)),
                apertureCenter.add(right.scale(-rAperture)).add(up.scale(-rAperture)),
                apertureCenter.add(right.scale(rAperture)).add(up.scale(-rAperture))
        };

        Vec3 hCenter = new Vec3(localHolo.x, localHolo.y + bob, localHolo.z);
        float rWaist = 0.15f;
        Vec3[] hCorners = new Vec3[] {
                hCenter.add(right.scale(rWaist)).add(up.scale(rWaist)),
                hCenter.add(right.scale(-rWaist)).add(up.scale(rWaist)),
                hCenter.add(right.scale(-rWaist)).add(up.scale(-rWaist)),
                hCenter.add(right.scale(rWaist)).add(up.scale(-rWaist))
        };

        float alpha = 0.20f;
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            vc.addVertex(mat, (float) apCorners[i].x, (float) apCorners[i].y, (float) apCorners[i].z)
                    .setColor(r, g, b, alpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
            vc.addVertex(mat, (float) apCorners[next].x, (float) apCorners[next].y, (float) apCorners[next].z)
                    .setColor(r, g, b, alpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
            vc.addVertex(mat, (float) hCorners[next].x, (float) hCorners[next].y, (float) hCorners[next].z)
                    .setColor(r, g, b, alpha * 0.5f).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
            vc.addVertex(mat, (float) hCorners[i].x, (float) hCorners[i].y, (float) hCorners[i].z)
                    .setColor(r, g, b, alpha * 0.5f).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        }
    }

    private static void renderScanlineRing(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 localHolo,
            float bob,
            float scale,
            float time,
            float r,
            float g,
            float b
    ) {
        VertexConsumer vc = bufferSource.getBuffer(OpticalDiffuserRenderer.SPOTLIGHT_BEAM);
        Matrix4f mat = poseStack.last().pose();

        float scanY = (((time % 50.0f) * 0.04f) % 2.0f - 1.0f) * scale * 0.8f;
        float ringRadius = 0.85f * scale;
        int segments = 20;

        for (int i = 0; i < segments; i++) {
            double a1 = (i / (double) segments) * Math.PI * 2.0;
            double a2 = ((i + 1) / (double) segments) * Math.PI * 2.0;

            float x1 = (float) (localHolo.x + Math.cos(a1) * ringRadius);
            float z1 = (float) (localHolo.z + Math.sin(a1) * ringRadius);
            float x2 = (float) (localHolo.x + Math.cos(a2) * ringRadius);
            float z2 = (float) (localHolo.z + Math.sin(a2) * ringRadius);

            float y = (float) (localHolo.y + bob + scanY);

            vc.addVertex(mat, x1, y - 0.02f, z1).setColor(r, g, b, 0.45f).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
            vc.addVertex(mat, x2, y - 0.02f, z2).setColor(r, g, b, 0.45f).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
            vc.addVertex(mat, x2, y + 0.02f, z2).setColor(r, g, b, 0.45f).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
            vc.addVertex(mat, x1, y + 0.02f, z1).setColor(r, g, b, 0.45f).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
        }
    }
}
