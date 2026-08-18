package com.pcdc.td.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pcdc.td.TDGame;
import com.pcdc.td.data.FusionDef;
import com.pcdc.td.data.TowerDef;
import com.pcdc.td.ui.Fonts;
import com.pcdc.td.ui.Lang;
import com.pcdc.td.ui.Widgets;

/**
 * Manual de tanques: el catálogo completo con sus estadísticas y, sobre todo,
 * el MAPA DE FUSIONES. Hasta ahora las recetas solo se descubrían tropezando
 * con ellas en partida o rastreando nodos sueltos del árbol.
 */
public class TankManualScreen extends ScreenAdapter {

    private final TDGame game;
    private final Stage stage = new Stage(com.pcdc.td.ui.UiScale.viewport());
    private final boolean compact = com.pcdc.td.ui.UiScale.compact();
    private final com.pcdc.td.render.TowerIconRenderer iconRenderer =
        new com.pcdc.td.render.TowerIconRenderer(96);
    private final com.badlogic.gdx.utils.ObjectMap<String,
        com.badlogic.gdx.graphics.g2d.TextureRegion> icons =
        new com.badlogic.gdx.utils.ObjectMap<>();
    private final com.pcdc.td.render.ModelFactory factory = new com.pcdc.td.render.ModelFactory();
    private final com.pcdc.td.ui.ScreenBg bg = new com.pcdc.td.ui.ScreenBg();

    public TankManualScreen(TDGame game) {
        this.game = game;
        build();
    }

    private void goBack() {
        game.setScreen(new BaseScreen(game));
        dispose();
    }

    /** ¿Se puede usar ya este tanque en una partida normal? */
    private boolean available(TowerDef def) {
        if (def.fusionOnly) return game.stats.unlockedFusions.contains(def.id);
        if (def.unique) return game.stats.unlockedTowers.contains(def.id);
        return game.stats.towerAvailable(def.id);
    }

    private void build() {
        stage.clear();

        Table content = new Table();
        content.defaults().top();

        // --- tanques base y únicos ---
        content.add(Widgets.sectionHeader(game.skin, Lang.get("manual.tanks")))
            .growX().padTop(compact ? 6 : 10).row();
        Table baseRow = new Table();
        baseRow.defaults().pad(compact ? 4 : 6).width(compact ? 150 : 190).top();
        int placed = 0;
        for (TowerDef def : game.catalogs.towers) {
            if (def.fusionOnly) continue;
            baseRow.add(buildCard(def));
            if (++placed % (compact ? 2 : 4) == 0) baseRow.row();
        }
        content.add(baseRow).row();

        // --- recetas de fusión ---
        content.add(Widgets.sectionHeader(game.skin, Lang.get("manual.fusions")))
            .growX().padTop(compact ? 8 : 14).row();
        Table recipes = new Table();
        recipes.defaults().pad(compact ? 3 : 5);
        for (FusionDef fusion : game.catalogs.fusions) {
            recipes.add(buildRecipe(fusion)).row();
        }
        content.add(recipes).row();

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFadeScrollBars(false);

        TextButton back = new TextButton("< " + Lang.get("select.back"), game.skin);
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                goBack();
            }
        });

        Table root = new Table();
        root.setFillParent(true);
        com.pcdc.td.ui.UiScale.padSafe(root, 10f);
        Table topBar = new Table();
        topBar.setBackground(game.skin.getDrawable("panel"));
        topBar.pad(6).defaults().padLeft(5).padRight(5);
        topBar.add(back).width(100).height(38);
        topBar.add(new Label(Lang.get("manual.title"), game.skin, "big")).expandX();
        root.add(topBar).growX().row();
        root.add(scroll).grow();
        stage.addActor(root);
    }

    /** Ficha de un tanque: modelo, nombre, estadísticas y cómo conseguirlo. */
    private Table buildCard(TowerDef def) {
        boolean owned = available(def);
        Table card = new Table();
        card.setBackground(game.skin.getDrawable(owned ? "card" : "panel-light"));
        card.pad(compact ? 6 : 8).defaults().pad(1);

        Image icon = new Image(icon(def));
        // los que aún no tienes salen en SILUETA: se ve que existen, no cómo son
        if (!owned) icon.setColor(0.12f, 0.14f, 0.18f, 0.85f);
        card.add(icon).size(compact ? 52 : 64).row();

        Label name = new Label(Lang.name("tower", def.id, def.name), game.skin,
            owned ? "default" : "dim");
        card.add(name).row();

        if (owned) {
            Label stats = new Label(statsText(def), game.skin, "dim");
            stats.setFontScale(Fonts.rel(0.8f));
            stats.setAlignment(com.badlogic.gdx.utils.Align.center);
            card.add(stats).row();
            if (def.unique) {
                Label unique = new Label(Lang.get("manual.unique"), game.skin, "gold");
                unique.setFontScale(Fonts.rel(0.78f));
                card.add(unique);
            }
        } else {
            Label how = new Label(Lang.get("manual.locked"), game.skin, "dim");
            how.setFontScale(Fonts.rel(0.78f));
            how.setWrap(true);
            how.setAlignment(com.badlogic.gdx.utils.Align.center);
            card.add(how).width(compact ? 130 : 168);
        }
        return card;
    }

    private String statsText(TowerDef def) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Lang.locale(), "DMG %.0f  RNG %.1f", def.damage, def.range));
        sb.append(String.format(Lang.locale(), "\nCAD %.1f/s", def.fireRate));
        if (def.penetration > 0f) sb.append("  PEN ").append((int) def.penetration);
        if (def.splashRadius > 0f) {
            sb.append(String.format(Lang.locale(), "\n%s %.1f",
                Lang.get("manual.splash"), def.splashRadius));
        }
        if (def.chainCount > 0) {
            sb.append('\n').append(Lang.get("manual.chain")).append(' ').append(def.chainCount);
        }
        if (def.pullDistance > 0f) sb.append('\n').append(Lang.get("manual.pull"));
        if (!def.hitsAir) sb.append('\n').append(Lang.get("manual.groundonly"));
        return sb.toString();
    }

    /** Línea "A + B = C" con el resultado oculto si aún no se conoce la receta. */
    private Table buildRecipe(FusionDef fusion) {
        TowerDef a = game.catalogs.tower(fusion.a);
        TowerDef b = game.catalogs.tower(fusion.b);
        TowerDef result = game.catalogs.tower(fusion.result);
        boolean known = result != null && game.stats.unlockedFusions.contains(fusion.result);

        Table row = new Table();
        row.setBackground(game.skin.getDrawable("panel-light"));
        row.pad(compact ? 4 : 6).defaults().padLeft(3).padRight(3);
        row.add(new Image(icon(a))).size(compact ? 30 : 38);
        row.add(new Label(Lang.name("tower", a.id, a.name), game.skin)).width(compact ? 84 : 112);
        row.add(new Label("+", game.skin, "dim"));
        row.add(new Image(icon(b))).size(compact ? 30 : 38);
        row.add(new Label(Lang.name("tower", b.id, b.name), game.skin)).width(compact ? 84 : 112);
        row.add(new Label("=", game.skin, "dim"));
        if (known) {
            row.add(new Image(icon(result))).size(compact ? 30 : 38);
            row.add(new Label(Lang.name("tower", result.id, result.name), game.skin, "gold"))
                .width(compact ? 96 : 124);
        } else {
            // receta sin descubrir: se sabe que existe y con qué se hace, pero
            // no qué sale — el árbol la revela al comprarla
            Image hidden = new Image(icon(result));
            hidden.setColor(0.12f, 0.14f, 0.18f, 0.85f);
            row.add(hidden).size(compact ? 30 : 38);
            row.add(new Label("?", game.skin, "dim")).width(compact ? 96 : 124);
        }
        return row;
    }

    private com.badlogic.gdx.graphics.g2d.TextureRegion icon(TowerDef def) {
        com.badlogic.gdx.graphics.g2d.TextureRegion cached = icons.get(def.id);
        if (cached == null) {
            cached = iconRenderer.render(factory, def, 1);
            icons.put(def.id, cached);
        }
        return cached;
    }

    @Override
    public void show() {
        stage.getRoot().getColor().a = 0f;
        stage.getRoot().addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.25f));
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputMultiplexer(
            new com.badlogic.gdx.InputAdapter() {
                @Override
                public boolean keyDown(int keycode) {
                    if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE
                        || keycode == com.badlogic.gdx.Input.Keys.BACK) {
                        goBack();
                        return true;
                    }
                    return false;
                }
            }, stage));
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.valueOf("141c26"), true);
        bg.render();
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
        iconRenderer.dispose();
        factory.dispose();
        bg.dispose();
    }
}
