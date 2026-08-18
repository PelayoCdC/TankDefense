package com.pcdc.td.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * Fondo estándar de las pantallas meta (árbol, base, opciones): gradiente
 * vertical azulado con viñeta en las esquinas, para que no vivan sobre un
 * color plano mientras el menú principal luce su tablero 3D.
 */
public class ScreenBg implements Disposable {

    private final Texture gradient;
    private final Texture vignette;
    private final SpriteBatch batch = new SpriteBatch();

    public ScreenBg() {
        // gradiente 1x256 estirado a pantalla completa
        Pixmap pm = new Pixmap(1, 256, Pixmap.Format.RGBA8888);
        Color top = Color.valueOf("1b2a3a");
        Color bottom = Color.valueOf("0a1016");
        Color tmp = new Color();
        for (int y = 0; y < 256; y++) {
            tmp.set(top).lerp(bottom, y / 255f);
            pm.setColor(tmp);
            pm.drawPixel(0, y);
        }
        gradient = new Texture(pm);
        gradient.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();

        // viñeta radial: oscurece esquinas, transparente en el centro
        int s = 128;
        Pixmap v = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        float half = s / 2f;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = (x - half) / half;
                float dy = (y - half) / half;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = Math.max(0f, d - 0.55f) * 0.55f;
                v.setColor(0f, 0f, 0f, Math.min(0.45f, a));
                v.drawPixel(x, y);
            }
        }
        vignette = new Texture(v);
        vignette.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        v.dispose();
    }

    /** Dibujar ANTES del stage de la pantalla. */
    public void render() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.begin();
        batch.draw(gradient, 0, 0, w, h);
        batch.draw(vignette, 0, 0, w, h);
        batch.end();
    }

    @Override
    public void dispose() {
        gradient.dispose();
        vignette.dispose();
        batch.dispose();
    }
}
