package com.pcdc.td.ui;

import com.badlogic.gdx.Gdx;

/** Calidad de efectos (partículas, estelas, sombras) con persistencia. */
public final class Quality {

    public static boolean high = true;
    /** Sacudida de cámara y vibración: molestan a bastante gente. */
    public static boolean shake = true;
    /** Números de daño flotantes en los golpes gordos y jefes. */
    public static boolean damageText = true;
    /**
     * Modo relajado: +50% de vidas en cada nivel. No cambia el resto del
     * equilibrio y se marca en los resultados, así que las estrellas de quien
     * lo use siguen contando lo mismo pero se sabe cómo se consiguieron.
     */
    public static boolean relaxed = false;
    /**
     * Contador de FPS y milisegundos por frame en la esquina del HUD. Existe
     * para MEDIR en el móvil de verdad: en escritorio todo va sobrado y los
     * draw calls solo son un indicio.
     */
    public static boolean showFps = false;

    private Quality() {
    }

    public static void load() {
        high = Gdx.app.getPreferences("td-settings").getBoolean("fxHigh", true);
        shake = Gdx.app.getPreferences("td-settings").getBoolean("shake", true);
        damageText = Gdx.app.getPreferences("td-settings").getBoolean("damageText", true);
        relaxed = Gdx.app.getPreferences("td-settings").getBoolean("relaxed", false);
        showFps = Gdx.app.getPreferences("td-settings").getBoolean("showFps", false);
    }

    public static void toggleRelaxed() {
        relaxed = !relaxed;
        Gdx.app.getPreferences("td-settings").putBoolean("relaxed", relaxed).flush();
    }

    public static void toggle() {
        high = !high;
        Gdx.app.getPreferences("td-settings").putBoolean("fxHigh", high).flush();
    }

    public static void toggleShake() {
        shake = !shake;
        Gdx.app.getPreferences("td-settings").putBoolean("shake", shake).flush();
    }

    public static void toggleFps() {
        showFps = !showFps;
        Gdx.app.getPreferences("td-settings").putBoolean("showFps", showFps).flush();
    }

    public static void toggleDamageText() {
        damageText = !damageText;
        Gdx.app.getPreferences("td-settings").putBoolean("damageText", damageText).flush();
    }
}
