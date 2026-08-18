package com.pcdc.td.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pcdc.td.TDGame;

/**
 * Presentación al arrancar: el logotipo entra, respira y se va. Se puede saltar
 * tocando la pantalla o con cualquier tecla.
 */
public class SplashScreen extends ScreenAdapter {

    private static final float HOLD = 1.15f;

    private final TDGame game;
    private final Stage stage = new Stage(com.pcdc.td.ui.UiScale.viewport());
    private final Texture logo;
    private boolean leaving;

    public SplashScreen(TDGame game) {
        this.game = game;
        logo = new Texture(Gdx.files.internal("ui/logo.png"));
        logo.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        float width = com.pcdc.td.ui.UiScale.compact() ? 260f : 520f;
        float height = width * logo.getHeight() / (float) logo.getWidth();

        Image image = new Image(logo);
        image.setOrigin(com.badlogic.gdx.utils.Align.center);
        image.setScale(0.92f);
        image.getColor().a = 0f;
        image.addAction(Actions.parallel(
            Actions.fadeIn(0.5f, com.badlogic.gdx.math.Interpolation.pow2Out),
            Actions.scaleTo(1f, 1f, 0.8f, com.badlogic.gdx.math.Interpolation.pow3Out)));

        Table root = new Table();
        root.setFillParent(true);
        root.add(image).size(width, height);
        stage.addActor(root);
    }

    /** Se va al menú; a prueba de dobles disparos (toque + temporizador). */
    private void toMenu() {
        if (leaving) return;
        leaving = true;
        stage.getRoot().addAction(Actions.sequence(
            Actions.fadeOut(0.28f),
            Actions.run(() -> {
                game.setScreen(new MainMenuScreen(game));
                dispose();
            })));
    }

    @Override
    public void show() {
        game.sounds.playMusic("menu");
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                toMenu();
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                toMenu();
                return true;
            }
        });
        stage.addAction(Actions.sequence(Actions.delay(HOLD + 0.7f), Actions.run(this::toMenu)));
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
        logo.dispose();
    }
}
