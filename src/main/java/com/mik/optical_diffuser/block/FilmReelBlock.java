package com.mik.optical_diffuser.block;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.block.entity.FilmReelBlockEntity;
import com.mik.optical_diffuser.item.FilmReelBlockItem;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class FilmReelBlock extends KineticBlock implements IBE<FilmReelBlockEntity> {

    public static final MapCodec<FilmReelBlock> CODEC = simpleCodec(FilmReelBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE_X = Block.box(2.0, 2.0, 2.0, 14.0, 14.0, 14.0);
    private static final VoxelShape SHAPE_Z = Block.box(2.0, 2.0, 2.0, 14.0, 14.0, 14.0);

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public FilmReelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction.Axis axis = getRotationAxis(state);
        return axis == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // Must strictly be placed on the right side of an adjacent Optical Diffuser
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof OpticalDiffuserBlock) {
                Direction diffuserFacing = neighborState.getValue(OpticalDiffuserBlock.FACING);
                Direction expectedRight = OpticalDiffuserBlock.getRightDirection(diffuserFacing);
                if (dir == expectedRight.getOpposite()) {
                    return this.defaultBlockState().setValue(FACING, diffuserFacing);
                }
            }
        }

        if (level.isClientSide && context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(
                    Component.literal("Film Reel must be placed against the right side of an Optical Diffuser!")
                            .withStyle(ChatFormatting.RED),
                    true
            );
        }
        return null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction diffuserFacing = state.getValue(FACING);
        Direction expectedRight = OpticalDiffuserBlock.getRightDirection(diffuserFacing);
        BlockPos diffuserPos = pos.relative(expectedRight.getOpposite());
        BlockState diffuserState = level.getBlockState(diffuserPos);
        return diffuserState.getBlock() instanceof OpticalDiffuserBlock
                && diffuserState.getValue(OpticalDiffuserBlock.FACING) == diffuserFacing;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);
        if (!state.canSurvive(level, pos)) {
            if (!level.isClientSide) {
                dropResources(state, level, pos, level.getBlockEntity(pos));
                level.removeBlock(pos, false);
            }
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FilmReelBlockEntity reelBe) {
            reelBe.setUrl(FilmReelBlockItem.getUrl(stack));
            reelBe.setCurrentTimeMs(FilmReelBlockItem.getCurrentTimeMs(stack));
        }
    }

    public ItemStack getItemWithData(FilmReelBlockEntity be) {
        ItemStack stack = new ItemStack(ModRegistry.FILM_REEL.get());
        if (be != null) {
            FilmReelBlockItem.setData(stack, be.getUrl(), be.getCurrentTimeMs());
        }
        return stack;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FilmReelBlockEntity reelBe) {
                ItemStack drop = getItemWithData(reelBe);
                popResource(level, pos, drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FilmReelBlockEntity reelBe) {
            return getItemWithData(reelBe);
        }
        return new ItemStack(ModRegistry.FILM_REEL.get());
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        Direction diffuserFacing = state.getValue(FACING);
        Direction outerFace = OpticalDiffuserBlock.getRightDirection(diffuserFacing);
        return face == outerFace;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        Direction diffuserFacing = state.getValue(FACING);
        Direction outerFace = OpticalDiffuserBlock.getRightDirection(diffuserFacing);
        return outerFace.getAxis();
    }

    @Override
    public Class<FilmReelBlockEntity> getBlockEntityClass() {
        return FilmReelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FilmReelBlockEntity> getBlockEntityType() {
        return ModRegistry.FILM_REEL_BE.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (itemId.contains("wrench")) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FilmReelBlockEntity reelBe) {
                com.mik.optical_diffuser.client.gui.FilmReelScreen.openBlock(pos, reelBe.getUrl());
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FilmReelBlockEntity reelBe) {
                com.mik.optical_diffuser.client.gui.FilmReelScreen.openBlock(pos, reelBe.getUrl());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

