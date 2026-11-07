package com.mik.optical_diffuser.block;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.block.entity.SpeakerBlockEntity;
import com.mik.optical_diffuser.item.SpeakerBlockItem;
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
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SpeakerBlockEntity speaker) {
            BlockPos linkedPos = SpeakerBlockItem.getLinkedDiffuserPos(stack);
            if (linkedPos != null) {
                speaker.setLinkedDiffuserPos(linkedPos);
            }
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SpeakerBlockEntity speaker && speaker.isLinked()) {
            SpeakerBlockItem.setLinkedDiffuserPos(stack, speaker.getLinkedDiffuserPos());
        }
        return stack;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack stack = new ItemStack(this);
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof SpeakerBlockEntity speaker && speaker.isLinked()) {
            SpeakerBlockItem.setLinkedDiffuserPos(stack, speaker.getLinkedDiffuserPos());
        }
        return List.of(stack);
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
        if (stack.is(ModRegistry.SPEAKER_ITEM.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
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

        // Shift + Right-Click with empty hand: Unlink
        if (player.isShiftKeyDown() && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            if (speaker.isLinked()) {
                speaker.setLinkedDiffuserPos(null);
                PacketDistributor.sendToServer(new LinkSpeakerPayload(pos, Optional.empty()));
                level.playSound(player, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0f, 0.8f);
                player.displayClientMessage(
                        Component.translatable("chat.create_optical_diffuser.speaker_unlinked")
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );
            }
            return;
        }

        // Normal interaction shows current link status
        if (speaker.isLinked()) {
            BlockPos linked = speaker.getLinkedDiffuserPos();
            player.displayClientMessage(
                    Component.translatable("chat.create_optical_diffuser.speaker_status_linked",
                            linked.getX(), linked.getY(), linked.getZ())
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
        } else {
            player.displayClientMessage(
                    Component.translatable("chat.create_optical_diffuser.speaker_status_unlinked")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );
        }
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
