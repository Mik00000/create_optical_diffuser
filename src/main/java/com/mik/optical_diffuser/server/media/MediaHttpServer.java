package com.mik.optical_diffuser.server.media;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaHttpServer {

    private static HttpServer server = null;
    private static ExecutorService executor = null;
    private static File mediaDirectory = null;
    private static int activePort = 0;

    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d*)-(\\d*)");

    public static synchronized void start(File mediaDir, int port) {
        if (server != null) {
            return;
        }

        try {
            mediaDirectory = mediaDir;
            if (!mediaDirectory.exists()) {
                mediaDirectory.mkdirs();
            }

            activePort = port;
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/media", new MediaHandler(mediaDirectory));

            executor = Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "ProjectorMediaHttp");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.start();

            OpticalDiffuserMod.LOGGER.info("Projector Media HTTP Server started on port {} (serving {})", port, mediaDirectory.getAbsolutePath());
        } catch (Throwable t) {
            OpticalDiffuserMod.LOGGER.error("Failed to start Projector Media HTTP Server on port " + port, t);
            stop();
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            try {
                server.stop(0);
                OpticalDiffuserMod.LOGGER.info("Projector Media HTTP Server stopped.");
            } catch (Throwable ignored) {}
            server = null;
        }
        if (executor != null) {
            try {
                executor.shutdownNow();
            } catch (Throwable ignored) {}
            executor = null;
        }
        mediaDirectory = null;
        activePort = 0;
    }

    public static File getMediaDirectory() {
        return mediaDirectory;
    }

    public static int getActivePort() {
        return activePort;
    }

    private static class MediaHandler implements HttpHandler {
        private final File baseDir;

        public MediaHandler(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod().toUpperCase();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith("/media/")) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            String rawFileName = path.substring("/media/".length());
            String decodedFileName = URLDecoder.decode(rawFileName, StandardCharsets.UTF_8);

            File targetFile = new File(baseDir, decodedFileName).getCanonicalFile();
            if (!targetFile.getPath().startsWith(baseDir.getCanonicalPath()) || !targetFile.exists() || !targetFile.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            long fileLength = targetFile.length();
            String mimeType = getMimeType(targetFile.getName());

            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            long start = 0;
            long end = fileLength - 1;
            boolean isPartial = false;

            if (rangeHeader != null) {
                Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
                if (matcher.matches()) {
                    String startStr = matcher.group(1);
                    String endStr = matcher.group(2);

                    try {
                        if (!startStr.isEmpty()) {
                            start = Long.parseLong(startStr);
                            if (!endStr.isEmpty()) {
                                end = Long.parseLong(endStr);
                            }
                        } else if (!endStr.isEmpty()) {
                            // Suffix byte range: e.g. bytes=-500 means last 500 bytes
                            long suffix = Long.parseLong(endStr);
                            start = Math.max(0, fileLength - suffix);
                            end = fileLength - 1;
                        }

                        if (start <= end && end < fileLength) {
                            isPartial = true;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            long contentLength = end - start + 1;

            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

            if (isPartial) {
                exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                exchange.sendResponseHeaders(206, "HEAD".equals(method) ? -1 : contentLength);
            } else {
                exchange.sendResponseHeaders(200, "HEAD".equals(method) ? -1 : fileLength);
            }

            if ("GET".equals(method)) {
                try (RandomAccessFile raf = new RandomAccessFile(targetFile, "r");
                     OutputStream os = exchange.getResponseBody()) {
                    raf.seek(start);
                    byte[] buffer = new byte[65536];
                    long bytesRemaining = contentLength;

                    while (bytesRemaining > 0) {
                        int toRead = (int) Math.min(buffer.length, bytesRemaining);
                        int read = raf.read(buffer, 0, toRead);
                        if (read == -1) break;
                        os.write(buffer, 0, read);
                        bytesRemaining -= read;
                    }
                } catch (IOException e) {
                    // Client disconnected or aborted stream (normal when seeking or stopping video)
                }
            }

            exchange.close();
        }

        private String getMimeType(String name) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".mp4")) return "video/mp4";
            if (lower.endsWith(".mkv")) return "video/x-matroska";
            if (lower.endsWith(".webm")) return "video/webm";
            if (lower.endsWith(".avi")) return "video/x-msvideo";
            if (lower.endsWith(".mov")) return "video/quicktime";
            return "application/octet-stream";
        }
    }
}
