package com.pcdc.td.android;

import android.os.Bundle;
import android.view.WindowManager;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.pcdc.td.TDGame;

public class AndroidLauncher extends AndroidApplication {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.numSamples = 2;
        TDGame game = new TDGame();
        // Gdx.app.exit() en Android solo hace Activity.finish() (la app queda en
        // Recientes y el proceso se reutiliza); cerrarla de verdad desde Salir.
        game.setPlatformExit(() -> {
            finishAndRemoveTask();
            System.exit(0);
        });
        initialize(game, config);
    }
}
