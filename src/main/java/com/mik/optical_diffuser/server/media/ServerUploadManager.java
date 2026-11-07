package com.mik.optical_diffuser.server.media;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.block.entity.FilmReelBlockEntity;
import com.mik.optical_diffuser.config.DiffuserServerConfig;
import com.mik.optical_diffuser.item.FilmReelBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerUploadManager {

    private static final Map<UUID, UploadSession> SESSIONS = new ConcurrentHashMap<>();

    public static class UploadSession {
        public final UUID uploadId;
        public final UUID playerUuid;
        public final String originalFileName;
        public final String cleanFileName;
        public final long totalSize;
        public final Optional<BlockPos> pos;
        public final Optional<InteractionHand> hand;
        public final String clientHost;
        public final File partFile;
        public FileOutputStream outputStream;
        public long receivedBytes = 0;
        public long lastActivityTime = System.currentTimeMillis();

        public UploadSession(UUID uploadId, UUID playerUuid, String originalFileName, String cleanFileName,
                             long totalSize, Optional<BlockPos> pos, Optional<InteractionHand> hand,
                             String clientHost, File partFile, FileOutputStream outputStream) {
            this.uploadId = uploadId;
            this.playerUuid = playerUuid;
            this.originalFileName = originalFileName;
            this.cleanFileName = cleanFileName;
            this.totalSize = totalSize;
            this.pos = pos;
            this.hand = hand;
            this.clientHost = clientHost;
            this.partFile = partFile;
            this.outputStream = outputStream;
        }
    }

    public static synchronized UUID startSession(ServerPlayer player, String fileName, long fileSize,
                                                Optional<BlockPos> pos, Optional<InteractionHand> hand,
                                                String clientHost) throws Exception {
        cleanupStaleSessions();

        if (player == null || !player.hasPermissions(2)) {
            throw new SecurityException("Only server operators can upload media files.");
        }

        long maxBytes = DiffuserServerConfig.INSTANCE.maxUploadMb.get() * 1024L * 1024L;
        if (fileSize <= 0 || fileSize > maxBytes) {
            throw new IllegalArgumentException("File size exceeds maximum allowed upload size (" + DiffuserServerConfig.INSTANCE.maxUploadMb.get() + " MB).");
        }

        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".mp4") && !lower.endsWith(".mkv") && !lower.endsWith(".webm")
                && !lower.endsWith(".avi") && !lower.endsWith(".mov")) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: .mp4, .mkv, .webm, .avi, .mov");
        }

        String cleanName = new File(fileName).getName().replaceAll("[^a-zA-Z0-9._ -]", "_");
        File mediaDir = MediaHttpServer.getMediaDirectory();
        if (mediaDir == null) {
            mediaDir = new File("projector_media");
            mediaDir.mkdirs();
        }

        UUID uploadId = UUID.randomUUID();
        File partFile = new File(mediaDir, cleanName + "_" + uploadId.toString().substring(0, 8) + ".part");
        FileOutputStream fos = new FileOutputStream(partFile);

        UploadSession session = new UploadSession(
                uploadId, player.getUUID(), fileName, cleanName, fileSize, pos, hand, clientHost, partFile, fos
        );
        SESSIONS.put(uploadId, session);
        OpticalDiffuserMod.LOGGER.info("Started media upload session {} for player {} (file='{}', size={} bytes)",
                uploadId, player.getName().getString(), cleanName, fileSize);
        return uploadId;
    }

    public static synchronized void writeChunk(ServerPlayer player, UUID uploadId, int chunkIndex, byte[] data) throws Exception {
        UploadSession session = SESSIONS.get(uploadId);
        if (session == null) {
            throw new IllegalStateException("Upload session expired or not found.");
        }
        if (player == null || !player.getUUID().equals(session.playerUuid)) {
            throw new SecurityException("Unauthorized upload chunk.");
        }

        session.outputStream.write(data);
        session.receivedBytes += data.length;
        session.lastActivityTime = System.currentTimeMillis();
    }

    public static synchronized String finishSession(ServerPlayer player, UUID uploadId) throws Exception {
        UploadSession session = SESSIONS.remove(uploadId);
        if (session == null) {
            throw new IllegalStateException("Upload session not found.");
        }
        if (player == null || !player.getUUID().equals(session.playerUuid)) {
            throw new SecurityException("Unauthorized upload finalization.");
        }

        try {
            session.outputStream.flush();
            session.outputStream.close();
        } catch (Throwable ignored) {}

        File mediaDir = MediaHttpServer.getMediaDirectory();
        if (mediaDir == null) {
            mediaDir = new File("projector_media");
        }

        File targetFile = new File(mediaDir, session.cleanFileName);
        if (targetFile.exists()) {
            targetFile.delete();
        }
        boolean renamed = session.partFile.renameTo(targetFile);
        if (!renamed) {
            throw new IOException("Failed to rename temporary upload file to " + targetFile.getName());
        }

        String host = DiffuserServerConfig.INSTANCE.publicHost.get();
        if (host == null || host.trim().isEmpty()) {
            host = (session.clientHost != null && !session.clientHost.trim().isEmpty())
                    ? session.clientHost.trim()
                    : "localhost";
        }
        // Strip any port from client host if present
        if (host.contains(":")) {
            host = host.split(":")[0];
        }

        int port = MediaHttpServer.getActivePort() > 0 ? MediaHttpServer.getActivePort() : DiffuserServerConfig.INSTANCE.httpPort.get();
        String encodedName = URLEncoder.encode(session.cleanFileName, StandardCharsets.UTF_8).replace("+", "%20");
        String finalUrl = "http://" + host + ":" + port + "/media/" + encodedName;

        // Automatically update the target film reel block or held item
        if (session.pos.isPresent()) {
            BlockPos pos = session.pos.get();
            if (player.serverLevel().isLoaded(pos)) {
                BlockEntity be = player.serverLevel().getBlockEntity(pos);
                if (be instanceof FilmReelBlockEntity reel) {
                    reel.setUrl(finalUrl);
                    reel.setCurrentTimeMs(0);
                }
            }
        } else if (session.hand.isPresent()) {
            ItemStack stack = player.getItemInHand(session.hand.get());
            if (stack.getItem() instanceof FilmReelBlockItem) {
                FilmReelBlockItem.setUrl(stack, finalUrl);
                FilmReelBlockItem.setCurrentTimeMs(stack, 0);
            }
        }

        OpticalDiffuserMod.LOGGER.info("Media upload session {} complete! Saved '{}' -> {}", uploadId, targetFile.getName(), finalUrl);
        return finalUrl;
    }

    public static synchronized void cancelSession(UUID uploadId) {
        UploadSession session = SESSIONS.remove(uploadId);
        if (session != null) {
            try {
                session.outputStream.close();
            } catch (Throwable ignored) {}
            try {
                session.partFile.delete();
            } catch (Throwable ignored) {}
            OpticalDiffuserMod.LOGGER.info("Cancelled media upload session {}", uploadId);
        }
    }

    private static void cleanupStaleSessions() {
        long now = System.currentTimeMillis();
        for (var entry : SESSIONS.entrySet()) {
            UploadSession s = entry.getValue();
            if (now - s.lastActivityTime > 60000L) { // 60 seconds timeout
                cancelSession(entry.getKey());
            }
        }
    }
}
