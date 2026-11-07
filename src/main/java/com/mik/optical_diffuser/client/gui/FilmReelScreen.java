package com.mik.optical_diffuser.client.gui;

import com.mik.optical_diffuser.item.FilmReelBlockItem;
import com.mik.optical_diffuser.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

public class FilmReelScreen extends Screen {

    private static FilmReelScreen currentInstance = null;

    private final InteractionHand hand;
    private final ItemStack stack;
    private final BlockPos blockPos;
    private final String initialUrl;
    private EditBox urlInput;

    private Button browseButton;
    private Button saveButton;
    private Button cancelButton;

    private boolean isUploading = false;
    private int uploadProgress = 0;
    private String statusMessage = "";
    private int statusColor = 0xAAAAAA;
    private UUID currentUploadSessionId = null;
    private volatile boolean uploadCancelled = false;
    private File pendingUploadFile = null;

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

    public static void handleUploadStatus(UUID uploadId, int statusCode, int progressPercent, String messageOrUrl) {
        Minecraft.getInstance().execute(() -> {
            if (currentInstance != null) {
                currentInstance.onUploadStatusReceived(uploadId, statusCode, progressPercent, messageOrUrl);
            }
        });
    }

    @Override
    protected void init() {
        super.init();
        currentInstance = this;

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.urlInput = new EditBox(this.font, centerX - 140, centerY - 20, 220, 20, Component.translatable("gui.create_optical_diffuser.film_reel.url_label"));
        this.urlInput.setMaxLength(2048);
        this.urlInput.setValue(this.initialUrl);
        this.addRenderableWidget(this.urlInput);

        boolean isSingleplayer = Minecraft.getInstance().hasSingleplayerServer();
        boolean isAdmin = isSingleplayer || (Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2));

        // Native File Dialog Browse button
        this.browseButton = Button.builder(Component.translatable("gui.create_optical_diffuser.film_reel.browse"), b -> {
            if (isAdmin) {
                openNativeFileDialog();
            } else {
                this.statusMessage = Component.translatable("gui.create_optical_diffuser.film_reel.admin_only_warn").getString();
                this.statusColor = 0xFF5555;
            }
        }).bounds(centerX + 85, centerY - 20, 60, 20).build();
        this.addRenderableWidget(this.browseButton);

        // Save button
        this.saveButton = Button.builder(Component.translatable("gui.create_optical_diffuser.film_reel.save"), b -> {
            saveAndClose();
        }).bounds(centerX - 105, centerY + 30, 100, 20).build();
        this.addRenderableWidget(this.saveButton);

        // Cancel button
        this.cancelButton = Button.builder(Component.translatable("gui.create_optical_diffuser.film_reel.cancel"), b -> {
            if (this.isUploading && this.currentUploadSessionId != null) {
                this.uploadCancelled = true;
                PacketDistributor.sendToServer(new CancelMediaUploadPayload(this.currentUploadSessionId));
            }
            this.onClose();
        }).bounds(centerX + 5, centerY + 30, 100, 20).build();
        this.addRenderableWidget(this.cancelButton);
    }

    @Override
    public void removed() {
        super.removed();
        if (currentInstance == this) {
            currentInstance = null;
        }
        if (this.isUploading && this.currentUploadSessionId != null) {
            this.uploadCancelled = true;
            PacketDistributor.sendToServer(new CancelMediaUploadPayload(this.currentUploadSessionId));
        }
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
                    Minecraft.getInstance().execute(() -> onFileSelected(file));
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, "FilmReelFileDialog").start();
    }

    private void onFileSelected(File file) {
        if (!file.exists() || !file.isFile()) return;

        boolean isSingleplayer = Minecraft.getInstance().hasSingleplayerServer();

        if (isSingleplayer) {
            // Singleplayer: copy into local projector_media folder without network overhead
            try {
                File mediaDir = new File(FMLPaths.GAMEDIR.get().toFile(), "projector_media");
                if (!mediaDir.exists()) mediaDir.mkdirs();
                File dest = new File(mediaDir, file.getName());
                if (!dest.getCanonicalPath().equals(file.getCanonicalPath())) {
                    Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                String fileUri = dest.toURI().toString();
                this.urlInput.setValue(fileUri);
                this.statusMessage = "Файл збережено в projector_media/" + dest.getName();
                this.statusColor = 0x55FF55;
            } catch (Throwable t) {
                this.statusMessage = "Помилка копіювання: " + t.getMessage();
                this.statusColor = 0xFF5555;
            }
        } else {
            // Dedicated Server: Initiate chunked admin upload over network
            this.pendingUploadFile = file;
            this.isUploading = true;
            this.uploadCancelled = false;
            this.uploadProgress = 0;
            this.statusMessage = "Запит дозволу на завантаження...";
            this.statusColor = 0xFFFF55;
            this.browseButton.active = false;
            this.saveButton.active = false;

            String clientHost = "localhost";
            var serverData = Minecraft.getInstance().getCurrentServer();
            if (serverData != null && serverData.ip != null) {
                clientHost = serverData.ip;
            }

            PacketDistributor.sendToServer(new StartMediaUploadPayload(
                    file.getName(),
                    file.length(),
                    Optional.ofNullable(this.blockPos),
                    Optional.ofNullable(this.hand),
                    clientHost
            ));
        }
    }

    private void onUploadStatusReceived(UUID uploadId, int statusCode, int progressPercent, String messageOrUrl) {
        if (statusCode == 0) {
            // APPROVED -> Start streaming file chunks
            this.currentUploadSessionId = uploadId;
            this.statusMessage = "Завантаження на сервер: 0%...";
            this.statusColor = 0x55FFFF;
            startStreamingChunks(uploadId, this.pendingUploadFile);
        } else if (statusCode == 1) {
            // UPLOADING progress
            this.uploadProgress = progressPercent;
            this.statusMessage = "Завантаження на сервер: " + progressPercent + "%...";
        } else if (statusCode == 2) {
            // COMPLETE
            this.isUploading = false;
            this.uploadProgress = 100;
            this.urlInput.setValue(messageOrUrl);
            this.statusMessage = "Завантажено успішно! URL встановлено.";
            this.statusColor = 0x55FF55;
            this.browseButton.active = true;
            this.saveButton.active = true;
        } else if (statusCode == 3) {
            // ERROR
            this.isUploading = false;
            this.statusMessage = "Помилка: " + messageOrUrl;
            this.statusColor = 0xFF5555;
            this.browseButton.active = true;
            this.saveButton.active = true;
        }
    }

    private void startStreamingChunks(UUID uploadId, File file) {
        new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[32768]; // 32 KB chunk
                int chunkIndex = 0;
                long totalBytes = file.length();
                long sentBytes = 0;

                while (!uploadCancelled) {
                    int read = fis.read(buffer);
                    if (read == -1) break;

                    byte[] chunkData = (read == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, read);
                    PacketDistributor.sendToServer(new MediaUploadChunkPayload(uploadId, chunkIndex++, chunkData));
                    sentBytes += read;

                    int percent = (int) ((sentBytes * 100) / Math.max(1, totalBytes));
                    Minecraft.getInstance().execute(() -> {
                        if (currentInstance != null && currentInstance.isUploading) {
                            currentInstance.uploadProgress = percent;
                            currentInstance.statusMessage = "Завантаження на сервер: " + percent + "%";
                        }
                    });

                    // Pace chunks slightly to prevent network buffer overflow (~3-4 MB/s)
                    Thread.sleep(8);
                }

                if (!uploadCancelled) {
                    PacketDistributor.sendToServer(new FinishMediaUploadPayload(uploadId));
                    Minecraft.getInstance().execute(() -> {
                        if (currentInstance != null) {
                            currentInstance.statusMessage = "Обробка файлу на сервері...";
                        }
                    });
                }
            } catch (Throwable t) {
                Minecraft.getInstance().execute(() -> {
                    if (currentInstance != null) {
                        currentInstance.isUploading = false;
                        currentInstance.statusMessage = "Помилка читання файлу: " + t.getMessage();
                        currentInstance.statusColor = 0xFF5555;
                        currentInstance.browseButton.active = true;
                        currentInstance.saveButton.active = true;
                    }
                });
            }
        }, "MediaChunkStreamer").start();
    }

    private void saveAndClose() {
        if (this.isUploading) return;
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

        boolean isSingleplayer = Minecraft.getInstance().hasSingleplayerServer();
        boolean isAdmin = isSingleplayer || (Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2));

        if (!isAdmin) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.create_optical_diffuser.film_reel.admin_only_note").withStyle(ChatFormatting.DARK_GRAY), centerX, centerY + 8, 0x888888);
        }

        if (this.isUploading) {
            // Draw progress bar
            int barWidth = 200;
            int barHeight = 8;
            int barX = centerX - barWidth / 2;
            int barY = centerY + 10;
            graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF333333);
            int filled = (int) (barWidth * (this.uploadProgress / 100.0f));
            graphics.fill(barX, barY, barX + filled, barY + barHeight, 0xFF44DD44);
        }

        if (!this.statusMessage.isEmpty()) {
            graphics.drawCenteredString(this.font, this.statusMessage, centerX, centerY + (this.isUploading ? 22 : 8), this.statusColor);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
