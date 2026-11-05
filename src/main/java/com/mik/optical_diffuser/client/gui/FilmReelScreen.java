package com.mik.optical_diffuser.client.gui;

import com.mik.optical_diffuser.item.FilmReelBlockItem;
import com.mik.optical_diffuser.network.UpdateFilmReelBlockPayload;
import com.mik.optical_diffuser.network.UpdateFilmReelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;

public class FilmReelScreen extends Screen {

    private final InteractionHand hand;
    private final ItemStack stack;
    private final BlockPos blockPos;
    private final String initialUrl;
    private EditBox urlInput;

    public FilmReelScreen(InteractionHand hand, ItemStack stack) {
        super(Component.translatable("gui.create_optical_diffuser.film_reel.title"));
        this.hand = hand;
        this.stack = stack;
        this.blockPos = null;
        this.initialUrl = FilmReelBlockItem.getUrl(stack);
    }

    public FilmReelScreen(BlockPos blockPos, String currentUrl) {
        super(Component.translatable("gui.create_optical_diffuser.film_reel.title"));
        this.hand = null;
        this.stack = ItemStack.EMPTY;
        this.blockPos = blockPos;
        this.initialUrl = currentUrl != null ? currentUrl : "";
    }

    public static void openHand(InteractionHand hand, ItemStack stack) {
        Minecraft.getInstance().setScreen(new FilmReelScreen(hand, stack));
    }

    public static void openBlock(BlockPos pos, String currentUrl) {
        Minecraft.getInstance().setScreen(new FilmReelScreen(pos, currentUrl));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.urlInput = new EditBox(this.font, centerX - 140, centerY - 20, 220, 20, Component.translatable("gui.create_optical_diffuser.film_reel.url_label"));
        this.urlInput.setMaxLength(2048);
        this.urlInput.setValue(this.initialUrl);
        this.addRenderableWidget(this.urlInput);

        // Native File Dialog Browse button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.create_optical_diffuser.film_reel.browse"), b -> {
            openNativeFileDialog();
        }).bounds(centerX + 85, centerY - 20, 60, 20).build());

        // Save button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.create_optical_diffuser.film_reel.save"), b -> {
            saveAndClose();
        }).bounds(centerX - 105, centerY + 20, 100, 20).build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.create_optical_diffuser.film_reel.cancel"), b -> {
            this.onClose();
        }).bounds(centerX + 5, centerY + 20, 100, 20).build());
    }

    private void openNativeFileDialog() {
        new Thread(() -> {
            try (MemoryStack mem = MemoryStack.stackPush()) {
                PointerBuffer filters = mem.mallocPointer(5);
                filters.put(mem.UTF8("*.mp4"));
                filters.put(mem.UTF8("*.mkv"));
                filters.put(mem.UTF8("*.avi"));
                filters.put(mem.UTF8("*.webm"));
                filters.put(mem.UTF8("*.mov"));
                filters.flip();

                String result = TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Video File",
                        "",
                        filters,
                        "Video Files (*.mp4, *.mkv, *.avi, *.webm, *.mov)",
                        false
                );

                if (result != null && !result.trim().isEmpty()) {
                    File file = new File(result);
                    String fileUri = file.toURI().toString();
                    Minecraft.getInstance().execute(() -> {
                        if (this.urlInput != null) {
                            this.urlInput.setValue(fileUri);
                        }
                    });
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, "FilmReelFileDialog").start();
    }

    private void saveAndClose() {
        String url = this.urlInput.getValue().trim();
        if (this.blockPos != null) {
            PacketDistributor.sendToServer(new UpdateFilmReelBlockPayload(this.blockPos, url));
        } else if (this.hand != null) {
            PacketDistributor.sendToServer(new UpdateFilmReelPayload(this.hand, url));
        }
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 50, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.create_optical_diffuser.film_reel.url_label"), centerX - 140, centerY - 32, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
