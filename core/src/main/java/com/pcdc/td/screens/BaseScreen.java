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
import com.pcdc.td.data.BuildingDef;

/** Base del jugador: edificios con niveles que dan bonus pasivos. */
public class BaseScreen extends ScreenAdapter {

    private final TDGame game;
    private final Stage stage = new Stage(com.pcdc.td.ui.UiScale.viewport());
    private final boolean compact = com.pcdc.td.ui.UiScale.compact();
    /** Previsualizaciones 3D del tanque con cada pintura (cacheadas entre rebuilds). */
    private final com.pcdc.td.render.TowerIconRenderer iconRenderer =
        new com.pcdc.td.render.TowerIconRenderer(128);
    private final com.badlogic.gdx.utils.ObjectMap<String,
        com.badlogic.gdx.graphics.g2d.TextureRegion> skinPreviews =
        new com.badlogic.gdx.utils.ObjectMap<>();

    /** true si se abrió desde el menú principal: Volver regresa allí. */
    private final boolean fromMenu;
    private final com.pcdc.td.ui.ScreenBg bg = new com.pcdc.td.ui.ScreenBg();

    public BaseScreen(TDGame game) {
        this(game, false);
    }

    public BaseScreen(TDGame game, boolean fromMenu) {
        this.game = game;
        this.fromMenu = fromMenu;
        build();
    }

    private void goBack() {
        if (fromMenu) game.setScreen(new MainMenuScreen(game));
        else game.setScreen(new LevelSelectScreen(game));
        dispose();
    }

    private void build() {
        stage.clear();

        Label medals = new Label(String.valueOf(game.profile.medals), game.skin, "medal");

        Table cards = new Table();
        cards.defaults().pad(compact ? 5 : 10).width(250).top();
        int cardCount = 0;
        for (BuildingDef building : game.catalogs.buildings) {
            cards.add(buildCard(building));
            // móvil: una columna; escritorio: con 8 edificios la fila única se salía
            if (compact || ++cardCount % 4 == 0) cards.row();
        }

        TextButton back = new TextButton("< " + com.pcdc.td.ui.Lang.get("select.back"), game.skin);
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                goBack();
            }
        });

        // contenido con scroll: en móvil no cabe entero y la barra superior se
        // quedaba fuera de la pantalla (no había forma de volver)
        Table content = new Table();
        content.add(cards).top().padTop(compact ? 10 : 24).row();
        content.add(com.pcdc.td.ui.Widgets.sectionHeader(game.skin,
                com.pcdc.td.ui.Lang.get("base.skins")))
            .growX().padTop(compact ? 10 : 16).row();
        Table skinsRow = new Table();
        skinsRow.defaults().pad(compact ? 4 : 6).width(compact ? 120 : 150).top();
        int placed = 0;
        for (com.pcdc.td.render.TowerSkins.Def skin : com.pcdc.td.render.TowerSkins.ALL) {
            skinsRow.add(buildSkinCard(skin));
            // ni en móvil ni en escritorio caben las siete en una fila
            if (++placed % (compact ? 3 : 4) == 0) skinsRow.row();
        }
        content.add(skinsRow).top();

        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll =
            new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(content);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(false, false);

        Table root = new Table();
        root.setFillParent(true);
        com.pcdc.td.ui.UiScale.padSafe(root, 10f);
        Table topBar = new Table();
        topBar.setBackground(game.skin.getDrawable("panel"));
        topBar.pad(6).defaults().padLeft(5).padRight(5);
        topBar.add(back).width(100).height(38);
        topBar.add(new Label(com.pcdc.td.ui.Lang.get("base.title"), game.skin, "big")).expandX();
        // acceso al manual: el sitio donde ya vienes a mirar tus tanques
        TextButton manual = new TextButton(com.pcdc.td.ui.Lang.get("manual.title"), game.skin);
        manual.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
        manual.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                game.setScreen(new TankManualScreen(game));
                dispose();
            }
        });
        topBar.add(manual).width(compact ? 96 : 118).height(38);
        topBar.add(com.pcdc.td.ui.Widgets.currency(game.skin, "icon-medal", medals)).padLeft(12).padRight(14);
        root.add(topBar).growX().row();
        root.add(scroll).grow();
        stage.addActor(root);
    }

    private Table buildSkinCard(com.pcdc.td.render.TowerSkins.Def skin) {
        boolean owned = game.profile.ownsSkin(skin.id);
        boolean active = skin.id.equals(game.profile.activeSkin);

        Table card = new Table();
        card.setBackground(game.skin.getDrawable(active ? "card-green" : "card"));
        card.pad(8).defaults().pad(3);

        // el tanque renderizado con esa pintura: se ve lo que compras
        com.badlogic.gdx.scenes.scene2d.ui.Image preview =
            new com.badlogic.gdx.scenes.scene2d.ui.Image(skinPreview(skin.id));
        card.add(preview).size(compact ? 62 : 78).row();
        card.add(new Label(com.pcdc.td.ui.Lang.get("skin." + skin.id), game.skin)).row();

        // las pinturas de trofeo no se compran: se ganan jugando (el Corruptor
        // regala la Vencedora; el resto salen de logros concretos)
        if (!owned && com.pcdc.td.render.TowerSkins.isReward(skin.id)) {
            Label locked = new Label(com.pcdc.td.ui.Lang.get("victor".equals(skin.id)
                ? "skin.victor_locked" : "skin.reward_locked"), game.skin, "dim");
            locked.setWrap(true);
            locked.setAlignment(com.badlogic.gdx.utils.Align.center);
            locked.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
            card.getColor().a = 0.6f;
            card.add(locked).width(compact ? 108 : 134);
            return card;
        }

        if (active) {
            card.add(new Label(com.pcdc.td.ui.Lang.get("base.equipped"), game.skin, "gold"));
        } else {
            String text = owned
                ? com.pcdc.td.ui.Lang.get("base.equip")
                : com.pcdc.td.ui.Lang.get("base.buy_skin") + " (" + skin.cost + ")";
            TextButton action = new TextButton(text, game.skin, owned ? "default" : "buy");
            action.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
            action.setDisabled(!owned && game.profile.medals < skin.cost);
            action.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (action.isDisabled()) {
                        game.sounds.playSfx("denied");
                        return;
                    }
                    if (!game.profile.ownsSkin(skin.id)) {
                        if (game.profile.medals < skin.cost) {
                            game.sounds.playSfx("denied");
                            return;
                        }
                        game.profile.medals -= skin.cost;
                        game.profile.skins.add(skin.id);
                        game.sounds.playSfx("buy");
                    } else {
                        game.sounds.playSfx("click");
                    }
                    game.profile.activeSkin = skin.id;
                    com.pcdc.td.render.ModelFactory.activeSkin = skin.id;
                    game.saveProfile();
                    build();
                }
            });
            card.add(action).height(28).fillX();
        }
        return card;
    }

    /** Tanque renderizado con una pintura concreta (una sola vez por skin). */
    private com.badlogic.gdx.graphics.g2d.TextureRegion skinPreview(String skinId) {
        com.badlogic.gdx.graphics.g2d.TextureRegion cached = skinPreviews.get(skinId);
        if (cached == null) {
            cached = iconRenderer.renderWithSkin(game.catalogs.tower("cannon"), skinId);
            skinPreviews.put(skinId, cached);
        }
        return cached;
    }

    private Table buildCard(BuildingDef building) {
        int level = game.profile.buildingLevel(building.id);
        boolean maxed = level >= building.levels.size;

        Table card = new Table();
        card.setBackground(game.skin.getDrawable(maxed ? "card-green" : "card"));
        card.pad(compact ? 7 : 10).defaults().pad(3);

        Table titleRow = new Table();
        titleRow.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(
            game.skin.get("icon-" + building.id, com.badlogic.gdx.graphics.g2d.TextureRegion.class)))
            .size(compact ? 22 : 26).padRight(6);
        titleRow.add(new Label(com.pcdc.td.ui.Lang.name("building", building.id, building.name),
            game.skin, "gold")).expandX().left();
        card.add(titleRow).growX().row();
        Label desc = new Label(
            com.pcdc.td.ui.Lang.name("buildingdesc", building.id, building.desc), game.skin, "dim");
        desc.setWrap(true);
        card.add(desc).width(220).left().row();
        card.add(new Label(com.pcdc.td.ui.Lang.fmt("base.level", level, building.levels.size), game.skin)).left().row();

        // DOCTRINA: dos formas excluyentes de usar el edificio. Se puede
        // cambiar libremente (es una decisión de partida, no una trampa
        // permanente) y afecta a TODOS los niveles ya comprados.
        if (building.doctrines.size > 0) {
            String chosen = game.profile.doctrines.get(building.id,
                building.doctrines.first().id);
            Table row = new Table();
            row.defaults().padRight(4);
            for (com.pcdc.td.data.BuildingDef.DoctrineDef d : building.doctrines) {
                boolean active = d.id.equals(chosen);
                TextButton opt = new TextButton(
                    com.pcdc.td.ui.Lang.get("doctrine." + building.id + "." + d.id),
                    game.skin, active ? "primary" : "default");
                opt.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.72f));
                opt.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        if (active) return;
                        game.sounds.playSfx("click");
                        game.profile.doctrines.put(building.id, d.id);
                        game.refreshStats();
                        game.saveProfile();
                        build();
                    }
                });
                row.add(opt).height(26).width(compact ? 100 : 110);
            }
            card.add(row).left().row();
        }

        if (maxed) {
            card.add(new Label(com.pcdc.td.ui.Lang.get("base.max"), game.skin, "gold")).left();
        } else {
            BuildingDef.LevelDefEntry next = building.levels.get(level);
            // los edificios se pagan con MEDALLAS (estrellas nuevas), no con cristales
            TextButton buy = new TextButton(
                com.pcdc.td.ui.Lang.get(level == 0 ? "base.build" : "base.upgrade") + " (" + next.cost + ")",
                game.skin, "buy");
            buy.setDisabled(game.profile.medals < next.cost);
            buy.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (buy.isDisabled() || game.profile.medals < next.cost) {
                        game.sounds.playSfx("denied");
                        return;
                    }
                    game.profile.medals -= next.cost;
                    game.profile.buildings.put(building.id, level + 1);
                    game.refreshStats();
                    game.saveProfile();
                    game.sounds.playSfx("buy");
                    build();
                }
            });
            Table buyRow = new Table();
            buyRow.add(buy).height(30);
            com.badlogic.gdx.scenes.scene2d.ui.Image medalIcon = new com.badlogic.gdx.scenes.scene2d.ui.Image(
                game.skin.get("icon-medal", com.badlogic.gdx.graphics.g2d.TextureRegion.class));
            buyRow.add(medalIcon).size(16).padLeft(6);
            card.add(buyRow).left();
        }
        return card;
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
        bg.dispose();
    }
}
