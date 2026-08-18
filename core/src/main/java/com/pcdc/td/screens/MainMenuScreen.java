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

/** Menú principal: Jugar, Opciones, Idioma, Salir. */
public class MainMenuScreen extends ScreenAdapter {

    private final TDGame game;
    private final Stage stage = new Stage(com.pcdc.td.ui.UiScale.viewport());
    private com.badlogic.gdx.graphics.Texture logoTexture;
    private final com.pcdc.td.render.TowerIconRenderer iconRenderer =
        new com.pcdc.td.render.TowerIconRenderer(192);

    // fondo 3D: un tablero decorativo girando lentamente
    private final com.pcdc.td.render.ModelFactory bgFactory = new com.pcdc.td.render.ModelFactory();
    private final com.pcdc.td.render.WorldRenderer bgRenderer = new com.pcdc.td.render.WorldRenderer();
    private final com.badlogic.gdx.utils.Array<com.badlogic.gdx.graphics.g3d.ModelInstance> bgInstances =
        new com.badlogic.gdx.utils.Array<>();
    private float bgCenterX, bgCenterZ;
    private float orbitDeg;

    public MainMenuScreen(TDGame game) {
        this.game = game;

        buildBackground();

        // en pantallas bajas (móvil apaisado) todo se compacta para que quepa
        boolean compact = com.pcdc.td.ui.UiScale.compact();

        Table root = new Table();
        root.setFillParent(true);
        root.defaults().pad(compact ? 3 : 7);

        // emblema: el tanque de mando renderizado en 3D, con la pintura equipada
        com.badlogic.gdx.scenes.scene2d.ui.Image emblem = new com.badlogic.gdx.scenes.scene2d.ui.Image(
            iconRenderer.render(bgFactory, game.catalogs.tower("command"), 1));
        emblem.setOrigin(48, 48);
        emblem.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(1.06f, 1.06f, 1.4f,
                    com.badlogic.gdx.math.Interpolation.sine),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(0.96f, 0.96f, 1.4f,
                    com.badlogic.gdx.math.Interpolation.sine))));
        if (!compact) root.add(emblem).size(96).padBottom(4).row();

        // logotipo del juego (imagen; el texto plano se veía a rótulo de menú)
        logoTexture = new com.badlogic.gdx.graphics.Texture(
            com.badlogic.gdx.Gdx.files.internal("ui/logo.png"));
        logoTexture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear,
            com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        com.badlogic.gdx.scenes.scene2d.ui.Image logo =
            new com.badlogic.gdx.scenes.scene2d.ui.Image(logoTexture);
        // en móvil apaisado el alto manda: a 300 el logo se salía por arriba
        float logoW = compact ? 196f : 460f;
        float logoH = logoW * logoTexture.getHeight() / (float) logoTexture.getWidth();
        root.add(logo).size(logoW, logoH).padBottom(compact ? 6 : 18).row();

        // botones dentro de un panel
        Table panel = new Table();
        panel.setBackground(game.skin.getDrawable("panel"));
        if (compact) {
            panel.pad(8).defaults().pad(2);
        } else {
            panel.pad(16).defaults().pad(6);
        }
        com.pcdc.td.meta.Checkpoint cp = game.saveManager.loadCheckpoint();
        // un checkpoint de un nivel que ya no existe (campaña acortada) se descarta
        if (cp != null && !com.badlogic.gdx.Gdx.files.internal(
                String.format("levels/level_%03d.json", cp.levelId)).exists()) {
            game.saveManager.clearCheckpoint();
            cp = null;
        }
        if (cp != null) {
            final com.pcdc.td.meta.Checkpoint resume = cp;
            String resumeText = cp.survival
                ? Lang.get("menu.resume_endless") : Lang.fmt("menu.resume", cp.levelId);
            addButton(panel, resumeText, "primary", () -> {
                game.setScreen(new GameplayScreen(game, resume));
                dispose();
            });
        }
        // "Jugar" es la acción principal salvo que haya partida que continuar
        addButton(panel, Lang.get("menu.play"), cp == null ? "primary" : "default", () -> {
            game.setScreen(new LevelSelectScreen(game));
            dispose();
        });
        addButton(panel, Lang.get("menu.options"), () -> {
            game.setScreen(new OptionsScreen(game));
            dispose();
        });
        // Idioma vive ahora dentro de Opciones: un botón menos en el menú
        addButton(panel, Lang.get("menu.exit"), game::requestExit);
        root.add(panel).row();

        // resumen del perfil: de un vistazo, por dónde vas
        int completed = game.profile.levels.size;
        if (completed > 0) {
            Label summary = new Label(Lang.fmt("menu.progress", completed,
                game.catalogs.loadLevelIndex().levels.size,
                game.profile.metaCurrency), game.skin, "dim");
            summary.setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
            root.add(summary).padTop(compact ? 6 : 10).row();
            // rango ganado con estrellas: un título propio, no solo un número
            Label rank = new Label(Lang.get(game.profile.rankKey()), game.skin, "gold");
            rank.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
            root.add(rank).padTop(2).row();
        }

        if (!compact) {
            // aviso bien visible: la partida de pruebas no es la del jugador
            Label version = new Label(com.pcdc.td.meta.SaveManager.DEBUG_PROFILE
                ? "v1.0 · PERFIL DE PRUEBAS" : "v1.0",
                game.skin, com.pcdc.td.meta.SaveManager.DEBUG_PROFILE ? "gold" : "dim");
            root.add(version).padTop(6).row();
        }

        stage.addActor(root);
    }

    private void addButton(Table root, String text, Runnable action) {
        addButton(root, text, "default", action);
    }

    private void addButton(Table root, String text, String style, Runnable action) {
        TextButton b = new TextButton(text, game.skin, style);
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                action.run();
            }
        });
        root.add(b).width(280).height(com.pcdc.td.ui.UiScale.compact() ? 40 : 48).row();
    }

    /** Tablero decorativo (el mapa del nivel 3) para orbitar detrás del menú. */
    private void buildBackground() {
        try {
            com.pcdc.td.data.LevelDef lv = game.catalogs.loadLevel(3);
            com.pcdc.td.world.GridMap map = new com.pcdc.td.world.GridMap(lv.gridWidth, lv.gridHeight);
            for (com.pcdc.td.data.LevelDef.PathDef p : lv.paths) map.addPath(p.waypoints);
            for (int[] c : lv.blockedCells) map.block(c[0], c[1]);
            bgCenterX = map.width / 2f;
            bgCenterZ = map.height / 2f;
            bgInstances.add(new com.badlogic.gdx.graphics.g3d.ModelInstance(
                bgFactory.buildBoard(map, game.catalogs.biome(lv.biome), 33L)));
            // unas torres decorativas
            String[] decor = {"cannon", "laser", "mortar", "flamethrower"};
            int placed = 0;
            for (int z = 0; z < map.height && placed < decor.length; z++) {
                for (int x = 0; x < map.width && placed < decor.length; x += 3) {
                    if (map.isBuildable(x, z) && ((x + z) % 4 == 0)) {
                        com.badlogic.gdx.graphics.g3d.ModelInstance t =
                            new com.badlogic.gdx.graphics.g3d.ModelInstance(
                                bgFactory.tower(game.catalogs.tower(decor[placed])));
                        t.transform.setToTranslation(x + 0.5f, 0.175f, z + 0.5f);
                        bgInstances.add(t);
                        placed++;
                    }
                }
            }
        } catch (Exception e) {
            Gdx.app.error("MainMenu", "fondo 3D", e);
        }
    }


    @Override
    public void show() {
        game.sounds.playMusic("menu");
        stage.getRoot().getColor().a = 0f;
        stage.getRoot().addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.25f));
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.valueOf("101820"), true);

        // órbita lenta alrededor del tablero decorativo
        if (bgInstances.size > 0) {
            orbitDeg += delta * 6f;
            float rad = orbitDeg * com.badlogic.gdx.math.MathUtils.degreesToRadians;
            float r = 13f;
            bgRenderer.camera.position.set(
                bgCenterX + com.badlogic.gdx.math.MathUtils.cos(rad) * r,
                9.5f,
                bgCenterZ + com.badlogic.gdx.math.MathUtils.sin(rad) * r);
            bgRenderer.camera.lookAt(bgCenterX, 0f, bgCenterZ);
            bgRenderer.camera.up.set(0f, 1f, 0f);
            bgRenderer.camera.update();
            bgRenderer.render(bgInstances);
        }

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        bgRenderer.resize(width, height);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
        iconRenderer.dispose();
        if (logoTexture != null) logoTexture.dispose();
        bgFactory.dispose();
        bgRenderer.dispose();
    }
}
