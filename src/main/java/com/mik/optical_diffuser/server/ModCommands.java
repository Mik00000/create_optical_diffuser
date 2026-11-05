package com.mik.optical_diffuser.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("diffuser")
                .then(Commands.literal("clear_lights")
                    .executes(ctx -> clearLights(ctx.getSource(), 64))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                        .executes(ctx -> clearLights(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))
                    )
                )
        );
    }

    private static int clearLights(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        int removedCount = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.hasChunkAt(cursor)) {
                        BlockState st = level.getBlockState(cursor);
                        if (st.is(Blocks.LIGHT)) {
                            if (st.hasProperty(LightBlock.WATERLOGGED) && st.getValue(LightBlock.WATERLOGGED)) {
                                level.setBlock(cursor, Blocks.WATER.defaultBlockState(), 3);
                            } else {
                                level.removeBlock(cursor, false);
                            }
                            removedCount++;
                        }
                    }
                }
            }
        }

        final int count = removedCount;
        source.sendSuccess(() -> Component.literal("Diffuser: Cleared " + count + " lingering light block(s) within radius " + radius)
                .withStyle(ChatFormatting.GREEN), true);
        return removedCount;
    }
}
