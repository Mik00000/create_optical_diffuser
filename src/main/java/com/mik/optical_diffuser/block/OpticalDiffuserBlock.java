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

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        Direction facing = state.getValue(FACING);
        if (facing == Direction.DOWN) {
            return face == Direction.NORTH;
        }
        if (facing == Direction.UP) {
            return face == Direction.SOUTH;
        }
        return face == Direction.DOWN;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        Direction facing = state.getValue(FACING);
        if (facing == Direction.DOWN || facing == Direction.UP) {
            return Direction.Axis.Z;
        }
        return Direction.Axis.Y;
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
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.contains("wrench");
    }

    private boolean isLensItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("create_optical_diffuser:focusing_lens")
                || id.equals("create_optical_diffuser:dispersing_lens")
                || id.equals("create_optical:beam_focuser")
                || id.equals("create_optical:beam_condenser");
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Allow Create Wrench to handle rotation / dismantling
        if (isWrenchItem(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OpticalDiffuserBlockEntity diffuser) {
            if (isLensItem(stack)) {
                if (!level.isClientSide) {
                    ItemStack oldModule = diffuser.getInstalledModuleItem();
                    ItemStack newModule = stack.copyWithCount(1);
                    diffuser.setInstalledModuleItem(newModule);

                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }

                    if (!oldModule.isEmpty()) {
                        if (!player.getInventory().add(oldModule)) {
                            Block.popResource(level, pos, oldModule);
                        }
                    }

                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.2f);
                    player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.module_installed", newModule.getHoverName()).withStyle(ChatFormatting.GOLD), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Shift + empty hand OR empty hand ПКМ removes installed lens/module
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OpticalDiffuserBlockEntity diffuser) {
            ItemStack installed = diffuser.getInstalledModuleItem();
            if (!installed.isEmpty()) {
                if (!level.isClientSide) {
                    diffuser.setInstalledModuleItem(ItemStack.EMPTY);
                    if (!player.getInventory().add(installed)) {
                        Block.popResource(level, pos, installed);
                    }
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                    player.displayClientMessage(Component.translatable("chat.create_optical_diffuser.module_removed", installed.getHoverName()).withStyle(ChatFormatting.YELLOW), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
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
                ItemStack module = diffuser.getInstalledModuleItem();
                if (!module.isEmpty()) {
                    Block.popResource(level, pos, module);
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
