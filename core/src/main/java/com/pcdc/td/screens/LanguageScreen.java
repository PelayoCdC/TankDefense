package com.pcdc.td.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.pcdc.td.TDGame;
import com.pcdc.td.ui.Lang;

/** Selección de idioma. */
public class LanguageScreen extends ScreenAdapter {

    private final TDGame game;
    private final Stage stage = new Stage(com.pcdc.td.ui.UiScale.viewport());

    public LanguageScreen(TDGame game) {
        this.game = game;

        Table root = new Table();
        root.setFillParent(true);
        root.defaults().pad(7);

        root.add(new Label(Lang.get("lang.title"), game.skin, "title")).padBottom(30).row();

        // se listan los idiomas que EXISTEN en assets/i18n: añadir uno nuevo es
        // dejar caer su .json (y su nombre aquí), sin tocar más código
        for (String[] lang : Lang.AVAILABLE) {
            if (com.badlogic.gdx.Gdx.files.internal("i18n/" + lang[0] + ".json").exists()) {
                addLangButton(root, lang[1], lang[0]);
            }
        }

        TextButton back = new TextButton(Lang.get("options.back"), game.skin);
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MainMenuScreen(game));
                dispose();
            }
        });
        root.add(back).width(300).height(46).padTop(20).row();

        stage.addActor(root);
    }

    private void addLangButton(Table root, String label, String code) {
        TextButton b = new TextButton(label + (Lang.code().equals(code) ? "  <" : ""), game.skin,
            Lang.code().equals(code) ? "toggle" : "default");
        b.setChecked(Lang.code().equals(code));
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Lang.setLanguage(code);
                game.setScreen(new MainMenuScreen(game));
                dispose();
            }
        });
        root.add(b).width(300).height(46).row();
    }

    @Override
    public void show() {
        game.sounds.playMusic("menu");
        stage.getRoot().getColor().a = 0f;
        stage.getRoot().addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.25f));
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputMultiplexer(
            new com.badlogic.gdx.InputAdapter() {
                @Override
                public boolean keyDown(int keycode) {
                    if (keycode == com.badlogic.gdx.Input.Keys.BACK
                        || keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                        game.setScreen(new MainMenuScreen(game));
                        dispose();
                        return true;
                    }
                    return false;
                }
            }, stage));
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.valueOf("101820"), true);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
