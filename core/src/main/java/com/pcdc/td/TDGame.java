package com.pcdc.td;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.pcdc.td.data.Catalogs;
import com.pcdc.td.meta.MetaEffects;
import com.pcdc.td.meta.PlayerProfile;
import com.pcdc.td.meta.SaveManager;
import com.pcdc.td.meta.StatBlock;
import com.pcdc.td.screens.GameplayScreen;
import com.pcdc.td.screens.MainMenuScreen;
import com.pcdc.td.ui.SkinFactory;

/** Punto de entrada compartido entre desktop y Android. */
public class TDGame extends Game {

    public final Catalogs catalogs = new Catalogs();
    public final SaveManager saveManager = new SaveManager();
    public final com.pcdc.td.audio.SoundManager sounds = new com.pcdc.td.audio.SoundManager();
    public PlayerProfile profile;
    public StatBlock stats = StatBlock.NONE;
    public Skin skin;
    private Runnable platformExit;

    @Override
    public void create() {
        Gdx.app.log("TDGame", "create()");
        Gdx.input.setCatchKey(com.badlogic.gdx.Input.Keys.BACK, true);
        com.pcdc.td.ui.UiScale.init();
        com.pcdc.td.ui.Quality.load();
        sounds.loadSettings();
        com.pcdc.td.ui.Lang.load();
        catalogs.load();
        skin = SkinFactory.build();
        profile = saveManager.load();
        if (profile.migrate()) saveManager.save(profile);
        refreshStats();
        com.pcdc.td.render.ModelFactory.activeSkin =
            profile.activeSkin == null ? "classic" : profile.activeSkin;

        // -Dtd.level=N arranca directamente en ese nivel (para pruebas)
        Integer testLevel = Integer.getInteger("td.level");
        if (testLevel != null) {
            // -Dtd.endless=true lo arranca en modo sin fin (para probar las cartas)
            setScreen(new GameplayScreen(this, Math.max(1, testLevel),
                Boolean.getBoolean("td.endless")));
        } else {
            setScreen(new com.pcdc.td.screens.SplashScreen(this));
        }
    }

    @Override
    public void render() {
        sounds.update(Gdx.graphics.getDeltaTime());
        super.render();
    }

    /** Recalcula los bonus agregados; llamar tras comprar mejoras o edificios. */
    public void refreshStats() {
        stats = MetaEffects.compute(profile, catalogs);
    }

    public void saveProfile() {
        saveManager.save(profile);
    }

    @Override
    public void pause() {
        super.pause();
        // Android puede matar el proceso al ir a segundo plano
        if (profile != null) saveProfile();
    }

    /** El launcher puede instalar un cierre específico de plataforma (Android
     *  necesita más que Gdx.app.exit(), que solo hace Activity.finish()). */
    public void setPlatformExit(Runnable platformExit) {
        this.platformExit = platformExit;
    }

    /** Cierra la aplicación entera; usa el hook de plataforma si existe. */
    public void requestExit() {
        if (profile != null) saveProfile();
        if (platformExit != null) {
            platformExit.run();
        } else {
            Gdx.app.exit();
        }
    }

    /** Desktop: el launcher avisa cuando la ventana pierde el foco. */
    public void onFocusLost() {
        if (screen instanceof GameplayScreen) ((GameplayScreen) screen).autoPause();
    }

    @Override
    public void dispose() {
        super.dispose();
        if (screen != null) screen.dispose();
        if (skin != null) skin.dispose();
        sounds.dispose();
    }
}
