package com.mik.optical_diffuser.client.gui;

import com.mik.optical_diffuser.menu.OpticalDiffuserMenu;
import com.mik.optical_diffuser.network.UpdateDiffuserPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class OpticalDiffuserScreen extends AbstractContainerScreen<OpticalDiffuserMenu> {

    private float coneAngle;
    private float spinSpeed;
    private int redstoneMode;

    private static final String[] REDSTONE_LABELS = {
            "Редстоун: Завжди увімкнено",
            "Редстоун: Потрібен сигнал",
            "Редстоун: Тільки без сигналу"
    };

    public OpticalDiffuserScreen(OpticalDiffuserMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 220;
        this.imageHeight = 115;

        this.coneAngle = menu.getConeAngle();
        this.spinSpeed = 0.0f;
        this.redstoneMode = menu.getRedstoneMode();
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos + 20;
        int y = this.topPos + 35;

        // Slider 1: Cone Angle (10° - 75°)
        this.addRenderableWidget(new AbstractSliderButton(x, y, 180, 20, Component.literal("Кут розсіювання: " + (int) coneAngle + "°"), (coneAngle - 10.0f) / 65.0f) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("Кут розсіювання: " + (int) (10.0f + value * 65.0f) + "°"));
            }

            @Override
            protected void applyValue() {
                coneAngle = 10.0f + (float) value * 65.0f;
                syncSettings();
            }
        });

        // Button 2: Redstone Mode Toggle (Always On -> Requires Signal -> Inverted)
        y += 28;
        Button redstoneBtn = Button.builder(
                Component.literal(REDSTONE_LABELS[redstoneMode % REDSTONE_LABELS.length]),
                btn -> {
                    redstoneMode = (redstoneMode + 1) % REDSTONE_LABELS.length;
                    btn.setMessage(Component.literal(REDSTONE_LABELS[redstoneMode]));
                    syncSettings();
                }
        ).pos(x, y).size(180, 20).build();
        this.addRenderableWidget(redstoneBtn);
    }

    private void syncSettings() {
        PacketDistributor.sendToServer(new UpdateDiffuserPayload(
                this.menu.getPos(),
                this.coneAngle,
                this.spinSpeed,
                this.redstoneMode
        ));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xF0181A20);
        guiGraphics.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF4A5568);
        guiGraphics.fill(x + 10, y + 25, x + this.imageWidth - 10, y + 26, 0xFF4A5568);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 20, 10, 0xFFE2E8F0, false);
    }
}
