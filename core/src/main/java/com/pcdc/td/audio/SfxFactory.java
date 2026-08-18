package com.pcdc.td.audio;

import com.badlogic.gdx.files.FileHandle;

import static com.pcdc.td.audio.AudioSynth.NOISE;
import static com.pcdc.td.audio.AudioSynth.SAW;
import static com.pcdc.td.audio.AudioSynth.SINE;
import static com.pcdc.td.audio.AudioSynth.SQUARE;
import static com.pcdc.td.audio.AudioSynth.TRIANGLE;
import static com.pcdc.td.audio.AudioSynth.midi;

/** Efectos de sonido, todos sintetizados por código. */
public final class SfxFactory {

    public static final String[] IDS = {
        "shoot", "shoot_light", "shoot_sniper", "hit", "explosion", "death",
        "build", "sell", "fusion", "click", "buy", "denied", "wave", "victory", "defeat", "firework",
        "zap", "sting_champion", "sting_overlord", "radar_ping", "event_alert"
    };

    private SfxFactory() {
    }

    public static void generate(String id, FileHandle out) {
        AudioSynth s;
        switch (id) {
            case "shoot": // cañonazo seco
                s = new AudioSynth(0.14f);
                s.tone(0f, 0.09f, 300f, 70f, 0.55f, SINE, 0.002f, 0.05f);
                s.tone(0f, 0.05f, 0f, 0f, 0.25f, NOISE, 0.001f, 0.04f);
                break;
            case "shoot_light": // tiro de metralleta
                s = new AudioSynth(0.06f);
                s.tone(0f, 0.035f, 900f, 500f, 0.22f, SQUARE, 0.001f, 0.02f);
                s.tone(0f, 0.02f, 0f, 0f, 0.12f, NOISE, 0.001f, 0.015f);
                break;
            case "shoot_sniper": // crack de francotirador
                s = new AudioSynth(0.2f);
                s.tone(0f, 0.06f, 0f, 0f, 0.6f, NOISE, 0.001f, 0.05f);
                s.tone(0f, 0.09f, 1400f, 180f, 0.35f, SAW, 0.001f, 0.06f);
                break;
            case "hit": // impacto pequeño
                s = new AudioSynth(0.07f);
                s.tone(0f, 0.04f, 520f, 320f, 0.3f, SQUARE, 0.001f, 0.025f);
                break;
            case "explosion": // pepinazo del mortero
                s = new AudioSynth(0.5f);
                s.tone(0f, 0.4f, 0f, 0f, 0.7f, NOISE, 0.002f, 0.32f);
                s.tone(0f, 0.35f, 110f, 38f, 0.8f, SINE, 0.002f, 0.28f);
                break;
            case "death": // muerte de bicho
                s = new AudioSynth(0.28f);
                s.tone(0f, 0.2f, 420f, 90f, 0.4f, SAW, 0.005f, 0.12f);
                s.tone(0.03f, 0.12f, 300f, 70f, 0.2f, TRIANGLE, 0.005f, 0.08f);
                break;
            case "build": // colocar torre
                s = new AudioSynth(0.18f);
                s.tone(0f, 0.05f, 0f, 0f, 0.3f, NOISE, 0.001f, 0.04f);
                s.tone(0.02f, 0.12f, 200f, 380f, 0.4f, SINE, 0.01f, 0.06f);
                break;
            case "sell": // monedas
                s = new AudioSynth(0.24f);
                s.note(0f, 0.07f, 900f, 0.3f, TRIANGLE, 0.002f, 0.04f);
                s.note(0.08f, 0.1f, 1250f, 0.3f, TRIANGLE, 0.002f, 0.06f);
                break;
            case "fusion": // fusión mágica ascendente
                s = new AudioSynth(0.5f);
                s.tone(0f, 0.35f, 250f, 950f, 0.35f, SINE, 0.02f, 0.15f);
                s.note(0.22f, 0.1f, midi(88), 0.22f, TRIANGLE, 0.005f, 0.06f);
                s.note(0.32f, 0.15f, midi(93), 0.22f, TRIANGLE, 0.005f, 0.1f);
                break;
            case "click": // UI
                s = new AudioSynth(0.05f);
                s.note(0f, 0.03f, 700f, 0.2f, SQUARE, 0.001f, 0.02f);
                break;
            case "buy": // compra confirmada: dos notas ascendentes claras
                s = new AudioSynth(0.3f);
                s.note(0f, 0.09f, midi(76), 0.28f, TRIANGLE, 0.002f, 0.05f);
                s.note(0.08f, 0.16f, midi(83), 0.3f, TRIANGLE, 0.002f, 0.1f);
                break;
            case "denied": // acción imposible: zumbido grave y corto
                s = new AudioSynth(0.18f);
                s.note(0f, 0.12f, midi(41), 0.3f, SQUARE, 0.002f, 0.06f);
                s.note(0.05f, 0.1f, midi(38), 0.26f, SQUARE, 0.002f, 0.06f);
                break;
            case "wave": // cuerno de oleada
                s = new AudioSynth(0.6f);
                s.note(0f, 0.25f, midi(45), 0.4f, SAW, 0.02f, 0.1f);
                s.note(0.22f, 0.33f, midi(52), 0.4f, SAW, 0.02f, 0.2f);
                break;
            case "victory": // fanfarria
                s = new AudioSynth(1.1f);
                s.note(0f, 0.16f, midi(72), 0.35f, TRIANGLE, 0.005f, 0.06f);
                s.note(0.16f, 0.16f, midi(76), 0.35f, TRIANGLE, 0.005f, 0.06f);
                s.note(0.32f, 0.16f, midi(79), 0.35f, TRIANGLE, 0.005f, 0.06f);
                s.note(0.48f, 0.5f, midi(84), 0.4f, TRIANGLE, 0.005f, 0.3f);
                s.note(0.48f, 0.5f, midi(72), 0.2f, SINE, 0.005f, 0.3f);
                break;
            case "zap": // descarga eléctrica del Tesla: chisporroteo agudo descendente
                s = new AudioSynth(0.16f);
                s.tone(0f, 0.1f, 2400f, 600f, 0.3f, SQUARE, 0.001f, 0.06f);
                s.tone(0f, 0.07f, 0f, 0f, 0.22f, NOISE, 0.001f, 0.05f);
                s.tone(0.04f, 0.08f, 1600f, 300f, 0.18f, SAW, 0.001f, 0.05f);
                break;
            case "firework": // silbido ascendente + tres estallidos (victoria perfecta)
                s = new AudioSynth(1.6f);
                s.tone(0f, 0.45f, 350f, 1200f, 0.2f, SINE, 0.01f, 0.1f);
                s.tone(0.5f, 0.35f, 0f, 0f, 0.55f, NOISE, 0.002f, 0.28f);
                s.tone(0.75f, 0.3f, 0f, 0f, 0.4f, NOISE, 0.002f, 0.24f);
                s.tone(0.95f, 0.4f, 0f, 0f, 0.45f, NOISE, 0.002f, 0.32f);
                s.note(0.55f, 0.3f, midi(88), 0.16f, TRIANGLE, 0.005f, 0.2f);
                s.note(0.8f, 0.3f, midi(91), 0.14f, TRIANGLE, 0.005f, 0.2f);
                s.note(1.0f, 0.4f, midi(96), 0.14f, TRIANGLE, 0.005f, 0.3f);
                break;
            case "sting_champion": // el mini-jefe asoma: dos notas graves amenazantes
                s = new AudioSynth(0.7f);
                s.note(0f, 0.28f, midi(43), 0.4f, SAW, 0.01f, 0.14f);
                s.note(0.26f, 0.4f, midi(46), 0.42f, SAW, 0.01f, 0.26f);
                s.tone(0.26f, 0.3f, 0f, 0f, 0.12f, NOISE, 0.01f, 0.24f);
                break;
            case "sting_overlord": // EL CORRUPTOR: tríada descendente + trueno
                s = new AudioSynth(1.5f);
                s.note(0f, 0.3f, midi(50), 0.45f, SAW, 0.01f, 0.14f);
                s.note(0.28f, 0.3f, midi(46), 0.45f, SAW, 0.01f, 0.14f);
                s.note(0.56f, 0.6f, midi(38), 0.5f, SAW, 0.01f, 0.4f);
                s.tone(0.56f, 0.7f, 0f, 0f, 0.5f, NOISE, 0.005f, 0.55f);
                s.tone(0.6f, 0.6f, 70f, 32f, 0.5f, SINE, 0.005f, 0.5f);
                break;
            case "radar_ping": // barrido del radar: blip sonar suave
                s = new AudioSynth(0.35f);
                s.note(0f, 0.06f, 1180f, 0.14f, SINE, 0.002f, 0.04f);
                s.tone(0.05f, 0.26f, 1180f, 1120f, 0.06f, SINE, 0.01f, 0.22f);
                break;
            case "heartbeat": // pocas vidas: dos golpes graves de pecho
                s = new AudioSynth(0.6f);
                s.tone(0f, 0.16f, 78f, 44f, 0.5f, SINE, 0.006f, 0.12f);
                s.tone(0.22f, 0.2f, 68f, 38f, 0.42f, SINE, 0.006f, 0.16f);
                break;
            case "chopper": // el helicóptero suelta la carga: palas y aire
                s = new AudioSynth(0.55f);
                for (int beat = 0; beat < 7; beat++) { // batir de palas
                    s.tone(beat * 0.075f, 0.05f, 0f, 0f, 0.16f, NOISE, 0.004f, 0.03f);
                    s.tone(beat * 0.075f, 0.06f, 120f, 70f, 0.2f, SINE, 0.004f, 0.04f);
                }
                break;
            case "landing": // el tanque toca suelo: golpe seco y chapa
                s = new AudioSynth(0.32f);
                s.tone(0f, 0.12f, 150f, 45f, 0.5f, SINE, 0.002f, 0.09f);
                s.tone(0f, 0.09f, 0f, 0f, 0.3f, NOISE, 0.001f, 0.07f);
                s.note(0.06f, 0.12f, midi(52), 0.16f, TRIANGLE, 0.003f, 0.09f);
                break;
            case "event_alert": // oleada con evento: dos toques metálicos urgentes
                s = new AudioSynth(0.45f);
                s.note(0f, 0.09f, midi(81), 0.3f, SQUARE, 0.002f, 0.05f);
                s.note(0.14f, 0.2f, midi(81), 0.32f, SQUARE, 0.002f, 0.14f);
                s.note(0.14f, 0.2f, midi(85), 0.18f, SQUARE, 0.002f, 0.14f);
                break;
            default: // defeat: descenso sombrío
                s = new AudioSynth(1.2f);
                s.note(0f, 0.3f, midi(57), 0.35f, SAW, 0.01f, 0.15f);
                s.note(0.3f, 0.3f, midi(53), 0.35f, SAW, 0.01f, 0.15f);
                s.note(0.6f, 0.55f, midi(48), 0.4f, SAW, 0.01f, 0.4f);
                break;
        }
        s.writeWav(out);
    }
}
