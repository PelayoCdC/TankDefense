package com.pcdc.td.audio;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectFloatMap;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Audio del juego. Todos los WAV se sintetizan por código la primera vez y se
 * cachean en almacenamiento local; subir CACHE_VERSION los regenera.
 */
public class SoundManager implements Disposable {

    private static final int CACHE_VERSION = 1;

    private float musicVolume = 0.5f;
    private float sfxVolume = 0.8f;
    /** Intervalo mínimo entre repeticiones del mismo efecto (anti-metralleo). */
    private static final ObjectFloatMap<String> MIN_INTERVAL = new ObjectFloatMap<>();

    static {
        MIN_INTERVAL.put("shoot", 0.06f);
        MIN_INTERVAL.put("shoot_light", 0.07f);
        MIN_INTERVAL.put("shoot_sniper", 0.08f);
        MIN_INTERVAL.put("hit", 0.06f);
        MIN_INTERVAL.put("explosion", 0.12f);
        MIN_INTERVAL.put("death", 0.08f);
    }

    private final ObjectMap<String, Sound> sfx = new ObjectMap<>();
    private final ObjectFloatMap<String> lastPlayed = new ObjectFloatMap<>();
    private Music music;
    private String musicId;
    private float time;

    /** Fundido entre temas (0 = silencio, 1 = volumen pleno) y tema en espera. */
    private static final float FADE_SPEED = 2.2f;
    private float fadeLevel = 1f;
    private String pendingMusicId;

    private FileHandle dir() {
        String folder = "audio_v" + CACHE_VERSION + "/";
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            return Gdx.files.local(folder);
        }
        return Gdx.files.external(".towerdefense/" + folder);
    }

    /** Cargar los volúmenes guardados; llamar una vez con Gdx listo. */
    public void loadSettings() {
        musicVolume = Gdx.app.getPreferences("td-settings").getFloat("musicVol", 0.5f);
        sfxVolume = Gdx.app.getPreferences("td-settings").getFloat("sfxVol", 0.8f);
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public float getSfxVolume() {
        return sfxVolume;
    }

    public void setMusicVolume(float v) {
        musicVolume = Math.max(0f, Math.min(1f, v));
        applyMusicVolume();
        Gdx.app.getPreferences("td-settings").putFloat("musicVol", musicVolume).flush();
    }

    private void applyMusicVolume() {
        if (music != null) music.setVolume(musicVolume * fadeLevel * duck);
    }

    /**
     * Atenuación temporal de la música (0,35 = "a punto de perder"). Deja sitio
     * al latido y hace que el final apurado de una partida se sienta.
     */
    private float duck = 1f;

    public void setMusicDuck(float level) {
        float v = Math.max(0.2f, Math.min(1f, level));
        if (Math.abs(v - duck) < 0.01f) return;
        duck = v;
        applyMusicVolume();
    }

    public void setSfxVolume(float v) {
        sfxVolume = Math.max(0f, Math.min(1f, v));
        Gdx.app.getPreferences("td-settings").putFloat("sfxVol", sfxVolume).flush();
    }

    /** Llamar cada frame para el throttling temporal y el fundido de música. */
    public void update(float delta) {
        time += delta;

        if (pendingMusicId != null) {
            // baja el tema actual y, al llegar a cero, engancha el nuevo
            fadeLevel -= delta * FADE_SPEED;
            if (fadeLevel <= 0f) {
                fadeLevel = 0f;
                String next = pendingMusicId;
                pendingMusicId = null;
                startMusic(next);
            }
            applyMusicVolume();
        } else if (fadeLevel < 1f && music != null) {
            fadeLevel = Math.min(1f, fadeLevel + delta * FADE_SPEED);
            applyMusicVolume();
        }
    }

    public void playSfx(String id) {
        float last = lastPlayed.get(id, -100f);
        if (time - last < MIN_INTERVAL.get(id, 0f)) return;
        lastPlayed.put(id, time);

        Sound sound = sfx.get(id);
        if (sound == null) {
            try {
                FileHandle file = dir().child("sfx_" + id + ".wav");
                if (!file.exists()) {
                    file.parent().mkdirs();
                    SfxFactory.generate(id, file);
                }
                sound = Gdx.audio.newSound(file);
                sfx.put(id, sound);
            } catch (Exception e) {
                Gdx.app.error("SoundManager", "sfx " + id, e);
                return;
            }
        }
        if (sfxVolume > 0.001f) sound.play(sfxVolume);
    }

    /**
     * Cambia la música de fondo; el id es "menu", el de un bioma o su variante
     * de jefe. Si ya sonaba otra cosa, se encadena con un fundido corto.
     */
    public void playMusic(String themeId) {
        if (!MusicFactory.hasTheme(themeId)) themeId = "menu";
        if (themeId.equals(musicId) && music != null) {
            pendingMusicId = null; // se canceló el cambio antes de completarse
            return;
        }
        if (themeId.equals(pendingMusicId)) return;

        if (music != null) {
            pendingMusicId = themeId;
            return;
        }
        fadeLevel = 0f;
        startMusic(themeId);
    }

    /**
     * Sintetiza el tema si aún no está en caché, sin reproducirlo. Se usa para
     * dejar listo el tema de jefe al cargar el nivel: generarlo en el momento en
     * que aparece el jefe metía un tirón en plena acción.
     */
    public void prepareMusic(String themeId) {
        if (!MusicFactory.hasTheme(themeId)) return;
        try {
            FileHandle file = dir().child("music_" + themeId + ".wav");
            if (!file.exists()) {
                file.parent().mkdirs();
                MusicFactory.generate(themeId, file);
            }
        } catch (Exception e) {
            Gdx.app.error("SoundManager", "prepare " + themeId, e);
        }
    }

    private void startMusic(String themeId) {
        stopMusic();
        try {
            FileHandle file = dir().child("music_" + themeId + ".wav");
            if (!file.exists()) {
                file.parent().mkdirs();
                MusicFactory.generate(themeId, file);
            }
            music = Gdx.audio.newMusic(file);
            music.setLooping(true);
            music.setVolume(musicVolume * fadeLevel);
            music.play();
            musicId = themeId;
        } catch (Exception e) {
            Gdx.app.error("SoundManager", "music " + themeId, e);
            music = null;
            musicId = null;
        }
    }

    public void stopMusic() {
        pendingMusicId = null; // un corte explícito cancela el fundido en curso
        if (music != null) {
            music.stop();
            music.dispose();
            music = null;
            musicId = null;
        }
    }

    @Override
    public void dispose() {
        stopMusic();
        for (Sound s : sfx.values()) s.dispose();
        sfx.clear();
    }
}
