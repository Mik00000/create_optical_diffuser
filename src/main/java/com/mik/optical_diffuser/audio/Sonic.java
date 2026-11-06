package com.mik.optical_diffuser.audio;

/*
 * Sonic library
 * Copyright 2010, 2011 Bill Cox
 *
 * This file is part of the Sonic Library.
 *
 * The Sonic Library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * The Sonic Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

public class Sonic {

    private static final int SONIC_MIN_PITCH = 65;
    private static final int SONIC_MAX_PITCH = 400;
    private static final int SONIC_AMDF_FREQ = 4000;

    private final int sampleRate;
    private final int numChannels;
    private final int minPeriod;
    private final int maxPeriod;
    private final int maxRequired;

    private short[] inputBuffer;
    private short[] outputBuffer;
    private short[] pitchBuffer;
    private short[] downSampleBuffer;

    private float speed = 1.0f;
    private float volume = 1.0f;
    private float pitch = 1.0f;
    private float rate = 1.0f;

    private int numInputSamples = 0;
    private int numOutputSamples = 0;
    private int numPitchSamples = 0;
    private int inputBufferSize;
    private int outputBufferSize;
    private int pitchBufferSize;
    private int prevPeriod = 0;
    private int prevMinDiff = 0;

    public Sonic(int sampleRate, int numChannels) {
        this.sampleRate = sampleRate;
        this.numChannels = numChannels;
        this.minPeriod = sampleRate / SONIC_MAX_PITCH;
        this.maxPeriod = sampleRate / SONIC_MIN_PITCH;
        this.maxRequired = 2 * maxPeriod;

        this.inputBufferSize = maxRequired;
        this.outputBufferSize = maxRequired;
        this.pitchBufferSize = maxRequired;

        this.inputBuffer = new short[maxRequired * numChannels];
        this.outputBuffer = new short[maxRequired * numChannels];
        this.pitchBuffer = new short[maxRequired * numChannels];
        this.downSampleBuffer = new short[maxRequired];
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.1f, Math.min(speed, 10.0f));
    }

    public float getSpeed() {
        return speed;
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(0.1f, Math.min(pitch, 10.0f));
    }

    public float getPitch() {
        return pitch;
    }

    public void setRate(float rate) {
        this.rate = Math.max(0.1f, Math.min(rate, 10.0f));
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getNumChannels() {
        return numChannels;
    }

    public int samplesAvailable() {
        return numOutputSamples;
    }

    private void allocateInputBuffer(int numSamples) {
        if (numInputSamples + numSamples > inputBufferSize) {
            inputBufferSize += (inputBufferSize >> 1) + numSamples;
            short[] newBuffer = new short[inputBufferSize * numChannels];
            System.arraycopy(inputBuffer, 0, newBuffer, 0, numInputSamples * numChannels);
            inputBuffer = newBuffer;
        }
    }

    private void allocateOutputBuffer(int numSamples) {
        if (numOutputSamples + numSamples > outputBufferSize) {
            outputBufferSize += (outputBufferSize >> 1) + numSamples;
            short[] newBuffer = new short[outputBufferSize * numChannels];
            System.arraycopy(outputBuffer, 0, newBuffer, 0, numOutputSamples * numChannels);
            outputBuffer = newBuffer;
        }
    }

    public boolean writeShortToStream(short[] samples, int numSamples) {
        if (numSamples == 0) return true;
        allocateInputBuffer(numSamples);
        System.arraycopy(samples, 0, inputBuffer, numInputSamples * numChannels, numSamples * numChannels);
        numInputSamples += numSamples;
        processStreamInput();
        return true;
    }

    public int readShortFromStream(short[] samples, int maxSamples) {
        int numSamples = Math.min(numOutputSamples, maxSamples);
        if (numSamples == 0) return 0;
        System.arraycopy(outputBuffer, 0, samples, 0, numSamples * numChannels);
        moveNewSamplesToPitchBuffer(numSamples);
        return numSamples;
    }

    public void flushStream() {
        int remaining = numInputSamples;
        float s = speed / pitch;
        float r = rate * pitch;
        int expectedOutput = numOutputSamples + (int) ((remaining / s + numPitchSamples) / r + 0.5f);

        allocateInputBuffer(remaining + 2 * maxRequired);
        for (int x = 0; x < 2 * maxRequired * numChannels; x++) {
            inputBuffer[numInputSamples * numChannels + x] = 0;
        }
        numInputSamples += 2 * maxRequired;
        writeShortToStream(null, 0);
        if (numOutputSamples > expectedOutput) {
            numOutputSamples = expectedOutput;
        }
        numInputSamples = 0;
        prevPeriod = 0;
        prevMinDiff = 0;
    }

    private void processStreamInput() {
        int originalNumOutputSamples = numOutputSamples;
        float s = speed / pitch;
        float r = rate * pitch;

        if (s > 1.00001 || s < 0.99999) {
            changeSpeed(s);
        } else {
            copyInputToOutput(0, numInputSamples);
            numInputSamples = 0;
        }

        if (r != 1.0f) {
            adjustRate(r, originalNumOutputSamples);
        }

        if (volume != 1.0f) {
            scaleSamples(outputBuffer, originalNumOutputSamples, numOutputSamples - originalNumOutputSamples, volume);
        }
    }

    private void copyInputToOutput(int position, int numSamples) {
        allocateOutputBuffer(numSamples);
        System.arraycopy(inputBuffer, position * numChannels, outputBuffer, numOutputSamples * numChannels, numSamples * numChannels);
        numOutputSamples += numSamples;
    }

    private void copyToOutput(short[] samples, int position, int numSamples) {
        allocateOutputBuffer(numSamples);
        System.arraycopy(samples, position * numChannels, outputBuffer, numOutputSamples * numChannels, numSamples * numChannels);
        numOutputSamples += numSamples;
    }

    private void moveNewSamplesToPitchBuffer(int originalNumOutputSamples) {
        int remaining = numOutputSamples - originalNumOutputSamples;
        if (remaining > 0) {
            System.arraycopy(outputBuffer, originalNumOutputSamples * numChannels, outputBuffer, 0, remaining * numChannels);
        }
        numOutputSamples = remaining;
    }

    private void scaleSamples(short[] samples, int position, int numSamples, float volume) {
        int fixedVolume = (int) (volume * 4096.0f);
        int start = position * numChannels;
        int end = start + numSamples * numChannels;
        for (int i = start; i < end; i++) {
            int value = (samples[i] * fixedVolume) >> 12;
            if (value > 32767) value = 32767;
            else if (value < -32768) value = -32768;
            samples[i] = (short) value;
        }
    }

    private void changeSpeed(float speed) {
        int numSamples = numInputSamples;
        if (numSamples < maxRequired) return;

        int position = 0;
        do {
            if (speed > 1.0f) {
                int period = findPitchPeriod(inputBuffer, position, false);
                int newSamples = (int) (period / (speed - 1.0f));
                allocateOutputBuffer(newSamples);
                overlapAdd(newSamples, numChannels, outputBuffer, numOutputSamples, inputBuffer, position, inputBuffer, position + period);
                numOutputSamples += newSamples;
                position += period + newSamples;
            } else {
                int period = findPitchPeriod(inputBuffer, position, false);
                int newSamples = (int) (period * speed / (1.0f - speed));
                allocateOutputBuffer(period + newSamples);
                copyToOutput(inputBuffer, position, period);
                overlapAdd(newSamples, numChannels, outputBuffer, numOutputSamples, inputBuffer, position + period, inputBuffer, position);
                numOutputSamples += newSamples;
                position += newSamples;
            }
        } while (position + maxRequired <= numSamples);

        removeInputSamples(position);
    }

    private void removeInputSamples(int position) {
        int remaining = numInputSamples - position;
        if (remaining > 0) {
            System.arraycopy(inputBuffer, position * numChannels, inputBuffer, 0, remaining * numChannels);
        }
        numInputSamples = remaining;
    }

    private int findPitchPeriod(short[] samples, int position, boolean preferNew) {
        int period;
        int retPeriod;
        int skip = 1;

        if (sampleRate > SONIC_AMDF_FREQ && minPeriod > 0) {
            skip = sampleRate / SONIC_AMDF_FREQ;
        }

        if (numChannels == 1 && skip == 1) {
            period = findPitchPeriodInRange(samples, position, minPeriod, maxPeriod);
        } else {
            downSample(samples, position, skip);
            period = findPitchPeriodInRange(downSampleBuffer, 0, minPeriod / skip, maxPeriod / skip);
            if (skip > 1) {
                period *= skip;
                int minP = period - (skip << 2);
                int maxP = period + (skip << 2);
                if (minP < minPeriod) minP = minPeriod;
                if (maxP > maxPeriod) maxP = maxPeriod;
                if (numChannels == 1) {
                    period = findPitchPeriodInRange(samples, position, minP, maxP);
                } else {
                    downSample(samples, position, 1);
                    period = findPitchPeriodInRange(downSampleBuffer, 0, minP, maxP);
                }
            }
        }

        retPeriod = prevPeriod != 0 && prevMinDiff != 0 ? prevPeriod : period;
        prevMinDiff = 0;
        prevPeriod = period;
        return retPeriod;
    }

    private int findPitchPeriodInRange(short[] samples, int position, int minPeriod, int maxPeriod) {
        int bestPeriod = minPeriod;
        int minDiff = Integer.MAX_VALUE;

        for (int period = minPeriod; period <= maxPeriod; period++) {
            int diff = 0;
            int p1 = position;
            int p2 = position + period;
            for (int i = 0; i < period; i++) {
                int s1 = samples[p1 + i];
                int s2 = samples[p2 + i];
                diff += Math.abs(s1 - s2);
            }
            if (diff * bestPeriod < minDiff * period) {
                minDiff = diff;
                bestPeriod = period;
            }
        }
        return bestPeriod;
    }

    private void downSample(short[] samples, int position, int skip) {
        int numSamples = maxRequired / skip;
        int samplesPerValue = numChannels * skip;
        int valueIndex = position * numChannels;

        for (int i = 0; i < numSamples; i++) {
            int value = 0;
            for (int j = 0; j < samplesPerValue; j++) {
                value += samples[valueIndex++];
            }
            downSampleBuffer[i] = (short) (value / samplesPerValue);
        }
    }

    private void overlapAdd(int numSamples, int numChannels, short[] out, int outPos,
                            short[] rampDown, int rampDownPos, short[] rampUp, int rampUpPos) {
        for (int i = 0; i < numChannels; i++) {
            int o = outPos * numChannels + i;
            int u = rampUpPos * numChannels + i;
            int d = rampDownPos * numChannels + i;
            for (int t = 0; t < numSamples; t++) {
                out[o] = (short) ((rampDown[d] * (numSamples - t) + rampUp[u] * t) / numSamples);
                o += numChannels;
                d += numChannels;
                u += numChannels;
            }
        }
    }

    private void adjustRate(float rate, int originalNumOutputSamples) {
        if (numOutputSamples == originalNumOutputSamples) return;
        int newNumOutputSamples = originalNumOutputSamples + (int) ((numOutputSamples - originalNumOutputSamples) / rate);
        allocateOutputBuffer(newNumOutputSamples - numOutputSamples);
        // Linear interpolation rate adjustment if needed
    }
}
