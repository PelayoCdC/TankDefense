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
import com.pcdc.td.TDGame;
import com.pcdc.td.data.LevelDef;
import com.pcdc.td.meta.LevelSummary;
import com.pcdc.td.ui.Lang;
import com.pcdc.td.ui.Widgets;

/** Pantalla de resultados tras victoria, derrota o carrera de supervivencia. */
public class ResultsScreen extends ScreenAdapter {

    private final TDGame game;
    private final Stage stage = new Stage(com.pcdc.td.ui.UiScale.viewport());
    private final boolean celebrate;

    /** Lluvia de confeti para las victorias perfectas (3 estrellas). */
    private static class ConfettiActor extends com.badlogic.gdx.scenes.scene2d.Actor {
        private static final Color[] COLORS = {
            Color.valueOf("ffd54a"), Color.valueOf("7dff7d"), Color.valueOf("6ab8ff"),
            Color.valueOf("ff8a5c"), Color.valueOf("e87dff"),
        };
        private final com.badlogic.gdx.graphics.g2d.TextureRegion white;
        private final float[] x, y, vy, sway, phase, rot, size;
        private final Color[] color;
        private float time;

        ConfettiActor(com.badlogic.gdx.graphics.g2d.TextureRegion white, int count) {
            this.white = white;
            x = new float[count];
            y = new float[count];
            vy = new float[count];
            sway = new float[count];
            phase = new float[count];
            rot = new float[count];
            size = new float[count];
            color = new Color[count];
            for (int i = 0; i < count; i++) reset(i, true);
            setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        }

        private void reset(int i, boolean initial) {
            float h = getStage() != null ? getStage().getHeight() : 720f;
            float w = getStage() != null ? getStage().getWidth() : 1280f;
            x[i] = com.badlogic.gdx.math.MathUtils.random(0f, w);
            y[i] = initial ? com.badlogic.gdx.math.MathUtils.random(h * 0.4f, h * 1.6f)
                : h + com.badlogic.gdx.math.MathUtils.random(0f, h * 0.3f);
            vy[i] = com.badlogic.gdx.math.MathUtils.random(70f, 150f);
            sway[i] = com.badlogic.gdx.math.MathUtils.random(18f, 48f);
            phase[i] = com.badlogic.gdx.math.MathUtils.random(0f, 6.28f);
            rot[i] = com.badlogic.gdx.math.MathUtils.random(90f, 320f);
            size[i] = com.badlogic.gdx.math.MathUtils.random(5f, 9f);
            color[i] = COLORS[com.badlogic.gdx.math.MathUtils.random(COLORS.length - 1)];
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
            for (int i = 0; i < x.length; i++) {
                y[i] -= vy[i] * delta;
                if (y[i] < -12f) reset(i, false);
            }
        }

        @Override
        public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
            Color prev = batch.getColor();
            for (int i = 0; i < x.length; i++) {
                batch.setColor(color[i].r, color[i].g, color[i].b, 0.9f * parentAlpha);
                float px = x[i] + com.badlogic.gdx.math.MathUtils.sin(time * 2.2f + phase[i]) * sway[i] * 0.2f;
                batch.draw(white, px, y[i], size[i] / 2f, size[i] / 4f,
                    size[i], size[i] * 0.5f, 1f, 1f, time * rot[i] + phase[i] * 57f);
            }
            batch.setColor(prev);
        }
    }

    public ResultsScreen(TDGame game, LevelDef level, LevelSummary s) {
        this.game = game;
        this.celebrate = (!s.survival && s.victory && s.stars == 3) || s.campaignComplete;

        // columna con scroll: en móvil apaisado la lista completa no cabe
        boolean compact = com.pcdc.td.ui.UiScale.compact();
        Table root = new Table();
        root.defaults().pad(compact ? 3 : 6);

        if (s.survival) {
            Label title = new Label(Lang.get("select.survival"), game.skin, "big");
            title.setColor(Color.valueOf("c88ae8"));
            root.add(title).row();
            root.add(new Label(Lang.name("biome", level.biome, level.biome), game.skin, "dim")).row();
            root.add(new Label(Lang.fmt("results.wave_reached", s.waveReached), game.skin, "gold")).row();
            root.add(new Label(Lang.fmt("results.record", s.bestWave), game.skin, "dim")).row();
            // las cartas que te llevaron hasta ahí: es la combinación lo que
            // da ganas de repetir, y sin listarla no quedaba registro
            for (int[] m : s.milestones) {
                Label ms = new Label(Lang.fmt("results.milestone", m[0], m[1]), game.skin, "gold");
                root.add(ms).padTop(2).row();
            }
            if (s.boons.size > 0) {
                StringBuilder taken = new StringBuilder();
                for (int i = 0; i < s.boons.size; i++) {
                    if (i > 0) taken.append("  ·  ");
                    taken.append(Lang.get("boon." + s.boons.get(i)));
                }
                Label boonTitle = new Label(Lang.get("results.boons"), game.skin, "dim");
                boonTitle.setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
                root.add(boonTitle).padTop(6).row();
                Label boonList = new Label(taken.toString(), game.skin, "gold");
                boonList.setWrap(true);
                boonList.setAlignment(com.badlogic.gdx.utils.Align.center);
                boonList.setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
                root.add(boonList).width(com.pcdc.td.ui.UiScale.compact() ? 320 : 420).row();
            }
        } else {
            // la primera caída del Corruptor merece cabecera de gala
            String titleKey = s.campaignComplete ? "results.campaign"
                : s.victory ? "results.victory" : "results.defeat";
            Label title = new Label(Lang.get(titleKey), game.skin, "big");
            title.setColor(s.campaignComplete ? Color.valueOf("ffd54a")
                : s.victory ? Color.valueOf("7dff7d") : Color.valueOf("ff6d6d"));
            root.add(title).row();
            // se jugó con vidas extra: se dice, para que una estrella siempre
            // signifique lo mismo al mirarla dos meses después
            if (com.pcdc.td.ui.Quality.relaxed && !s.survival) {
                Label relaxed = new Label(Lang.get("results.relaxed"), game.skin, "dim");
                relaxed.setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
                root.add(relaxed).row();
            }
            if (s.campaignComplete) {
                // la gesta en tres líneas + el trofeo
                int totalStars = 0;
                for (com.badlogic.gdx.utils.ObjectIntMap.Entry<String> e
                    : game.profile.levels) totalStars += e.value;
                com.pcdc.td.data.TowerDef favorite = null;
                int bestDmg = 0;
                for (com.pcdc.td.data.TowerDef def : game.catalogs.towers) {
                    int d = game.profile.stat("towerdmg." + def.id);
                    if (d > bestDmg) {
                        bestDmg = d;
                        favorite = def;
                    }
                }
                Label feat = new Label(Lang.fmt("results.campaign_feat",
                    totalStars, game.profile.stat("kills")), game.skin);
                root.add(feat).row();
                if (favorite != null) {
                    root.add(new Label(Lang.fmt("results.campaign_favorite",
                        Lang.name("tower", favorite.id, favorite.name)), game.skin, "dim")).row();
                }
                Label trophy = new Label(Lang.get("results.campaign_skin"), game.skin, "gold");
                root.add(trophy).row();
                Label next = new Label(Lang.get("results.campaign_next"), game.skin, "dim");
                next.setWrap(true);
                next.setAlignment(com.badlogic.gdx.utils.Align.center);
                root.add(next).width(compact ? 360 : 430).row();
            }
            // el nombre del nivel vive en el JSON (español); la clave level.N lo
            // traduce y el propio JSON queda como respaldo
            root.add(new Label(level.id + " - "
                + Lang.name("level", String.valueOf(level.id), level.name), game.skin, "dim")).row();
            if (s.victory) {
                Table starsRow = new Table();
                for (int i = 0; i < 3; i++) {
                    com.badlogic.gdx.scenes.scene2d.ui.Image starImg =
                        new com.badlogic.gdx.scenes.scene2d.ui.Image(game.skin.get(
                            i < s.stars ? "icon-star" : "icon-star-empty",
                            com.badlogic.gdx.graphics.g2d.TextureRegion.class));
                    starsRow.add(starImg).size(34).pad(3);
                }
                root.add(starsRow).row();
            }
        }

        if (s.crystals > 0 || s.medals > 0) {
            Table rewards = new Table();
            Label earned = new Label("+" + s.crystals, game.skin, "crystal");
            rewards.add(Widgets.currency(game.skin, "icon-crystal", earned)).padRight(16);
            if (s.medals > 0) {
                Label medals = new Label("+" + s.medals, game.skin, "medal");
                rewards.add(Widgets.currency(game.skin, "icon-medal", medals));
            }
            root.add(rewards).row();
        }

        // DIAGNÓSTICO DE DERROTA: por qué se perdió, no solo que se perdió
        if (!s.victory && s.leakTopEnemy != null) {
            Table diag = new Table();
            diag.setBackground(game.skin.getDrawable("panel-light"));
            diag.pad(compact ? 5 : 8).defaults().padBottom(1);
            diag.add(new Label(Lang.get("results.why"), game.skin, "gold")).row();
            String enemyName = Lang.name("enemy", s.leakTopEnemy, s.leakTopEnemy);
            diag.add(new Label(Lang.fmt("results.why_wave", s.waveReached, s.leakTotal),
                game.skin)).row();
            diag.add(new Label(Lang.fmt("results.why_leak", s.leakTopCount, enemyName),
                game.skin)).row();
            Label advice = new Label(defeatAdvice(s), game.skin, "dim");
            advice.setWrap(true);
            advice.setAlignment(com.badlogic.gdx.utils.Align.center);
            diag.add(advice).width(compact ? 300 : 380).padTop(3);
            root.add(diag).padTop(compact ? 3 : 6).row();
        }

        // resumen de la partida
        root.add(new Label(Lang.fmt("results.kills", s.kills), game.skin)).row();
        if (s.mvpName != null) {
            root.add(new Label(Lang.fmt("results.mvp", s.mvpName), game.skin)).row();
        }

        // barras comparativas de daño por tipo de torre
        if (s.towerDamage.size > 0) {
            root.add(new Label(Lang.get("results.damage"), game.skin, "dim"))
                .padTop(compact ? 2 : 6).row();
            float maxDamage = s.towerDamage.first().damage;
            Table bars = new Table();
            for (int i = 0; i < s.towerDamage.size; i++) {
                LevelSummary.TowerDamage td = s.towerDamage.get(i);
                bars.add(new Label(td.name, game.skin)).right().padRight(8);
                float w = 24f + 136f * (td.damage / maxDamage);
                Color barColor = i == 0 ? Color.valueOf("ffd54a") : Color.valueOf("6c8cb0");
                com.badlogic.gdx.scenes.scene2d.ui.Image bar =
                    new com.badlogic.gdx.scenes.scene2d.ui.Image(game.skin.newDrawable("white", barColor));
                bars.add(bar).width(w).height(9).left();
                bars.add(new Label(String.valueOf(Math.round(td.damage)), game.skin, "dim"))
                    .left().padLeft(8);
                bars.row().padTop(compact ? 2 : 3);
            }
            root.add(bars).row();
        }
        // retos del nivel: qué has cumplido y qué te queda por intentar
        if (!s.survival && s.victory) {
            root.add(new Label(Lang.get("challenge.title"), game.skin, "dim"))
                .padTop(compact ? 2 : 6).row();
            Table list = new Table();
            for (int i = 0; i < com.pcdc.td.meta.Challenges.IDS.length; i++) {
                int flag = com.pcdc.td.meta.Challenges.FLAGS[i];
                boolean done = (s.challengesDone & flag) != 0;
                Label mark = new Label(done ? "[+]" : "[  ]", game.skin, done ? "gold" : "dim");
                String id = com.pcdc.td.meta.Challenges.IDS[i];
                // el tope de tanques escala con el nivel: el texto lleva el número
                String challengeText = "fewtanks".equals(id)
                    ? Lang.fmt("challenge.fewtanks", com.pcdc.td.meta.Challenges.maxTanks(level.id))
                    : Lang.get("challenge." + id);
                Label text = new Label(challengeText, game.skin, done ? "default" : "dim");
                list.add(mark).right().padRight(6);
                list.add(text).left();
                if ((s.challengesNew & flag) != 0) {
                    list.add(new Label(Lang.fmt("challenge.new", com.pcdc.td.meta.Challenges.REWARD),
                        game.skin, "crystal")).left().padLeft(8);
                } else {
                    list.add(new Label("", game.skin)).left().padLeft(8);
                }
                list.row().padTop(compact ? 1 : 2);
            }
            root.add(list).row();
        }

        for (String ach : s.achievements) {
            Label achLabel = new Label(ach, game.skin, "gold");
            root.add(achLabel).row();
        }

        int buttonH = compact ? 36 : 40;
        TextButton retry = new TextButton(Lang.get("results.retry"), game.skin);
        retry.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                // conserva la colocación inicial de la partida anterior Y el modo
                // (reintentar un Nivel+ volvía en Normal sin avisar)
                GameplayScreen.requestRetryLoadout();
                game.setScreen(new GameplayScreen(game, level.id, s.survival, s.plus, null));
                dispose();
            }
        });
        root.add(retry).width(220).height(buttonH).padTop(compact ? 4 : 8).row();

        boolean hasNext = !s.survival && s.victory
            && Gdx.files.internal(String.format("levels/level_%03d.json", level.id + 1)).exists();
        if (hasNext) {
            TextButton next = new TextButton(Lang.get("results.next"), game.skin, "primary");
            next.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.sounds.playSfx("click");
                    game.setScreen(new GameplayScreen(game, level.id + 1));
                    dispose();
                }
            });
            root.add(next).width(220).height(buttonH).row();
        }

        // con los cristales recién ganados en la mano: Árbol y Base a un toque
        Table shortcuts = new Table();
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
        shortcuts.add(upgrades).width(106).height(buttonH).padRight(8);
        shortcuts.add(base).width(106).height(buttonH);
        root.add(shortcuts).row();

        TextButton menu = new TextButton(Lang.get("results.menu"), game.skin);
        menu.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                game.setScreen(new LevelSelectScreen(game));
                dispose();
            }
        });
        root.add(menu).width(220).height(buttonH).row();

        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll =
            new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(root);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        Table outer = new Table();
        outer.setFillParent(true);
        com.pcdc.td.ui.UiScale.padSafe(outer, 6f);
        outer.add(scroll).grow();
        stage.addActor(outer);

        if (celebrate) {
            stage.addActor(new ConfettiActor(
                game.skin.get("white", com.badlogic.gdx.graphics.g2d.TextureRegion.class), 70));
        }
    }

    @Override
    public void show() {
        game.sounds.playMusic("menu");
        if (celebrate) game.sounds.playSfx("firework");
        stage.getRoot().getColor().a = 0f;
        stage.getRoot().addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.25f));
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputMultiplexer(
            new com.badlogic.gdx.InputAdapter() {
                @Override
                public boolean keyDown(int keycode) {
                    if (keycode == com.badlogic.gdx.Input.Keys.BACK
                        || keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                        game.setScreen(new LevelSelectScreen(game));
                        dispose();
                        return true;
                    }
                    return false;
                }
            }, stage));
    }

    /**
     * Consejo derivado de QUIÉN se coló, no una frase genérica. El orden
     * importa: el oficio del enemigo explica la derrota mejor que su armadura,
     * y la armadura mejor que "pon más tanques".
     */
    private String defeatAdvice(LevelSummary s) {
        com.pcdc.td.data.EnemyDef def = game.catalogs.enemy(s.leakTopEnemy);
        if (def != null && !"none".equals(def.role)) {
            return Lang.get("advice." + def.role);
        }
        if (def != null && def.flying) return Lang.get("advice.air");
        if (def != null && def.armor >= 5f) return Lang.get("advice.armor");
        if (s.towersAtEnd <= 4) return Lang.get("advice.fewtanks");
        return Lang.get("advice.generic");
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.valueOf("141c26"), true);
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
