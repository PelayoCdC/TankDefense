package com.pcdc.td.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pcdc.td.TDGame;
import com.pcdc.td.data.BiomeDef;
import com.pcdc.td.data.LevelDef;
import com.pcdc.td.data.LevelIndex;
import com.pcdc.td.ui.Lang;
import com.pcdc.td.ui.Widgets;
import com.pcdc.td.world.CellType;
import com.pcdc.td.world.GridMap;

/** Selección de nivel por biomas, con miniaturas del trazado y modo Sin fin. */
public class LevelSelectScreen extends ScreenAdapter {

    private static final int THUMB_W = 60, THUMB_H = 40;

    private final TDGame game;
    private final Stage stage = new Stage(com.pcdc.td.ui.UiScale.viewport());
    private Texture thumbAtlas;

    public LevelSelectScreen(TDGame game) {
        this.game = game;
        LevelIndex index = game.catalogs.loadLevelIndex();

        // atlas de miniaturas: una celda por nivel, dibujadas desde el JSON
        TextureRegion[] thumbs = buildThumbnails(index);

        Table content = new Table();
        content.defaults().pad(4);

        // primer nivel sin completar: el scroll aterriza en su bioma al abrir
        int firstPending = -1;
        for (int i = 0; i < index.levels.size; i++) {
            if (!game.profile.isCompleted(index.levels.get(i).id)) {
                firstPending = index.levels.get(i).id;
                break;
            }
        }

        String currentBiome = null;
        Table row = null;
        Table focusRow = null;
        for (int i = 0; i < index.levels.size; i++) {
            LevelIndex.Entry entry = index.levels.get(i);
            if (!entry.biome.equals(currentBiome)) {
                currentBiome = entry.biome;
                int biomeLastLevel = ((entry.id - 1) / 5 + 1) * 5; // 5 niveles por bioma

                // cabecera con franja del color del bioma + su mecánica debajo
                BiomeDef biomeDef = game.catalogs.biome(entry.biome);
                Table header = new Table();
                Color strip = new Color(biomeDef.groundA()).lerp(Color.BLACK, 0.35f);
                strip.a = 0.55f;
                header.setBackground(game.skin.newDrawable("white", strip));
                header.pad(4).padLeft(10).padRight(10).defaults().padRight(10);
                Label headerLabel = new Label(Lang.name("biome", entry.biome,
                    entry.biomeName != null ? entry.biomeName : entry.biome), game.skin, "gold");
                header.add(headerLabel);
                header.add(buildSurvivalButton(entry.biome, biomeLastLevel)).height(30);
                // bestiario del bioma: mecánica, rasgo y jefe
                final String biomeId = entry.biome;
                TextButton info = new TextButton("?", game.skin);
                info.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        game.sounds.playSfx("click");
                        showBestiary(game.catalogs.biome(biomeId));
                    }
                });
                header.add(info).width(30).height(30);
                Label mechLine = new Label(Lang.get("mech." + biomeDef.mechanic), game.skin, "dim");
                mechLine.setFontScale(com.pcdc.td.ui.Fonts.rel(0.78f));
                header.add(mechLine).expandX().right();
                content.add(header).growX().padTop(14).row();

                row = new Table();
                row.defaults().pad(3);
                content.add(row).left().row();
            }
            row.add(buildLevelButton(entry, thumbs[i])).width(72).height(78);
            if (entry.id == firstPending) focusRow = row;
        }

        ScrollPane scroll = new ScrollPane(content, game.skin);
        scroll.setFadeScrollBars(false);
        // scroll también horizontal: en pantallas estrechas la fila de 10 niveles no cabe
        scroll.setScrollingDisabled(false, false);

        TextButton back = new TextButton("< " + Lang.get("select.back"), game.skin);
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                game.setScreen(new MainMenuScreen(game));
                dispose();
            }
        });
        TextButton upgrades = new TextButton(Lang.get("select.upgrades"), game.skin);
        upgrades.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                game.setScreen(new UpgradeTreeScreen(game));
                dispose();
            }
        });
        TextButton base = new TextButton(Lang.get("select.base"), game.skin);
        base.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                game.setScreen(new BaseScreen(game));
                dispose();
            }
        });

        Label crystals = new Label(String.valueOf(game.profile.metaCurrency), game.skin, "crystal");
        Label medals = new Label(String.valueOf(game.profile.medals), game.skin, "medal");

        Table root = new Table();
        root.setFillParent(true);
        com.pcdc.td.ui.UiScale.padSafe(root, 10f);
        Table topBar = new Table();
        topBar.setBackground(game.skin.getDrawable("panel"));
        topBar.pad(6).defaults().padLeft(5).padRight(5);
        topBar.add(back).width(100).height(38);
        // título + progreso de campaña debajo (niveles completados y estrellas)
        int completed = 0, totalStars = 0;
        for (int i = 0; i < index.levels.size; i++) {
            int id = index.levels.get(i).id;
            if (game.profile.isCompleted(id)) completed++;
            totalStars += game.profile.starsFor(id);
        }
        Table titleCol = new Table();
        titleCol.add(new Label(Lang.get("select.title"), game.skin, "big")).row();
        Label progress = new Label(Lang.fmt("select.progress",
            completed, index.levels.size, totalStars), game.skin, "dim");
        progress.setFontScale(com.pcdc.td.ui.Fonts.rel(0.78f));
        titleCol.add(progress);
        topBar.add(titleCol).expandX();
        topBar.add(upgrades).width(110).height(38);
        topBar.add(base).width(90).height(38);
        topBar.add(Widgets.currency(game.skin, "icon-crystal", crystals)).padLeft(12);
        topBar.add(Widgets.currency(game.skin, "icon-medal", medals)).padLeft(8).padRight(14);
        root.add(topBar).growX().row();
        root.add(scroll).grow().padTop(6);
        stage.addActor(root);

        // aterrizar donde toca: el bioma del primer nivel pendiente
        if (focusRow != null) {
            root.validate();
            scroll.scrollTo(0f, focusRow.getY() - 20f, focusRow.getWidth(),
                focusRow.getHeight() + 60f);
            scroll.updateVisualScroll();
        }

        // bestiario automático la primera vez que se desbloquea un bioma
        BiomeDef newBiome = null;
        boolean changed = false;
        for (int t = 0; t < game.catalogs.biomes.size; t++) {
            BiomeDef biome = game.catalogs.biomes.get(t);
            if (!game.profile.isUnlocked(t * 5 + 1)) continue;
            if (!game.profile.seenBiomes.contains(biome.id, false)) {
                game.profile.seenBiomes.add(biome.id);
                newBiome = biome;
                changed = true;
            }
        }
        if (changed) {
            game.saveProfile();
            showBestiary(newBiome);
        }
    }

    /** Tipos de enemigo que aparecen en los 5 niveles de este bioma. */
    private com.badlogic.gdx.utils.ObjectSet<String> biomeEnemies(BiomeDef biome) {
        com.badlogic.gdx.utils.ObjectSet<String> ids = new com.badlogic.gdx.utils.ObjectSet<>();
        int tier = game.catalogs.biomes.indexOf(biome, true);
        if (tier < 0) return ids;
        for (int id = tier * 5 + 1; id <= tier * 5 + 5; id++) {
            try {
                LevelDef lv = game.catalogs.loadLevel(id);
                for (LevelDef.WaveDef w : lv.waves) {
                    for (LevelDef.SpawnGroupDef g : w.groups) ids.add(g.enemy);
                }
            } catch (Exception ignored) {
                // nivel inexistente o ilegible: se omite
            }
        }
        return ids;
    }

    /** Ficha del bioma: mecánica del mapa, rasgo de los enemigos y jefe. */
    private void showBestiary(BiomeDef biome) {
        Table panel = com.pcdc.td.ui.Widgets.modal(stage, game.skin,
            Lang.name("biome", biome.id, biome.name));
        // el contenido crecio con la unidad insignia y ya no cabia de alto:
        // va dentro de un ScrollPane con tope, y el ancho se aprieta en movil
        boolean tight = com.pcdc.td.ui.UiScale.compact();
        float textW = tight ? 300f : 360f;
        Table body = new Table();
        ScrollPane bestiaryScroll = new ScrollPane(body, game.skin);
        bestiaryScroll.setFadeScrollBars(false);
        bestiaryScroll.setScrollingDisabled(true, false);
        panel.add(bestiaryScroll)
            .maxHeight(stage.getHeight() - (tight ? 120f : 170f)).width(textW + 24f).row();

        // dos líneas de ambientación: cada mundo con su voz
        Label lore = new Label(Lang.get("lore." + biome.id), game.skin, "dim");
        lore.setWrap(true);
        lore.setAlignment(com.badlogic.gdx.utils.Align.center);
        lore.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
        body.add(lore).width(textW).padBottom(6).row();

        Label mech = new Label(Lang.get("best.mechanic") + "\n"
            + Lang.get("mech." + biome.mechanic), game.skin);
        mech.setWrap(true);
        mech.setAlignment(com.badlogic.gdx.utils.Align.center);
        body.add(mech).width(textW).row();

        Label trait = new Label(Lang.get("best.trait") + "\n"
            + Lang.get("trait." + biome.trait), game.skin);
        trait.setWrap(true);
        trait.setAlignment(com.badlogic.gdx.utils.Align.center);
        body.add(trait).width(textW).padTop(6).row();

        if (!"none".equals(biome.bossAbility)) {
            Label boss = new Label(Lang.get("best.boss") + "\n"
                + Lang.get("bossability." + biome.bossAbility), game.skin);
            boss.setWrap(true);
            boss.setAlignment(com.badlogic.gdx.utils.Align.center);
            boss.setColor(Color.valueOf("ff9a6a"));
            body.add(boss).width(textW).padTop(6).row();
        }

        // unidad insignia: solo sale aquí y en las tierras corruptas, así que
        // conviene presentarla antes de que aparezca
        for (com.pcdc.td.data.EnemyDef def : game.catalogs.enemies) {
            if (!biome.id.equals(def.biome)) continue;
            Label unit = new Label(Lang.get("best.special") + ": "
                + Lang.name("enemy", def.id, def.id) + "\n"
                + Lang.get("special." + def.special), game.skin);
            unit.setWrap(true);
            unit.setAlignment(com.badlogic.gdx.utils.Align.center);
            unit.setColor(Color.valueOf("ffd54a"));
            body.add(unit).width(textW).padTop(6).row();
            break;
        }

        // sinergia de clima del bioma (solo tormenta y volcán la tienen)
        if ("storm".equals(biome.id) || "volcano".equals(biome.id)) {
            Label syn = new Label(Lang.get("best.synergy") + "\n"
                + Lang.get("synergy." + biome.id), game.skin);
            syn.setWrap(true);
            syn.setAlignment(com.badlogic.gdx.utils.Align.center);
            syn.setColor(Color.valueOf("7de8c8"));
            body.add(syn).width(textW).padTop(6).row();
        }

        // enemigos con su chip de color (tintado por el bioma). SOLO los que
        // salen de verdad en sus niveles: recorrer el catálogo entero metía al
        // Campeón y a El Corruptor en el bestiario de los diez biomas
        Table foes = new Table();
        foes.defaults().pad(4);
        com.badlogic.gdx.utils.ObjectSet<String> local = biomeEnemies(biome);
        // en fila única se salían por los lados: en las corruptas hay diez
        int perRow = tight ? 4 : 5;
        int shown = 0;
        for (com.pcdc.td.data.EnemyDef def : game.catalogs.enemies) {
            if (!local.contains(def.id)) continue;
            if (shown > 0 && shown % perRow == 0) foes.row();
            shown++;
            Table foe = new Table();
            Color c = new Color(def.color());
            if (!"meadow".equals(biome.id)) c.lerp(biome.enemyTint(), 0.4f);
            Image chip = new Image(game.skin.newDrawable("white", c));
            foe.add(chip).size(16).row();
            Label name = new Label(Lang.name("enemy", def.id, def.id), game.skin, "dim");
            name.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
            foe.add(name);
            foes.add(foe);
        }
        body.add(foes).padTop(8).row();

        TextButton close = new TextButton(Lang.get("options.back"), game.skin);
        close.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                panel.getParent().remove(); // el overlay del modal
            }
        });
        panel.add(close).width(200).height(38).padTop(10);
    }

    /** Botón "Sin fin" del bioma; se desbloquea al completar su último nivel. */
    private TextButton buildSurvivalButton(String biomeId, int lastLevelId) {
        int best = game.profile.survivalBest.get(biomeId, 0);
        String text = Lang.get("select.survival") + (best > 0 ? " [" + best + "]" : "");
        TextButton b = new TextButton(text, game.skin);
        b.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
        boolean unlocked = game.profile.isCompleted(lastLevelId);
        b.setDisabled(!unlocked);
        if (!unlocked) b.getColor().a = 0.4f;
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (b.isDisabled()) return;
                game.sounds.playSfx("click");
                game.setScreen(new GameplayScreen(game, lastLevelId, true));
                dispose();
            }
        });
        return b;
    }

    private Button buildLevelButton(LevelIndex.Entry entry, TextureRegion thumb) {
        final int levelId = entry.id;
        boolean unlocked = game.profile.isUnlocked(levelId);
        int stars = game.profile.starsFor(levelId);

        Button b = new Button(game.skin.get("default", TextButton.TextButtonStyle.class));
        Table inner = new Table();
        if (thumb != null) {
            Image img = new Image(thumb);
            inner.add(img).size(THUMB_W, THUMB_H).row();
        }
        Table info = new Table();
        Label label = new Label(String.valueOf(levelId), game.skin);
        label.setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
        if (stars > 0) label.setColor(Color.valueOf("ffd54a"));
        info.add(label);
        if (unlocked) {
            for (int s = 0; s < 3; s++) {
                Image star = new Image(game.skin.get(s < stars ? "icon-star" : "icon-star-empty",
                    TextureRegion.class));
                info.add(star).size(11).padLeft(s == 0 ? 5 : 1);
            }
        } else {
            // candado: más claro que la "x" que había antes
            info.add(new Image(game.skin.get("icon-lock", TextureRegion.class)))
                .size(13).padLeft(6);
        }
        inner.add(info);
        b.add(inner).pad(2);

        b.setDisabled(!unlocked);
        if (!unlocked) b.getColor().a = 0.45f;
        final boolean offerPlus = stars >= 3;
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (b.isDisabled()) return;
                game.sounds.playSfx("click");
                game.profile.lastLevelPlayed = levelId;
                showLevelDialog(levelId, offerPlus);
            }
        });
        return b;
    }

    /**
     * Previa del nivel: enemigos que van a salir y nº de oleadas, con Jugar y
     * (con 3 estrellas) Nivel+ (+30% vida y cristales). Permite decidir el
     * arsenal antes de entrar, ahora que los tanques se eligen en el árbol.
     */
    private void showLevelDialog(int levelId, boolean offerPlus) {
        // "select.title" es el rótulo en plural de la pantalla ("Niveles"):
        // aquí hace falta el singular con el número
        Table panel = com.pcdc.td.ui.Widgets.modal(stage, game.skin,
            Lang.fmt("plus.level", levelId));
        panel.defaults().pad(6);

        // previa: oleadas y enemigos agregados de todo el nivel
        try {
            LevelDef lv = game.catalogs.loadLevel(levelId);
            com.badlogic.gdx.utils.OrderedMap<String, Integer> byType =
                new com.badlogic.gdx.utils.OrderedMap<>();
            for (LevelDef.WaveDef w : lv.waves) {
                for (LevelDef.SpawnGroupDef g : w.groups) {
                    Integer prev = byType.get(g.enemy);
                    byType.put(g.enemy, (prev == null ? 0 : prev) + g.count);
                }
            }
            panel.add(new Label(Lang.fmt("select.waves", lv.waves.size), game.skin, "dim")).row();
            Table chips = new Table();
            int col = 0;
            for (com.badlogic.gdx.utils.ObjectMap.Entry<String, Integer> e : byType) {
                Table chip = new Table();
                chip.setBackground(game.skin.getDrawable("panel-light"));
                chip.pad(3).padLeft(7).padRight(8);
                boolean isBoss = "boss".equals(e.key);
                Image dot = new Image(game.skin.newDrawable("white",
                    game.catalogs.enemy(e.key).color()));
                chip.add(dot).size(9).padRight(5);
                String name = Lang.name("enemy", e.key, e.key);
                chip.add(new Label(isBoss ? name : e.value + "x " + name,
                    game.skin, isBoss ? "gold" : "default"));
                chips.add(chip).padRight(5).padBottom(4);
                if (++col % (com.pcdc.td.ui.UiScale.compact() ? 2 : 3) == 0) chips.row();
            }
            panel.add(chips).row();
        } catch (Exception ignored) {
            // sin previa si el JSON no carga; el nivel sigue siendo jugable
        }

        TextButton normal = new TextButton(Lang.get("plus.normal"), game.skin, "primary");
        normal.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                game.setScreen(new GameplayScreen(game, levelId));
                dispose();
            }
        });
        panel.add(normal).width(240).height(42).row();

        if (offerPlus) {
            TextButton plus = new TextButton(Lang.get("plus.hard"), game.skin);
            plus.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.sounds.playSfx("click");
                    game.setScreen(new GameplayScreen(game, levelId, false, true, null));
                    dispose();
                }
            });
            panel.add(plus).width(240).height(42).row();
        }

        // salida explícita: tocar fuera también cierra, pero sin botón el
        // diálogo se sentía una trampa (no se podía elegir otro nivel)
        TextButton cancel = new TextButton(Lang.get("common.cancel"), game.skin);
        cancel.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                panel.getParent().remove(); // el overlay del modal
            }
        });
        panel.add(cancel).width(240).height(38).padTop(2);
    }

    /** Dibuja el trazado de cada nivel en un único atlas (spawn verde, meta dorada). */
    private TextureRegion[] buildThumbnails(LevelIndex index) {
        int count = index.levels.size;
        int cols = 10;
        int rows = Math.max(1, (count + cols - 1) / cols);
        Pixmap atlas = new Pixmap(cols * THUMB_W, rows * THUMB_H, Pixmap.Format.RGBA8888);
        TextureRegion[] regions = new TextureRegion[count];

        for (int i = 0; i < count; i++) {
            LevelIndex.Entry entry = index.levels.get(i);
            int ox = (i % cols) * THUMB_W;
            int oy = (i / cols) * THUMB_H;
            try {
                LevelDef lv = game.catalogs.loadLevel(entry.id);
                BiomeDef biome = game.catalogs.biome(lv.biome);
                GridMap map = new GridMap(lv.gridWidth, lv.gridHeight);
                for (LevelDef.PathDef p : lv.paths) map.addPath(p.waypoints);
                for (int[] c : lv.blockedCells) map.block(c[0], c[1]);

                float cell = Math.min((THUMB_W - 4f) / map.width, (THUMB_H - 4f) / map.height);
                int mx = ox + (int) ((THUMB_W - cell * map.width) / 2f);
                int my = oy + (int) ((THUMB_H - cell * map.height) / 2f);

                atlas.setColor(new Color(biome.groundA()).mul(0.55f, 0.55f, 0.6f, 1f));
                atlas.fillRectangle(mx, my, (int) (cell * map.width), (int) (cell * map.height));
                for (int z = 0; z < map.height; z++) {
                    for (int x = 0; x < map.width; x++) {
                        CellType t = map.get(x, z);
                        if (t == CellType.PATH) {
                            atlas.setColor(biome.path());
                        } else if (t == CellType.BLOCKED) {
                            atlas.setColor(biome.rock());
                        } else {
                            continue;
                        }
                        atlas.fillRectangle(mx + (int) (x * cell), my + (int) (z * cell),
                            Math.max(1, (int) cell), Math.max(1, (int) cell));
                    }
                }
                // spawn (verde) y meta (dorado) sobre la línea central
                for (com.badlogic.gdx.utils.Array<com.badlogic.gdx.math.Vector3> path : map.worldPaths) {
                    if (path.size < 3) continue;
                    atlas.setColor(Color.valueOf("58e858"));
                    atlas.fillCircle(mx + (int) (path.get(1).x * cell), my + (int) (path.get(1).z * cell), 2);
                    atlas.setColor(Color.valueOf("ffd24a"));
                    atlas.fillCircle(mx + (int) (path.get(path.size - 2).x * cell),
                        my + (int) (path.get(path.size - 2).z * cell), 2);
                }
            } catch (Exception e) {
                Gdx.app.error("LevelSelect", "miniatura del nivel " + entry.id, e);
            }
        }

        thumbAtlas = new Texture(atlas);
        thumbAtlas.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        atlas.dispose();
        for (int i = 0; i < count; i++) {
            regions[i] = new TextureRegion(thumbAtlas,
                (i % cols) * THUMB_W, (i / cols) * THUMB_H, THUMB_W, THUMB_H);
        }
        return regions;
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
                        // con un diálogo abierto, atrás cierra el diálogo
                        if (Widgets.dismissTopModal(stage)) return true;
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
        if (thumbAtlas != null) thumbAtlas.dispose();
    }
}
