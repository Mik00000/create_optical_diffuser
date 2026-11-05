package com.mik.optical_diffuser.menu;

import com.mik.optical_diffuser.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class OpticalDiffuserMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final float coneAngle;
    private final float spinSpeed;
    private final int redstoneMode;

    public OpticalDiffuserMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(
                containerId,
                playerInv,
                buf.readBlockPos(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt()
        );
    }

    public OpticalDiffuserMenu(int containerId, Inventory playerInv, BlockPos pos,
                               float coneAngle, float spinSpeed, int redstoneMode) {
        super(ModRegistry.OPTICAL_DIFFUSER_MENU.get(), containerId);
        this.pos = pos;
        this.coneAngle = coneAngle;
        this.spinSpeed = spinSpeed;
        this.redstoneMode = redstoneMode;
    }

    public BlockPos getPos() {
        return pos;
    }

    public float getConeAngle() {
        return coneAngle;
    }

    public float getSpinSpeed() {
        return spinSpeed;
    }

    public int getRedstoneMode() {
        return redstoneMode;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // Return true to avoid premature closure on Sable ships or position mismatches
        return true;
    }
}
