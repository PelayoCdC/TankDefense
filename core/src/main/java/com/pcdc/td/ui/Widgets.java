package com.pcdc.td.ui;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Scaling;

/** Composites de UI reutilizables. */
public final class Widgets {

    private Widgets() {
    }

    /**
     * Diálogo modal estándar: fondo oscurecido (tocar fuera lo cierra), panel
     * centrado y título opcional. Devuelve el panel para añadir contenido.
     * Todos los popups del juego pasan por aquí para verse iguales.
     */
    public static Table modal(com.badlogic.gdx.scenes.scene2d.Stage stage, Skin skin, String title) {
        Table overlay = new Table();
        overlay.setName(MODAL);
        overlay.setFillParent(true);
        overlay.setBackground(skin.newDrawable("white",
            new com.badlogic.gdx.graphics.Color(0f, 0f, 0f, 0.58f)));
        // Table nace con Touchable.childrenOnly: sin esto el overlay NUNCA se
        // devolvía a sí mismo en hit(), así que ni cerraba al tocar fuera ni
        // frenaba los toques, que atravesaban al contenido de debajo.
        overlay.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        overlay.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (event.getTarget() == overlay) overlay.remove();
            }
        });
        Table panel = new Table();
        panel.setBackground(skin.getDrawable("panel"));
        // el panel se traga sus propios toques (si no, tocar su fondo cerraría)
        panel.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        panel.pad(16).defaults().pad(4);
        if (title != null) {
            panel.add(new Label(title, skin, "big")).padBottom(6).row();
        }
        overlay.add(panel);
        stage.addActor(overlay);
        return panel;
    }

    /** Nombre del actor overlay: permite localizar el modal abierto. */
    private static final String MODAL = "modal-overlay";

    /**
     * Cierra el modal abierto más reciente, si lo hay. Las pantallas lo llaman
     * desde su ESC/atrás ANTES de salir: con el diálogo abierto, atrás cierra
     * el diálogo y no la pantalla entera.
     */
    public static boolean dismissTopModal(com.badlogic.gdx.scenes.scene2d.Stage stage) {
        com.badlogic.gdx.utils.Array<com.badlogic.gdx.scenes.scene2d.Actor> actors =
            stage.getActors();
        for (int i = actors.size - 1; i >= 0; i--) {
            if (MODAL.equals(actors.get(i).getName())) {
                actors.get(i).remove();
                return true;
            }
        }
        return false;
    }

    /** Cabecera de sección: [línea] TÍTULO [línea], para listas largas. */
    public static Table sectionHeader(Skin skin, String text) {
        Table t = new Table();
        com.badlogic.gdx.graphics.Color line =
            new com.badlogic.gdx.graphics.Color(1f, 1f, 1f, 0.16f);
        Label label = new Label(text, skin, "gold");
        label.setFontScale(Fonts.rel(0.9f));
        t.add(new Image(skin.newDrawable("white", line))).height(2).growX().padRight(10);
        t.add(label);
        t.add(new Image(skin.newDrawable("white", line))).height(2).growX().padLeft(10);
        return t;
    }

    /** Fila [icono de moneda][cantidad]. La label se actualiza desde fuera. */
    public static Table currency(Skin skin, String icon, Label valueLabel) {
        Table t = new Table();
        Image img = new Image(skin.get(icon, TextureRegion.class));
        img.setScaling(Scaling.fit);
        t.add(img).size(18).padRight(5);
        t.add(valueLabel);
        return t;
    }

    /** Sliders de volumen de música y efectos, conectados al SoundManager. */
    public static Table volumeControls(Skin skin, com.pcdc.td.audio.SoundManager sounds) {
        Table t = new Table();
        t.defaults().pad(4);

        com.badlogic.gdx.scenes.scene2d.ui.Slider musicSlider =
            new com.badlogic.gdx.scenes.scene2d.ui.Slider(0f, 1f, 0.05f, false, skin);
        musicSlider.setValue(sounds.getMusicVolume());
        musicSlider.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                sounds.setMusicVolume(musicSlider.getValue());
            }
        });

        com.badlogic.gdx.scenes.scene2d.ui.Slider sfxSlider =
            new com.badlogic.gdx.scenes.scene2d.ui.Slider(0f, 1f, 0.05f, false, skin);
        sfxSlider.setValue(sounds.getSfxVolume());
        sfxSlider.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                sounds.setSfxVolume(sfxSlider.getValue());
                sounds.playSfx("click"); // preview del nuevo volumen
            }
        });

        t.add(new Label(Lang.get("options.music"), skin)).left().width(90);
        t.add(musicSlider).width(190).row();
        t.add(new Label(Lang.get("options.sfx"), skin)).left().width(90);
        t.add(sfxSlider).width(190).row();
        return t;
    }
}
