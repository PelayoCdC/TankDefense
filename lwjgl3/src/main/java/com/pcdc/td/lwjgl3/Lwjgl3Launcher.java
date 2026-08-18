package com.pcdc.td.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.pcdc.td.TDGame;

public class Lwjgl3Launcher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Tank Defense");
        config.setWindowIcon("icons/icon128.png", "icons/icon64.png",
            "icons/icon32.png", "icons/icon16.png");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4); // MSAA x4
        TDGame game = new TDGame();
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
                Gdx.app.postRunnable(game::onFocusLost);
            }
        });
        new Lwjgl3Application(game, config);
    }
}
