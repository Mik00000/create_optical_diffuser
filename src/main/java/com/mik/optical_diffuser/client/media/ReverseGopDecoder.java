package com.mik.optical_diffuser.client.media;

import com.mik.optical_diffuser.util.DiffuserLogger;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;

import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous Dual-Buffer GOP (Group of Pictures) Reverse Decoder.
 *
 * Replaces O(N^2) naive backward seek slideshow with O(1) memory lookup:
 * 1. Background virtual thread decodes preceding GOPs forward into OffHeapGopBuffer.
 * 2. Ping-pong buffers allow seamless continuous backward playback at 30-60 FPS.
 * 3. High-speed trick play (> 64 RPM) decodes keyframes only.
 */
public class ReverseGopDecoder implements AutoCloseable {

    private static final int GOP_CAPACITY = 75; // 75 frames (~2.5 seconds at 30fps)
    private static final int MAX_DECODE_WIDTH = 854; // 854x480 standard 480p (fast & sharp)

    private final String sourceUrl;
    private final ExecutorService worker;
    private final AtomicBoolean isDecoding = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private boolean available = false;
    private int width = 0;
    private int height = 0;
    private double timeBaseSec = 0.001;
    private int videoStreamIndex = -1;

    private AVFormatContext formatContext = null;
    private AVCodecContext videoCodecContext = null;
    private SwsContext swsContext = null;
    private AVPacket pkt = null;
    private AVFrame frame = null;

    private ByteBuffer tempDirectBuffer = null;
    private BytePointer dstBytePointer = null;
    private PointerPointer dstData = null;
    private IntPointer dstLinesize = null;

    private OffHeapGopBuffer activeBuffer = null;
    private OffHeapGopBuffer standbyBuffer = null;
    private Future<?> currentDecodeFuture = null;
    private volatile boolean hasDecodedAny = false;

    public ReverseGopDecoder(String url) {
        this.sourceUrl = url;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ReverseGopDecoder-" + Integer.toHexString(url.hashCode()));
            t.setDaemon(true);
            return t;
        });

        // Initialize FFmpeg in background
        this.worker.submit(this::initFfmpeg);
    }

    private void initFfmpeg() {
        if (closed.get()) return;
        try {
            String inputPath = sourceUrl;
            try {
                URI uri = new URI(sourceUrl);
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    inputPath = uri.getPath();
                } else if (uri.getScheme() == null) {
                    inputPath = sourceUrl;
                } else {
                    inputPath = uri.toString();
                }
            } catch (Throwable t) {
                try {
                    inputPath = URLDecoder.decode(sourceUrl, StandardCharsets.UTF_8);
                    if (inputPath.startsWith("file://")) inputPath = inputPath.substring(7);
                    else if (inputPath.startsWith("file:")) inputPath = inputPath.substring(5);
                } catch (Throwable ignored) {}
            }

            File localFile = new File(inputPath);
            if (localFile.exists()) {
                inputPath = localFile.getAbsolutePath();
            }

            DiffuserLogger.info("Opening FFmpeg input for reverse decoding: {}", inputPath);
            formatContext = avformat.avformat_alloc_context();
            if (formatContext == null) {
                DiffuserLogger.error("Failed to allocate AVFormatContext");
                return;
            }

            BytePointer pathPtr = new BytePointer(inputPath, StandardCharsets.UTF_8);
            int ret = avformat.avformat_open_input(formatContext, pathPtr, null, null);
            if (ret < 0) {
                // Fallback to direct String overload
                ret = avformat.avformat_open_input(formatContext, inputPath, null, null);
            }

            if (ret < 0) {
                DiffuserLogger.warn("avformat_open_input failed with code: {} for path: {}", ret, inputPath);
                return;
            }

            ret = avformat.avformat_find_stream_info(formatContext, (PointerPointer) null);
            if (ret < 0) {
                DiffuserLogger.warn("avformat_find_stream_info failed with code: {}", ret);
                return;
            }

            int nbStreams = formatContext.nb_streams();
            AVStream vStream = null;
            for (int i = 0; i < nbStreams; i++) {
                AVStream st = formatContext.streams(i);
                if (st.codecpar().codec_type() == avutil.AVMEDIA_TYPE_VIDEO) {
                    videoStreamIndex = i;
                    vStream = st;
                    break;
                }
            }

            if (videoStreamIndex < 0 || vStream == null) {
                DiffuserLogger.warn("No video stream found for reverse decoding");
                return;
            }

            AVCodecParameters codecpar = vStream.codecpar();
            AVCodec codec = avcodec.avcodec_find_decoder(codecpar.codec_id());
            if (codec == null) {
                DiffuserLogger.warn("No decoder found for codec id: {}", codecpar.codec_id());
                return;
            }

            videoCodecContext = avcodec.avcodec_alloc_context3(codec);
            if (videoCodecContext == null) {
                DiffuserLogger.warn("Failed to allocate AVCodecContext");
                return;
            }

            avcodec.avcodec_parameters_to_context(videoCodecContext, codecpar);
            ret = avcodec.avcodec_open2(videoCodecContext, codec, (PointerPointer) null);
            if (ret < 0) {
                DiffuserLogger.warn("avcodec_open2 failed with code: {}", ret);
                return;
            }

            AVRational tb = vStream.time_base();
            timeBaseSec = (tb.den() > 0) ? ((double) tb.num() / tb.den()) : 0.001;

            int origW = codecpar.width();
            int origH = codecpar.height();
            if (origW <= 0 || origH <= 0) {
                origW = 1280;
                origH = 720;
            }

            int targetW = Math.min(MAX_DECODE_WIDTH, Math.max(320, origW));
            int targetH = (int) Math.round((double) targetW / origW * origH);
            if (targetW % 2 != 0) targetW--;
            if (targetH % 2 != 0) targetH--;

            this.width = targetW;
            this.height = targetH;

            int srcFormat = codecpar.format();
            if (srcFormat < 0) {
                srcFormat = videoCodecContext.pix_fmt();
            }

            swsContext = swscale.sws_getContext(
                    origW, origH, srcFormat,
                    targetW, targetH, avutil.AV_PIX_FMT_RGBA,
                    swscale.SWS_BILINEAR, null, null, (DoublePointer) null
            );

            if (swsContext == null) {
                DiffuserLogger.warn("Failed to initialize SwsContext for {}x{} -> {}x{}", origW, origH, targetW, targetH);
                return;
            }

            pkt = avcodec.av_packet_alloc();
            frame = avutil.av_frame_alloc();

            tempDirectBuffer = ByteBuffer.allocateDirect(targetW * targetH * 4);
            dstBytePointer = new BytePointer(tempDirectBuffer);
            dstData = new PointerPointer(dstBytePointer);
            dstLinesize = new IntPointer(targetW * 4, 0, 0, 0);

            activeBuffer = new OffHeapGopBuffer(targetW, targetH, GOP_CAPACITY);
            standbyBuffer = new OffHeapGopBuffer(targetW, targetH, GOP_CAPACITY);

            available = true;
            DiffuserLogger.info("ReverseGopDecoder successfully initialized: input='{}', resolution={}x{}, timeBase={}", inputPath, targetW, targetH, timeBaseSec);
        } catch (Throwable t) {
            DiffuserLogger.error("Failed to initialize ReverseGopDecoder", t);
        }
    }

    public boolean isAvailable() {
        return available && !closed.get();
    }

    public boolean hasRenderableFrame() {
        return hasDecodedAny && ((activeBuffer != null && activeBuffer.isReady()) || (standbyBuffer != null && standbyBuffer.isReady()));
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Gets decoded frame for the target timestamp in O(1) time.
     * Manages asynchronous ping-pong predecoding of earlier GOPs.
     */
    public synchronized ByteBuffer getFrameForTime(long targetPtsMs) {
        if (!isAvailable()) return null;

        // 1. If active buffer contains target timestamp, return frame immediately
        if (activeBuffer != null && activeBuffer.isReady() && activeBuffer.contains(targetPtsMs)) {
            int idx = activeBuffer.findClosestFrameIndex(targetPtsMs);
            ByteBuffer buf = activeBuffer.getFrameBuffer(idx);
            hasDecodedAny = true;

            // Approaching start of active GOP (within 600ms): trigger pre-decode of earlier GOP
            long distToStart = targetPtsMs - activeBuffer.getStartPtsMs();
            if (distToStart < 600 && !isDecoding.get()) {
                long targetPredecode = Math.max(0, activeBuffer.getStartPtsMs() - 250);
                if (standbyBuffer == null || !standbyBuffer.contains(targetPredecode)) {
                    triggerAsyncPredecode(targetPredecode);
                }
            }
            return buf;
        }

        // 2. If standby buffer contains target timestamp, swap ping-pong buffers
        if (standbyBuffer != null && standbyBuffer.isReady() && standbyBuffer.contains(targetPtsMs)) {
            OffHeapGopBuffer temp = activeBuffer;
            activeBuffer = standbyBuffer;
            standbyBuffer = temp;

            int idx = activeBuffer.findClosestFrameIndex(targetPtsMs);
            ByteBuffer buf = activeBuffer.getFrameBuffer(idx);
            hasDecodedAny = true;

            // Trigger pre-decode for earlier GOP
            if (!isDecoding.get()) {
                long targetPredecode = Math.max(0, activeBuffer.getStartPtsMs() - 250);
                triggerAsyncPredecode(targetPredecode);
            }
            return buf;
        }

        // 3. Cache miss: trigger decode for targetPtsMs if not already decoding
        triggerAsyncPredecode(targetPtsMs);

        // If active buffer is not ready (e.g. first reverse call), wait briefly (up to 80ms)
        if (currentDecodeFuture != null && (activeBuffer == null || !activeBuffer.isReady())) {
            try {
                currentDecodeFuture.get(80, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {}

            if (activeBuffer != null && activeBuffer.isReady()) {
                int idx = activeBuffer.findClosestFrameIndex(targetPtsMs);
                return activeBuffer.getFrameBuffer(idx);
            }
        }

        // Return closest available frame while background decode catches up
        if (activeBuffer != null && activeBuffer.isReady()) {
            int idx = activeBuffer.findClosestFrameIndex(targetPtsMs);
            return activeBuffer.getFrameBuffer(idx);
        }
        return null;
    }

    /**
     * High-speed Trick Play mode (> 64 RPM): decodes keyframes only.
     */
    public synchronized ByteBuffer getTrickPlayKeyframe(long targetPtsMs) {
        if (!isAvailable()) return null;
        if (!isDecoding.get()) {
            isDecoding.set(true);
            currentDecodeFuture = worker.submit(() -> {
                try {
                    decodeSingleKeyframe(targetPtsMs);
                } finally {
                    isDecoding.set(false);
                }
            });
        }
        if (activeBuffer != null && activeBuffer.isReady()) {
            return activeBuffer.getFrameBuffer(activeBuffer.getFrameCount() - 1);
        }
        return null;
    }

    private synchronized void triggerAsyncPredecode(long targetPtsMs) {
        if (closed.get() || !available || isDecoding.get()) return;
        isDecoding.set(true);
        currentDecodeFuture = worker.submit(() -> {
            try {
                decodeGopSegment(targetPtsMs);
            } finally {
                isDecoding.set(false);
            }
        });
    }

    private void decodeGopSegment(long targetPtsMs) {
        if (closed.get() || formatContext == null || videoCodecContext == null) return;
        try {
            long seekTargetMs = Math.max(0, targetPtsMs - 300);
            long seekTargetPts = (long) ((seekTargetMs / 1000.0) / timeBaseSec);
            avformat.av_seek_frame(formatContext, videoStreamIndex, seekTargetPts, avformat.AVSEEK_FLAG_BACKWARD);
            avcodec.avcodec_flush_buffers(videoCodecContext);

            standbyBuffer.clear();

            while (!closed.get() && avformat.av_read_frame(formatContext, pkt) >= 0) {
                if (pkt.stream_index() == videoStreamIndex) {
                    int sendRet = avcodec.avcodec_send_packet(videoCodecContext, pkt);
                    if (sendRet >= 0) {
                        while (!closed.get() && avcodec.avcodec_receive_frame(videoCodecContext, frame) >= 0) {
                            long rawPts = frame.best_effort_timestamp();
                            if (rawPts == avutil.AV_NOPTS_VALUE || rawPts < 0) {
                                rawPts = frame.pts();
                            }
                            if (rawPts == avutil.AV_NOPTS_VALUE || rawPts < 0) {
                                rawPts = frame.pkt_dts();
                            }
                            long pts = (long) (Math.max(0, rawPts) * timeBaseSec * 1000.0);

                            tempDirectBuffer.clear();
                            swscale.sws_scale(
                                    swsContext,
                                    frame.data(),
                                    frame.linesize(),
                                    0,
                                    frame.height(),
                                    dstData,
                                    dstLinesize
                            );
                            tempDirectBuffer.position(0);
                            tempDirectBuffer.limit(width * height * 4);

                            standbyBuffer.appendFrame(pts, tempDirectBuffer);

                            if (standbyBuffer.getFrameCount() >= GOP_CAPACITY || pts >= targetPtsMs + 400) {
                                break;
                            }
                        }
                    }
                }
                avcodec.av_packet_unref(pkt);
                if (standbyBuffer.getFrameCount() >= GOP_CAPACITY || standbyBuffer.getEndPtsMs() >= targetPtsMs + 400) {
                    break;
                }
            }

            standbyBuffer.setReady(true);
            hasDecodedAny = true;

            // If active buffer is empty or does not cover target, swap immediately
            synchronized (this) {
                if (activeBuffer == null || !activeBuffer.isReady() || !activeBuffer.contains(targetPtsMs)) {
                    OffHeapGopBuffer temp = activeBuffer;
                    activeBuffer = standbyBuffer;
                    standbyBuffer = temp;
                }
            }
        } catch (Throwable t) {
            DiffuserLogger.error("Error decoding GOP segment in background", t);
        }
    }

    private void decodeSingleKeyframe(long targetPtsMs) {
        if (closed.get() || formatContext == null || videoCodecContext == null) return;
        try {
            long seekTargetPts = (long) ((Math.max(0, targetPtsMs) / 1000.0) / timeBaseSec);
            avformat.av_seek_frame(formatContext, videoStreamIndex, seekTargetPts, avformat.AVSEEK_FLAG_BACKWARD);
            avcodec.avcodec_flush_buffers(videoCodecContext);

            standbyBuffer.clear();

            while (!closed.get() && avformat.av_read_frame(formatContext, pkt) >= 0) {
                if (pkt.stream_index() == videoStreamIndex) {
                    int sendRet = avcodec.avcodec_send_packet(videoCodecContext, pkt);
                    if (sendRet >= 0) {
                        if (avcodec.avcodec_receive_frame(videoCodecContext, frame) >= 0) {
                            long rawPts = frame.best_effort_timestamp();
                            if (rawPts == avutil.AV_NOPTS_VALUE || rawPts < 0) {
                                rawPts = frame.pts();
                            }
                            if (rawPts == avutil.AV_NOPTS_VALUE || rawPts < 0) {
                                rawPts = frame.pkt_dts();
                            }
                            long pts = (long) (Math.max(0, rawPts) * timeBaseSec * 1000.0);

                            tempDirectBuffer.clear();
                            swscale.sws_scale(
                                    swsContext,
                                    frame.data(),
                                    frame.linesize(),
                                    0,
                                    frame.height(),
                                    dstData,
                                    dstLinesize
                            );
                            tempDirectBuffer.position(0);
                            tempDirectBuffer.limit(width * height * 4);

                            standbyBuffer.appendFrame(pts, tempDirectBuffer);
                            avcodec.av_packet_unref(pkt);
                            break;
                        }
                    }
                }
                avcodec.av_packet_unref(pkt);
            }

            standbyBuffer.setReady(true);
            synchronized (this) {
                OffHeapGopBuffer temp = activeBuffer;
                activeBuffer = standbyBuffer;
                standbyBuffer = temp;
                hasDecodedAny = true;
            }
        } catch (Throwable t) {
            DiffuserLogger.error("Error decoding keyframe in background", t);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            available = false;
            worker.shutdownNow();

            worker.submit(() -> {
                try {
                    if (activeBuffer != null) activeBuffer.close();
                    if (standbyBuffer != null) standbyBuffer.close();
                    if (swsContext != null) swscale.sws_freeContext(swsContext);
                    if (videoCodecContext != null) avcodec.avcodec_free_context(videoCodecContext);
                    if (formatContext != null) avformat.avformat_close_input(formatContext);
                    if (frame != null) avutil.av_frame_free(frame);
                    if (pkt != null) avcodec.av_packet_free(pkt);
                } catch (Throwable ignored) {}
            });
        }
    }
}
