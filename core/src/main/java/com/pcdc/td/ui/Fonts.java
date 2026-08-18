package com.pcdc.td.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

/**
 * Fuentes TTF (Roboto, Apache 2.0) generadas con FreeType al tamaño físico
 * del panel: nítidas en Android (la BitmapFont por defecto se escalaba y se
 * veía borrosa) y con acentos y ¡¿ reales en ambos idiomas.
 */
public final class Fonts {

    /** Signos españoles y tipográficos que faltan en DEFAULT_CHARS. */
    private static final String EXTRA_CHARS = "¡¿ºª«»…–—“”‘’";

    private Fonts() {
    }

    /** Fuente para la UI: tamaño en unidades de UI, rasterizada a píxel real. */
    public static BitmapFont ui(float sizeUnits) {
        return generate(sizeUnits * UiScale.value, UiScale.value);
    }

    /** Fuente en píxeles físicos, para renderers que dibujan fuera de los Stages. */
    public static BitmapFont pixels(float sizePx) {
        return generate(sizePx, 1f);
    }

    /**
     * Escala para Label/TextButton.setFontScale: las fuentes de la UI están
     * rasterizadas a píxel físico con escala base 1/UiScale, y setFontScale la
     * SUSTITUYE (no multiplica). Usar siempre setFontScale(Fonts.rel(x)) para
     * "x veces el tamaño normal" o el texto sale gigante en Android.
     */
    public static float rel(float x) {
        return x / UiScale.value;
    }

    private static BitmapFont generate(float sizePx, float unitScale) {
        FreeTypeFontGenerator gen =
            new FreeTypeFontGenerator(Gdx.files.internal("fonts/Roboto-Regular.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter p =
            new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = Math.max(8, Math.round(sizePx));
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + EXTRA_CHARS;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        BitmapFont font = gen.generateFont(p);
        gen.dispose();
        if (unitScale != 1f) font.getData().setScale(1f / unitScale);
        return font;
    }
}
