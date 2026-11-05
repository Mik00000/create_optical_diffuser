package com.mik.optical_diffuser.block;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.block.entity.SpeakerBlockEntity;
import com.mik.optical_diffuser.client.DiffuserSelectionTracker;
import com.mik.optical_diffuser.network.LinkSpeakerPayload;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class SpeakerBlock extends Block implements IBE<SpeakerBlockEntity> {

    public static final MapCodec<SpeakerBlock> CODEC = simpleCodec(SpeakerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public SpeakerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public Class<SpeakerBlockEntity> getBlockEntityClass() {
        return SpeakerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpeakerBlockEntity> getBlockEntityType() {
        return ModRegistry.SPEAKER_BE.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            handleInteraction(level, pos, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            handleInteraction(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void handleInteraction(Level level, BlockPos pos, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SpeakerBlockEntity speaker)) return;

        // Shift + Right-Click: Unlink
        if (player.isShiftKeyDown()) {
            if (speaker.isLinked()) {
                speaker.setLinkedDiffuserPos(null);
                PacketDistributor.sendToServer(new LinkSpeakerPayload(pos, Optional.empty()));
                level.playSound(player, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0f, 0.8f);
                player.displayClientMessage(Component.literal("Speaker unlinked.").withStyle(ChatFormatting.YELLOW), true);
            }
            return;
        }

        // Try pre-selected diffuser first
        BlockPos targetPos = DiffuserSelectionTracker.getLastSelectedDiffuser();
        if (targetPos != null && level.getBlockState(targetPos).getBlock() instanceof OpticalDiffuserBlock) {
            linkToDiffuser(level, pos, speaker, targetPos, player);
            return;
        }

        // Otherwise find nearest Optical Diffuser within 32 blocks
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        int searchRadius = 32;

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius / 2; dy <= searchRadius / 2; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (level.getBlockState(p).getBlock() instanceof OpticalDiffuserBlock) {
                        double distSq = pos.distSqr(p);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = p.immutable();
                        }
                    }
                }
            }
        }

        if (nearest != null) {
            linkToDiffuser(level, pos, speaker, nearest, player);
        } else {
            player.displayClientMessage(
                    Component.literal("No Optical Diffuser found nearby! Right-click an Optical Diffuser first to select it.")
                            .withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    private void linkToDiffuser(Level level, BlockPos pos, SpeakerBlockEntity speaker, BlockPos targetDiffuser, Player player) {
        speaker.setLinkedDiffuserPos(targetDiffuser);
        PacketDistributor.sendToServer(new LinkSpeakerPayload(pos, Optional.of(targetDiffuser)));
        level.playSound(player, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0f, 1.4f);
        player.displayClientMessage(
                Component.literal(String.format("Speaker connected to Optical Diffuser at [%d, %d, %d]! (Coverage: 9x9 zone ahead)",
                        targetDiffuser.getX(), targetDiffuser.getY(), targetDiffuser.getZ()))
                        .withStyle(ChatFormatting.GREEN),
                true
        );
    }

    public static boolean isInsideSpeakerZone(BlockPos speakerPos, Direction facing, Vec3 playerPos) {
        Vec3 center = Vec3.atCenterOf(speakerPos);
        Vec3 rel = playerPos.subtract(center);

        Vec3 forwardDir = Vec3.atLowerCornerOf(facing.getNormal());
        double forwardDist = rel.dot(forwardDir);
        // Forward depth: 0 to 9 blocks ahead
        if (forwardDist < -0.5 || forwardDist > 9.5) {
            return false;
        }

        // Lateral check: 4 blocks to each side = 9 blocks total width
        if (facing.getAxis() == Direction.Axis.Y) {
            return Math.abs(rel.x) <= 4.5 && Math.abs(rel.z) <= 4.5;
        } else {
            Direction right = facing.getClockWise();
            Vec3 rightDir = Vec3.atLowerCornerOf(right.getNormal());
            double lateral = Math.abs(rel.dot(rightDir));
            double vertical = Math.abs(rel.y);
            return lateral <= 4.5 && vertical <= 4.5;
        }
    }
}

