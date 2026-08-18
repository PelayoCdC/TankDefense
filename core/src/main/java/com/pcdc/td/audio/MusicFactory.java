package com.pcdc.td.audio;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.Random;

import static com.pcdc.td.audio.AudioSynth.NOISE;
import static com.pcdc.td.audio.AudioSynth.SAW;
import static com.pcdc.td.audio.AudioSynth.SINE;
import static com.pcdc.td.audio.AudioSynth.SQUARE;
import static com.pcdc.td.audio.AudioSynth.TRIANGLE;
import static com.pcdc.td.audio.AudioSynth.midi;

/**
 * Música generativa: un loop de 8 compases por tema, compuesto por código
 * (bajo, arpegio, colchón y percusión) sobre la escala y tempo del bioma.
 */
public final class MusicFactory {

    private static class Theme {
        final int root;        // nota MIDI de la tónica
        final int[] scale;     // intervalos de la escala
        final float bpm;
        final int leadWave;
        final float bassVol, leadVol, padVol, percVol;
        final int[] progression; // grados (0-based) por cada 2 compases
        final int seed;

        Theme(int root, int[] scale, float bpm, int leadWave,
              float bassVol, float leadVol, float padVol, float percVol,
              int[] progression, int seed) {
            this.root = root;
            this.scale = scale;
            this.bpm = bpm;
            this.leadWave = leadWave;
            this.bassVol = bassVol;
            this.leadVol = leadVol;
            this.padVol = padVol;
            this.percVol = percVol;
            this.progression = progression;
            this.seed = seed;
        }
    }

    private static final int[] MAJOR_PENT = {0, 2, 4, 7, 9};
    private static final int[] MINOR_PENT = {0, 3, 5, 7, 10};
    private static final int[] MINOR = {0, 2, 3, 5, 7, 8, 10};
    private static final int[] DORIAN = {0, 2, 3, 5, 7, 9, 10};
    private static final int[] PHRYGIAN = {0, 1, 3, 5, 7, 8, 10};
    private static final int[] LYDIAN = {0, 2, 4, 6, 7, 9, 11};
    private static final int[] HARM_MINOR = {0, 2, 3, 5, 7, 8, 11};
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};

    private static final ObjectMap<String, Theme> THEMES = new ObjectMap<>();

    static {
        //                        root  escala       bpm  lead      bass  lead  pad   perc  progresión         seed
        THEMES.put("menu", new Theme(57, MAJOR_PENT, 80, TRIANGLE, 0.30f, 0.16f, 0.10f, 0.00f, new int[]{0, 3, 4, 3}, 11));
        THEMES.put("meadow", new Theme(60, MAJOR_PENT, 96, TRIANGLE, 0.32f, 0.20f, 0.08f, 0.10f, new int[]{0, 3, 4, 2}, 21));
        THEMES.put("desert", new Theme(52, PHRYGIAN, 92, SAW, 0.30f, 0.16f, 0.09f, 0.12f, new int[]{0, 1, 0, 4}, 31));
        THEMES.put("forest", new Theme(55, DORIAN, 86, TRIANGLE, 0.30f, 0.17f, 0.10f, 0.08f, new int[]{0, 2, 5, 3}, 41));
        THEMES.put("snow", new Theme(64, MAJOR, 74, SINE, 0.26f, 0.15f, 0.13f, 0.00f, new int[]{0, 5, 3, 4}, 51));
        THEMES.put("volcano", new Theme(50, MINOR, 116, SAW, 0.34f, 0.18f, 0.08f, 0.16f, new int[]{0, 5, 6, 4}, 61));
        THEMES.put("swamp", new Theme(53, MINOR_PENT, 70, SINE, 0.30f, 0.14f, 0.12f, 0.07f, new int[]{0, 2, 0, 3}, 71));
        THEMES.put("crystal", new Theme(62, LYDIAN, 100, TRIANGLE, 0.28f, 0.20f, 0.10f, 0.09f, new int[]{0, 3, 1, 4}, 81));
        THEMES.put("ruins", new Theme(54, HARM_MINOR, 82, TRIANGLE, 0.30f, 0.16f, 0.11f, 0.08f, new int[]{0, 3, 5, 6}, 91));
        THEMES.put("storm", new Theme(52, MINOR, 122, SQUARE, 0.33f, 0.17f, 0.07f, 0.17f, new int[]{0, 5, 3, 6}, 101));
        THEMES.put("corrupt", new Theme(49, HARM_MINOR, 106, SAW, 0.33f, 0.17f, 0.09f, 0.15f, new int[]{0, 1, 5, 6}, 111));
    }

    /** Sufijo de la variante de jefe: "volcano" → "volcano_boss". */
    public static final String BOSS_SUFFIX = "_boss";

    public static String bossTheme(String biomeId) {
        return biomeId + BOSS_SUFFIX;
    }

    private MusicFactory() {
    }

    public static boolean hasTheme(String id) {
        return THEMES.containsKey(baseId(id));
    }

    private static String baseId(String id) {
        return id.endsWith(BOSS_SUFFIX)
            ? id.substring(0, id.length() - BOSS_SUFFIX.length()) : id;
    }

    /**
     * Variante de combate del tema: más rápida, con el bajo y la percusión
     * subidos y el arpegio en sierra. Es el mismo material, así que el corte al
     * aparecer el jefe se reconoce como "la misma canción, desatada".
     */
    private static Theme toBoss(Theme t) {
        return new Theme(t.root - 2, t.scale, t.bpm * 1.14f, SAW,
            t.bassVol * 1.15f, t.leadVol * 1.05f, t.padVol * 0.7f,
            Math.max(0.13f, t.percVol * 1.7f), t.progression, t.seed + 7);
    }

    private static int scaleNote(Theme t, int degree, int octaveShift) {
        int len = t.scale.length;
        int idx = ((degree % len) + len) % len;
        int octave = Math.floorDiv(degree, len);
        return t.root + t.scale[idx] + 12 * (octave + octaveShift);
    }

    public static void generate(String id, FileHandle out) {
        Theme t = THEMES.get(baseId(id));
        if (t == null) t = THEMES.get("menu");
        if (id.endsWith(BOSS_SUFFIX)) t = toBoss(t);
        Random rng = new Random(t.seed);

        int bars = 8;
        float beat = 60f / t.bpm;
        float total = bars * 4 * beat;
        AudioSynth s = new AudioSynth(total + 0.001f);

        for (int bar = 0; bar < bars; bar++) {
            float barStart = bar * 4 * beat;
            int degree = t.progression[(bar / 2) % t.progression.length];

            // bajo: tónica del acorde con un patrón raíz-raíz-quinta-raíz
            for (int b = 0; b < 4; b++) {
                int deg = (b == 2) ? degree + 2 : degree;
                float f = midi(scaleNote(t, deg, -1));
                s.note(barStart + b * beat, beat * 0.85f, f, t.bassVol, SINE, 0.008f, beat * 0.3f);
                s.note(barStart + b * beat, beat * 0.4f, f, t.bassVol * 0.35f, SQUARE, 0.008f, beat * 0.15f);
            }

            // colchón: tríada del acorde sostenida todo el compás
            if (t.padVol > 0f) {
                for (int i = 0; i < 3; i++) {
                    float f = midi(scaleNote(t, degree + i * 2, 0));
                    s.note(barStart, 4 * beat, f, t.padVol / 3f, SAW, beat * 0.8f, beat);
                }
            }

            // arpegio: corcheas sobre notas del acorde (con silencios aleatorios)
            for (int e = 0; e < 8; e++) {
                if (rng.nextFloat() < 0.25f) continue;
                int pick = rng.nextInt(4);
                int deg = degree + (pick == 3 ? 7 : pick * 2);
                float f = midi(scaleNote(t, deg, 1));
                s.note(barStart + e * beat * 0.5f, beat * 0.42f, f, t.leadVol, t.leadWave, 0.008f, beat * 0.2f);
            }

            // percusión: bombo en 1 y 3, caja en 2 y 4, corcheas de "hi-hat"
            if (t.percVol > 0f) {
                s.tone(barStart, 0.1f, 100f, 42f, t.percVol * 3f, SINE, 0.002f, 0.07f);
                s.tone(barStart + 2 * beat, 0.1f, 100f, 42f, t.percVol * 3f, SINE, 0.002f, 0.07f);
                s.tone(barStart + beat, 0.07f, 0f, 0f, t.percVol * 1.4f, NOISE, 0.001f, 0.05f);
                s.tone(barStart + 3 * beat, 0.07f, 0f, 0f, t.percVol * 1.4f, NOISE, 0.001f, 0.05f);
                for (int e = 0; e < 8; e++) {
                    s.tone(barStart + e * beat * 0.5f, 0.03f, 0f, 0f, t.percVol * 0.5f, NOISE, 0.001f, 0.02f);
                }
            }
        }

        s.writeWav(out);
    }
}
