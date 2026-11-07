package com.mik.optical_diffuser.client.media;

import java.nio.ByteBuffer;

/**
 * High-performance off-heap ring buffer holding decoded RGBA frames for one GOP.
 * Uses direct off-heap ByteBuffers to bypass JVM Garbage Collection without preview flags.
 */
public class OffHeapGopBuffer implements AutoCloseable {

    private final int capacity;
    private final int width;
    private final int height;
    private final int frameSizeBytes;

    private ByteBuffer directBuffer;
    private final long[] ptsMs;
    private int frameCount;
    private long startPtsMs;
    private long endPtsMs;
    private volatile boolean ready;

    public OffHeapGopBuffer(int width, int height, int capacity) {
        this.width = width;
        this.height = height;
        this.capacity = capacity;
        this.frameSizeBytes = width * height * 4; // RGBA 4 bytes per pixel
        long totalAllocatedBytes = (long) capacity * frameSizeBytes;
        this.ptsMs = new long[capacity];
        this.frameCount = 0;
        this.startPtsMs = -1;
        this.endPtsMs = -1;
        this.ready = false;

        this.directBuffer = ByteBuffer.allocateDirect((int) totalAllocatedBytes);
    }

    public synchronized void appendFrame(long pts, ByteBuffer rgbaSource) {
        if (frameCount >= capacity || directBuffer == null) return;
        int offset = frameCount * frameSizeBytes;
        directBuffer.position(offset);
        directBuffer.limit(offset + frameSizeBytes);
        directBuffer.put(rgbaSource);

        ptsMs[frameCount] = pts;
        if (frameCount == 0) {
            startPtsMs = pts;
            endPtsMs = pts;
        } else {
            startPtsMs = Math.min(startPtsMs, pts);
            endPtsMs = Math.max(endPtsMs, pts);
        }
        frameCount++;
    }

    public synchronized void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isReady() {
        return ready && frameCount > 0;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public long getStartPtsMs() {
        return startPtsMs;
    }

    public long getEndPtsMs() {
        return endPtsMs;
    }

    public boolean contains(long targetPtsMs) {
        if (!isReady()) return false;
        return targetPtsMs >= (startPtsMs - 150) && targetPtsMs <= (endPtsMs + 150);
    }

    /**
     * Finds the closest frame index for the given target timestamp.
     * Uses linear scan over <= 75 items (~30 nanoseconds), fully supporting
     * non-monotonic B-frame PTS ordering without binary search failures.
     */
    public synchronized int findClosestFrameIndex(long targetPtsMs) {
        if (frameCount <= 0) return -1;
        if (frameCount == 1) return 0;

        int bestIdx = 0;
        long minDiff = Math.abs(ptsMs[0] - targetPtsMs);
        for (int i = 1; i < frameCount; i++) {
            long diff = Math.abs(ptsMs[i] - targetPtsMs);
            if (diff < minDiff) {
                minDiff = diff;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Gets a direct ByteBuffer for the frame at the specified index.
     */
    public synchronized ByteBuffer getFrameBuffer(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= frameCount || directBuffer == null) return null;
        int offset = frameIndex * frameSizeBytes;
        ByteBuffer duplicate = directBuffer.duplicate();
        duplicate.position(offset);
        duplicate.limit(offset + frameSizeBytes);
        return duplicate.slice();
    }

    public synchronized void clear() {
        this.frameCount = 0;
        this.startPtsMs = -1;
        this.endPtsMs = -1;
        this.ready = false;
    }

    @Override
    public synchronized void close() {
        clear();
        this.directBuffer = null;
    }
}
