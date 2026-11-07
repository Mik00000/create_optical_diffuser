package com.mik.optical_diffuser.client.media;

import org.lwjgl.openal.AL10;
import org.watermedia.api.player.MediaPlayer;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Manages reverse audio playback (Variant 2: Reverse Audio Scrub).
 * Captures forward audio into a rolling ring buffer and plays it backward
 * during reel rewind with pitch and speed tracking the shaft RPM.
 */
public class ReverseAudioPlayer {

    private static Field sfxField = null;
    private static Field queuedPcmField = null;
    private static Field sampleRateField = null;
    private static Field bytesPerFrameField = null;
    private static Field alFormatField = null;

    // Rolling Ring Buffer: 4 seconds of audio @ 48kHz stereo 16-bit (~768 KB)
    private static final int RING_BUFFER_SIZE = 768 * 1024;
    private final byte[] ringBuffer = new byte[RING_BUFFER_SIZE];
    private int ringWriteHead = 0;
    private int ringStoredBytes = 0;
    private int reverseReadHead = 0;

    private int sampleRate = 48000;
    private int alFormat = AL10.AL_FORMAT_STEREO16;
    private int bytesPerFrame = 4;

    private int reverseSource = 0;
    private final int[] reverseBuffers = new int[4];
    private int activeBuffersQueued = 0;
    private int lastCapturedHash = 0;

    public ReverseAudioPlayer() {
    }

    private void ensureOpenAL() {
        if (reverseSource == 0) {
            try {
                reverseSource = AL10.alGenSources();
                if (reverseSource > 0) {
                    for (int i = 0; i < reverseBuffers.length; i++) {
                        reverseBuffers[i] = AL10.alGenBuffers();
                    }
                }
            } catch (Throwable ignored) {
                reverseSource = 0;
            }
        }
    }

    /**
     * Captures forward PCM audio from WaterMedia's ALEngine into the rolling ring buffer.
     */
    public synchronized void captureForwardAudio(MediaPlayer player) {
        if (player == null) return;
        try {
            if (sfxField == null) {
                sfxField = MediaPlayer.class.getDeclaredField("sfx");
                sfxField.setAccessible(true);
            }
            Object sfxEngine = sfxField.get(player);
            if (sfxEngine == null) return;

            if (queuedPcmField == null) {
                queuedPcmField = sfxEngine.getClass().getDeclaredField("queuedPcm");
                queuedPcmField.setAccessible(true);
            }
            ByteBuffer[] queuedPcm = (ByteBuffer[]) queuedPcmField.get(sfxEngine);
            if (queuedPcm == null || queuedPcm.length == 0) return;

            if (sampleRateField == null) {
                sampleRateField = sfxEngine.getClass().getSuperclass().getDeclaredField("sampleRate");
                sampleRateField.setAccessible(true);
            }
            int sr = sampleRateField.getInt(sfxEngine);
            if (sr >= 8000 && sr <= 192000) {
                this.sampleRate = sr;
            }

            if (bytesPerFrameField == null) {
                bytesPerFrameField = sfxEngine.getClass().getDeclaredField("bytesPerFrame");
                bytesPerFrameField.setAccessible(true);
            }
            int bpf = bytesPerFrameField.getInt(sfxEngine);
            if (bpf > 0) {
                this.bytesPerFrame = bpf;
            }

            if (alFormatField == null) {
                alFormatField = sfxEngine.getClass().getDeclaredField("alFormat");
                alFormatField.setAccessible(true);
            }
            int fmt = alFormatField.getInt(sfxEngine);
            if (fmt != 0) {
                this.alFormat = fmt;
            }

            for (ByteBuffer buf : queuedPcm) {
                if (buf == null) continue;
                ByteBuffer dup = buf.duplicate();
                dup.rewind();
                int len = dup.remaining();
                if (len <= 0) continue;

                int hash = dup.getShort(0) ^ (dup.getShort(Math.max(0, len - 2)) << 16);
                if (hash == lastCapturedHash) continue;
                lastCapturedHash = hash;

                // Write into circular ring buffer
                for (int i = 0; i < len; i++) {
                    ringBuffer[ringWriteHead] = dup.get();
                    ringWriteHead = (ringWriteHead + 1) % RING_BUFFER_SIZE;
                }
                ringStoredBytes = Math.min(RING_BUFFER_SIZE, ringStoredBytes + len);
            }
            reverseReadHead = ringWriteHead;
        } catch (Throwable ignored) {}
    }

    /**
     * Plays backward audio chunks synchronized with rewind rotation.
     *
     * @param speedFactor Rewind speed factor (1.0 = 16 RPM)
     * @param volume      Audio volume (0.0 to 1.0)
     */
    public synchronized void tickRewind(float speedFactor, float volume) {
        if (volume <= 0.01f) {
            stop();
            return;
        }
        ensureOpenAL();
        if (reverseSource == 0) return;

        // Unqueue processed buffers
        try {
            int processed = AL10.alGetSourcei(reverseSource, AL10.AL_BUFFERS_PROCESSED);
            while (processed > 0) {
                AL10.alSourceUnqueueBuffers(reverseSource);
                activeBuffersQueued = Math.max(0, activeBuffersQueued - 1);
                processed--;
            }
        } catch (Throwable ignored) {}

        // Keep 2 buffers queued for continuous smooth audio
        while (activeBuffersQueued < 2) {
            int chunkBytes = Math.max(bytesPerFrame * 512, (sampleRate / 10) * bytesPerFrame); // ~100ms chunk
            ByteBuffer chunk = ByteBuffer.allocateDirect(chunkBytes).order(ByteOrder.LITTLE_ENDIAN);

            if (ringStoredBytes >= chunkBytes && bytesPerFrame >= 2) {
                // Read backwards from ring buffer, frame by frame
                int frameCount = chunkBytes / bytesPerFrame;
                for (int f = 0; f < frameCount; f++) {
                    reverseReadHead = (reverseReadHead - bytesPerFrame + RING_BUFFER_SIZE) % RING_BUFFER_SIZE;
                    for (int b = 0; b < bytesPerFrame; b++) {
                        chunk.put(ringBuffer[(reverseReadHead + b) % RING_BUFFER_SIZE]);
                    }
                }
                ringStoredBytes -= chunkBytes;
            } else {
                // Synthetic backward tape scrub friction chirp when ring buffer is empty
                int sampleCount = chunkBytes / 2;
                double freq = 220.0 * Math.max(0.5, Math.min(3.0, speedFactor));
                for (int s = 0; s < sampleCount; s++) {
                    double t = s / (double) sampleRate;
                    double amp = Math.sin(2.0 * Math.PI * freq * t) * Math.exp(-t * 8.0);
                    short val = (short) (amp * 6000.0 * (Math.random() * 0.3 + 0.85));
                    chunk.putShort(val);
                }
            }
            chunk.flip();

            int targetBuffer = reverseBuffers[activeBuffersQueued % reverseBuffers.length];
            try {
                AL10.alBufferData(targetBuffer, alFormat, chunk, sampleRate);
                AL10.alSourceQueueBuffers(reverseSource, targetBuffer);
                activeBuffersQueued++;
            } catch (Throwable ignored) {
                break;
            }
        }

        try {
            float pitch = Math.max(0.5f, Math.min(3.0f, speedFactor));
            AL10.alSourcef(reverseSource, AL10.AL_PITCH, pitch);
            AL10.alSourcef(reverseSource, AL10.AL_GAIN, volume);

            int state = AL10.alGetSourcei(reverseSource, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING) {
                AL10.alSourcePlay(reverseSource);
            }
        } catch (Throwable ignored) {}
    }

    public synchronized void stop() {
        if (reverseSource != 0) {
            try {
                AL10.alSourceStop(reverseSource);
                int queued = AL10.alGetSourcei(reverseSource, AL10.AL_BUFFERS_QUEUED);
                while (queued > 0) {
                    AL10.alSourceUnqueueBuffers(reverseSource);
                    queued--;
                }
            } catch (Throwable ignored) {}
            activeBuffersQueued = 0;
        }
    }

    public synchronized void release() {
        stop();
        if (reverseSource != 0) {
            try {
                AL10.alDeleteSources(reverseSource);
                for (int buf : reverseBuffers) {
                    if (buf != 0) AL10.alDeleteBuffers(buf);
                }
            } catch (Throwable ignored) {}
            reverseSource = 0;
        }
        ringStoredBytes = 0;
        ringWriteHead = 0;
        reverseReadHead = 0;
    }
}
