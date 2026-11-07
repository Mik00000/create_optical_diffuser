package com.mik.optical_diffuser.client.media;

import net.minecraft.core.BlockPos;
import org.lwjgl.openal.AL10;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * Authentic Cine-Mechanical Projector Sound Engine.
 *
 * Smooth vintage mechanical soundscape:
 * 1. Film reel whir / spool flutter (smooth lowpass-filtered Brownian noise tracking RPM).
 * 2. Subtle sprocket ratchet clicks (soft acoustic transients spaced at 16 pulses/sec).
 * 3. Spatialized 3D OpenAL audio centered at the projector block position.
 * 4. Dynamic pitch and volume modulation linked to Create shaft RPM.
 */
public class ProjectorAudioEngine {

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SECONDS = 1;
    private static final int TOTAL_SAMPLES = SAMPLE_RATE * BUFFER_SECONDS;

    private int alSource = 0;
    private int alBuffer = 0;
    private boolean initialized = false;

    public ProjectorAudioEngine() {
    }

    private synchronized void ensureInitialized() {
        if (initialized) return;
        try {
            alSource = AL10.alGenSources();
            if (alSource <= 0) {
                alSource = 0;
                return;
            }

            alBuffer = AL10.alGenBuffers();
            if (alBuffer <= 0) {
                AL10.alDeleteSources(alSource);
                alSource = 0;
                return;
            }

            // Synthesize vintage cine-mechanical loop (1 second mono 16-bit PCM @ 44.1kHz):
            ByteBuffer pcm = ByteBuffer.allocateDirect(TOTAL_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN);
            Random random = new Random(42);

            // 16 sprocket pulses per second (at standard 16 RPM = 1.0x projector speed)
            int pulseInterval = SAMPLE_RATE / 16;
            double brownian = 0.0;

            for (int i = 0; i < TOTAL_SAMPLES; i++) {
                // 1. Spool flutter (integrated pink/brown noise filtered to low frequencies)
                double white = (random.nextDouble() * 2.0 - 1.0);
                brownian = (brownian * 0.97) + (white * 0.03);
                double spoolWhir = brownian * 0.35;

                // 2. Soft mechanical sprocket click (subtle damped oscillation)
                int pulsePhase = i % pulseInterval;
                double sprocketClick = 0.0;
                if (pulsePhase < 80) {
                    double t = pulsePhase / (double) SAMPLE_RATE;
                    sprocketClick = Math.sin(2.0 * Math.PI * 1100.0 * t) * Math.exp(-pulsePhase * 0.08) * 0.18;
                }

                // Sum & soft clamp
                double mixed = spoolWhir + sprocketClick;
                mixed = Math.max(-1.0, Math.min(1.0, mixed));
                short sample = (short) (mixed * 32767.0 * 0.50);
                pcm.putShort(sample);
            }
            pcm.flip();

            AL10.alBufferData(alBuffer, AL10.AL_FORMAT_MONO16, pcm, SAMPLE_RATE);
            AL10.alSourcei(alSource, AL10.AL_BUFFER, alBuffer);
            AL10.alSourcei(alSource, AL10.AL_LOOPING, AL10.AL_TRUE);
            AL10.alSourcef(alSource, AL10.AL_REFERENCE_DISTANCE, 4.0f);
            AL10.alSourcef(alSource, AL10.AL_MAX_DISTANCE, 24.0f);
            AL10.alSourcef(alSource, AL10.AL_ROLLOFF_FACTOR, 1.2f);

            initialized = true;
        } catch (Throwable t) {
            com.mik.optical_diffuser.util.DiffuserLogger.error("Failed to initialize ProjectorAudioEngine OpenAL", t);
            release();
        }
    }

    public synchronized void tickRewind(float speedFactor, float volume, BlockPos pos) {
        if (volume <= 0.01f || speedFactor <= 0.05f) {
            stop();
            return;
        }

        ensureInitialized();
        if (alSource == 0) return;

        try {
            // Position source at the projector block in 3D world space
            if (pos != null) {
                AL10.alSource3f(alSource, AL10.AL_POSITION, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
            }

            // Pitch tracks shaft RPM smoothly (1.0x at 16 RPM, 2.0x at 32 RPM, 0.5x at 8 RPM)
            float pitch = Math.max(0.35f, Math.min(3.5f, speedFactor));
            AL10.alSourcef(alSource, AL10.AL_PITCH, pitch);

            // Gain scales with volume and speed (quieter at low RPM, capped at 0.45 to avoid harshness)
            float gain = volume * Math.min(0.45f, 0.20f + speedFactor * 0.20f);
            AL10.alSourcef(alSource, AL10.AL_GAIN, gain);

            int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING) {
                AL10.alSourcePlay(alSource);
            }
        } catch (Throwable ignored) {}
    }

    public synchronized void stop() {
        if (alSource != 0) {
            try {
                int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
                if (state == AL10.AL_PLAYING) {
                    AL10.alSourceStop(alSource);
                }
            } catch (Throwable ignored) {}
        }
    }

    public synchronized void release() {
        stop();
        if (alSource != 0) {
            try {
                AL10.alDeleteSources(alSource);
                if (alBuffer != 0) {
                    AL10.alDeleteBuffers(alBuffer);
                }
            } catch (Throwable ignored) {}
            alSource = 0;
            alBuffer = 0;
        }
        initialized = false;
    }
}
