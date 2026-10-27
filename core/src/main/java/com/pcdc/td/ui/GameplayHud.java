package com.pcdc.td.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.StringBuilder;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.pcdc.td.data.TowerDef;
import com.pcdc.td.world.GameWorld;
import com.pcdc.td.world.Tower;

/** HUD del nivel: barra superior de estado, barra inferior de torres y panel de venta. */
public class GameplayHud {

    /** Datos estructurados del panel de tanque (los monta la pantalla). */
    public static class PanelInfo {
        public float dmg, rng, rate, pen, dps;
        /** Lo que darían esas mismas stats con la mejora pagada (0 = al máximo). */
        public float nextDmg, nextRng, nextRate;
        public int kills;
        /** Líneas extra (aura, herencia, en traslado), o null. */
        public String extra;
        public final Array<FusionOption> fusions = new Array<>();
    }

    /** Receta desbloqueada que parte del tanque del panel. */
    public static class FusionOption {
        public String partnerId;
        public String resultName;
    }

    public interface Listener {
        /** id de torre seleccionada en la barra, o null al deseleccionar. */
        void onBuildSelected(String towerId);

        /** Chip de la previa de oleada tocado: mostrar la carta de ese enemigo. */
        default void onPreviewEnemyTapped(String enemyId) {
        }

        void onCallWave();

        void onSpeedChanged(float multiplier);

        /** Cicla la estrategia de targeting de la torre seleccionada. */
        void onCycleTargeting();

        /** Sube de nivel la torre seleccionada. */
        void onUpgrade();

        void onSell();

        /** Arma la reubicación de la torre seleccionada (nodo Logística). */
        void onMove();

        /** Deshace la última colocación (solo antes de arrancar la oleada). */
        void onUndoPlace();

        void onExit();

        /** Vuelve a empezar el nivel desde cero. */
        void onRestart();

        /** true si salir al menú guarda la partida (falso en modo sin fin). */
        boolean canResumeLater();

        void onRotateCamera();

        /** El jugador ha pausado o reanudado desde la UI. */
        void onPauseChanged(boolean paused);

        /** Habilidad activada: 0 meteorito, 1 escarcha, 2 fiebre del oro. */
        void onAbility(int index);

        /** El jugador tocó el panel de tutorial para descartarlo. */
        void onTutorialDismissed();

        /** Arrastre de una torre desde la barra (previsualización sobre el tablero). */
        void onBuildDragMoved(String towerId, float screenX, float screenY);

        /** Soltar el arrastre: colocar si la celda es válida. */
        void onBuildDragDropped(String towerId, float screenX, float screenY);
    }

    public final Stage stage;
    private final Skin skin;
    private final Listener listener;

    private final Label goldLabel, livesLabel, waveLabel, countdownLabel;
    private final Label waveProgressLabel;
    private final com.badlogic.gdx.scenes.scene2d.ui.ProgressBar waveProgressBar;
    private int lastWaveProgress = -1;
    private Table tipPanel;
    private Label tipLabel;
    /** Devuelve los botones de confirmación de la pausa a su estado inicial. */
    private final Array<Runnable> confirmResets = new Array<>();
    private final TextButton callWaveButton, speedButton, sellButton;
    private TextButton moveButton;
    private TextButton undoButton;
    /** El perfil tiene el nodo Logística: el panel muestra "Mover". */
    private boolean relocateUnlocked;
    private final Table sellPanel;
    private final Label sellInfoLabel;
    private final Label noticeLabel;
    private final Table tutorialPanel;
    private final Label tutorialLabel;
    private final ObjectMap<String, TextButton> towerButtons = new ObjectMap<>();
    private final Array<TowerDef> availableTowers;

    private final StringBuilder sb = new StringBuilder();
    private int lastGold = -1, lastLives = -1, lastWave = -1, lastCountdown = -1;

    private String selectedBuildId;

    private final Image weatherOverlay;
    private final com.pcdc.td.audio.SoundManager sounds;
    private final com.pcdc.td.data.Catalogs catalogs;
    private Table pauseOverlay;
    private Table previewInner;
    private Label fpsLabel;
    private float fpsTimer, worstFrameMs;
    private Table towerBarBase, towerBarFusion;
    private TextButton baseTab, fusionTab;
    /** Filas de botones de cada pestaña (1 o 2) y si hay pestañas. */
    private int barRowsBase = 1, barRowsFusion = 1;
    private boolean barTabbed, fusionTabVisible;
    private TextButton autoWaveButton;
    private boolean autoWave;

    /**
     * Refresca el contador: FPS y el PEOR frame del último medio segundo. La
     * media miente — un tirón de 40 ms se nota y no mueve la media.
     */
    public void updateFps(float delta) {
        if (fpsLabel == null) return;
        worstFrameMs = Math.max(worstFrameMs, delta * 1000f);
        fpsTimer += delta;
        if (fpsTimer < 0.5f) return;
        fpsTimer = 0f;
        fpsLabel.setText(com.badlogic.gdx.Gdx.graphics.getFramesPerSecond() + " FPS   "
            + String.format(Lang.locale(), "%.1f ms", worstFrameMs));
        worstFrameMs = 0f;
    }

    /** Auto-oleada activada por el jugador (persistida en ajustes). */
    public boolean autoWaveEnabled() {
        return autoWave;
    }
    private int lastPreviewKey = Integer.MIN_VALUE;
    private float speedMult = 1f;
    private Label towerStatsLabel;
    private Image panelIcon;
    private Label levelChip;
    private Table statsGrid;
    private Label extraLabel;
    /** Previsualización de lo que da la mejora antes de pagarla. */
    private Label upgradePreview;
    private Table fusionRow;
    private TextButton targetButton;
    private TextButton upgradeButton;
    /** Venta a dos toques: un roce accidental no debe malvender un tanque mejorado. */
    private boolean sellArmed;
    private Tower panelTower;
    private int panelRefund;

    private static final String[] ABILITY_IDS = {"meteor", "frost", "gold"};
    private static final String[] ABILITY_ICONS = {"meteor", "frost", "goldrush"};
    private Table bossPanel;
    private com.badlogic.gdx.scenes.scene2d.ui.ProgressBar bossBar;
    private Label bossAbilityLabel;
    private Label bossTitleLabel;
    private Table buildInfoPanel;
    private Label buildInfoLabel;
    private final com.badlogic.gdx.scenes.scene2d.ui.Button[] abilityButtons =
        new com.badlogic.gdx.scenes.scene2d.ui.Button[3];
    private final Label[] abilityLabels = new Label[3];
    private final int[] lastAbilityCd = {-1, -1, -1};
    private Image damageOverlay;
    private final com.badlogic.gdx.utils.ObjectSet<String> abilities;

    /** Iconos 3D pre-renderizados por id de torre (puede estar vacío). */
    private final com.badlogic.gdx.utils.ObjectMap<String, com.badlogic.gdx.graphics.g2d.TextureRegion> towerIcons;

    public GameplayHud(Skin skin, Array<TowerDef> availableTowers,
                       com.badlogic.gdx.utils.ObjectMap<String, com.badlogic.gdx.graphics.g2d.TextureRegion> towerIcons,
                       com.badlogic.gdx.utils.ObjectSet<String> abilities,
                       com.pcdc.td.audio.SoundManager sounds,
                       com.pcdc.td.data.Catalogs catalogs, Listener listener) {
        this.skin = skin;
        this.listener = listener;
        this.sounds = sounds;
        this.catalogs = catalogs;
        this.towerIcons = towerIcons;
        this.abilities = abilities;
        this.availableTowers = availableTowers;
        this.stage = new Stage(com.pcdc.td.ui.UiScale.viewport());

        // tinte de clima a pantalla completa (tormenta de arena, viento...), bajo la UI
        weatherOverlay = new Image(skin.newDrawable("white", Color.WHITE));
        weatherOverlay.setFillParent(true);
        weatherOverlay.setVisible(false);
        weatherOverlay.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        stage.addActor(weatherOverlay);

        // ---- barra superior ----
        goldLabel = new Label("", skin, "gold");
        livesLabel = new Label("", skin);
        waveLabel = new Label("", skin);
        countdownLabel = new Label("", skin, "dim");

        callWaveButton = new TextButton(Lang.get("hud.callwave"), skin, "primary");
        callWaveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                listener.onCallWave();
            }
        });

        speedButton = new TextButton("x1", skin);
        speedButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                float next = speedMult >= 3f ? 1f : speedMult + 1f;
                setSpeed(next);
                listener.onSpeedChanged(next);
            }
        });

        // AUTO-OLEADA: salta la cuenta atrás en cuanto el tablero queda limpio.
        // El bonus de oro por adelantar queda reservado al toque MANUAL.
        autoWave = com.badlogic.gdx.Gdx.app.getPreferences("td-settings")
            .getBoolean("autoWave", false);
        autoWaveButton = new TextButton(Lang.get("hud.auto"), skin, "toggle");
        autoWaveButton.setChecked(autoWave);
        autoWaveButton.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
        autoWaveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                autoWave = autoWaveButton.isChecked();
                com.badlogic.gdx.Gdx.app.getPreferences("td-settings")
                    .putBoolean("autoWave", autoWave).flush();
            }
        });

        undoButton = new TextButton(Lang.get("hud.undo"), skin);
        buttonIcon(undoButton, "icon-undo");
        undoButton.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
        undoButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                listener.onUndoPlace();
            }
        });
        undoButton.setVisible(false);

        Table top = new Table();
        top.setFillParent(true);
        top.top();
        UiScale.padSafe(top, 6f); // respeta notch y esquinas redondeadas
        // en móvil el ancho útil está topado (UiScale) y la barra llegó a pedir
        // más de lo que cabe: con Deshacer en pantalla, la pausa se salía por
        // la derecha. En compacto se aprietan pads, anchos y el propio Deshacer.
        boolean tight = UiScale.compact();
        Table topBar = new Table();
        topBar.setBackground(skin.getDrawable("panel"));
        topBar.pad(tight ? 4 : 6).defaults()
            .padLeft(tight ? 4 : 9).padRight(tight ? 4 : 9);
        // izquierda: los DATOS de la partida. Anchos fijos para que nada baile
        // al cambiar los textos.
        topBar.add(Widgets.currency(skin, "icon-gold", goldLabel)).width(tight ? 72 : 86);
        topBar.add(Widgets.currency(skin, "icon-heart", livesLabel)).width(tight ? 54 : 64);
        topBar.add(waveLabel).width(tight ? 76 : 90);
        topBar.add(countdownLabel).width(tight ? 92 : 112);
        // separador: a partir de aquí son MANDOS, no información
        Image divider = new Image(skin.newDrawable("white", new Color(1f, 1f, 1f, 0.16f)));
        topBar.add(divider).width(2).height(26).padLeft(2).padRight(tight ? 3 : 6);
        topBar.add(callWaveButton).width(tight ? 86 : 96).height(32);
        // Deshacer se queda en icono a secas: es efímero y con texto no cabía
        if (tight) undoButton.setText("");
        topBar.add(undoButton).width(tight ? 40 : 84).height(32).padLeft(2).padRight(2);
        topBar.add(speedButton).width(tight ? 40 : 46).height(32).padLeft(3).padRight(3);
        topBar.add(autoWaveButton).width(tight ? 52 : 60).height(32).padLeft(2).padRight(2);
        topBar.add(iconButton("icon-rotate", listener::onRotateCamera))
            .size(tight ? 34 : 38, 32).padLeft(2).padRight(2);
        topBar.add(iconButton("icon-pause", () -> setPaused(true)))
            .size(tight ? 34 : 38, 32).padLeft(2);
        top.add(topBar).row();

        // vista previa de la siguiente oleada (chips de color por tipo de enemigo)
        // y, mientras la oleada corre, el progreso: ocupan el mismo hueco porque
        // nunca coinciden en pantalla.
        waveProgressBar = new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar(0f, 1f, 0.01f, false,
            skin.get("wave-horizontal",
                com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle.class));
        waveProgressLabel = new Label("", skin, "dim");
        waveProgressLabel.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
        previewInner = new Table();
        previewInner.setBackground(skin.getDrawable("panel"));
        previewInner.pad(4).defaults().pad(2);
        top.add(previewInner).padTop(4);
        stage.addActor(top);

        // ---- barra inferior de torres ----
        Table bottom = new Table();
        bottom.setFillParent(true);
        bottom.bottom();
        UiScale.padSafe(bottom, 6f);
        // PESTAÑAS: con Fabricación avanzada llegaban a la barra 11 tanques
        // base + 21 fusiones, dos filas larguísimas que se comían media
        // pantalla. Ahora se ve un grupo cada vez y el otro está a un toque.
        Array<TowerDef> baseTowers = new Array<>();
        Array<TowerDef> fusionTowers = new Array<>();
        for (TowerDef def : availableTowers) {
            (def.fusionOnly ? fusionTowers : baseTowers).add(def);
        }
        boolean tabbed = fusionTowers.size > 0;

        towerBarBase = buildTowerRow(baseTowers);
        towerBarFusion = tabbed ? buildTowerRow(fusionTowers) : null;
        barRowsBase = baseTowers.size > 11 ? 2 : 1;
        barRowsFusion = fusionTowers.size > 11 ? 2 : 1;
        barTabbed = tabbed;

        Table barWrap = new Table();
        if (tabbed) {
            Table tabs = new Table();
            tabs.defaults().padLeft(3).padRight(3);
            baseTab = new TextButton(Lang.get("hud.tab_base"), skin, "primary");
            fusionTab = new TextButton(Lang.get("hud.tab_fusions"), skin);
            baseTab.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
            fusionTab.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
            baseTab.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    sounds.playSfx("click");
                    showFusionTab(false);
                }
            });
            fusionTab.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    sounds.playSfx("click");
                    showFusionTab(true);
                }
            });
            tabs.add(baseTab).width(96).height(26);
            tabs.add(fusionTab).width(112).height(26);
            barWrap.add(tabs).padBottom(2).row();
        }
        // Stack, no dos celdas: en celdas contiguas la pestaña oculta seguía
        // reservando su ancho y empujaba la visible a un lado de la pantalla
        Stack stackWrap = new Stack();
        stackWrap.add(towerBarBase);
        if (towerBarFusion != null) stackWrap.add(towerBarFusion);
        barWrap.add(stackWrap);
        bottom.add(barWrap);
        stage.addActor(bottom);
        if (towerBarFusion != null) showFusionTab(false);

        // ---- panel de torre seleccionada ----
        sellInfoLabel = new Label("", skin);
        panelIcon = new Image();
        panelIcon.setScaling(Scaling.fit);
        levelChip = new Label("", skin, "gold");
        levelChip.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
        statsGrid = new Table();
        extraLabel = new Label("", skin, "dim");
        extraLabel.setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
        upgradePreview = new Label("", skin, "gold");
        upgradePreview.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
        upgradePreview.setVisible(false);
        fusionRow = new Table();
        towerStatsLabel = new Label("", skin, "dim");
        towerStatsLabel.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
        // el icono de ciclo deja claro que es un botón que rota entre opciones,
        // antes parecía una etiqueta de estado
        targetButton = new TextButton("", skin);
        targetButton.getLabelCell().padRight(4);
        targetButton.add(new Image(skin.get("icon-cycle",
            com.badlogic.gdx.graphics.g2d.TextureRegion.class))).size(14);
        targetButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                listener.onCycleTargeting();
            }
        });
        // mejorar GASTA oro: estilo ámbar "buy", no el verde de confirmar
        upgradeButton = new TextButton("", skin, "buy");
        buttonIcon(upgradeButton, "icon-up");
        upgradeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                listener.onUpgrade();
            }
        });
        sellButton = new TextButton("Vender", skin, "danger");
        buttonIcon(sellButton, "icon-gold");
        sellButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                armOrSell();
            }
        });
        moveButton = new TextButton(Lang.get("hud.move"), skin);
        buttonIcon(moveButton, "icon-move");
        moveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                listener.onMove();
            }
        });
        moveButton.setVisible(false); // requiere el nodo Logística
        sellPanel = new Table();
        sellPanel.setFillParent(true);
        sellPanel.right().bottom();
        UiScale.padSafe(sellPanel, 6f);
        // con Fabricación avanzada la barra ocupa DOS filas: los paneles
        // inferiores tienen que subir o la pisan (se veía con uiscale alto)
        float barPad = (availableTowers.size > 10 ? 152 : 90) + UiScale.safeBottom();
        sellPanel.padBottom(barPad);
        // en móvil apaisado el panel entero (cabecera + stats + fusiones + 4
        // botones en columna) NO cabía de alto: se salía por arriba y el
        // nombre y las stats quedaban cortados tras la barra superior
        boolean tightPanel = UiScale.compact();
        Table panel = new Table();
        panel.setBackground(skin.getDrawable("panel-light"));
        panel.pad(tightPanel ? 5 : 8).defaults().pad(tightPanel ? 2 : 3);
        // cabecera: icono 3D + nombre + chip de nivel
        Table header = new Table();
        header.add(panelIcon).size(tightPanel ? 26 : 34).padRight(6);
        header.add(sellInfoLabel).left();
        Table chipWrap = new Table();
        chipWrap.setBackground(skin.getDrawable("panel"));
        chipWrap.pad(1).padLeft(7).padRight(7);
        chipWrap.add(levelChip);
        header.add(chipWrap).padLeft(8);
        panel.add(header).left().row();
        panel.add(statsGrid).left().row();
        panel.add(extraLabel).left().row();
        panel.add(fusionRow).left().row();
        panel.add(upgradePreview).left().row();
        if (tightPanel) {
            // los mismos cuatro botones en dos filas de dos: se recuperan
            // dos alturas de fila, que es justo lo que se salía
            Table upper = new Table();
            upper.add(upgradeButton).height(28).growX().uniform().padRight(3);
            upper.add(targetButton).height(28).growX().uniform();
            panel.add(upper).fillX().row();
            Table lower = new Table();
            lower.add(moveButton).height(28).growX().uniform().padRight(3);
            lower.add(sellButton).height(28).growX().uniform();
            panel.add(lower).fillX();
        } else {
            panel.add(upgradeButton).height(30).fillX().row();
            panel.add(targetButton).height(28).fillX().row();
            panel.add(moveButton).height(28).fillX().row();
            panel.add(sellButton).height(30).fillX();
        }
        sellPanel.add(panel);
        sellPanel.setVisible(false);
        stage.addActor(sellPanel);

        // ---- contador de rendimiento (Opciones) ----
        if (com.pcdc.td.ui.Quality.showFps) {
            fpsLabel = new Label("", skin, "dim");
            fpsLabel.setFontScale(com.pcdc.td.ui.Fonts.rel(0.75f));
            Table fpsTable = new Table();
            fpsTable.setFillParent(true);
            fpsTable.top().left().padTop(UiScale.compact() ? 46 : 54).padLeft(10);
            fpsTable.add(fpsLabel);
            fpsTable.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
            stage.addActor(fpsTable);
        }

        // ---- aviso de mecánica del bioma ----
        noticeLabel = new Label("", skin, "gold");
        Table noticeTable = new Table();
        noticeTable.setFillParent(true);
        noticeTable.top().padTop(52);
        noticeTable.add(noticeLabel);
        noticeTable.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        stage.addActor(noticeTable);

        // ---- panel de tutorial ----
        tutorialLabel = new Label("", skin);
        tutorialLabel.setWrap(true);
        tutorialLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
        tutorialPanel = new Table();
        tutorialPanel.setFillParent(true);
        // arriba, bajo la previa de oleada: abajo tapaba justo la zona del
        // tablero donde ocurre la acción en los niveles del tutorial
        tutorialPanel.top().padTop(132 + UiScale.safeTop());
        Table tp = new Table();
        tp.setBackground(skin.getDrawable("panel-light"));
        tp.pad(10);
        tp.add(tutorialLabel).width(420).row();
        Label dismissHint = new Label(Lang.get("tutorial.dismiss"), skin, "dim");
        dismissHint.setFontScale(com.pcdc.td.ui.Fonts.rel(0.75f));
        tp.add(dismissHint).right().padTop(2);
        // tocar el mensaje lo descarta (los últimos avisos no molestan más)
        tp.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        tp.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                listener.onTutorialDismissed();
            }
        });
        tutorialPanel.add(tp);
        tutorialPanel.setVisible(false);
        // solo el panel interior captura toques; el resto pasa al tablero
        tutorialPanel.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly);
        stage.addActor(tutorialPanel);

        // ---- consejo contextual (primera vez que aparece cada mecánica) ----
        tipLabel = new Label("", skin);
        tipLabel.setWrap(true);
        tipLabel.setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
        tipPanel = new Table();
        tipPanel.setFillParent(true);
        tipPanel.bottom().padBottom((availableTowers.size > 10 ? 158 : 96) + UiScale.safeBottom());
        tipPanel.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        Table tipInner = new Table();
        tipInner.setBackground(skin.getDrawable("panel-light"));
        tipInner.pad(8).defaults().pad(2);
        Label tipTitle = new Label(Lang.get("tip.title"), skin, "gold");
        tipTitle.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
        tipInner.add(tipTitle).left().row();
        tipInner.add(tipLabel).width(360).left();
        tipPanel.add(tipInner);
        tipPanel.setVisible(false);
        stage.addActor(tipPanel);

        // ---- botones de habilidades activas (izquierda) ----
        Table abilityPanel = new Table();
        abilityPanel.setFillParent(true);
        abilityPanel.left();
        abilityPanel.padLeft(8 + UiScale.safeLeft());
        Table abilityCol = new Table();
        abilityCol.defaults().pad(3);
        for (int i = 0; i < ABILITY_IDS.length; i++) {
            if (!abilities.contains(ABILITY_IDS[i])) continue;
            final int index = i;
            // icono + estado: eran los últimos botones del juego sin imagen
            com.badlogic.gdx.scenes.scene2d.ui.Button b =
                new com.badlogic.gdx.scenes.scene2d.ui.Button(
                    skin.get("toggle", TextButton.TextButtonStyle.class));
            Label state = new Label("", skin);
            state.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
            state.setAlignment(com.badlogic.gdx.utils.Align.center);
            b.add(new Image(skin.get("icon-" + ABILITY_ICONS[i],
                com.badlogic.gdx.graphics.g2d.TextureRegion.class))).size(24).padRight(5);
            b.add(state).width(52);
            b.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    listener.onAbility(index);
                }
            });
            abilityButtons[i] = b;
            abilityLabels[i] = state;
            abilityCol.add(b).width(104).height(42).row();
        }
        abilityPanel.add(abilityCol);
        stage.addActor(abilityPanel);

        // barra de vida del jefe: compacta, pegada bajo la barra superior
        bossPanel = new Table();
        bossPanel.setFillParent(true);
        bossPanel.top().padTop(72 + UiScale.safeTop());
        bossPanel.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        Table bossInner = new Table();
        bossInner.setBackground(skin.getDrawable("panel"));
        bossInner.pad(3).defaults().pad(1);
        bossTitleLabel = new Label(Lang.get("boss.banner"), skin, "gold");
        bossTitleLabel.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
        Label bossLabel = bossTitleLabel;
        bossBar = new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar(0f, 1f, 0.01f, false, skin);
        Table bossRow = new Table();
        bossRow.add(bossLabel).padRight(6);
        bossRow.add(bossBar).width(170).height(9);
        bossInner.add(bossRow).row();
        bossAbilityLabel = new Label("", skin, "dim");
        bossAbilityLabel.setFontScale(com.pcdc.td.ui.Fonts.rel(0.72f));
        bossInner.add(bossAbilityLabel);
        bossPanel.add(bossInner);
        bossPanel.setVisible(false);
        stage.addActor(bossPanel);

        // ficha de la torre seleccionada en la barra (antes de construir)
        buildInfoLabel = new Label("", skin);
        buildInfoLabel.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
        buildInfoPanel = new Table();
        buildInfoPanel.setFillParent(true);
        buildInfoPanel.left().bottom();
        UiScale.padSafe(buildInfoPanel, 6f);
        buildInfoPanel.padBottom((availableTowers.size > 10 ? 152 : 90) + UiScale.safeBottom());
        buildInfoPanel.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        Table bip = new Table();
        bip.setBackground(skin.getDrawable("panel-light"));
        bip.pad(8);
        bip.add(buildInfoLabel).left();
        buildInfoPanel.add(bip);
        buildInfoPanel.setVisible(false);
        stage.addActor(buildInfoPanel);

        // flash rojo al perder una vida (encima de todo menos la pausa)
        damageOverlay = new Image(skin.newDrawable("white", new Color(1f, 0.1f, 0.1f, 1f)));
        damageOverlay.setFillParent(true);
        damageOverlay.setVisible(false);
        damageOverlay.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        stage.addActor(damageOverlay);

        // ---- panel de pausa (oculto por defecto) ----
        buildPauseOverlay();
    }

    /** Barra de vida del jefe; null la oculta. */
    public void setBoss(com.pcdc.td.world.Enemy boss) {
        bossPanel.setVisible(boss != null);
        if (boss != null) {
            bossBar.setValue(boss.hp / boss.maxHp);
            // el jefe final tiene NOMBRE propio; el resto, el rótulo genérico
            bossTitleLabel.setText("overlord".equals(boss.def.id)
                ? Lang.name("enemy", "overlord", "overlord").toUpperCase()
                : Lang.get("boss.banner"));
            String ability = "none".equals(boss.bossAbility)
                ? "" : Lang.get("bossability." + boss.bossAbility);
            // afijo de Nivel+ visible junto a la habilidad del bioma
            if (!"none".equals(boss.affix)) {
                String affix = Lang.get("affix." + boss.affix);
                ability = ability.isEmpty() ? affix : ability + "  ·  " + affix;
            }
            // en fase 2 el jefe se desata: se avisa junto a su habilidad
            bossAbilityLabel.setText(boss.phase == 2
                ? Lang.get("boss.phase2") + "  ·  " + ability : ability);
        }
    }

    /** Ficha informativa de la torre seleccionada en la barra; null la oculta. */
    public void showBuildInfo(String text) {
        buildInfoPanel.setVisible(text != null);
        if (text != null) buildInfoLabel.setText(text);
    }

    /** Flash rojo + desvanecimiento (al colarse un enemigo). */
    public void flashDamage() {
        damageOverlay.clearActions();
        damageOverlay.getColor().a = 0.4f;
        damageOverlay.setVisible(true);
        damageOverlay.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut(0.45f),
            com.badlogic.gdx.scenes.scene2d.actions.Actions.visible(false)));
    }

    /** Botón solo con icono: para mandos donde la palabra estorbaba (girar, pausa). */
    private com.badlogic.gdx.scenes.scene2d.ui.Button iconButton(String icon, Runnable action) {
        com.badlogic.gdx.scenes.scene2d.ui.Button b =
            new com.badlogic.gdx.scenes.scene2d.ui.Button(
                skin.get("default", TextButton.TextButtonStyle.class));
        b.add(new Image(skin.get(icon, com.badlogic.gdx.graphics.g2d.TextureRegion.class))).size(18);
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
            }
        });
        return b;
    }

    /** Marca el botón de una habilidad como "apuntando" (meteorito). */
    public void setAbilityAiming(int index, boolean aiming) {
        if (abilityButtons[index] != null) abilityButtons[index].setChecked(aiming);
    }

    private void buildPauseOverlay() {
        // un Group nunca se devuelve a sí mismo en hit(): sin este override los
        // toques ATRAVIESAN el fondo oscurecido y llegan al HUD/tablero de debajo
        pauseOverlay = new Table() {
            @Override
            public com.badlogic.gdx.scenes.scene2d.Actor hit(float x, float y, boolean touchable) {
                com.badlogic.gdx.scenes.scene2d.Actor a = super.hit(x, y, touchable);
                if (a == null && isVisible()
                    && x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) a = this;
                return a;
            }
        };
        pauseOverlay.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true; // modal: que el toque no caiga a la cámara/el mundo
            }
        });
        pauseOverlay.setFillParent(true);
        pauseOverlay.setBackground(skin.newDrawable("white", new Color(0f, 0f, 0f, 0.55f)));
        pauseOverlay.setVisible(false);

        // en móvil apaisado la altura manda: con el botón de reiniciar, el panel
        // a tamaño normal se salía por abajo en pantallas alargadas
        boolean compact = UiScale.compact();
        float btnH = compact ? 34f : 42f;

        Table panel = new Table();
        panel.setBackground(skin.getDrawable("panel"));
        panel.pad(compact ? 12 : 20).defaults().pad(compact ? 3 : 6);

        panel.add(new Label(Lang.get("pause.title"), skin, compact ? "default" : "big"))
            .padBottom(compact ? 4 : 10).row();
        panel.add(Widgets.volumeControls(skin, sounds)).row();

        TextButton quality = new TextButton(
            Lang.fmt("options.quality", Lang.get(Quality.high ? "quality.high" : "quality.low")), skin);
        quality.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Quality.toggle();
                quality.setText(Lang.fmt("options.quality",
                    Lang.get(Quality.high ? "quality.high" : "quality.low")));
            }
        });
        panel.add(quality).width(240).height(compact ? 32 : 38).row();

        TextButton resume = new TextButton(Lang.get("pause.continue"), skin, "primary");
        resume.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setPaused(false);
            }
        });
        panel.add(resume).width(240).height(btnH).padTop(compact ? 5 : 10).row();

        panel.add(confirmButton("pause.restart", "default",
            () -> "pause.restart_confirm", listener::onRestart)).width(240).height(btnH).row();

        // salir guarda la partida salvo en sin fin, que no tiene checkpoint
        panel.add(confirmButton("pause.exit", "danger",
            () -> listener.canResumeLater() ? "pause.exit_confirm" : "pause.exit_confirm_lose",
            listener::onExit)).width(240).height(btnH).row();

        pauseOverlay.add(panel);
        stage.addActor(pauseOverlay);
    }

    /** Clave del texto de confirmación, resuelta en el momento de pulsar. */
    private interface ConfirmText {
        String key();
    }

    /**
     * Botón de dos toques: el primero cambia el texto a la pregunta y el segundo
     * ejecuta. Se registra para volver a su estado inicial al reabrir la pausa.
     */
    private TextButton confirmButton(final String textKey, String style,
                                     final ConfirmText confirmKey, final Runnable action) {
        final TextButton b = new TextButton(Lang.get(textKey), skin, style);
        final boolean[] confirming = {false};
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!confirming[0]) {
                    confirming[0] = true;
                    b.setText(Lang.get(confirmKey.key()));
                } else {
                    action.run();
                }
            }
        });
        confirmResets.add(() -> {
            confirming[0] = false;
            b.setText(Lang.get(textKey));
        });
        return b;
    }

    /** Muestra/oculta la pausa y avisa al listener. */
    public void setPaused(boolean paused) {
        // al reabrir la pausa las confirmaciones vuelven a empezar: si no, un
        // "¿Seguro?" a medias de hace un rato se ejecutaba a la primera
        if (paused) {
            for (Runnable r : confirmResets) r.run();
        }
        pauseOverlay.setVisible(paused);
        pauseOverlay.toFront();
        listener.onPauseChanged(paused);
    }

    public boolean isPaused() {
        return pauseOverlay.isVisible();
    }

    public void setNotice(String text) {
        noticeLabel.setText(text == null ? "" : text);
    }

    /** Tinte de clima; null lo oculta. */
    public void setWeather(Color color) {
        weatherOverlay.setVisible(color != null);
        if (color != null) weatherOverlay.setColor(color);
    }

    /** Consejo contextual sobre la barra de tanques; null lo oculta. */
    public void setTip(String text) {
        tipPanel.setVisible(text != null);
        if (text != null) {
            tipLabel.setText(text);
            tipPanel.toFront();
            tipPanel.getColor().a = 0f;
            tipPanel.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.25f));
        }
    }

    public void setTutorial(String text) {
        tutorialPanel.setVisible(text != null);
        if (text != null) tutorialLabel.setText(text);
    }

    private TextButton buildTowerButton(TowerDef def) {
        com.badlogic.gdx.graphics.g2d.TextureRegion icon = towerIcons.get(def.id);
        sb.setLength(0);
        if (icon == null) sb.append(Lang.name("tower", def.id, def.name)).append("\n");
        sb.append(def.cost);
        TextButton b = new TextButton(sb.toString(), skin, "toggle");
        b.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
        b.clearChildren();
        if (icon != null) {
            // icono 3D + NOMBRE + coste: con 21 fusiones el color y la silueta
            // no bastan para reconocerlas en la barra (el nombre va con puntos
            // suspensivos si no cabe; la ficha sigue mostrándolo entero)
            Table content = new Table();
            Image img = new Image(icon);
            img.setScaling(Scaling.fit);
            content.add(img).size(26).row();
            Label name = new Label(Lang.name("tower", def.id, def.name), skin);
            name.setFontScale(com.pcdc.td.ui.Fonts.rel(0.52f));
            name.setEllipsis(true);
            name.setAlignment(com.badlogic.gdx.utils.Align.center);
            content.add(name).width(56).row();
            content.add(b.getLabel());
            b.add(content).grow();
            addTowerButtonListener(b, def);
            return b;
        }
        // fallback: franja del color de la torre encima del botón
        Image colorStrip = new Image(skin.newDrawable("white", def.color()));
        colorStrip.setScaling(Scaling.stretch);
        Stack stack = new Stack();
        Table strip = new Table();
        strip.top();
        strip.add(colorStrip).height(5).growX();
        stack.add(strip);
        Table text = new Table();
        text.add(b.getLabel());
        stack.add(text);
        b.add(stack).grow();
        addTowerButtonListener(b, def);
        return b;
    }

    private final com.badlogic.gdx.math.Vector2 dragTmp = new com.badlogic.gdx.math.Vector2();

    private void addTowerButtonListener(TextButton b, TowerDef def) {
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (def.id.equals(selectedBuildId)) {
                    setSelectedBuild(null);
                } else {
                    setSelectedBuild(def.id);
                }
                listener.onBuildSelected(selectedBuildId);
            }
        });
        // arrastrar desde la barra: previsualiza el ghost + rango y coloca al soltar
        com.badlogic.gdx.scenes.scene2d.utils.DragListener drag =
            new com.badlogic.gdx.scenes.scene2d.utils.DragListener() {
                @Override
                public void dragStart(InputEvent event, float x, float y, int pointer) {
                    setSelectedBuild(def.id);
                    listener.onBuildSelected(def.id);
                }

                @Override
                public void drag(InputEvent event, float x, float y, int pointer) {
                    dragTmp.set(event.getStageX(), event.getStageY());
                    stage.stageToScreenCoordinates(dragTmp);
                    listener.onBuildDragMoved(def.id, dragTmp.x, dragTmp.y);
                }

                @Override
                public void dragStop(InputEvent event, float x, float y, int pointer) {
                    dragTmp.set(event.getStageX(), event.getStageY());
                    stage.stageToScreenCoordinates(dragTmp);
                    listener.onBuildDragDropped(def.id, dragTmp.x, dragTmp.y);
                }
            };
        drag.setTapSquareSize(10f);
        b.addListener(drag);
    }

    public void setSelectedBuild(String towerId) {
        selectedBuildId = towerId;
        for (ObjectMap.Entry<String, TextButton> e : towerButtons) {
            e.value.setChecked(e.key.equals(towerId));
        }
    }

    public String selectedBuild() {
        return selectedBuildId;
    }

    private static final String[] TARGET_KEYS = {
        "target.first", "target.last", "target.strong", "target.near", "target.air"};

    /**
     * Primer toque en Vender arma la confirmación, el segundo vende. Público
     * para que el atajo de teclado siga el mismo camino que el botón.
     */
    public void armOrSell() {
        if (!sellPanel.isVisible()) return;
        if (sellArmed) {
            sellArmed = false;
            listener.onSell();
        } else {
            sellArmed = true;
            sellButton.setText(Lang.fmt("hud.sell_confirm", panelRefund));
        }
    }

    /** Muestra u oculta el panel de la torre colocada seleccionada. */
    public void showTowerPanel(Tower tower, int refund, int upgradeCost, PanelInfo info) {
        if (tower == null) {
            sellPanel.setVisible(false);
            sellArmed = false;
            panelTower = null;
            return;
        }
        // el panel se refresca cada 0,5 s: solo desarmar si cambia el tanque
        if (tower != panelTower) {
            panelTower = tower;
            sellArmed = false;
        }
        panelRefund = refund;
        sellInfoLabel.setText(Lang.name("tower", tower.def.id, tower.def.name));
        levelChip.setText(Lang.fmt("hud.level", tower.level));
        com.badlogic.gdx.graphics.g2d.TextureRegion icon = towerIcons.get(tower.def.id);
        panelIcon.setDrawable(icon == null ? null : iconDrawable(tower.def.id, icon));
        rebuildStatsGrid(info);
        if (info != null && info.extra != null) {
            extraLabel.setText(info.extra);
            extraLabel.setVisible(true);
        } else {
            extraLabel.setText("");
            extraLabel.setVisible(false);
        }
        rebuildFusionRow(info);
        rebuildUpgradePreview(info, tower);
        if (upgradeCost < 0) {
            upgradeButton.setText(Lang.get("hud.maxlevel"));
            upgradeButton.setDisabled(true);
        } else {
            upgradeButton.setText(Lang.fmt("hud.upgrade", upgradeCost));
            upgradeButton.setDisabled(lastGold < upgradeCost);
        }
        targetButton.setText(Lang.fmt("hud.target",
            Lang.get(TARGET_KEYS[Math.min(tower.targeting, TARGET_KEYS.length - 1)])));
        sellButton.setText(Lang.fmt(sellArmed ? "hud.sell_confirm" : "hud.sell", refund));
        moveButton.setVisible(relocateUnlocked);
        sellPanel.setVisible(true);
    }

    /** Drawables de icono cacheados (el panel se refresca 2 veces/s). */
    private final ObjectMap<String, com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable>
        iconDrawables = new ObjectMap<>();

    private com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable iconDrawable(
        String id, com.badlogic.gdx.graphics.g2d.TextureRegion region) {
        com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable d = iconDrawables.get(id);
        if (d == null) {
            d = new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(region);
            iconDrawables.put(id, d);
        }
        return d;
    }

    /**
     * Fila de PREVISUALIZACIÓN de la mejora: "Nv 2: DMG 21 > 29 · RNG 3,4 > 3,6".
     * Sin esto el botón pedía oro sin decir qué daba a cambio.
     */
    private void rebuildUpgradePreview(PanelInfo info, Tower tower) {
        upgradePreview.setText("");
        upgradePreview.setVisible(false);
        if (info == null || info.nextDmg <= 0f || tower == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(Lang.fmt("hud.level", tower.level + 1)).append(": ");
        sb.append("DMG ").append(Math.round(info.dmg)).append(" > ")
            .append(Math.round(info.nextDmg));
        if (info.nextRng > info.rng + 0.05f) {
            sb.append("  ·  RNG ").append(String.format(Lang.locale(), "%.1f", info.rng))
                .append(" > ").append(String.format(Lang.locale(), "%.1f", info.nextRng));
        }
        if (info.nextRate > info.rate + 0.05f) {
            sb.append("  ·  CAD ").append(String.format(Lang.locale(), "%.1f", info.rate))
                .append(" > ").append(String.format(Lang.locale(), "%.1f", info.nextRate));
        }
        upgradePreview.setText(sb.toString());
        upgradePreview.setVisible(true);
    }

    /** Par etiqueta+valor de la rejilla de stats (3 pares por fila). */
    private void rebuildStatsGrid(PanelInfo info) {
        statsGrid.clear();
        if (info == null) return;
        int col = 0;
        col = addStat(col, "DMG", String.valueOf(Math.round(info.dmg)));
        col = addStat(col, "CAD", String.format(Lang.locale(), "%.1f/s", info.rate));
        col = addStat(col, "RNG", String.format(Lang.locale(), "%.1f", info.rng));
        if (info.pen > 0f) col = addStat(col, "PEN", String.valueOf((int) info.pen));
        col = addStat(col, "DPS", String.valueOf(Math.round(info.dps)));
        addStat(col, Lang.get("hud.kills_label"), String.valueOf(info.kills));
    }

    private int addStat(int col, String name, String value) {
        Label n = new Label(name, skin, "dim");
        n.setFontScale(com.pcdc.td.ui.Fonts.rel(0.75f));
        Label v = new Label(value, skin);
        v.setFontScale(com.pcdc.td.ui.Fonts.rel(0.9f));
        statsGrid.add(n).right().padRight(4);
        statsGrid.add(v).left().padRight(col % 3 == 2 ? 0 : 12);
        if (col % 3 == 2) statsGrid.row().padTop(1);
        return col + 1;
    }

    /** Chips de las recetas desbloqueadas: tocar uno selecciona al compañero. */
    private void rebuildFusionRow(PanelInfo info) {
        fusionRow.clear();
        boolean any = info != null && info.fusions.size > 0;
        fusionRow.setVisible(any);
        if (!any) return;
        Label head = new Label(Lang.get("panel.fusions"), skin, "dim");
        head.setFontScale(com.pcdc.td.ui.Fonts.rel(0.75f));
        fusionRow.add(head).left().padRight(5);
        // en móvil, tres fichas en fila estiraban el panel a media pantalla:
        // la cabecera va sola y las fichas se parten de dos en dos
        boolean wrap = UiScale.compact();
        if (wrap) fusionRow.row();
        int chipCol = 0;
        for (int i = 0; i < info.fusions.size; i++) {
            FusionOption op = info.fusions.get(i);
            Table chip = new Table();
            chip.setBackground(skin.getDrawable("panel"));
            chip.pad(2).padLeft(4).padRight(6);
            com.badlogic.gdx.graphics.g2d.TextureRegion pIcon = towerIcons.get(op.partnerId);
            if (pIcon != null) chip.add(new Image(iconDrawable(op.partnerId, pIcon))).size(20).padRight(3);
            Label name = new Label(op.resultName, skin);
            name.setFontScale(com.pcdc.td.ui.Fonts.rel(0.75f));
            chip.add(name);
            boolean selectable = towerButtons.containsKey(op.partnerId);
            if (selectable) {
                chip.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
                chip.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        // seleccionar al compañero en la barra: listo para fusionar
                        sounds.playSfx("click");
                        setSelectedBuild(op.partnerId);
                        listener.onBuildSelected(op.partnerId);
                    }
                });
            } else {
                chip.getColor().a = 0.45f; // el compañero no está en la barra
            }
            fusionRow.add(chip).left().padRight(4);
            if (wrap && ++chipCol % 2 == 0) fusionRow.row().padTop(2);
        }
    }

    /** Una fila (o dos) de botones de tanque para una pestaña. */
    private Table buildTowerRow(Array<TowerDef> defs) {
        Table bar = new Table();
        bar.setBackground(skin.getDrawable("panel"));
        bar.pad(6).defaults().pad(3);
        // más de 11 en una fila no caben ni en escritorio: se parten en dos
        int perRow = defs.size > 11 ? (defs.size + 1) / 2 : Integer.MAX_VALUE;
        int added = 0;
        for (TowerDef def : defs) {
            TextButton b = buildTowerButton(def);
            towerButtons.put(def.id, b);
            boolean compactBtn = towerIcons.get(def.id) != null;
            // algo más altos desde que llevan el nombre bajo el icono
            bar.add(b).width(compactBtn ? 62 : 86).height(compactBtn ? 58 : 48);
            if (++added % perRow == 0) bar.row();
        }
        return bar;
    }

    /**
     * Alto que ocupa la barra visible ahora mismo: los paneles de encima
     * (ficha, consejo, panel de tanque) se apoyan en él para no ser tapados.
     */
    private float barHeight() {
        int rows = fusionTabVisible ? barRowsFusion : barRowsBase;
        // botones de 58 de alto desde que muestran el nombre
        return (rows > 1 ? 172 : 100) + (barTabbed ? 28 : 0);
    }

    /** Reposiciona los paneles inferiores tras cambiar de pestaña. */
    private void refreshBottomPads() {
        float pad = barHeight() + UiScale.safeBottom();
        if (sellPanel != null) sellPanel.padBottom(pad);
        if (buildInfoPanel != null) buildInfoPanel.padBottom(pad);
        if (tipPanel != null) tipPanel.padBottom(pad + 6f);
    }

    /** Cambia de pestaña (base / fusiones) y deselecciona lo que había armado. */
    private void showFusionTab(boolean fusions) {
        if (towerBarFusion == null) return;
        fusionTabVisible = fusions;
        towerBarBase.setVisible(!fusions);
        towerBarFusion.setVisible(fusions);
        baseTab.setStyle(skin.get(fusions ? "default" : "primary", TextButton.TextButtonStyle.class));
        fusionTab.setStyle(skin.get(fusions ? "primary" : "default", TextButton.TextButtonStyle.class));
        baseTab.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
        fusionTab.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
        if (selectedBuildId != null) {
            TowerDef sel = catalogs.tower(selectedBuildId);
            if (sel != null && sel.fusionOnly != fusions) {
                setSelectedBuild(null);
                listener.onBuildSelected(null);
            }
        }
        refreshBottomPads();
    }

    /** El perfil tiene el nodo Logística: el panel enseña el botón Mover. */
    public void setRelocateUnlocked(boolean unlocked) {
        this.relocateUnlocked = unlocked;
    }

    /** Botón Deshacer: solo visible antes de la oleada y con algo que deshacer. */
    public void setUndoAvailable(boolean available) {
        undoButton.setVisible(available);
    }

    /** Iconito a la derecha del texto (mismo patrón que el botón de objetivo). */
    private void buttonIcon(TextButton b, String icon) {
        b.getLabelCell().padRight(4);
        Image img = new Image(skin.get(icon, com.badlogic.gdx.graphics.g2d.TextureRegion.class));
        img.setScaling(Scaling.fit);
        b.add(img).size(14);
    }

    /** Fija la velocidad mostrada (x1/x2/x3). */
    public void setSpeed(float mult) {
        speedMult = mult;
        speedButton.setText("x" + (int) mult);
    }

    public void update(GameWorld world) {
        // el traslado va en caliente: solo se apaga si el tanque ya está de viaje
        if (relocateUnlocked && sellPanel.isVisible() && panelTower != null) {
            moveButton.setDisabled(panelTower.driving());
        }
        if (world.gold != lastGold) {
            lastGold = world.gold;
            sb.setLength(0);
            sb.append(lastGold);
            goldLabel.setText(sb);
            refreshAffordability(world);
        }
        if (world.lives != lastLives) {
            lastLives = world.lives;
            sb.setLength(0);
            sb.append(lastLives);
            livesLabel.setText(sb);
        }
        int wave = world.waves.displayWave();
        if (wave != lastWave) {
            lastWave = wave;
            int total = world.waves.totalWaves();
            waveLabel.setText(total < 0
                ? Lang.fmt("hud.wave_endless", wave)
                : Lang.fmt("hud.wave", wave, total));
        }
        // habilidades: enfriamiento, cargas restantes y precio de recarga
        for (int i = 0; i < abilityButtons.length; i++) {
            com.badlogic.gdx.scenes.scene2d.ui.Button b = abilityButtons[i];
            if (b == null) continue;
            int cd = (int) Math.ceil(world.abilityCooldown[i]);
            int charges = world.abilityCharges[i];
            boolean canPay = world.gold >= GameWorld.ABILITY_RELOAD_COST[i];
            // el estado cabe en un entero: así solo se redibuja cuando cambia
            int state = cd * 100 + charges * 10 + (canPay ? 1 : 0);
            if (state != lastAbilityCd[i]) {
                lastAbilityCd[i] = state;
                if (charges <= 0) {
                    // sin munición: el botón pasa a ser de recarga
                    b.setDisabled(!canPay);
                    abilityLabels[i].setText(Lang.fmt("ability.reload",
                        GameWorld.ABILITY_RELOAD_COST[i]));
                } else if (cd > 0) {
                    b.setDisabled(true);
                    abilityLabels[i].setText(cd + "s");
                } else {
                    b.setDisabled(false);
                    abilityLabels[i].setText("x" + charges);
                }
            }
        }

        // misma fila: el progreso de la oleada en curso y la previa de la siguiente
        com.pcdc.td.data.LevelDef.WaveDef upcoming = world.waves.upcomingWave();
        boolean showProgress = world.waves.started
            && (world.waves.spawning || world.enemies.size > 0);
        // la racha entra en la clave: si se rompe a media oleada (fuga) el
        // rótulo tiene que desaparecer YA, no en la oleada siguiente
        int previewKey = (showProgress ? 1 : 0)
            + 2 * (upcoming == null ? 0 : (world.waves.started ? world.waves.waveIndex + 2 : 1))
            + 1000 * world.perfectStreak;
        if (previewKey != lastPreviewKey) {
            lastPreviewKey = previewKey;
            lastWaveProgress = -1;
            previewInner.clear();
            previewInner.setVisible(showProgress || upcoming != null || world.perfectStreak >= 2);
            if (world.perfectStreak >= 2) {
                Label streak = new Label(Lang.fmt("hud.streak", world.perfectStreak,
                    world.streakMult()), skin, "gold");
                streak.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
                previewInner.add(streak).padRight(10);
            }
            if (showProgress) {
                previewInner.add(waveProgressBar).width(120).height(9).padRight(6);
                previewInner.add(waveProgressLabel).padRight(upcoming != null ? 10 : 2);
            }
            if (upcoming != null) {
                Label arrow = new Label(">>", skin, "dim");
                previewInner.add(arrow).padRight(4);
                // evento raro de la oleada que viene: se anuncia por delante
                if (upcoming.event != null && !upcoming.event.isEmpty()) {
                    Label ev = new Label(Lang.get("event." + upcoming.event), skin, "gold");
                    ev.setFontScale(com.pcdc.td.ui.Fonts.rel(0.8f));
                    previewInner.add(ev).padRight(6);
                }
                for (com.pcdc.td.data.LevelDef.SpawnGroupDef g : upcoming.groups) {
                    // chip TOCABLE: abre la carta del enemigo que viene
                    final String enemyId = g.enemy;
                    Table chipGroup = new Table();
                    Image chip = new Image(skin.newDrawable("white", catalogs.enemy(g.enemy).color()));
                    chipGroup.add(chip).size(13);
                    chipGroup.add(new Label("x" + g.count, skin)).padLeft(2);
                    chipGroup.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
                    chipGroup.addListener(new ClickListener() {
                        @Override
                        public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event,
                                            float x, float y) {
                            listener.onPreviewEnemyTapped(enemyId);
                        }
                    });
                    previewInner.add(chipGroup).padRight(6);
                }
            }
        }
        if (showProgress) {
            int alive = world.enemies.size;
            int pending = world.waves.pendingSpawns();
            int key = alive * 1000 + pending;
            if (key != lastWaveProgress) {
                lastWaveProgress = key;
                int total = Math.max(1, world.waves.waveTotal());
                // los enemigos que se dividen o invocan pueden pasar del total:
                // el progreso se recorta para que la barra no retroceda de más
                waveProgressBar.setValue(MathUtils.clamp(1f - (alive + pending) / (float) total, 0f, 1f));
                waveProgressLabel.setText(alive + pending == 0
                    ? Lang.get("hud.wave_clear")
                    : Lang.fmt("hud.wave_progress", alive, pending));
            }
        }

        if (!world.waves.started) {
            if (lastCountdown != -99) {
                lastCountdown = -99;
                countdownLabel.setText("");
                callWaveButton.setText(Lang.get("hud.start"));
                callWaveButton.setVisible(true);
            }
        } else {
            int cd = world.waves.canCallEarly() ? (int) Math.ceil(world.waves.countdown) : -1;
            if (cd != lastCountdown) {
                lastCountdown = cd;
                countdownLabel.setText(cd > 0 ? Lang.fmt("hud.next", cd) : "");
                callWaveButton.setText(Lang.get("hud.callwave"));
                callWaveButton.setVisible(cd > 0);
            }
        }
    }

    private void refreshAffordability(GameWorld world) {
        for (TowerDef def : availableTowers) {
            TextButton b = towerButtons.get(def.id);
            // los únicos se agotan al colocarlos, aunque sobre el oro
            boolean available = world.gold >= def.cost
                && !(def.unique && world.uniquePlaced(def.id) != null);
            b.setDisabled(!available);
            b.getColor().a = available ? 1f : 0.55f;
        }
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    public void dispose() {
        stage.dispose();
    }
}
