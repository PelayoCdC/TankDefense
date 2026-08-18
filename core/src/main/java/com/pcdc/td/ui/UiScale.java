package com.pcdc.td.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * Escala global de la UI. En Android la interfaz se dibuja en "dp" (según la
 * densidad del panel) para que no se vea diminuta; en desktop queda a 1:1.
 * -Dtd.uiscale=N fuerza una escala para previsualizar el modo móvil.
 */
public final class UiScale {

    public static float value = 1f;
    /** Ajuste manual del jugador sobre la escala automática (accesibilidad). */
    public static float userScale = 1f;
    public static final float USER_MIN = 0.8f, USER_MAX = 1.5f;

    private UiScale() {
    }

    public static void init() {
        userScale = Gdx.app.getPreferences("td-settings").getFloat("uiScale", 1f);
        String forced = System.getProperty("td.uiscale");
        if (forced != null) {
            value = Float.parseFloat(forced) * userScale;
            return;
        }
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            float target = Math.max(1.3f, Gdx.graphics.getDensity());
            // tope: que siempre quepan ~820 unidades de ancho (la barra del HUD)
            float maxScale = Gdx.graphics.getWidth() / 820f;
            value = Math.max(1f, Math.min(target, maxScale)) * userScale;
        } else {
            value = userScale;
        }
    }

    /** Guarda la escala elegida; se aplica al reconstruir las pantallas. */
    public static void setUserScale(float scale) {
        userScale = Math.max(USER_MIN, Math.min(USER_MAX, scale));
        Gdx.app.getPreferences("td-settings").putFloat("uiScale", userScale).flush();
        init();
    }

    /** Alto de la pantalla en unidades de UI (para layouts compactos en móvil). */
    public static float worldHeight() {
        return Gdx.graphics.getHeight() / value;
    }

    /** true si la pantalla es baja en unidades de UI (móvil apaisado). */
    public static boolean compact() {
        return worldHeight() < 560f;
    }

    /** Viewport de UI con la escala aplicada. */
    public static ScreenViewport viewport() {
        ScreenViewport v = new ScreenViewport();
        v.setUnitsPerPixel(1f / value);
        return v;
    }

    // ---- safe area (notch y esquinas redondeadas), en unidades de UI ----

    public static float safeTop() {
        return Gdx.graphics.getSafeInsetTop() / value;
    }

    public static float safeBottom() {
        return Gdx.graphics.getSafeInsetBottom() / value;
    }

    public static float safeLeft() {
        return Gdx.graphics.getSafeInsetLeft() / value;
    }

    public static float safeRight() {
        return Gdx.graphics.getSafeInsetRight() / value;
    }

    /** Aplica base + safe area como padding de una tabla a pantalla completa. */
    public static void padSafe(com.badlogic.gdx.scenes.scene2d.ui.Table root, float base) {
        root.padTop(base + safeTop());
        root.padBottom(base + safeBottom());
        root.padLeft(base + safeLeft());
        root.padRight(base + safeRight());
    }
}
