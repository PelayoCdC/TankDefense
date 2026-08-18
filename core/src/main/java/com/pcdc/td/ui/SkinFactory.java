package com.pcdc.td.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

/**
 * Skin generada por código: ninepatch con borde, iconos de moneda
 * (oro = círculo dorado, cristal = rombo azul) y fuente TTF Roboto
 * rasterizada por densidad (Fonts).
 */
public final class SkinFactory {

    private SkinFactory() {
    }

    public static Skin build() {
        Skin skin = new Skin();

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        Texture whiteTex = new Texture(pm);
        skin.add("white", whiteTex);
        skin.add("white", new TextureRegion(whiteTex), TextureRegion.class);
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano

        // paneles/botones con borde (ninepatch 8x8, borde 2px)
        skin.add("panel", ninePatch(skin, Color.valueOf("101b26f2"), Color.valueOf("33475c")), Drawable.class);
        skin.add("panel-light", ninePatch(skin, Color.valueOf("18283af2"), Color.valueOf("41586f")), Drawable.class);
        skin.add("btn-up", ninePatch(skin, Color.valueOf("2e4053"), Color.valueOf("55708c")), Drawable.class);
        skin.add("btn-over", ninePatch(skin, Color.valueOf("3a5169"), Color.valueOf("6c8cb0")), Drawable.class);
        skin.add("btn-down", ninePatch(skin, Color.valueOf("1e2c3a"), Color.valueOf("55708c")), Drawable.class);
        skin.add("btn-disabled", ninePatch(skin, Color.valueOf("1c242c"), Color.valueOf("2c3844")), Drawable.class);
        skin.add("btn-checked", ninePatch(skin, Color.valueOf("4a6a2b"), Color.valueOf("74a04a")), Drawable.class);
        // acción principal de cada pantalla (verde) y acción destructiva (roja):
        // sin esto, "Jugar" y "Borrar progreso" pesaban lo mismo
        skin.add("btn-primary-up", ninePatch(skin, Color.valueOf("2e6a52"), Color.valueOf("55b98d")), Drawable.class);
        skin.add("btn-primary-over", ninePatch(skin, Color.valueOf("388263"), Color.valueOf("6fd6a6")), Drawable.class);
        skin.add("btn-primary-down", ninePatch(skin, Color.valueOf("24523f"), Color.valueOf("55b98d")), Drawable.class);
        skin.add("btn-danger-up", ninePatch(skin, Color.valueOf("6b2b32"), Color.valueOf("c2565f")), Drawable.class);
        skin.add("btn-danger-over", ninePatch(skin, Color.valueOf("853640"), Color.valueOf("df6a74")), Drawable.class);
        skin.add("btn-danger-down", ninePatch(skin, Color.valueOf("511f25"), Color.valueOf("c2565f")), Drawable.class);
        // gastar moneda (ámbar): distinto de confirmar (verde) y destruir (rojo)
        skin.add("btn-buy-up", ninePatch(skin, Color.valueOf("6a521c"), Color.valueOf("c9a23c")), Drawable.class);
        skin.add("btn-buy-over", ninePatch(skin, Color.valueOf("816526"), Color.valueOf("e5bd51")), Drawable.class);
        skin.add("btn-buy-down", ninePatch(skin, Color.valueOf("4e3c13"), Color.valueOf("c9a23c")), Drawable.class);
        skin.add("card", ninePatch(skin, Color.valueOf("14202ef2"), Color.valueOf("2c3f52")), Drawable.class);
        skin.add("card-green", ninePatch(skin, Color.valueOf("1a3320f2"), Color.valueOf("3c6a48")), Drawable.class);

        // iconos de moneda
        TextureRegion gold = goldIcon();
        TextureRegion crystal = crystalIcon();
        TextureRegion medal = medalIcon();
        TextureRegion heart = heartIcon();
        TextureRegion star = starIcon(true);
        TextureRegion starEmpty = starIcon(false);
        skin.add("icon-gold", gold, TextureRegion.class);
        skin.add("icon-crystal", crystal, TextureRegion.class);
        skin.add("icon-medal", medal, TextureRegion.class);
        skin.add("icon-heart", heart, TextureRegion.class);
        skin.add("icon-star", star, TextureRegion.class);
        skin.add("icon-star-empty", starEmpty, TextureRegion.class);
        skin.add("icon-gold-tex", gold.getTexture()); // para que el skin los libere
        skin.add("icon-crystal-tex", crystal.getTexture());
        skin.add("icon-medal-tex", medal.getTexture());
        skin.add("icon-heart-tex", heart.getTexture());
        skin.add("icon-star-tex", star.getTexture());
        skin.add("icon-star-empty-tex", starEmpty.getTexture());

        // iconos de rama del árbol y de edificios de la base
        register(skin, "icon-branch-attack", crosshairIcon());
        register(skin, "icon-branch-economy", coinsIcon());
        register(skin, "icon-branch-utility", shieldIcon());
        register(skin, "icon-branch-fusion", fusionIcon());
        register(skin, "icon-lock", lockIcon());
        register(skin, "icon-pause", pauseIcon());
        register(skin, "icon-rotate", rotateIcon());
        register(skin, "icon-cycle", rotateIcon());
        register(skin, "icon-meteor", meteorIcon());
        register(skin, "icon-frost", frostIcon());
        register(skin, "icon-goldrush", goldRushIcon());
        register(skin, "icon-goldmine", ingotIcon());
        register(skin, "icon-lab", flaskIcon());
        register(skin, "icon-workshop", gearIcon());
        register(skin, "icon-armory", shellIcon());
        register(skin, "icon-optics", scopeIcon());
        register(skin, "icon-vault", chestIcon());
        register(skin, "icon-depot", crateIcon());
        register(skin, "icon-walls", wallIcon());
        register(skin, "icon-up", upIcon());
        register(skin, "icon-move", moveIcon());
        register(skin, "icon-undo", undoIcon());

        BitmapFont font = Fonts.ui(16f);
        skin.add("font", font);

        BitmapFont bigFont = Fonts.ui(29f);
        skin.add("font-big", bigFont);

        skin.add("default", new Label.LabelStyle(font, Color.valueOf("e8eef4")));
        skin.add("gold", new Label.LabelStyle(font, Color.valueOf("ffd54a")));
        skin.add("crystal", new Label.LabelStyle(font, Color.valueOf("6fc8f0")));
        skin.add("medal", new Label.LabelStyle(font, Color.valueOf("e0a35c")));
        skin.add("dim", new Label.LabelStyle(font, Color.valueOf("8a99a8")));
        skin.add("big", new Label.LabelStyle(bigFont, Color.valueOf("e8eef4")));
        Label.LabelStyle title = new Label.LabelStyle(bigFont, Color.valueOf("ffd54a"));
        skin.add("title", title);

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
        button.font = font;
        button.fontColor = Color.valueOf("e8eef4");
        button.downFontColor = Color.valueOf("b8c4d0");
        button.disabledFontColor = Color.valueOf("5c6a78");
        // el contenido se hunde al pulsar: en táctil es la señal de que respondió
        button.pressedOffsetY = -2f;
        button.up = skin.getDrawable("btn-up");
        button.down = skin.getDrawable("btn-down");
        button.over = skin.getDrawable("btn-over");
        button.disabled = skin.getDrawable("btn-disabled");
        skin.add("default", button);

        TextButton.TextButtonStyle toggle = new TextButton.TextButtonStyle(button);
        toggle.checked = skin.getDrawable("btn-checked");
        skin.add("toggle", toggle);

        TextButton.TextButtonStyle primary = new TextButton.TextButtonStyle(button);
        primary.up = skin.getDrawable("btn-primary-up");
        primary.over = skin.getDrawable("btn-primary-over");
        primary.down = skin.getDrawable("btn-primary-down");
        primary.fontColor = Color.valueOf("eafff4");
        skin.add("primary", primary);

        TextButton.TextButtonStyle danger = new TextButton.TextButtonStyle(button);
        danger.up = skin.getDrawable("btn-danger-up");
        danger.over = skin.getDrawable("btn-danger-over");
        danger.down = skin.getDrawable("btn-danger-down");
        danger.fontColor = Color.valueOf("ffe8ea");

        TextButton.TextButtonStyle buy = new TextButton.TextButtonStyle(button);
        buy.up = skin.getDrawable("btn-buy-up");
        buy.over = skin.getDrawable("btn-buy-over");
        buy.down = skin.getDrawable("btn-buy-down");
        buy.fontColor = Color.valueOf("ffeec2");
        skin.add("buy", buy);
        skin.add("danger", danger);

        Window.WindowStyle window = new Window.WindowStyle(font, Color.WHITE, skin.getDrawable("panel"));
        skin.add("default", window);

        com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle slider =
            new com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle();
        slider.background = skin.newDrawable("white", Color.valueOf("2c3844"));
        slider.background.setMinHeight(8f);
        slider.knob = skin.newDrawable("white", Color.valueOf("6c8cb0"));
        slider.knob.setMinWidth(16f);
        slider.knob.setMinHeight(26f);
        slider.knobBefore = skin.newDrawable("white", Color.valueOf("4a6a8c"));
        slider.knobBefore.setMinHeight(8f);
        skin.add("default-horizontal", slider);

        com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle bossBar =
            new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle();
        bossBar.background = skin.newDrawable("white", Color.valueOf("2a1218"));
        bossBar.background.setMinHeight(14f);
        bossBar.knobBefore = skin.newDrawable("white", Color.valueOf("d83848"));
        bossBar.knobBefore.setMinHeight(14f);
        skin.add("default-horizontal", bossBar);

        // progreso de la oleada: verde, para no confundirlo con la vida del jefe
        com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle waveBar =
            new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle();
        waveBar.background = skin.newDrawable("white", Color.valueOf("1d2b25"));
        waveBar.background.setMinHeight(9f);
        waveBar.knobBefore = skin.newDrawable("white", Color.valueOf("55b98d"));
        waveBar.knobBefore.setMinHeight(9f);
        skin.add("wave-horizontal", waveBar);

        ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        scroll.vScrollKnob = skin.newDrawable("white", Color.valueOf("41586f"));
        scroll.hScrollKnob = skin.newDrawable("white", Color.valueOf("41586f"));
        skin.add("default", scroll);

        return skin;
    }

    private static NinePatchDrawable ninePatch(Skin skin, Color fill, Color border) {
        int size = 8, b = 2;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(border);
        pm.fill();
        pm.setColor(fill);
        pm.fillRectangle(b, b, size - 2 * b, size - 2 * b);
        // esquinas achaflanadas: quita el aire cuadradote sin cambiar el layout
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f);
        pm.drawPixel(0, 0);
        pm.drawPixel(size - 1, 0);
        pm.drawPixel(0, size - 1);
        pm.drawPixel(size - 1, size - 1);
        Texture tex = new Texture(pm);
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        skin.add("np-" + fill + border, tex); // para que el skin lo libere
        return new NinePatchDrawable(new NinePatch(tex, b + 1, b + 1, b + 1, b + 1));
    }

    /** Flecha hacia arriba (Mejorar). */
    private static TextureRegion upIcon() {
        int s = 24;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setColor(Color.valueOf("bef0d6"));
        pm.fillTriangle(12, 2, 3, 12, 21, 12);
        pm.fillRectangle(8, 12, 8, 9);
        TextureRegion r = new TextureRegion(new Texture(pm));
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        return r;
    }

    /** Cuatro flechas hacia fuera (Mover). */
    private static TextureRegion moveIcon() {
        int s = 24;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setColor(Color.valueOf("cfe0f0"));
        pm.fillTriangle(12, 1, 7, 7, 17, 7);
        pm.fillTriangle(12, 23, 7, 17, 17, 17);
        pm.fillTriangle(1, 12, 7, 7, 7, 17);
        pm.fillTriangle(23, 12, 17, 7, 17, 17);
        pm.fillRectangle(10, 10, 4, 4);
        TextureRegion r = new TextureRegion(new Texture(pm));
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        return r;
    }

    /** Flecha de retorno (Deshacer). */
    private static TextureRegion undoIcon() {
        int s = 24;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setColor(Color.valueOf("cfe0f0"));
        pm.fillTriangle(2, 9, 11, 2, 11, 16);
        pm.fillRectangle(11, 6, 9, 6);
        pm.fillRectangle(16, 6, 4, 12);
        TextureRegion r = new TextureRegion(new Texture(pm));
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        return r;
    }

    /** Oro: círculo dorado con borde oscuro. */
    private static TextureRegion goldIcon() {
        int s = 24;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setColor(Color.valueOf("a8842a"));
        pm.fillCircle(s / 2, s / 2, s / 2 - 1);
        pm.setColor(Color.valueOf("ffd24a"));
        pm.fillCircle(s / 2, s / 2, s / 2 - 3);
        pm.setColor(Color.valueOf("ffeaa0"));
        pm.fillCircle(s / 2 - 3, s / 2 - 3, 3);
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        return new TextureRegion(tex);
    }

    /** Corazón rojo para las vidas. */
    private static TextureRegion heartIcon() {
        int s = 24;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        Color dark = Color.valueOf("8a1c24");
        Color red = Color.valueOf("e8384a");
        Color shine = Color.valueOf("ff8a94");
        // dos lóbulos + triángulo inferior, con borde oscuro (dibujado detrás, más grande)
        pm.setColor(dark);
        pm.fillCircle(7, 9, 6);
        pm.fillCircle(17, 9, 6);
        pm.fillTriangle(1, 11, 23, 11, 12, 23);
        pm.setColor(red);
        pm.fillCircle(7, 9, 4);
        pm.fillCircle(17, 9, 4);
        pm.fillTriangle(3, 11, 21, 11, 12, 21);
        pm.setColor(shine);
        pm.fillCircle(7, 8, 2);
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        return new TextureRegion(tex);
    }

    /** Medalla: estrella de 6 puntas cobriza (edificios de la base). */
    private static TextureRegion medalIcon() {
        int s = 24;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        int c = s / 2;
        pm.setColor(Color.valueOf("8a5424"));
        pm.fillTriangle(c, 0, s - 2, s - 6, 2, s - 6);
        pm.fillTriangle(c, s - 1, s - 2, 5, 2, 5);
        pm.setColor(Color.valueOf("e0923c"));
        pm.fillTriangle(c, 3, s - 5, s - 8, 5, s - 8);
        pm.fillTriangle(c, s - 4, s - 5, 7, 5, 7);
        pm.setColor(Color.valueOf("ffc878"));
        pm.fillCircle(c, c, 3);
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        return new TextureRegion(tex);
    }

    /** Registra un icono y su textura (para que el skin la libere al hacer dispose). */
    private static void register(Skin skin, String name, TextureRegion region) {
        skin.add(name, region, TextureRegion.class);
        skin.add(name + "-tex", region.getTexture());
    }

    private static final int ICON = 28;

    private static Pixmap newIconPixmap() {
        Pixmap pm = new Pixmap(ICON, ICON, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None); // permite "perforar" con alfa 0
        return pm;
    }

    private static TextureRegion finish(Pixmap pm) {
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        return new TextureRegion(tex);
    }

    /** Rama de ataque: punto de mira. */
    private static TextureRegion crosshairIcon() {
        Pixmap pm = newIconPixmap();
        int c = ICON / 2;
        pm.setColor(Color.valueOf("d96a4a"));
        pm.fillCircle(c, c, 12);
        pm.setColor(0, 0, 0, 0);
        pm.fillCircle(c, c, 9); // anillo
        pm.setColor(Color.valueOf("f0a080"));
        pm.fillRectangle(c - 1, 1, 3, 8);
        pm.fillRectangle(c - 1, ICON - 9, 3, 8);
        pm.fillRectangle(1, c - 1, 8, 3);
        pm.fillRectangle(ICON - 9, c - 1, 8, 3);
        pm.setColor(Color.valueOf("d96a4a"));
        pm.fillCircle(c, c, 2);
        return finish(pm);
    }

    /** Rama de economía: pila de monedas. */
    private static TextureRegion coinsIcon() {
        Pixmap pm = newIconPixmap();
        for (int i = 0; i < 3; i++) {
            int y = 20 - i * 6;
            pm.setColor(Color.valueOf("a8842a"));
            capsule(pm, y, 6);
            pm.setColor(Color.valueOf("ffd24a"));
            capsule(pm, y - 1, 4);
        }
        return finish(pm);
    }

    /** Barra horizontal con extremos redondeados centrada en y. */
    private static void capsule(Pixmap pm, int y, int halfHeight) {
        pm.fillCircle(8, y, halfHeight);
        pm.fillCircle(20, y, halfHeight);
        pm.fillRectangle(8, y - halfHeight, 12, halfHeight * 2);
    }

    /** Rama de utilidad: escudo. */
    private static TextureRegion shieldIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("2a5a7a"));
        pm.fillRectangle(4, 3, 20, 11);
        pm.fillTriangle(4, 14, 24, 14, 14, 26);
        pm.setColor(Color.valueOf("6fc8f0"));
        pm.fillRectangle(7, 6, 14, 8);
        pm.fillTriangle(7, 14, 21, 14, 14, 22);
        return finish(pm);
    }

    /** Rama de fusión: dos piezas que se funden en una. */
    private static TextureRegion fusionIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("6a3a8a"));
        pm.fillCircle(9, 13, 8);
        pm.fillCircle(19, 13, 8);
        pm.setColor(Color.valueOf("c88ae8"));
        pm.fillCircle(9, 13, 5);
        pm.fillCircle(19, 13, 5);
        pm.setColor(Color.valueOf("f4dcff"));
        pm.fillCircle(14, 13, 5);
        return finish(pm);
    }

    /** Pausa: dos barras. */
    private static TextureRegion pauseIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("d8e2ec"));
        pm.fillRectangle(7, 5, 5, 18);
        pm.fillRectangle(16, 5, 5, 18);
        return finish(pm);
    }

    /** Girar/ciclar: flecha circular (anillo abierto con punta). */
    private static TextureRegion rotateIcon() {
        Pixmap pm = newIconPixmap();
        int c = ICON / 2;
        pm.setColor(Color.valueOf("d8e2ec"));
        pm.fillCircle(c, c, 10);
        pm.setColor(0, 0, 0, 0);
        pm.fillCircle(c, c, 7);
        pm.fillRectangle(c, 0, ICON - c, c); // corta el cuadrante para abrir el anillo
        pm.setColor(Color.valueOf("d8e2ec"));
        pm.fillTriangle(c + 2, 2, c + 13, 6, c + 2, 12); // punta de flecha
        return finish(pm);
    }

    /** Meteorito: roca con estela. */
    private static TextureRegion meteorIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("ff8a3c"));
        pm.fillTriangle(2, 2, 12, 6, 6, 13);
        pm.setColor(Color.valueOf("ffd24a"));
        pm.fillTriangle(4, 4, 10, 7, 7, 11);
        pm.setColor(Color.valueOf("8a6a56"));
        pm.fillCircle(18, 18, 7);
        pm.setColor(Color.valueOf("b08a70"));
        pm.fillCircle(17, 17, 4);
        return finish(pm);
    }

    /** Escarcha: copo de seis brazos. */
    private static TextureRegion frostIcon() {
        Pixmap pm = newIconPixmap();
        int c = ICON / 2;
        pm.setColor(Color.valueOf("9fd8f0"));
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI / 3;
            int dx = (int) Math.round(Math.cos(a) * 11);
            int dy = (int) Math.round(Math.sin(a) * 11);
            pm.drawLine(c - dx, c - dy, c + dx, c + dy);
            pm.drawLine(c - dx, c - dy + 1, c + dx, c + dy + 1);
        }
        pm.setColor(Color.valueOf("e8f8ff"));
        pm.fillCircle(c, c, 3);
        return finish(pm);
    }

    /** Fiebre del oro: moneda con destello. */
    private static TextureRegion goldRushIcon() {
        Pixmap pm = newIconPixmap();
        int c = ICON / 2;
        pm.setColor(Color.valueOf("a8842a"));
        pm.fillCircle(c, c + 1, 10);
        pm.setColor(Color.valueOf("ffd24a"));
        pm.fillCircle(c, c + 1, 7);
        pm.setColor(Color.valueOf("fff0b0"));
        pm.fillRectangle(c - 1, 1, 3, 6);
        pm.fillRectangle(ICON - 7, c - 1, 6, 3);
        return finish(pm);
    }

    /** Nivel bloqueado: candado (más claro que una "x"). */
    private static TextureRegion lockIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("6a7684"));
        // arco: anillo al que se le corta la mitad inferior
        pm.fillCircle(14, 12, 7);
        pm.setColor(0, 0, 0, 0);
        pm.fillCircle(14, 12, 4);
        pm.fillRectangle(0, 13, ICON, ICON - 13);
        pm.setColor(Color.valueOf("8a99a8"));
        pm.fillRectangle(6, 13, 16, 12); // cuerpo
        pm.setColor(Color.valueOf("39434f"));
        pm.fillCircle(14, 18, 2);        // bombín
        return finish(pm);
    }

    /** Mina de oro: lingote. */
    private static TextureRegion ingotIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("8a6a1a"));
        pm.fillTriangle(3, 22, 25, 22, 21, 9);
        pm.fillTriangle(3, 22, 21, 9, 7, 9);
        pm.setColor(Color.valueOf("ffd24a"));
        pm.fillTriangle(6, 20, 22, 20, 19, 11);
        pm.fillTriangle(6, 20, 19, 11, 9, 11);
        pm.setColor(Color.valueOf("fff0b0"));
        pm.fillRectangle(10, 13, 6, 2);
        return finish(pm);
    }

    /** Laboratorio: matraz con líquido. */
    private static TextureRegion flaskIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("1e4a4a"));
        pm.fillRectangle(11, 2, 6, 8);
        pm.fillTriangle(4, 25, 24, 25, 14, 8);
        pm.setColor(Color.valueOf("d0f0f0"));
        pm.fillRectangle(12, 3, 4, 7);
        pm.setColor(Color.valueOf("4fd0a2"));
        pm.fillTriangle(7, 23, 21, 23, 14, 13);
        pm.setColor(Color.valueOf("a8ffe0"));
        pm.fillCircle(12, 20, 2);
        return finish(pm);
    }

    /** Taller: engranaje. */
    private static TextureRegion gearIcon() {
        Pixmap pm = newIconPixmap();
        int c = ICON / 2;
        pm.setColor(Color.valueOf("8a99a8"));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int tx = c + (int) Math.round(Math.cos(a) * 11) - 3;
            int ty = c + (int) Math.round(Math.sin(a) * 11) - 3;
            pm.fillRectangle(tx, ty, 6, 6);
        }
        pm.fillCircle(c, c, 10);
        pm.setColor(Color.valueOf("d0dae4"));
        pm.fillCircle(c, c, 8);
        pm.setColor(0, 0, 0, 0);
        pm.fillCircle(c, c, 4); // eje hueco
        return finish(pm);
    }

    /** Armería: cartucho de latón con punta. */
    private static TextureRegion shellIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("b08a30"));
        pm.fillRectangle(10, 12, 8, 13);
        pm.setColor(Color.valueOf("d8b050"));
        pm.fillRectangle(11, 13, 3, 11);
        pm.setColor(Color.valueOf("c0762a"));
        pm.fillTriangle(10, 12, 18, 12, 14, 3);
        pm.setColor(Color.valueOf("8a6a1a"));
        pm.fillRectangle(9, 24, 10, 2);
        return finish(pm);
    }

    /** Torre de observación: mira telescópica. */
    private static TextureRegion scopeIcon() {
        Pixmap pm = newIconPixmap();
        int c = ICON / 2;
        pm.setColor(Color.valueOf("8a99a8"));
        pm.fillCircle(c, c, 10);
        pm.setColor(Color.valueOf("2a4a6a"));
        pm.fillCircle(c, c, 8);
        pm.setColor(Color.valueOf("6fc8f0"));
        pm.fillCircle(c - 3, c - 3, 3);
        pm.setColor(Color.valueOf("d0dae4"));
        pm.fillRectangle(c - 1, c - 6, 2, 12); // retícula
        pm.fillRectangle(c - 6, c - 1, 12, 2);
        return finish(pm);
    }

    /** Cámara del botín: cofre con cerradura. */
    private static TextureRegion chestIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("7a5426"));
        pm.fillRectangle(4, 8, 20, 6);  // tapa
        pm.fillRectangle(4, 15, 20, 10); // cuerpo
        pm.setColor(Color.valueOf("a8783a"));
        pm.fillRectangle(5, 9, 18, 4);
        pm.fillRectangle(5, 16, 18, 8);
        pm.setColor(Color.valueOf("ffd24a"));
        pm.fillRectangle(12, 12, 4, 6);  // cierre dorado
        pm.setColor(Color.valueOf("8a6a1a"));
        pm.fillCircle(14, 16, 1);
        return finish(pm);
    }

    /** Depósito de suministros: caja con tablones cruzados. */
    private static TextureRegion crateIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("8a6a3a"));
        pm.fillRectangle(4, 6, 20, 19);
        pm.setColor(Color.valueOf("b08a50"));
        pm.fillRectangle(6, 8, 16, 15);
        pm.setColor(Color.valueOf("6a4e28"));
        pm.drawLine(6, 8, 21, 22);
        pm.drawLine(21, 8, 6, 22);
        pm.fillRectangle(4, 6, 20, 2);
        pm.fillRectangle(4, 23, 20, 2);
        return finish(pm);
    }

    /** Murallas: almenas de fortaleza. */
    private static TextureRegion wallIcon() {
        Pixmap pm = newIconPixmap();
        pm.setColor(Color.valueOf("6a7684"));
        pm.fillRectangle(3, 12, 22, 13);
        pm.fillRectangle(3, 7, 5, 6);   // almenas
        pm.fillRectangle(12, 7, 5, 6);
        pm.fillRectangle(20, 7, 5, 6);
        pm.setColor(Color.valueOf("8a99a8"));
        pm.fillRectangle(4, 13, 20, 11);
        pm.setColor(Color.valueOf("39434f"));
        pm.fillRectangle(11, 17, 6, 8); // portón
        return finish(pm);
    }

    /** Estrella de 5 puntas: dorada (conseguida) o hueca gris (pendiente). */
    private static TextureRegion starIcon(boolean filled) {
        int s = 26;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        float cx = s / 2f, cy = s / 2f + 1f;
        drawStar(pm, cx, cy, 12f, 5f,
            filled ? Color.valueOf("8a5f14") : Color.valueOf("39434f"));
        drawStar(pm, cx, cy, 9f, 3.7f,
            filled ? Color.valueOf("ffd54a") : Color.valueOf("1a232e"));
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        return new TextureRegion(tex);
    }

    /** Rellena una estrella como abanico de triángulos desde el centro. */
    private static void drawStar(Pixmap pm, float cx, float cy, float outer, float inner, Color color) {
        pm.setColor(color);
        int[] xs = new int[10];
        int[] ys = new int[10];
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5;
            float r = i % 2 == 0 ? outer : inner;
            xs[i] = Math.round(cx + (float) Math.cos(a) * r);
            ys[i] = Math.round(cy - (float) Math.sin(a) * r); // y de Pixmap crece hacia abajo
        }
        for (int i = 0; i < 10; i++) {
            int j = (i + 1) % 10;
            pm.fillTriangle((int) cx, (int) cy, xs[i], ys[i], xs[j], ys[j]);
        }
    }

    /** Cristal: rombo azul con borde oscuro. */
    private static TextureRegion crystalIcon() {
        int s = 24;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        int c = s / 2;
        pm.setColor(Color.valueOf("2a5a8a"));
        pm.fillTriangle(c, 0, s - 1, c, c, s - 1);
        pm.fillTriangle(c, 0, 0, c, c, s - 1);
        pm.setColor(Color.valueOf("58a8f0"));
        pm.fillTriangle(c, 3, s - 4, c, c, s - 4);
        pm.fillTriangle(c, 3, 3, c, c, s - 4);
        pm.setColor(Color.valueOf("a8d8ff"));
        pm.fillTriangle(c, 5, c + 4, c - 2, c - 4, c - 2);
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        return new TextureRegion(tex);
    }
}
