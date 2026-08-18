package com.pcdc.td.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
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
import com.pcdc.td.meta.PlayerProfile;
import com.pcdc.td.ui.Lang;

/** Opciones: pantalla completa (desktop) y borrado de progreso. */
public class OptionsScreen extends ScreenAdapter {

    private final TDGame game;
    private final Stage stage = new Stage(com.pcdc.td.ui.UiScale.viewport());
    private final com.pcdc.td.ui.ScreenBg bg = new com.pcdc.td.ui.ScreenBg();
    private boolean confirmingReset;
    private TextButton resetButton;

    public OptionsScreen(TDGame game) {
        this.game = game;

        // todo el contenido va en una columna con scroll: en móvil no cabe entero
        Table root = new Table();
        root.defaults().pad(7);

        root.add(new Label(Lang.get("options.title"), game.skin, "title")).padBottom(18).row();

        root.add(com.pcdc.td.ui.Widgets.sectionHeader(game.skin, Lang.get("options.sec_settings")))
            .growX().padTop(2).row();
        root.add(com.pcdc.td.ui.Widgets.volumeControls(game.skin, game.sounds)).padBottom(6).row();

        TextButton quality = new TextButton(qualityText(), game.skin);
        quality.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                com.pcdc.td.ui.Quality.toggle();
                quality.setText(qualityText());
            }
        });
        root.add(quality).width(300).height(46).row();

        TextButton shake = new TextButton(shakeText(), game.skin);
        shake.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                com.pcdc.td.ui.Quality.toggleShake();
                shake.setText(shakeText());
            }
        });
        root.add(shake).width(300).height(46).row();

        TextButton dmgText = new TextButton(damageTextText(), game.skin);
        dmgText.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                com.pcdc.td.ui.Quality.toggleDamageText();
                dmgText.setText(damageTextText());
            }
        });
        root.add(dmgText).width(300).height(46).row();

        TextButton fps = new TextButton(fpsText(), game.skin);
        fps.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                com.pcdc.td.ui.Quality.toggleFps();
                fps.setText(fpsText());
            }
        });
        root.add(fps).width(300).height(46).row();

        TextButton relaxed = new TextButton(relaxedText(), game.skin);
        relaxed.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                com.pcdc.td.ui.Quality.toggleRelaxed();
                relaxed.setText(relaxedText());
            }
        });
        root.add(relaxed).width(300).height(46).row();
        Label relaxedHint = new Label(Lang.get("options.relaxed_hint"), game.skin, "dim");
        relaxedHint.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
        relaxedHint.setWrap(true);
        relaxedHint.setAlignment(com.badlogic.gdx.utils.Align.center);
        root.add(relaxedHint).width(320).padBottom(4).row();

        TextButton about = new TextButton(Lang.get("options.about"), game.skin);
        about.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                showAbout();
            }
        });
        root.add(about).width(300).height(46).row();

        // el idioma se cambia desde aquí (antes ocupaba un botón del menú)
        TextButton language = new TextButton(Lang.get("menu.language"), game.skin);
        language.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new LanguageScreen(game));
                dispose();
            }
        });
        root.add(language).width(300).height(46).row();

        // escala de interfaz: al soltar se reconstruye la pantalla con el tamaño nuevo
        Table scaleRow = new Table();
        scaleRow.add(new Label(Lang.get("options.uiscale"), game.skin)).padRight(10);
        final com.badlogic.gdx.scenes.scene2d.ui.Slider scale =
            new com.badlogic.gdx.scenes.scene2d.ui.Slider(
                com.pcdc.td.ui.UiScale.USER_MIN, com.pcdc.td.ui.UiScale.USER_MAX,
                0.05f, false, game.skin);
        scale.setValue(com.pcdc.td.ui.UiScale.userScale);
        scale.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (scale.isDragging()) return; // solo al soltar, no en cada píxel
                com.pcdc.td.ui.UiScale.setUserScale(scale.getValue());
                game.setScreen(new OptionsScreen(game));
                dispose();
            }
        });
        scaleRow.add(scale).width(180);
        root.add(scaleRow).row();

        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            TextButton fullscreen = new TextButton(fullscreenText(), game.skin);
            fullscreen.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (Gdx.graphics.isFullscreen()) {
                        Gdx.graphics.setWindowedMode(1280, 720);
                    } else {
                        Graphics.DisplayMode mode = Gdx.graphics.getDisplayMode();
                        Gdx.graphics.setFullscreenMode(mode);
                    }
                    fullscreen.setText(fullscreenText());
                }
            });
            root.add(fullscreen).width(300).height(46).row();
        }

        root.add(com.pcdc.td.ui.Widgets.sectionHeader(game.skin, Lang.get("options.sec_profile")))
            .growX().padTop(10).row();
        resetButton = new TextButton(Lang.get("options.reset"), game.skin, "danger");
        resetButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!confirmingReset) {
                    confirmingReset = true;
                    resetButton.setText(Lang.get("options.reset_confirm"));
                } else {
                    game.profile = new PlayerProfile();
                    game.refreshStats();
                    game.saveProfile();
                    confirmingReset = false;
                    resetButton.setText(Lang.get("options.reset"));
                }
            }
        });
        root.add(resetButton).width(300).height(46).row();

        // copia de seguridad del perfil vía portapapeles
        Table backupRow = new Table();
        backupRow.defaults().pad(3);
        TextButton exportBtn = new TextButton(Lang.get("options.export"), game.skin);
        exportBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                com.badlogic.gdx.utils.Json j = new com.badlogic.gdx.utils.Json(
                    com.badlogic.gdx.utils.JsonWriter.OutputType.json);
                Gdx.app.getClipboard().setContents(j.toJson(game.profile));
                exportBtn.setText(Lang.get("options.exported"));
            }
        });
        TextButton importBtn = new TextButton(Lang.get("options.import"), game.skin);
        importBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                try {
                    com.badlogic.gdx.utils.Json j = new com.badlogic.gdx.utils.Json();
                    j.setIgnoreUnknownFields(true);
                    PlayerProfile imported = j.fromJson(PlayerProfile.class,
                        Gdx.app.getClipboard().getContents());
                    if (imported == null || imported.levels == null) throw new IllegalStateException();
                    game.profile = imported;
                    game.profile.migrate();
                    game.refreshStats();
                    game.saveProfile();
                    importBtn.setText(Lang.get("options.imported"));
                } catch (Exception e) {
                    importBtn.setText(Lang.get("options.import_fail"));
                }
            }
        });
        backupRow.add(exportBtn).width(220).height(40);
        backupRow.add(importBtn).width(220).height(40);
        root.add(backupRow).row();

        // estadísticas acumuladas del perfil
        root.add(com.pcdc.td.ui.Widgets.sectionHeader(game.skin, Lang.get("stats.title")))
            .growX().padTop(12).row();
        Table statList = new Table();
        statList.defaults().pad(2);
        addStatRow(statList, "stats.kills", game.profile.stat("kills"));
        addStatRow(statList, "stats.wins", game.profile.stat("wins"));
        addStatRow(statList, "stats.perfects", game.profile.stat("perfects"));
        addStatRow(statList, "stats.fusions", game.profile.stat("fusions"));
        addStatRow(statList, "stats.sold", game.profile.stat("sold"));
        addStatRow(statList, "stats.challenges", game.profile.totalChallenges());
        boolean anySurvival = false;
        for (com.badlogic.gdx.utils.ObjectIntMap.Entry<String> e : game.profile.survivalBest) {
            if (e.value > 0) { anySurvival = true; break; }
        }
        if (anySurvival) {
            Label bw = new Label(Lang.get("stats.bestwave"), game.skin);
            bw.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
            statList.add(bw).left().width(320);
            statList.add(new Label("", game.skin, "dim")).right().row();
            for (com.badlogic.gdx.utils.ObjectIntMap.Entry<String> e : game.profile.survivalBest) {
                if (e.value <= 0) continue;
                Label biome = new Label("    " + Lang.name("biome", e.key, e.key), game.skin, "dim");
                biome.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
                Label val = new Label(String.valueOf(e.value), game.skin, "dim");
                val.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
                statList.add(biome).left().width(320);
                statList.add(val).right().row();
            }
        }
        root.add(statList).width(420).row();

        // logros con progreso
        // "Tu arsenal": los tanques con más daño acumulado en todo el perfil
        Table arsenal = new Table();
        arsenal.defaults().pad(2);
        java.util.List<com.pcdc.td.data.TowerDef> byDamage = new java.util.ArrayList<>();
        for (com.pcdc.td.data.TowerDef def : game.catalogs.towers) {
            if (game.profile.stat("towerdmg." + def.id) > 0) byDamage.add(def);
        }
        byDamage.sort((a, b) -> Integer.compare(
            game.profile.stat("towerdmg." + b.id), game.profile.stat("towerdmg." + a.id)));
        if (!byDamage.isEmpty()) {
            root.add(com.pcdc.td.ui.Widgets.sectionHeader(game.skin, Lang.get("stats.arsenal")))
                .growX().padTop(12).row();
            for (int i = 0; i < Math.min(6, byDamage.size()); i++) {
                com.pcdc.td.data.TowerDef def = byDamage.get(i);
                Label name = new Label(Lang.name("tower", def.id, def.name),
                    game.skin, i == 0 ? "gold" : "default");
                name.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
                arsenal.add(name).left().width(320);
                Label val = new Label(Lang.fmt("stats.arsenal_row",
                    game.profile.stat("towerdmg." + def.id),
                    game.profile.stat("towerkills." + def.id)), game.skin, "dim");
                val.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
                arsenal.add(val).right().row();
            }
            root.add(arsenal).row();
        }

        root.add(com.pcdc.td.ui.Widgets.sectionHeader(game.skin, Lang.get("ach.title")))
            .growX().padTop(12).row();
        Table achList = new Table();
        achList.defaults().pad(2);
        for (com.pcdc.td.meta.Achievements.Def def : com.pcdc.td.meta.Achievements.ALL) {
            boolean done = game.profile.achievements.contains(def.id, false);
            Label name = new Label((done ? "[OK] " : "") + Lang.get("ach." + def.id), game.skin,
                done ? "gold" : "default");
            name.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
            int progress = Math.min(game.profile.stat(def.statKey), def.target);
            Label prog = new Label(progress + "/" + def.target, game.skin, "dim");
            prog.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
            achList.add(name).left().width(320);
            achList.add(prog).right().row();
            // barra de avance bajo los pendientes: los logros a medias enganchan
            if (!done && progress > 0) {
                com.badlogic.gdx.scenes.scene2d.ui.Image bar = new com.badlogic.gdx.scenes.scene2d.ui.Image(
                    new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
                        game.skin.getRegion("white")));
                bar.setColor(1f, 0.82f, 0.4f, 0.5f);
                achList.add(bar).left().colspan(2).height(3)
                    .width(400f * progress / def.target).padBottom(4).row();
            }
        }
        // la lista va plana: el scroll lo pone la columna general (evita scroll anidado)
        root.add(achList).width(420).row();

        TextButton back = new TextButton(Lang.get("options.back"), game.skin);
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MainMenuScreen(game));
                dispose();
            }
        });
        root.add(back).width(300).height(46).padTop(12).row();

        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll =
            new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(root);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        Table outer = new Table();
        outer.setFillParent(true);
        com.pcdc.td.ui.UiScale.padSafe(outer, 6f);
        outer.add(scroll).grow();
        stage.addActor(outer);
    }

    private void addStatRow(Table list, String key, int value) {
        Label name = new Label(Lang.get(key), game.skin);
        name.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
        Label val = new Label(String.valueOf(value), game.skin, "dim");
        val.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
        list.add(name).left().width(320);
        list.add(val).right().row();
    }

    /**
     * Créditos y licencias. La atribución de Roboto (Apache 2.0) no es
     * decorativa: la licencia la exige para poder distribuir el juego.
     */
    private void showAbout() {
        Table panel = com.pcdc.td.ui.Widgets.modal(stage, game.skin, Lang.get("title"));
        panel.add(new Label(Lang.get("about.version"), game.skin, "gold")).row();
        Label body = new Label(Lang.get("about.body"), game.skin);
        body.setWrap(true);
        body.setAlignment(com.badlogic.gdx.utils.Align.center);
        panel.add(body).width(com.pcdc.td.ui.UiScale.compact() ? 340 : 420).padTop(6).row();
        Label license = new Label(Lang.get("about.license"), game.skin, "dim");
        license.setWrap(true);
        license.setAlignment(com.badlogic.gdx.utils.Align.center);
        license.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
        panel.add(license).width(com.pcdc.td.ui.UiScale.compact() ? 340 : 420).padTop(8).row();
        TextButton close = new TextButton(Lang.get("options.back"), game.skin);
        close.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                panel.getParent().remove();
            }
        });
        panel.add(close).width(200).height(38).padTop(12);
    }

    private String shakeText() {
        return Lang.fmt("options.shake",
            Lang.get(com.pcdc.td.ui.Quality.shake ? "options.on" : "options.off"));
    }

    private String fpsText() {
        return Lang.fmt("options.fps",
            Lang.get(com.pcdc.td.ui.Quality.showFps ? "options.on" : "options.off"));
    }

    private String damageTextText() {
        return Lang.fmt("options.damagetext",
            Lang.get(com.pcdc.td.ui.Quality.damageText ? "options.on" : "options.off"));
    }

    private String relaxedText() {
        return Lang.fmt("options.relaxed",
            Lang.get(com.pcdc.td.ui.Quality.relaxed ? "options.on" : "options.off"));
    }

    private String fullscreenText() {
        return Gdx.graphics.isFullscreen() ? Lang.get("options.windowed") : Lang.get("options.fullscreen");
    }

    private String qualityText() {
        return Lang.fmt("options.quality",
            Lang.get(com.pcdc.td.ui.Quality.high ? "quality.high" : "quality.low"));
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
                        // con los créditos abiertos, atrás cierra el modal
                        if (com.pcdc.td.ui.Widgets.dismissTopModal(stage)) return true;
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
        bg.dispose();
    }
}
