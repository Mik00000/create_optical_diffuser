package com.mik.optical_diffuser.block;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.lpcamors.optical.blocks.optical_source.BeamHelper;
import net.lpcamors.optical.blocks.optical_source.GenericOpticalSourceBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class OpticalDiffuserBlock extends KineticBlock implements IBE<OpticalDiffuserBlockEntity>, GenericOpticalSourceBlockEntity.IBeamActivator {

    public static final MapCodec<OpticalDiffuserBlock> CODEC = simpleCodec(OpticalDiffuserBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty BEAMING = BooleanProperty.create("beaming");

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public OpticalDiffuserBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BEAMING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BEAMING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    public static Direction getLeftDirection(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;
            case SOUTH -> Direction.EAST;
            case WEST  -> Direction.SOUTH;
            case EAST  -> Direction.NORTH;
            case UP    -> Direction.WEST;
            case DOWN  -> Direction.WEST;
        };
    }

    public static Direction getRightDirection(Direction facing) {
        return getLeftDirection(facing).getOpposite();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        Direction facing = state.getValue(FACING);
        Direction left = getLeftDirection(facing);
        // Kinetic shaft connects strictly to the left face (lens turret carousel drive)
        return face == left;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction left = getLeftDirection(facing);
        return left.getAxis();
    }

    @Override
    public Class<OpticalDiffuserBlockEntity> getBlockEntityClass() {
        return OpticalDiffuserBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends OpticalDiffuserBlockEntity> getBlockEntityType() {
        return ModRegistry.OPTICAL_DIFFUSER_BE.get();
    }

    private boolean isWrenchItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (com.simibubi.create.AllItems.WRENCH.isIn(stack)) return true;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.contains("wrench");
    }

    private boolean isLensItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("create_optical_diffuser:focusing_lens")
                || id.equals("create_optical_diffuser:dispersing_lens")
                || id.equals("create_optical_diffuser:silent_projector_lens")
                || id.equals("create_optical_diffuser:sound_projector_lens")
                || id.equals("create_optical:beam_focuser")
                || id.equals("create_optical:beam_condenser");
    }

    public static int getTargetCarouselSlot(BlockState state, BlockPos pos, Direction leftFace, Vec3 hitLocation, float carouselAngle) {
        Vec3 localPos = hitLocation.subtract(Vec3.atLowerCornerOf(pos));
        double u, v;

        switch (leftFace) {
            case SOUTH -> {
                u = localPos.x - 0.5;
                v = localPos.y - 0.5;
            }
            case NORTH -> {
                u = -(localPos.x - 0.5);
                v = localPos.y - 0.5;
            }
            case EAST -> {
                u = -(localPos.z - 0.5);
                v = localPos.y - 0.5;
            }
            case WEST -> {
                u = localPos.z - 0.5;
                v = localPos.y - 0.5;
            }
            case UP -> {
                u = localPos.x - 0.5;
                v = -(localPos.z - 0.5);
            }
            case DOWN -> {
                u = localPos.x - 0.5;
                v = localPos.z - 0.5;
            }
            default -> {
                return -1;
            }
        }

        double socketRadius = 0.26;
        float[] slotAngles = new float[]{0.0f, 90.0f, 180.0f, 270.0f};

        int bestSlot = -1;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < 4; i++) {
            double rad = Math.toRadians(slotAngles[i] + carouselAngle);
            double sx = Math.sin(rad) * socketRadius;
            double sy = Math.cos(rad) * socketRadius;
            double d = Math.sqrt((u - sx) * (u - sx) + (v - sy) * (v - sy));
            if (d < minDistance) {
                minDistance = d;
                bestSlot = i;
            }
        }

        // Target slot radius: 0.18 blocks around socket center
        if (minDistance <= 0.18) {
            return bestSlot;
        }
        return -1;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        Direction hitFace = hit.getDirection();
        Direction leftFace = getLeftDirection(state.getValue(FACING));

        // 1. Right-click with Wrench on carousel face: rotate carousel 90 degrees (crouching allows dismantle)
        if (isWrenchItem(stack) && hitFace == leftFace) {
            if (player.isShiftKeyDown()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof OpticalDiffuserBlockEntity diffuser) {
                    diffuser.rotateCarousel(90.0f);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SPYGLASS_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.9f, 1.3f);
                    int activeSlot = diffuser.getActiveCarouselSlot();
                    ItemStack active = diffuser.getCarouselLens(activeSlot);
                    if (!active.isEmpty()) {
                        player.displayClientMessage(
                                Component.literal("Carousel rotated 90° - Active Slot " + (activeSlot + 1) + ": ")
                                        .withStyle(ChatFormatting.GOLD)
                                        .append(active.getHoverName().copy().withStyle(ChatFormatting.YELLOW)),
                                true
                        );
                    } else {
                        player.displayClientMessage(
                                Component.literal("Carousel rotated 90° - Active Slot " + (activeSlot + 1) + " (Empty)")
                                        .withStyle(ChatFormatting.GRAY),
                                true
                        );
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // Create Wrench on other faces handles rotation / dismantling / speaker linking
        if (isWrenchItem(stack)) {
            if (level.isClientSide) {
                com.mik.optical_diffuser.client.DiffuserSelectionTracker.selectDiffuser(pos);
                player.displayClientMessage(
                        Component.literal("Selected Optical Diffuser for Speaker linking. Right-click a Speaker!").withStyle(ChatFormatting.AQUA),
                        true
                );
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 2. Lens installation and swapping strictly on carousel sockets
        if (isLensItem(stack)) {
            if (hitFace != leftFace) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OpticalDiffuserBlockEntity diffuser) {
                int targetSlot = getTargetCarouselSlot(state, pos, leftFace, hit.getLocation(), diffuser.getCarouselAngle());
                if (targetSlot >= 0 && targetSlot < 4) {
                    if (!level.isClientSide) {
                        ItemStack newLens = stack.copyWithCount(1);
                        ItemStack existing = diffuser.getCarouselLens(targetSlot);

                        diffuser.setCarouselLens(targetSlot, newLens);

                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }

                        if (!existing.isEmpty()) {
                            if (!player.getInventory().add(existing)) {
                                Block.popResource(level, pos, existing);
                            }
                        }

                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.2f);
                        player.displayClientMessage(
                                Component.translatable("chat.create_optical_diffuser.module_installed", newLens.getHoverName())
                                        .withStyle(ChatFormatting.GOLD),
                                true
                        );
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction hitFace = hit.getDirection();
        Direction leftFace = getLeftDirection(state.getValue(FACING));

        // 3. Remove lens from specific slot when clicking with empty hand
        if (hitFace == leftFace && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OpticalDiffuserBlockEntity diffuser) {
                int targetSlot = getTargetCarouselSlot(state, pos, leftFace, hit.getLocation(), diffuser.getCarouselAngle());
                if (targetSlot >= 0 && targetSlot < 4) {
                    ItemStack existing = diffuser.getCarouselLens(targetSlot);
                    if (!existing.isEmpty()) {
                        if (!level.isClientSide) {
                            ItemStack removed = diffuser.removeCarouselLens(targetSlot);
                            if (!removed.isEmpty()) {
                                if (!player.getInventory().add(removed)) {
                                    Block.popResource(level, pos, removed);
                                }
                                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                                player.displayClientMessage(
                                        Component.translatable("chat.create_optical_diffuser.module_removed", removed.getHoverName())
                                                .withStyle(ChatFormatting.YELLOW),
                                        true
                                );
                            }
                        }
                        return InteractionResult.sidedSuccess(level.isClientSide);
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (!level.isClientSide) {
            int power = level.getBestNeighborSignal(pos);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OpticalDiffuserBlockEntity diffuser) {
                diffuser.onRedstonePowerChanged(power);
                diffuser.updateColorAndSpeed();
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            int power = level.getBestNeighborSignal(pos);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OpticalDiffuserBlockEntity diffuser) {
                diffuser.onRedstonePowerChanged(power);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OpticalDiffuserBlockEntity diffuser) {
                diffuser.clearIllumination();
                for (int i = 0; i < 4; i++) {
                    ItemStack lens = diffuser.getCarouselLens(i);
                    if (!lens.isEmpty()) {
                        Block.popResource(level, pos, lens);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OpticalDiffuserBlockEntity diffuser) {
            if (!diffuser.isActive() || !diffuser.hasBeam()) {
                return 0;
            }
            return Math.min(15, Math.max(1, (int) Math.ceil((diffuser.getCachedSpeed() / 256.0f) * 15)));
        }
        return 0;
    }

    // --- IBeamActivator Implementation ---

    @Override
    public boolean canReceive(Level level, BlockState state, BlockPos pos, BeamHelper.BeamProperties properties) {
        // Laser must enter strictly through the rear port (opposite of front lens)
        Direction facing = state.getValue(FACING);
        return properties.direction() == facing;
    }

    @Override
    public void onReceiveBeam(Level level, BlockState state, BlockPos pos, BeamHelper.BeamProperties properties) {
        Direction facing = state.getValue(FACING);
        if (properties.direction() != facing) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OpticalDiffuserBlockEntity diffuser) {
            diffuser.setBeam(properties);
            boolean shouldBeam = diffuser.isLightEnabled();
            if (state.getValue(BEAMING) != shouldBeam) {
                level.setBlock(pos, state.setValue(BEAMING, shouldBeam), 2);
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

    @Override
    public void onUpdateBeam(Level level, BlockState state, BlockPos pos, BeamHelper.BeamProperties properties) {
        Direction facing = state.getValue(FACING);
        if (properties.direction() != facing) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OpticalDiffuserBlockEntity diffuser) {
            diffuser.setBeam(properties);
            boolean shouldBeam = diffuser.isLightEnabled();
            if (state.getValue(BEAMING) != shouldBeam) {
                level.setBlock(pos, state.setValue(BEAMING, shouldBeam), 2);
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

    @Override
    public void onRemoveBeam(Level level, BlockState state, BlockPos pos, BeamHelper.BeamProperties properties) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OpticalDiffuserBlockEntity diffuser) {
            diffuser.removeBeam();
            if (state.getValue(BEAMING)) {
                level.setBlock(pos, state.setValue(BEAMING, false), 2);
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

    @Override
    public BeamHelper.BeamProperties transformProperties(Level level, BlockState state, BlockPos pos, BeamHelper.BeamProperties properties, GenericOpticalSourceBlockEntity source, int remainingSteps) {
        return null;
    }

    @Override
    public AABB getNonVisibleAABB(Level level, BlockState state, BlockPos pos) {
        return null;
    }
}
