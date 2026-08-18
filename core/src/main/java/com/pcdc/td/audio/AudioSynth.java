package com.pcdc.td.audio;

import com.badlogic.gdx.files.FileHandle;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * Sintetizador minimalista: mezcla tonos y ruido en un buffer mono y lo
 * escribe como WAV PCM 16-bit. Todo el audio del juego se genera con esto.
 */
public class AudioSynth {

    public static final int RATE = 22050;

    public static final int SINE = 0;
    public static final int SQUARE = 1;
    public static final int SAW = 2;
    public static final int TRIANGLE = 3;
    public static final int NOISE = 4;

    private final float[] buf;
    private final Random noiseRng = new Random(12345);

    public AudioSynth(float seconds) {
        buf = new float[Math.max(1, (int) (seconds * RATE))];
    }

    public float seconds() {
        return buf.length / (float) RATE;
    }

    /**
     * Añade una nota al buffer.
     *
     * @param freqStart/freqEnd frecuencia inicial y final (barrido lineal)
     * @param attack/release    envolvente lineal en segundos
     */
    public void tone(float start, float dur, float freqStart, float freqEnd,
                     float vol, int wave, float attack, float release) {
        int s0 = Math.max(0, (int) (start * RATE));
        int n = (int) (dur * RATE);
        int end = Math.min(buf.length, s0 + n);
        float phase = 0f;
        for (int i = s0; i < end; i++) {
            float t = (i - s0) / (float) n;
            float freq = freqStart + (freqEnd - freqStart) * t;
            phase += freq / RATE;
            float p = phase - (float) Math.floor(phase);

            float sample;
            switch (wave) {
                case SQUARE: sample = p < 0.5f ? 1f : -1f; break;
                case SAW: sample = 2f * p - 1f; break;
                case TRIANGLE: sample = p < 0.5f ? 4f * p - 1f : 3f - 4f * p; break;
                case NOISE: sample = noiseRng.nextFloat() * 2f - 1f; break;
                default: sample = (float) Math.sin(2.0 * Math.PI * p); break;
            }

            float tSec = (i - s0) / (float) RATE;
            float remSec = (end - i) / (float) RATE;
            float env = 1f;
            if (attack > 0f && tSec < attack) env = tSec / attack;
            if (release > 0f && remSec < release) env = Math.min(env, remSec / release);

            buf[i] += sample * vol * env;
        }
    }

    /** Nota simple sin barrido. */
    public void note(float start, float dur, float freq, float vol, int wave, float attack, float release) {
        tone(start, dur, freq, freq, vol, wave, attack, release);
    }

    public static float midi(int note) {
        return (float) (440.0 * Math.pow(2.0, (note - 69) / 12.0));
    }

    /** Escribe el buffer como WAV (PCM 16-bit mono) con limitador suave. */
    public void writeWav(FileHandle file) {
        try (OutputStream raw = file.write(false); BufferedOutputStream out = new BufferedOutputStream(raw, 1 << 16)) {
            int dataLen = buf.length * 2;
            writeHeader(out, dataLen);
            for (float sampleF : buf) {
                // limitador suave para evitar clipping duro al sumar voces
                float x = (float) Math.tanh(sampleF);
                int s = (int) (x * 32000f);
                out.write(s & 0xFF);
                out.write((s >> 8) & 0xFF);
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo escribir " + file, e);
        }
    }

    private static void writeHeader(OutputStream out, int dataLen) throws IOException {
        out.write(new byte[]{'R', 'I', 'F', 'F'});
        writeIntLE(out, 36 + dataLen);
        out.write(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        writeIntLE(out, 16);
        writeShortLE(out, 1);  // PCM
        writeShortLE(out, 1);  // mono
        writeIntLE(out, RATE);
        writeIntLE(out, RATE * 2); // byte rate
        writeShortLE(out, 2);  // block align
        writeShortLE(out, 16); // bits
        out.write(new byte[]{'d', 'a', 't', 'a'});
        writeIntLE(out, dataLen);
    }

    private static void writeIntLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static void writeShortLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }
}
