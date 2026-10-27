package com.pcdc.td.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pcdc.td.TDGame;
import com.pcdc.td.data.Balance;
import com.pcdc.td.data.LevelDef;
import com.pcdc.td.data.TowerDef;
import com.pcdc.td.input.CameraController;
import com.pcdc.td.input.CellPicker;
import com.pcdc.td.render.HealthBarRenderer;
import com.pcdc.td.render.ModelFactory;
import com.pcdc.td.render.WorldRenderer;
import com.pcdc.td.ui.GameplayHud;
import com.pcdc.td.world.Enemy;
import com.pcdc.td.world.Fx;
import com.pcdc.td.world.GameWorld;
import com.pcdc.td.world.Projectile;
import com.pcdc.td.world.Tower;

/** Pantalla del nivel: simulación con timestep fijo + render + HUD. */
public class GameplayScreen extends ScreenAdapter
    implements CameraController.TapListener, GameplayHud.Listener {

    /** Con victoria hay desfile: torretas girando y salvas al aire. */
    private static final float END_DELAY = 2.2f;

    private final TDGame game;
    private final LevelDef level;
    private final GameWorld world;

    private final ModelFactory modelFactory = new ModelFactory();
    private final WorldRenderer renderer = new WorldRenderer();
    private final HealthBarRenderer healthBars = new HealthBarRenderer();
    private final com.pcdc.td.render.FloatingTextRenderer floatingText = new com.pcdc.td.render.FloatingTextRenderer();
    private final CellPicker picker = new CellPicker();
    private final CameraController cameraController;
    private final GameplayHud hud;

    private final ModelInstance boardInstance;
    private final ModelInstance highlight;
    private final ColorAttribute highlightColor;
    private boolean highlightVisible;
    private final ModelInstance rangeDisc;
    private final ColorAttribute rangeDiscColor;
    private final ObjectMap<String, ModelInstance> ghosts = new ObjectMap<>();

    private final Array<ModelInstance> renderList = new Array<>(false, 256);
    private final Array<ModelInstance> beamPool = new Array<>();
    private int beamsUsed;

    private final GridPoint2 pickedCell = new GridPoint2();
    private final GridPoint2 hoverCell = new GridPoint2(-1, -1);
    private boolean hoverValid;
    private Tower selectedPlaced;

    private float accumulator;
    private float speedMultiplier = 1f;
    /** Último tanque colocado (no fusión): candidato del botón Deshacer. */
    private Tower lastPlacedTower;
    private float endTimer = -1f;
    private boolean paused;

    // overlays de celdas especiales del bioma
    private final Array<ModelInstance> staticOverlays = new Array<>();
    private final Array<ModelInstance> lavaOverlays = new Array<>();
    private int lastLavaVersion = -1;

    // tutorial
    private int tutorialIndex = -1;
    private int lastClearedWave = -1;
    /** Autocierre del último mensaje del tutorial (se quedaba abierto). */
    private float tutorialAutoClose = -1f;

    private final Vector3 tmpV = new Vector3();
    private final Vector3 tmpV2 = new Vector3();
    /** Fondo a juego con el terreno exterior del bioma. */
    private final Color clearColor = new Color();

    // VFX
    private static final Color POISON_TINT = Color.valueOf("35e02a");
    private static final Color ELITE_TINT = Color.valueOf("ffd23c");
    private static final Color FIRE_START = Color.valueOf("ffd54a");
    private static final Color FIRE_END = Color.valueOf("e04a20");
    private static final Color FROST_START = Color.valueOf("f0ffff");
    private static final Color FROST_END = Color.valueOf("58a8f0");
    private static final Color EXPLO_CORE = Color.valueOf("fff2b0");
    private static final Color DUST_START = Color.valueOf("cdc2ab");
    private static final Color DUST_END = Color.valueOf("8a8071");
    private static final Color WRECK_TINT = Color.valueOf("4a4038");
    private static final Color SCORCH_TINT = Color.valueOf("241a14");
    private static final Color TRACKS_TINT = Color.valueOf("2c2418");
    private static final Color RADAR_TINT = Color.valueOf("58c9a4");
    private static final Color OVERLORD_TINT = Color.valueOf("4a0f5e");
    private static final Color MIST_TINT = Color.valueOf("b8d0b0");
    private static final Color SPLASH_TINT = Color.valueOf("bcd6f0");
    // auras de las unidades insignia (savia del bosque; la bruma reusa MIST_TINT)
    private static final Color WARD_TINT = Color.valueOf("8ae0a0");
    /** Aviso del golpe cargado del jefe. */
    private static final Color CAST_TINT = Color.valueOf("ff7a4a");
    private static final Color AURA_TINT = Color.valueOf("ffdc64");
    /** Esquirlas que orbitan cada tanque potenciado (5 llena el giro sin saturar). */
    private static final int AURA_MOTES = 5;
    private float stateTime;
    private float lastDelta;
    private final Color tmpC = new Color();
    private final Color tmpWeather = new Color();

    /** Partícula de llama/escarcha del lanzallamas o polvo de las orugas. */
    private static class Puff {
        static final int FROST = 0, FIRE = 1, DUST = 2, SPLASH = 3, POISON = 4;
        float x, y, z, vx, vy, vz, life, maxLife, size;
        int kind;
    }

    private final Array<Puff> puffs = new Array<>(false, 128);
    private final com.badlogic.gdx.utils.Pool<Puff> puffPool = new com.badlogic.gdx.utils.Pool<Puff>() {
        @Override
        protected Puff newObject() {
            return new Puff();
        }
    };

    /** Partícula ambiental del bioma (nieve, luciérnagas, ascuas, lluvia...). */
    private static class Amb {
        float x, y, z, vx, vy, vz, life, maxLife, size, phase;
        /** Jirón de bruma rasante (pantano): plano, grande y casi transparente. */
        boolean mist;
    }

    private final Array<Amb> ambient = new Array<>(false, 32);
    private final com.badlogic.gdx.utils.Pool<Amb> ambientPool = new com.badlogic.gdx.utils.Pool<Amb>() {
        @Override
        protected Amb newObject() {
            return new Amb();
        }
    };
    private final Color ambColor = new Color();

    private final boolean plusMode;
    /** Torres construibles en la barra (para los atajos de teclado 1-8). */
    private Array<TowerDef> availableTowers;
    /** Refresco periódico del panel de torre (DPS en vivo). */
    private float panelRefreshTimer;
    /** Ciclo día/noche: última vez que se recalculó la luz. */
    private float lightUpdateTimer;
    private float simTime;
    private final Color dayAmbient = new Color();
    private final Color daySun = new Color();
    private boolean aimingMeteor;
    /** Tanque en proceso de reubicación (nodo Logística); el próximo toque lo mueve. */
    private Tower movingTower;
    private boolean bossSeen;
    /** Ya se comprobó/encoló el consejo de armadura en esta partida. */
    private boolean armorTipShown;
    private boolean eliteTipShown;
    private com.badlogic.gdx.graphics.profiling.GLProfiler profiler;
    private int lastCheckpointWave = -1;
    private int prevLives = Integer.MIN_VALUE;
    private final com.pcdc.td.render.TowerIconRenderer iconRenderer =
        new com.pcdc.td.render.TowerIconRenderer(96);
    private final Array<ModelInstance> gateGlows = new Array<>();
    /** Fortalezas de meta: se deterioran conforme pierdes vidas. */
    private final Array<ModelInstance> goalGates = new Array<>();
    /** Transformación y grado de daño actual de cada fortaleza. */
    private final Array<com.badlogic.gdx.math.Matrix4> goalGateTransforms = new Array<>();
    private int gateDamageTier;
    private int gateDamageLives = Integer.MIN_VALUE;
    /** Escombros que tapian las rutas todavía cerradas. */
    private final Array<ModelInstance> sealedRubble = new Array<>();
    private final Array<Vector3> sealedRubblePos = new Array<>();
    /** Segundos que el aviso de ruta abierta tiene prioridad sobre el del bioma. */
    private float pathNoticeTimer;

    /** Colocación inicial de la última partida jugada (para Reintentar). */
    private static com.pcdc.td.meta.Checkpoint retryLoadout;
    private static boolean retryLoadoutSurvival;
    private static boolean retryRequested;
    private boolean retryCaptured;

    /** El siguiente GameplayScreen restaurará la colocación inicial si coincide. */
    public static void requestRetryLoadout() {
        retryRequested = true;
    }

    public GameplayScreen(TDGame game, int levelId) {
        this(game, levelId, false, false, null);
    }

    public GameplayScreen(TDGame game, int levelId, boolean survival) {
        this(game, levelId, survival, false, null);
    }

    /** Reanuda una partida guardada. */
    public GameplayScreen(TDGame game, com.pcdc.td.meta.Checkpoint cp) {
        this(game, cp.levelId, cp.survival, cp.plus, cp);
    }

    public GameplayScreen(TDGame game, int levelId, boolean survival, boolean plus,
                          com.pcdc.td.meta.Checkpoint resume) {
        this.game = game;
        this.plusMode = plus;
        this.level = game.catalogs.loadLevel(levelId);
        if (survival) this.level.startGold = Math.round(this.level.startGold * 1.2f);
        this.world = new GameWorld(level, game.catalogs, game.stats, survival, plus ? 1.3f : 1f);

        if (resume != null) {
            world.gold = resume.gold;
            world.lives = resume.lives;
            for (com.pcdc.td.meta.Checkpoint.TowerState ts : resume.towers) {
                world.restoreTower(ts.x, ts.z, game.catalogs.tower(ts.def), ts.invested,
                    ts.targeting, ts.level);
            }
            world.boonsTaken.addAll(resume.boons);
            world.runDamageMult = resume.runDamageMult;
            world.runRangeMult = resume.runRangeMult;
            world.runGoldMult = resume.runGoldMult;
            world.runEffectMult = resume.runEffectMult;
            world.runPen = resume.runPen;
            world.runUpgradeDiscount = resume.runUpgradeDiscount;
            world.runEnemyArmorBonus = resume.runEnemyArmorBonus;
            world.runEnemySpeedMult = resume.runEnemySpeedMult;
            nextBoonWave = Math.max(10, resume.nextBoonWave);
            world.waves.resumeAt(resume.waveIndex);
            lastCheckpointWave = resume.waveIndex - 1;
        }

        // REINTENTAR: recupera la colocación (y el oro) con la que empezó la
        // partida anterior — ensayo-error sin volver a colocarlo todo. Queda
        // en estado pre-oleada: se puede retocar (vender pre-oleada = 100%).
        if (resume == null && retryRequested) {
            retryRequested = false;
            if (retryLoadout != null && retryLoadout.levelId == levelId
                && retryLoadout.plus == plus && retryLoadoutSurvival == survival
                && level.tutorial.size == 0) {
                world.gold = retryLoadout.gold;
                for (com.pcdc.td.meta.Checkpoint.TowerState ts : retryLoadout.towers) {
                    world.restoreTower(ts.x, ts.z, game.catalogs.tower(ts.def), ts.invested,
                        ts.targeting, ts.level);
                }
            }
        }

        boardInstance = new ModelInstance(modelFactory.buildBoard(world.map, world.biome, 5000L + levelId));
        clearColor.set(world.biome.groundA()).mul(0.55f, 0.55f, 0.6f, 1f);
        renderer.setLighting(world.biome.ambientLight(), world.biome.sunLight());
        dayAmbient.set(world.biome.ambientLight());
        daySun.set(world.biome.sunLight());
        // HORA DEL DÍA PROPIA DE CADA NIVEL: uno amanece, otro es mediodía y
        // otro atardece, con el sol cruzando el cielo. Sin esto los cinco
        // niveles de un bioma tenían la luz y las sombras idénticas.
        dayPhase = ((levelId * 37) % 100) / 100f;
        float sunAngle = MathUtils.PI * (0.15f + 0.7f * dayPhase);
        renderer.setSunDirection(MathUtils.cos(sunAngle) * 0.6f,
            -0.45f - 0.5f * MathUtils.sin(sunAngle), -0.3f);
        highlight = new ModelInstance(modelFactory.cellHighlight());
        highlightColor = (ColorAttribute) highlight.materials.first().get(ColorAttribute.Diffuse);
        rangeDisc = new ModelInstance(modelFactory.rangeDisc());
        rangeDiscColor = (ColorAttribute) rangeDisc.materials.first().get(ColorAttribute.Diffuse);

        cameraController = new CameraController(renderer.camera, world.map.width, world.map.height);
        cameraController.setTapListener(this);

        Array<TowerDef> available = new Array<>();
        if (!survival && level.allowedTowers.size > 0) {
            // lista explícita (tutoriales): manda sobre los desbloqueos del árbol
            for (String id : level.allowedTowers) {
                TowerDef def = game.catalogs.tower(id);
                if (!def.fusionOnly && !def.unique) available.add(def);
            }
        } else {
            // sin lista: todos los tanques base desbloqueados en el árbol
            for (TowerDef def : game.catalogs.towers) {
                if (!def.fusionOnly && !def.unique && game.stats.towerAvailable(def.id)) {
                    available.add(def);
                }
            }
        }
        // los tanques únicos (mando) solo si se desbloqueó su receta en el árbol
        for (TowerDef def : game.catalogs.towers) {
            if (def.unique && game.stats.unlockedTowers.contains(def.id)) available.add(def);
        }
        // Fabricación avanzada: las fusiones desbloqueadas se compran directamente
        if (game.stats.directFusion) {
            for (TowerDef def : game.catalogs.towers) {
                if (def.fusionOnly && game.stats.unlockedFusions.contains(def.id)) {
                    available.add(def);
                }
            }
        }
        this.availableTowers = available;
        hud = new GameplayHud(game.skin, available, renderTowerIcons(available),
            game.stats.unlockedAbilities, game.sounds, game.catalogs, this);
        hud.setRelocateUnlocked(game.stats.canRelocate);
        speedMultiplier = Gdx.app.getPreferences("td-settings").getFloat("gameSpeed", 1f);
        hud.setSpeed(speedMultiplier);
        buildStaticOverlays();
        buildGates();
        // al reanudar una partida guardada no se repite el tutorial: el jugador
        // ya iba por la mitad del nivel
        if (level.tutorial.size > 0 && resume == null) {
            tutorialIndex = 0;
            applyTutorialStep();
        }
        // los retos y el bonus por oleada limpia se explican en la primera partida
        if (!world.survival) queueTip("challenges");
        if (world.mechanics.hasPortals) queueTip("portals");
        // la barra con pestañas solo aparece con fusiones desbloqueadas
        for (TowerDef def : availableTowers) {
            if (def.fusionOnly) {
                queueTip("bar_tabs");
                break;
            }
        }
        // aviso RECURRENTE (no pasa por seenTips): vienen voladores y no hay
        // ningún antiaéreo desbloqueado — sin esto el nivel es una encerrona
        if (lacksAntiAir()) tipQueue.add("tip.no_antiair");

        // prueba automática: -Dtd.autofuse=true construye slow y fusiona poison encima
        if (Boolean.getBoolean("td.autofuse")) {
            world.gold += 1000;
            outer:
            for (int z = 0; z < world.map.height; z++) {
                for (int x = 0; x < world.map.width; x++) {
                    if (world.canBuildAt(x, z) && nearPath(x, z)) {
                        boolean a = world.tryPlace(x, z, game.catalogs.tower("slow"));
                        boolean b = world.tryPlace(x, z, game.catalogs.tower("poison"));
                        Gdx.app.log("Debug", "autofuse " + a + "/" + b + " -> "
                            + world.towerAt(x, z).def.id + " invested=" + world.towerAt(x, z).invested);
                        break outer;
                    }
                }
            }
        }

        // prueba automática: -Dtd.autotowers=true coloca torres variadas al arrancar
        // (-Dtd.tower=<id> fuerza un único tipo)
        if (Boolean.getBoolean("td.autotowers") && available.size > 0) {
            world.gold += 2000;
            String forcedId = System.getProperty("td.tower");
            TowerDef forced = forcedId != null ? game.catalogs.tower(forcedId) : null;
            int placed = 0;
            outer:
            for (int z = 0; z < world.map.height; z++) {
                for (int x = 0; x < world.map.width; x++) {
                    if (placed >= 8) break outer;
                    if (world.canBuildAt(x, z) && nearPath(x, z)) {
                        TowerDef def = forced != null ? forced : available.get(placed % available.size);
                        if (world.tryPlace(x, z, def)) placed++;
                    }
                }
            }
        }
    }

    /** true si tiene sentido guardar el estado actual (nivel normal en curso). */
    private boolean canCheckpoint() {
        // el sin fin también se guarda: con las cartas acumuladas, perder una
        // partida larga por cerrar la app era un castigo desproporcionado
        return world.waves.started && world.state == GameWorld.State.RUNNING;
    }

    /** Estado actual (torres, oro sin lo ganado en la oleada, vidas). */
    private com.pcdc.td.meta.Checkpoint snapshotState() {
        com.pcdc.td.meta.Checkpoint cp = new com.pcdc.td.meta.Checkpoint();
        cp.levelId = level.id;
        cp.plus = plusMode;
        cp.waveIndex = world.waves.waveIndex;
        // al reanudar se relanza la oleada entera, así que el oro de las bajas
        // que ya habías hecho en ella no cuenta (si no, cobrarías dos veces)
        cp.gold = Math.max(0, world.gold - world.goldEarnedThisWave);
        cp.lives = world.lives;
        cp.survival = world.survival;
        cp.nextBoonWave = nextBoonWave;
        cp.boons.addAll(world.boonsTaken);
        cp.runDamageMult = world.runDamageMult;
        cp.runRangeMult = world.runRangeMult;
        cp.runGoldMult = world.runGoldMult;
        cp.runEffectMult = world.runEffectMult;
        cp.runPen = world.runPen;
        cp.runUpgradeDiscount = world.runUpgradeDiscount;
        cp.runEnemyArmorBonus = world.runEnemyArmorBonus;
        cp.runEnemySpeedMult = world.runEnemySpeedMult;
        for (int i = 0; i < world.towers.size; i++) {
            Tower t = world.towers.get(i);
            com.pcdc.td.meta.Checkpoint.TowerState ts = new com.pcdc.td.meta.Checkpoint.TowerState();
            ts.x = t.cellX;
            ts.z = t.cellZ;
            ts.def = t.def.id;
            ts.invested = t.invested;
            ts.targeting = t.targeting;
            ts.level = t.level;
            cp.towers.add(ts);
        }
        return cp;
    }

    /** Guarda el estado actual para poder reanudar la partida. */
    private void saveCheckpoint() {
        game.saveManager.saveCheckpoint(snapshotState());
    }

    /** Hitos del sin fin y su pago base (se multiplica por el tier del bioma). */
    private static final int[] MILESTONE_WAVES = {15, 25, 40};
    private static final int[] MILESTONE_PAY = {40, 90, 200};

    /** Recompensas, estadísticas, logros y salto a resultados. */
    private void finishLevel() {
        boolean victory = world.state == GameWorld.State.VICTORY;
        com.pcdc.td.meta.LevelSummary s = new com.pcdc.td.meta.LevelSummary();
        s.victory = victory;
        s.survival = world.survival;
        s.boons.addAll(world.boonsTaken);
        s.plus = plusMode;
        s.kills = world.totalKills;

        // diagnóstico de derrota: quién se coló más veces
        // (waveReached solo se rellenaba en sin fin, así que el diagnóstico
        // decía "caíste en la oleada 0")
        s.waveReached = world.waves.displayWave();
        s.towersAtEnd = world.towers.size;
        for (com.badlogic.gdx.utils.ObjectIntMap.Entry<String> e : world.leaksByType) {
            s.leakTotal += e.value;
            if (e.value > s.leakTopCount) {
                s.leakTopCount = e.value;
                s.leakTopEnemy = e.key;
            }
        }

        Tower mvp = null;
        for (int i = 0; i < world.towers.size; i++) {
            Tower t = world.towers.get(i);
            if (mvp == null || t.damageDealt > mvp.damageDealt) mvp = t;
        }
        if (mvp != null && mvp.damageDealt > 0f) {
            s.mvpName = com.pcdc.td.ui.Lang.name("tower", mvp.def.id, mvp.def.name);
        }

        // daño agregado por tipo de torre (para las barras de resultados)
        ObjectMap<String, com.pcdc.td.meta.LevelSummary.TowerDamage> dmgByType = new ObjectMap<>();
        for (int i = 0; i < world.towers.size; i++) {
            Tower t = world.towers.get(i);
            if (t.damageDealt <= 0f) continue;
            com.pcdc.td.meta.LevelSummary.TowerDamage td = dmgByType.get(t.def.id);
            if (td == null) {
                td = new com.pcdc.td.meta.LevelSummary.TowerDamage();
                td.name = com.pcdc.td.ui.Lang.name("tower", t.def.id, t.def.name);
                dmgByType.put(t.def.id, td);
            }
            td.damage += t.damageDealt;
            // histórico del perfil: alimenta "Tu arsenal" en Estadísticas
            game.profile.addStat("towerdmg." + t.def.id, Math.round(t.damageDealt));
            game.profile.addStat("towerkills." + t.def.id, t.kills);
        }
        for (com.pcdc.td.meta.LevelSummary.TowerDamage td : dmgByType.values()) {
            s.towerDamage.add(td);
        }
        s.towerDamage.sort((a, b) -> Float.compare(b.damage, a.damage));
        s.towerDamage.truncate(6);

        if (world.survival) {
            s.waveReached = world.waves.displayWave();
            // pago por oleada según lo duro que sea el bioma: con una fórmula
            // cuadrática plana salía a cuenta farmear la pradera eternamente
            int tier = (level.id - 1) / 5;
            s.crystals = s.waveReached * (2 + tier);
            game.profile.metaCurrency += s.crystals;
            int prev = game.profile.survivalBest.get(world.biome.id, 0);
            s.bestWave = Math.max(prev, s.waveReached);
            game.profile.survivalBest.put(world.biome.id, s.bestWave);
            game.profile.maxStat("bestWave", s.waveReached);
            // HITOS: pago único por llegar lejos en cada bioma. El sin fin
            // pagaba solo por oleada, así que no había NADA que perseguir
            for (int i = 0; i < MILESTONE_WAVES.length; i++) {
                int wave = MILESTONE_WAVES[i];
                if (s.waveReached < wave) break;
                String key = world.biome.id + ":" + wave;
                if (game.profile.survivalMilestones.contains(key, false)) continue;
                game.profile.survivalMilestones.add(key);
                int pay = MILESTONE_PAY[i] * (1 + tier);
                game.profile.metaCurrency += pay;
                s.crystals += pay;
                s.milestones.add(new int[]{wave, pay});
            }
        } else if (victory) {
            s.stars = Balance.stars(world.lives, world.startLives);
            int baseReward = Math.round(level.rewards.metaCurrency * (plusMode ? 1.3f : 1f));
            com.pcdc.td.meta.PlayerProfile.LevelReward earned = game.profile.completeLevel(
                level.id, s.stars, baseReward,
                level.rewards.firstClearBonus, game.stats.metaIncomeFlat);
            s.crystals = earned.crystals;
            s.medals = earned.medals;

            // retos: los que se cumplen por primera vez pagan cristales
            s.challengesDone = com.pcdc.td.meta.Challenges.evaluate(world);
            int already = game.profile.challengesFor(level.id);
            s.challengesNew = s.challengesDone & ~already;
            if (s.challengesNew != 0) {
                int bonus = com.pcdc.td.meta.Challenges.count(s.challengesNew)
                    * com.pcdc.td.meta.Challenges.REWARD;
                s.crystals += bonus;
                game.profile.metaCurrency += bonus;
                game.profile.challenges.put(String.valueOf(level.id), already | s.challengesDone);
            }
        }

        // primera victoria sobre el Corruptor: campaña completada + trofeo
        boolean testEnd = Boolean.getBoolean("td.campaignend"); // solo pruebas
        if ((victory && !world.survival && level.id == 50
                && game.profile.stat("campaignDone") == 0) || testEnd) {
            s.campaignComplete = true;
            game.profile.addStat("campaignDone", 1);
            if (!game.profile.skins.contains("victor", false)) {
                game.profile.skins.add("victor"); // la pintura Vencedora
            }
        }

        // cristales de élites dorados: se abonan siempre (también al perder)
        if (world.crystalsEarned > 0) {
            s.crystals += world.crystalsEarned;
            game.profile.metaCurrency += world.crystalsEarned;
        }

        game.profile.addStat("kills", world.totalKills);
        game.profile.addStat("fusions", world.fusionsMade);
        game.profile.addStat("sold", world.towersSold);
        game.profile.addStat("elites", world.elitesKilled);
        game.profile.addStat("champions", world.championsKilled);
        game.profile.addStat("synergies", world.synergiesTriggered);
        if (victory && !world.survival) {
            game.profile.addStat("wins", 1);
            if (s.stars == 3) game.profile.addStat("perfects", 1);
        }
        for (com.pcdc.td.meta.Achievements.Def d : com.pcdc.td.meta.Achievements.claim(game.profile)) {
            s.achievements.add(com.pcdc.td.ui.Lang.fmt("results.achievement",
                com.pcdc.td.ui.Lang.get("ach." + d.id), d.reward));
        }

        game.saveManager.clearCheckpoint();
        game.saveProfile();
        game.setScreen(new ResultsScreen(game, level, s));
        dispose();
    }

    /**
     * Renderiza cada torre a una textura. Se renderizan TODAS (no solo la
     * barra): el panel de tanque y sus chips de fusión necesitan el icono de
     * cualquier def, incluidas fusiones que no están en la barra.
     */
    private ObjectMap<String, com.badlogic.gdx.graphics.g2d.TextureRegion> renderTowerIcons(Array<TowerDef> defs) {
        ObjectMap<String, com.badlogic.gdx.graphics.g2d.TextureRegion> icons = new ObjectMap<>();
        for (TowerDef def : game.catalogs.towers) {
            icons.put(def.id, iconRenderer.render(modelFactory, def, 1));
        }
        return icons;
    }

    /** Portales de entrada (spawn) y fortalezas de meta en cada camino. */
    private void buildGates() {
        for (int pathNo = 0; pathNo < world.map.worldPaths.size; pathNo++) {
            com.badlogic.gdx.utils.Array<Vector3> path = world.map.worldPaths.get(pathNo);
            if (path.size < 3) continue;
            Vector3 spawnPt = path.get(1);
            Vector3 dirIn = new Vector3(spawnPt).sub(path.first()).nor();
            ModelInstance gate = new ModelInstance(modelFactory.spawnGate());
            gate.transform.setToTranslation(spawnPt.x, 0.05f, spawnPt.z)
                .rotate(Vector3.Y, MathUtils.atan2Deg360(dirIn.x, dirIn.z));
            staticOverlays.add(gate);
            gateGlows.add(gate);

            // las rutas aún selladas se tapian con escombros hasta que se abren
            if (pathNo >= world.openPaths) {
                ModelInstance rubble = new ModelInstance(modelFactory.rubble(world.biome));
                rubble.transform.setToTranslation(spawnPt.x, 0.05f, spawnPt.z)
                    .rotate(Vector3.Y, MathUtils.atan2Deg360(dirIn.x, dirIn.z));
                sealedRubble.add(rubble);
                sealedRubblePos.add(new Vector3(spawnPt.x, 0.6f, spawnPt.z));
            }

            Vector3 goalPt = path.get(path.size - 2);
            Vector3 dirOut = new Vector3(path.peek()).sub(goalPt).nor();
            ModelInstance goal = new ModelInstance(
                modelFactory.goalGate(world.biome, biomeStarTier()));
            goal.transform.setToTranslation(goalPt.x, 0.05f, goalPt.z)
                .rotate(Vector3.Y, MathUtils.atan2Deg360(dirOut.x, dirOut.z));
            staticOverlays.add(goal);
            goalGates.add(goal);
            goalGateTransforms.add(new com.badlogic.gdx.math.Matrix4(goal.transform));
            // color original de cada material, para teñir sin ir acumulando
            for (int m = 0; m < goal.materials.size; m++) {
                ColorAttribute attr =
                    (ColorAttribute) goal.materials.get(m).get(ColorAttribute.Diffuse);
                baseGateColors.add(attr == null ? null : new Color(attr.color));
            }
        }
    }

    /**
     * 0..3 según las estrellas acumuladas en los 5 niveles del bioma actual:
     * la fortaleza luce los trofeos (banderines, filete, remate).
     */
    private int biomeStarTier() {
        int first = (level.id - 1) / 5 * 5 + 1;
        int stars = 0;
        for (int id = first; id < first + 5; id++) {
            stars += game.profile.starsFor(id);
        }
        if (stars >= 15) return 3;
        if (stars >= 10) return 2;
        if (stars >= 5) return 1;
        return 0;
    }

    /** Overlays fijos de bioma: arbustos, hielo, niebla y cristales. */
    private void buildStaticOverlays() {
        addCellOverlays(world.mechanics.bushCells, Color.valueOf("2a7a2a"), 0.7f, 0.25f);
        addCellOverlays(world.mechanics.fogCells, Color.valueOf("aaaaaa"), 0.22f, 1.1f);
        // el hielo lleva modelo propio: un tinte azulado sobre nieve blanca no
        // se distinguía del suelo y el jugador no sabía dónde perdía cadencia
        com.badlogic.gdx.utils.IntSet.IntSetIterator ice = world.mechanics.iceCells.iterator();
        while (ice.hasNext) {
            int idx = ice.next();
            ModelInstance plate = new ModelInstance(modelFactory.icePatch());
            plate.transform.setToTranslation(
                idx % world.map.width + 0.5f, 0.1f, idx / world.map.width + 0.5f);
            staticOverlays.add(plate);
        }
        com.badlogic.gdx.utils.IntSet.IntSetIterator it = world.mechanics.crystalCells.iterator();
        while (it.hasNext) {
            int idx = it.next();
            int x = idx % world.map.width, z = idx / world.map.width;
            ModelInstance crystal = new ModelInstance(modelFactory.crystal());
            crystal.transform.setToTranslation(x + 0.5f, 0.45f, z + 0.5f);
            staticOverlays.add(crystal);
        }
    }

    private void addCellOverlays(com.badlogic.gdx.utils.IntSet cells, Color color, float alpha, float y) {
        com.badlogic.gdx.utils.IntSet.IntSetIterator it = cells.iterator();
        while (it.hasNext) {
            int idx = it.next();
            int x = idx % world.map.width, z = idx / world.map.width;
            ModelInstance inst = new ModelInstance(modelFactory.cellHighlight());
            tintInstance(inst, color, alpha);
            inst.transform.setToTranslation(x + 0.5f, y, z + 0.5f);
            staticOverlays.add(inst);
        }
    }

    private void refreshLavaOverlays() {
        if (world.mechanics.lavaVersion == lastLavaVersion) return;
        lastLavaVersion = world.mechanics.lavaVersion;
        lavaOverlays.clear();
        com.badlogic.gdx.utils.IntSet.IntSetIterator it = world.mechanics.lavaCells.iterator();
        while (it.hasNext) {
            int idx = it.next();
            int x = idx % world.map.width, z = idx / world.map.width;
            ModelInstance inst = new ModelInstance(modelFactory.cellHighlight());
            tintInstance(inst, Color.valueOf("ff5a2a"), 0.6f);
            inst.transform.setToTranslation(x + 0.5f, 0.09f, z + 0.5f);
            lavaOverlays.add(inst);
        }
    }

    // ---- consejos contextuales ----

    /** Consejos pendientes de mostrar y tiempo que le queda al que está en pantalla. */
    private final Array<String> tipQueue = new Array<>();
    private float tipTimer;
    private static final float TIP_SECONDS = 7f;

    /**
     * Encola la explicación de una mecánica. Solo pasa la primera vez en toda la
     * vida del perfil: a partir de ahí la mecánica ya se conoce.
     */
    private void queueTip(String id) {
        if (!game.profile.markTipSeen(id)) return;
        tipQueue.add("tip." + id);
        game.saveProfile();
    }

    /** Tras colocar con éxito: el tanque de mando estrena su explicación. */
    private void afterPlace(TowerDef def) {
        queueTip("airdrop");
        if ("command".equals(def.id)) queueTip("command_aura");
        else if ("radar".equals(def.id)) queueTip("radar");
    }

    /** ¿El nivel trae voladores y el arsenal disponible no tiene antiaéreos? */
    private boolean lacksAntiAir() {
        boolean flyers = false;
        for (com.pcdc.td.data.LevelDef.WaveDef w : level.waves) {
            for (com.pcdc.td.data.LevelDef.SpawnGroupDef g : w.groups) {
                if (game.catalogs.enemy(g.enemy).flying) {
                    flyers = true;
                    break;
                }
            }
        }
        if (!flyers) return false;
        for (TowerDef def : game.catalogs.towers) {
            if (def.fusionOnly || !def.hitsAir) continue;
            if (isTowerAllowed(def.id)) return false;
        }
        return true;
    }

    private void updateTips(float delta) {
        if (paused) return;
        if (tipTimer > 0f) {
            tipTimer -= delta;
            if (tipTimer <= 0f) hud.setTip(null);
            return;
        }
        // los consejos esperan a que el tutorial del nivel termine: dos paneles
        // de texto a la vez es ruido
        if (tipQueue.size == 0 || tutorialIndex >= 0) return;
        hud.setTip(com.pcdc.td.ui.Lang.get(tipQueue.removeIndex(0)));
        tipTimer = TIP_SECONDS;
    }

    // ---- tutorial ----

    private void applyTutorialStep() {
        if (tutorialIndex < 0) return;
        if (tutorialIndex >= level.tutorial.size) {
            hud.setTutorial(null);
            tutorialIndex = -1;
            tutorialAutoClose = -1f;
            return;
        }
        LevelDef.TutorialStepDef step = level.tutorial.get(tutorialIndex);
        boolean en = "en".equals(com.pcdc.td.ui.Lang.code());
        hud.setTutorial(en && step.messageEn != null ? step.messageEn : step.message);
        // el último mensaje se cierra solo pasados unos segundos
        tutorialAutoClose = tutorialIndex == level.tutorial.size - 1 ? 10f : -1f;
    }

    private void tutorialEvent(String type) {
        if (tutorialIndex < 0 || tutorialIndex >= level.tutorial.size) return;
        if (level.tutorial.get(tutorialIndex).until.equals(type)) {
            tutorialIndex++;
            applyTutorialStep();
        }
    }

    private boolean nearPath(int x, int z) {
        return world.map.inBounds(x + 1, z) && world.map.get(x + 1, z) == com.pcdc.td.world.CellType.PATH
            || world.map.inBounds(x - 1, z) && world.map.get(x - 1, z) == com.pcdc.td.world.CellType.PATH
            || world.map.inBounds(x, z + 1) && world.map.get(x, z + 1) == com.pcdc.td.world.CellType.PATH
            || world.map.inBounds(x, z - 1) && world.map.get(x, z - 1) == com.pcdc.td.world.CellType.PATH;
    }

    @Override
    public void show() {
        game.sounds.playMusic(world.biome.id);
        // el tema de jefe se deja sintetizado ya: hacerlo al aparecer el jefe
        // metía un tirón justo en el peor momento
        game.sounds.prepareMusic(com.pcdc.td.audio.MusicFactory.bossTheme(world.biome.id));

        // barrido inicial: del portal a la fortaleza (se salta tocando)
        if (world.waves.waveIndex < 0 && world.map.worldPaths.size > 0) {
            com.badlogic.gdx.utils.Array<Vector3> path = world.map.worldPaths.first();
            if (path.size >= 3) {
                cameraController.cinematic(path.get(1), path.get(path.size - 2), 2.4f);
            }
        }

        // -Dtd.profile=true: log de draw calls para la pasada de rendimiento
        if (Boolean.getBoolean("td.profile") && profiler == null) {
            profiler = new com.badlogic.gdx.graphics.profiling.GLProfiler(Gdx.graphics);
            profiler.enable();
        }

        InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(new com.badlogic.gdx.InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == com.badlogic.gdx.Input.Keys.BACK
                    || keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                    // atrás/ESC alterna la pausa; se sale desde el panel
                    hud.setPaused(!hud.isPaused());
                    return true;
                }
                if (hud.isPaused() || world.state != GameWorld.State.RUNNING) return false;
                // atajos de teclado (desktop): 1-8 torres, espacio oleada, R girar,
                // T targeting, V vender
                if (keycode >= com.badlogic.gdx.Input.Keys.NUM_1
                    && keycode <= com.badlogic.gdx.Input.Keys.NUM_8) {
                    int idx = keycode - com.badlogic.gdx.Input.Keys.NUM_1;
                    if (idx < availableTowers.size) {
                        String id = availableTowers.get(idx).id;
                        hud.setSelectedBuild(id.equals(hud.selectedBuild()) ? null : id);
                        onBuildSelected(hud.selectedBuild());
                    }
                    return true;
                }
                switch (keycode) {
                    case com.badlogic.gdx.Input.Keys.SPACE:
                        onCallWave();
                        return true;
                    case com.badlogic.gdx.Input.Keys.R:
                        onRotateCamera();
                        return true;
                    case com.badlogic.gdx.Input.Keys.T:
                        onCycleTargeting();
                        return true;
                    case com.badlogic.gdx.Input.Keys.V:
                        hud.armOrSell();
                        return true;
                    case com.badlogic.gdx.Input.Keys.M:
                        onUpgrade();
                        return true;
                }
                return false;
            }
        });
        mux.addProcessor(hud.stage);
        mux.addProcessor(new GestureDetector(cameraController));
        mux.addProcessor(cameraController);
        Gdx.input.setInputProcessor(mux);
    }

    @Override
    public void render(float delta) {
        if (!paused) {
            stateTime += delta;
            lastDelta = delta;
            cameraController.update(delta);
            updateTension(delta);
            checkEndlessBoon();
            accumulator += Math.min(delta, 0.25f) * speedMultiplier;
            while (accumulator >= GameWorld.STEP) {
                world.step(GameWorld.STEP);
                simTime += GameWorld.STEP;
                accumulator -= GameWorld.STEP;
            }
        } else {
            lastDelta = 0f; // congela también las partículas
        }

        // ciclo día/noche suave: amanecer cálido → mediodía → atardecer (4 min).
        // La base se recalcula una vez por segundo; el clima se aplica cada frame
        // porque un relámpago tiene que verse al instante.
        lightUpdateTimer += delta;
        if (lightUpdateTimer >= 1f || !dayLightReady) {
            lightUpdateTimer = 0f;
            dayLightReady = true;
            // arranca en la hora del nivel y avanza despacio durante la partida
            float p = MathUtils.clamp(dayPhase + simTime / 900f, 0f, 1f);
            float dawn = MathUtils.clamp(1f - p * 5f, 0f, 1f) * 0.8f;
            float dusk = MathUtils.clamp((p - 0.75f) * 4f, 0f, 1f);
            float warm = Math.max(dawn, dusk);
            baseSun.set(daySun).lerp(1f, 0.62f, 0.42f, 1f, warm * 0.5f);
            float dim = 1f - 0.16f * warm;
            baseAmbient.set(dayAmbient.r * dim, dayAmbient.g * dim, dayAmbient.b * dim, 1f);
        }
        updateWeather(delta);

        ScreenUtils.clear(clearColor, true);

        assembleRenderList();
        renderer.render(renderList);
        healthBars.render(renderer.camera, world.enemies, delta);
        floatingText.render(renderer.camera, world.fx);

        // eventos de audio generados por la simulación
        for (int i = 0; i < world.soundEvents.size; i++) {
            game.sounds.playSfx(world.soundEvents.get(i));
        }
        world.soundEvents.clear();

        // feedback al perder vidas: vibración + flash rojo + sacudida
        if (prevLives != Integer.MIN_VALUE && world.lives < prevLives
            && world.state == GameWorld.State.RUNNING) {
            hud.flashDamage();
            jolt(0.4f, 60);
        }
        prevLives = world.lives;

        // AUTO-OLEADA: con el tablero limpio, salta la cuenta atrás tras un
        // respiro de 1.2 s — SIN bonus de oro (ese premio es del toque manual)
        if (!paused && hud.autoWaveEnabled() && world.state == GameWorld.State.RUNNING
            && world.waves.started && world.waves.canCallEarly()
            && !world.waves.spawning && world.enemies.size == 0) {
            autoWaveCalm += delta;
            if (autoWaveCalm >= 1.2f) {
                autoWaveCalm = 0f;
                world.waves.skipCountdown();
            }
        } else {
            autoWaveCalm = 0f;
        }

        // instantánea de la colocación inicial en cuanto arranca la primera
        // oleada: Reintentar la reutiliza (también en sin fin y Nivel+)
        if (!retryCaptured && world.waves.started) {
            retryCaptured = true;
            retryLoadout = snapshotState();
            retryLoadoutSurvival = world.survival;
        }

        // checkpoint al arrancar cada oleada (solo niveles normales)
        if (!world.survival && world.waves.spawning && world.waves.waveIndex > lastCheckpointWave) {
            lastCheckpointWave = world.waves.waveIndex;
            saveCheckpoint();
        }

        hud.update(world);
        // Deshacer: solo pre-oleada y si el tanque sigue en pie tal cual
        if (lastPlacedTower != null
            && (world.waves.started || !world.towers.contains(lastPlacedTower, true))) {
            lastPlacedTower = null;
        }
        hud.setUndoAvailable(lastPlacedTower != null);
        // la carta de enemigo caduca sola (si la ficha no la ocupa la barra)
        if (enemyCardTimer > 0f) {
            enemyCardTimer -= delta;
            if (enemyCardTimer <= 0f && hud.selectedBuild() == null) {
                hud.showBuildInfo(null);
            }
        }
        updateTips(delta);
        if (world.perfectWaveGold > 0) {
            // el "+oro" ya sale flotando sobre la fortaleza; aquí solo el consejo
            world.perfectWaveGold = 0;
            queueTip("perfect_wave");
        }
        if (pathNoticeTimer <= 0f) {
            hud.setNotice(world.mechanics.noticeTimer > 0f ? world.mechanics.notice : null);
        }

        // refresco del panel de torre seleccionada (DPS en vivo)
        panelRefreshTimer += delta;
        if (selectedPlaced != null && panelRefreshTimer >= 0.5f) {
            panelRefreshTimer = 0f;
            if (world.towers.contains(selectedPlaced, true)) {
                hud.showTowerPanel(selectedPlaced, world.sellRefund(selectedPlaced),
                    world.upgradeCost(selectedPlaced), towerStats(selectedPlaced));
            } else {
                deselectPlaced(); // vendida o fusionada
            }
        }

        // se derrumba el sello de una ruta: polvareda, sacudida y aviso
        if (world.pathJustOpened) {
            world.pathJustOpened = false;
            for (int i = 0; i < sealedRubblePos.size; i++) {
                Vector3 at = sealedRubblePos.get(i);
                for (int n = 0; n < 14; n++) {
                    if (puffs.size > 200) break;
                    Puff puff = puffPool.obtain();
                    float yaw = MathUtils.random(0f, MathUtils.PI2);
                    puff.x = at.x + MathUtils.random(-0.6f, 0.6f);
                    puff.y = MathUtils.random(0.1f, 0.9f);
                    puff.z = at.z + MathUtils.random(-0.6f, 0.6f);
                    puff.vx = MathUtils.sin(yaw) * MathUtils.random(0.8f, 2.2f);
                    puff.vy = MathUtils.random(0.4f, 1.4f);
                    puff.vz = MathUtils.cos(yaw) * MathUtils.random(0.8f, 2.2f);
                    puff.maxLife = puff.life = MathUtils.random(0.6f, 1.1f);
                    puff.size = MathUtils.random(0.12f, 0.22f);
                    puff.kind = Puff.DUST;
                    puffs.add(puff);
                }
            }
            sealedRubble.clear();
            sealedRubblePos.clear();
            jolt(0.6f, 120);
            hud.setNotice(com.pcdc.td.ui.Lang.get("notice.path_open"));
            pathNoticeTimer = 4f;
            queueTip("path_open");
            // enseña el recorrido de la ruta recién abierta
            openedArrowTimer = 6f;
            openedArrowPath = world.openPaths - 1;
        }
        if (pathNoticeTimer > 0f) pathNoticeTimer -= delta;
        if (openedArrowTimer > 0f) openedArrowTimer -= delta;

        // la fortaleza se va desmoronando con las vidas perdidas
        if (world.lives != gateDamageLives) {
            gateDamageLives = world.lives;
            updateGateDamage();
        }

        // el primer élite dorado del perfil estrena su explicación
        if (!eliteTipShown) {
            for (int i = 0; i < world.enemies.size; i++) {
                if (world.enemies.get(i).alive && world.enemies.get(i).crystalElite) {
                    eliteTipShown = true;
                    queueTip("elite");
                    break;
                }
            }
        }

        // el primer enemigo blindado del perfil estrena la explicación de armadura
        if (!armorTipShown) {
            for (int i = 0; i < world.enemies.size; i++) {
                if (world.enemies.get(i).alive && world.enemies.get(i).armor() > 0f) {
                    armorTipShown = true;
                    queueTip("armor");
                    break;
                }
            }
        }

        // cae el Corruptor: sacudida larga de despedida
        if (world.overlordDied) {
            world.overlordDied = false;
            jolt(1.1f, 500);
        }

        // reacción entre zonas de suelo: sacudida corta y consejo la primera vez
        if (world.synergyEvent) {
            world.synergyEvent = false;
            jolt(0.35f, 60);
            queueTip("synergy");
        }

        // el jefe se ha desatado: sacudida y aviso
        if (world.bossSlamEvent) {
            world.bossSlamEvent = false;
            jolt(0.45f, 260);
            queueTip("boss_slam");
        }
        if (world.bossPhaseEvent) {
            world.bossPhaseEvent = false;
            jolt(0.5f, 100);
            queueTip("boss_phase");
        }

        // barra y entrada escénica del jefe
        Enemy boss = world.strongestBoss();
        hud.setBoss(boss);
        if (boss != null && !bossSeen) {
            bossSeen = true;
            jolt(0.55f, 120);
            game.sounds.playSfx("wave");
            // el tema del bioma se desata mientras el jefe esté vivo
            game.sounds.playMusic(com.pcdc.td.audio.MusicFactory.bossTheme(world.biome.id));
        } else if (boss == null && bossSeen) {
            bossSeen = false;
            game.sounds.playMusic(world.biome.id);
        }

        // tinte de clima en pantalla mientras la mecánica está activa
        if (world.mechanics.stormActive) {
            hud.setWeather(tmpWeather.set(0.72f, 0.48f, 0.16f, 0.28f + 0.08f * MathUtils.sin(stateTime * 3.2f)));
        } else if (world.mechanics.gustActive) {
            hud.setWeather(tmpWeather.set(0.62f, 0.78f, 1f, 0.16f + 0.06f * MathUtils.sin(stateTime * 5f)));
        } else {
            hud.setWeather(null);
        }

        // avance de tutorial por oleada limpiada
        if (world.state == GameWorld.State.RUNNING && world.waves.waveIndex > lastClearedWave
            && !world.waves.spawning && world.enemies.size == 0) {
            lastClearedWave = world.waves.waveIndex;
            tutorialEvent("waveclear");
        }

        // autocierre del último mensaje del tutorial
        if (tutorialAutoClose > 0f && !paused) {
            tutorialAutoClose -= delta;
            if (tutorialAutoClose <= 0f) {
                tutorialIndex++;
                applyTutorialStep();
            }
        }

        hud.updateFps(delta);
        hud.stage.act(delta);
        hud.stage.draw();

        if (profiler != null) {
            if (Gdx.graphics.getFrameId() % 120 == 0) {
                Gdx.app.log("Perf", "drawCalls=" + profiler.getDrawCalls()
                    + " enemigos=" + world.enemies.size + " proyectiles=" + world.projectiles.size);
            }
            profiler.reset();
        }

        // transición a resultados
        if (world.state != GameWorld.State.RUNNING) {
            if (endTimer < 0f) {
                // la derrota no se celebra: pasa antes a resultados
                endTimer = world.state == GameWorld.State.VICTORY ? END_DELAY : 1.4f;
                game.sounds.stopMusic();
                game.sounds.playSfx(world.state == GameWorld.State.VICTORY ? "victory" : "defeat");
            } else {
                endTimer -= delta;
                if (endTimer <= 0f) {
                    finishLevel();
                }
            }
        }
    }

    private static final Color ARROW_TINT = Color.valueOf("ffd85a");
    private final Color fxTmpColor = new Color();

    /** Color de disparo de la torre pasado por la pintura equipada. */
    private Color fxColor(TowerDef def) {
        fxTmpColor.set(def.color());
        com.pcdc.td.render.TowerSkins.fxTint(com.pcdc.td.render.ModelFactory.activeSkin, fxTmpColor);
        return fxTmpColor;
    }
    /** Segundos que quedan de flechas tras abrirse una ruta sellada (-1 = solo pre-partida). */
    private float openedArrowTimer;
    private int openedArrowPath = -1;

    /**
     * Flechas doradas recorriendo los caminos: antes de pulsar ¡Empezar! en
     * todos los abiertos, y unos segundos sobre una ruta recién desellada.
     * En los mapas grandes con cruces no siempre es obvio por dónde vendrán.
     */
    private void assembleRouteArrows() {
        boolean preGame = !world.waves.started;
        if (!preGame && openedArrowTimer <= 0f) return;

        float spacing = 2.1f;
        float slide = (stateTime * 2.4f) % spacing;
        for (int pi = 0; pi < world.map.worldPaths.size; pi++) {
            if (pi >= world.openPaths) continue;               // sellada: sin flechas
            if (!preGame && pi != openedArrowPath) continue;   // tras abrirse, solo la nueva
            Array<Vector3> path = world.map.worldPaths.get(pi);
            float next = slide;
            for (int s = 0; s < path.size - 1; s++) {
                Vector3 a = path.get(s), b = path.get(s + 1);
                float segLen = a.dst(b);
                if (segLen < 0.0001f) continue;
                float dx = (b.x - a.x) / segLen, dz = (b.z - a.z) / segLen;
                float yaw = MathUtils.atan2Deg360(dx, dz);
                while (next < segLen) {
                    ModelInstance arrow = obtainBeam(modelFactory.routeArrow());
                    arrow.transform.setToTranslation(a.x + dx * next, 0.12f, a.z + dz * next)
                        .rotate(Vector3.Y, yaw).scale(0.8f, 1f, 0.8f);
                    // parpadeo suave corrido a lo largo del camino
                    float alpha = 0.5f + 0.3f * MathUtils.sin(stateTime * 4f - next * 0.6f);
                    if (!preGame) alpha *= Math.min(1f, openedArrowTimer / 1.2f);
                    tintInstance(arrow, ARROW_TINT, alpha);
                    renderList.add(arrow);
                    next += spacing;
                }
                next -= segLen;
            }
        }
    }

    private void assembleRenderList() {
        renderList.clear();
        beamsUsed = 0;
        renderList.add(boardInstance);

        refreshLavaOverlays();
        // brillo pulsante de los portales de spawn
        for (int i = 0; i < gateGlows.size; i++) {
            ModelInstance gate = gateGlows.get(i);
            if (gate.materials.size > 1) {
                BlendingAttribute ba = (BlendingAttribute) gate.materials.get(1).get(BlendingAttribute.Type);
                if (ba != null) ba.opacity = 0.4f + 0.25f * MathUtils.sin(stateTime * 4f + i);
            }
        }
        renderList.addAll(staticOverlays);
        renderList.addAll(lavaOverlays);
        renderList.addAll(sealedRubble);

        // zonas de suelo (charca tóxica / placa de hielo / napalm)
        for (int i = 0; i < world.zones.size; i++) {
            com.pcdc.td.world.GroundZone z = world.zones.get(i);
            ModelInstance disc = obtainBeam(modelFactory.cellHighlight());
            float fade = Math.min(1f, z.ttl / 0.8f); // se desvanece al final
            float s = z.radius * 2f * (1f + 0.03f * MathUtils.sin(stateTime * 3f + i));
            disc.transform.setToTranslation(z.position.x, 0.095f, z.position.z).scale(s, 1f, s);
            tintInstance(disc, zoneColor(z.kind), 0.3f * fade);
            renderList.add(disc);
            emitZoneParticles(z);
        }

        assembleRouteArrows();
        if (world.mechanics.hasPortals) assemblePortals();

        // torres
        for (int i = 0; i < world.towers.size; i++) {
            Tower t = world.towers.get(i);
            // veteranía: 25 y 75 bajas pintan galones en el casco
            int vet = t.kills >= 75 ? 2 : t.kills >= 25 ? 1 : 0;
            String towerKey = t.def.id + "#" + t.level + "#" + vet;
            if (t.renderInstance == null || !towerKey.equals(t.renderKey)) {
                t.renderInstance = new ModelInstance(modelFactory.tower(t.def, t.level, vet));
                t.renderKey = towerKey;
            }
            // los tanques nuevos NO brotan del suelo: los trae un helicóptero
            // y bajan en paracaídas (las fusiones sí crecen en el sitio)
            float drop = t.airdrop ? assembleAirdrop(t) : 0f;
            float grow = t.airdrop ? 1f : Math.min(1f, t.age / 0.35f);
            boolean driving = t.driving();
            // conduciendo: leve brinco de marcha, como los enemigos
            float driveBob = driving
                ? Math.abs(MathUtils.sin(t.driveProgress() * 26f)) * 0.035f : 0f;
            t.renderInstance.transform.setToTranslation(
                t.position.x, 0.175f - (1f - grow) * 0.5f + driveBob + drop, t.position.z);
            // el casco entero mira hacia donde conduce
            if (driving) t.renderInstance.transform.rotate(Vector3.Y, t.driveYawDeg);
            if (grow < 1f) {
                float gs = 0.25f + 0.75f * grow;
                t.renderInstance.transform.scale(gs, grow, gs);
            }
            // colgando del paracaídas oscila despacio sobre su eje
            if (!driving && drop > 0f) {
                t.renderInstance.transform.rotate(Vector3.Y,
                    MathUtils.sin(stateTime * 1.6f + t.cellX) * 22f);
            }
            // amortiguación al tocar suelo: se aplasta y se recupera
            float sinceLand = t.age - Tower.DEPLOY_TIME;
            if (t.airdrop && sinceLand >= 0f && sinceLand < 0.22f) {
                float k = 1f - sinceLand / 0.22f;
                t.renderInstance.transform.scale(1f + 0.18f * k, 1f - 0.3f * k, 1f + 0.18f * k);
                if (com.pcdc.td.ui.Quality.high) emitTrackDust(t);
                if (!t.landingDone) {
                    t.landingDone = true;
                    game.sounds.playSfx("landing");
                }
            }
            Node head = t.renderInstance.getNode("head");
            // la trituradora no apunta: sus sierras giran como un torbellino
            // (el Demoledor tampoco apunta, pero MARTILLEA en vez de girar)
            boolean spinner = t.def.type() == TowerDef.AttackType.AREA
                && t.def.coneAngleDeg >= 180f && t.def.pulseInterval <= 0f;
            boolean radar = "radar".equals(t.def.id);
            float headYaw = spinner && t.attacking ? stateTime * 540f
                : radar ? stateTime * 70f : t.headYawDeg; // la antena barre sin parar
            // desfile de victoria: todas las torretas giran celebrando
            if (world.state == GameWorld.State.VICTORY) {
                headYaw = stateTime * 260f + (t.cellX * 37 + t.cellZ * 53);
                if (MathUtils.randomBoolean(0.045f)) emitVictorySalvo(t);
            }
            // si el casco va rotado, la torreta es relativa a él
            if (driving) headYaw -= t.driveYawDeg;
            head.rotation.setFromAxis(0f, 1f, 0f, headYaw);
            // retroceso: el cabezal recula al disparar
            float yawRad = t.headYawDeg * MathUtils.degreesToRadians;
            // martillo neumático: el cabezal entero vibra machacando el suelo
            float headY = 0.32f;
            if (t.def.pulseInterval > 0f && t.attacking) {
                headY -= 0.07f * Math.abs(MathUtils.sin(stateTime * 26f));
            }
            head.translation.set(
                -MathUtils.sin(yawRad) * 0.14f * t.recoil,
                headY,
                -MathUtils.cos(yawRad) * 0.14f * t.recoil);
            t.renderInstance.calculateTransforms();
            renderList.add(t.renderInstance);

            // polvo tras las orugas durante el viaje
            if (driving && com.pcdc.td.ui.Quality.high) emitDriveDust(t);

            // barrido del radar: anillo tenue pulsante con su radio de escaneo
            if (radar && !driving) {
                ModelInstance ring = obtainBeam(modelFactory.cellHighlight());
                float rr = world.rangeFor(t) * 2f;
                ring.transform.setToTranslation(t.position.x, 0.078f, t.position.z)
                    .rotate(Vector3.Y, stateTime * 70f).scale(rr, 1f, rr);
                tintInstance(ring, RADAR_TINT, 0.05f + 0.025f * MathUtils.sin(stateTime * 2.5f));
                renderList.add(ring);
            }

            // sombra en la casilla: sin ella, con el sol bajo del amanecer o el
            // atardecer, los tanques parecen flotar sobre el tablero
            if (com.pcdc.td.ui.Quality.high && grow >= 1f) {
                // desplazada respecto al casco: centrada y a tamaño de casilla
                // parecía una placa negra bajo el tanque, no una sombra
                ModelInstance shadow = obtainBeam(modelFactory.shadow());
                // cayendo: la sombra se abre y se aclara con la altura
                float ss = 0.78f * (1f + drop * 0.14f);
                shadow.transform.setToTranslation(
                        t.position.x + 0.14f, 0.072f, t.position.z + 0.12f)
                    .scale(ss, 1f, ss);
                // el alfa se fija SIEMPRE: las instancias del pool se reciclan
                // y una sombra pálida se heredaría en el siguiente tanque
                tintInstance(shadow, Color.BLACK, 0.3f * (1f - drop / DROP_HEIGHT * 0.7f));
                renderList.add(shadow);
            }

            // potenciado por el tanque de mando: ascuas doradas que brotan del
            // suelo alrededor de las orugas, suben oscilando y se apagan en
            // naranja, como brasas avivadas.
            if (world.underCommandAura(t)) {
                for (int k = 0; k < AURA_MOTES; k++) {
                    // fase y ángulo fijos por ascua (y por tanque) para que el
                    // ciclo no se sincronice entre vecinos
                    float offset = k * 0.211f + (t.cellX * 7 + t.cellZ * 13) * 0.037f;
                    float tc = (stateTime * 0.5f + offset) % 1f; // 0 brota, 1 se apaga
                    float ang = offset * 39.7f;
                    float radius = 0.28f + 0.22f * ((k * 37) % 10) / 10f;
                    float sway = 0.06f * MathUtils.sin(stateTime * 3.1f + k * 2.1f);
                    ModelInstance ember = obtainBeam(modelFactory.fxSphere());
                    float es = (0.062f - 0.028f * tc)
                        * (1f + 0.15f * MathUtils.sin(stateTime * 17f + k * 5f));
                    ember.transform.setToTranslation(
                            t.position.x + MathUtils.cos(ang) * radius + sway,
                            0.08f + 0.85f * tc,
                            t.position.z + MathUtils.sin(ang) * radius + sway * 0.6f)
                        .scale(es, es * 1.4f, es);
                    tmpC.set(AURA_TINT).lerp(FIRE_END, tc * 0.8f);
                    tintInstance(ember, tmpC, Math.min(1f, tc * 6f) * (1f - tc) * 0.95f);
                    renderList.add(ember);
                }
            }

            // saboteado: chispas parpadeantes sobre la torreta apagada
            if (t.disabledTimer > 0f) {
                ModelInstance spark = obtainBeam(modelFactory.fxSphere());
                float flick = 0.2f + 0.08f * MathUtils.sin(stateTime * 30f);
                spark.transform.setToTranslation(t.position.x, 1.0f, t.position.z)
                    .scale(flick, flick, flick);
                tintInstance(spark, Color.valueOf("ffb03c"),
                    0.65f + 0.35f * MathUtils.sin(stateTime * 22f));
                renderList.add(spark);
                // anillo en el suelo: se ve aunque la torreta quede tapada
                ModelInstance ring = obtainBeam(modelFactory.cellHighlight());
                ring.transform.setToTranslation(t.position.x, 0.1f, t.position.z)
                    .scale(1.05f, 1f, 1.05f);
                tintInstance(ring, Color.valueOf("ff8a3c"),
                    0.3f + 0.2f * MathUtils.sin(stateTime * 22f));
                renderList.add(ring);
            }

            // polvo de las orugas al girar la torreta (el chasis "corrige" la pose)
            float yawDelta = Math.abs(((t.headYawDeg - t.renderYaw) % 360f + 540f) % 360f - 180f);
            if (yawDelta > 2.5f && grow >= 1f && com.pcdc.td.ui.Quality.high) emitTrackDust(t);
            t.renderYaw = t.headYawDeg;

            // rayo del láser / llamarada del lanzallamas
            if (t.attacking && t.def.type() == TowerDef.AttackType.BEAM && t.target != null) {
                tmpV.set(t.target.position.x, t.target.centerY(), t.target.position.z);
                float mult = t.beamMult();
                float thickness = (0.05f + 0.05f * (mult - 1f)) * (1f + 0.2f * MathUtils.sin(stateTime * 24f));
                ModelInstance beam = obtainBeam(modelFactory.beam());
                setBeamTransform(beam, t.muzzle(), tmpV, thickness);
                tintInstance(beam, fxColor(t.def), Math.min(0.55f + 0.15f * mult, 1f));
                renderList.add(beam);
                // núcleo blanco interior
                ModelInstance core = obtainBeam(modelFactory.beam());
                setBeamTransform(core, t.muzzle(), tmpV, thickness * 0.4f);
                tintInstance(core, Color.WHITE, 0.9f);
                renderList.add(core);
                // refracción del Prisma: rayos finos del objetivo a los cercanos
                for (int c = 0; c < t.chainTargets.size; c++) {
                    Enemy chained = t.chainTargets.get(c);
                    if (!chained.alive) continue;
                    ModelInstance split = obtainBeam(modelFactory.beam());
                    tmpV2.set(chained.position.x, chained.centerY(), chained.position.z);
                    setBeamTransform(split, tmpV, tmpV2, thickness * 0.5f);
                    tintInstance(split, fxColor(t.def), 0.5f);
                    renderList.add(split);
                }
            } else if (t.attacking && t.def.type() == TowerDef.AttackType.AREA
                && t.def.coneAngleDeg < 180f) {
                ModelInstance cone = obtainBeam(modelFactory.flameCone());
                float spread = (float) Math.tan(t.def.coneAngleDeg * MathUtils.degreesToRadians);
                cone.transform.setToTranslation(t.muzzle())
                    .rotate(Vector3.Y, t.headYawDeg)
                    .scale(spread * t.def.range, 0.5f, t.def.range * 0.95f);
                // el cono se tiñe con el color del tanque: el aliento gélido
                // escupía un cono naranja de lanzallamas
                tintInstance(cone, fxColor(t.def), 0.16f);
                renderList.add(cone);
                emitFlameParticles(t);
            } else if (t.attacking && t.def.type() == TowerDef.AttackType.AREA) {
                // trituradora: chispas metálicas alrededor de las sierras
                emitSawSparks(t);
            }
        }

        // enemigos (+ sombra falsa)
        for (int i = 0; i < world.enemies.size; i++) {
            Enemy e = world.enemies.get(i);
            if (e.renderInstance == null || !e.def.id.equals(e.renderKey)) {
                e.renderInstance = new ModelInstance(modelFactory.enemy(e.def, world.biome));
                e.renderKey = e.def.id;
            }
            // animación de marcha: brinco y bamboleo sincronizados con la distancia recorrida
            float phase = e.pathProgress * 7f + (e.hashCode() & 7);
            float y = e.position.y + (e.isFlying() ? 0f : e.def.size * 0.55f);
            float roll;
            if (e.isFlying()) {
                y += MathUtils.sin(e.pathProgress * 3f) * 0.12f;
                roll = MathUtils.sin(e.pathProgress * 2f) * 5f;
            } else {
                y += Math.abs(MathUtils.sin(phase)) * e.def.size * 0.16f;
                roll = MathUtils.sin(phase) * 8f;
            }
            e.renderInstance.transform.setToTranslation(e.position.x, y, e.position.z);
            e.renderInstance.transform.rotate(0f, 1f, 0f, e.yawDeg);
            e.renderInstance.transform.rotate(0f, 0f, 1f, roll);
            // asalto a la muralla: se agacha tomando impulso y embiste inclinado
            if (e.assaulting) {
                if (e.assaultTimer < com.pcdc.td.world.Enemy.ASSAULT_WINDUP) {
                    float k = e.assaultTimer / com.pcdc.td.world.Enemy.ASSAULT_WINDUP;
                    e.renderInstance.transform.rotate(1f, 0f, 0f, -14f * k);
                    e.renderInstance.transform.scale(1f, 1f - 0.12f * k, 1f);
                } else {
                    e.renderInstance.transform.rotate(1f, 0f, 0f, 18f);
                }
            }
            if (e.eliteAura) e.renderInstance.transform.scale(1.3f, 1.3f, 1.3f);
            if (e.crystalElite) e.renderInstance.transform.scale(1.18f, 1.18f, 1.18f);
            if (e.splitChild) e.renderInstance.transform.scale(0.7f, 0.7f, 0.7f);
            // emerge del portal y es absorbido por la fortaleza
            float appear = MathUtils.clamp(e.pathProgress / 1.3f + 0.12f, 0.12f, 1f);
            float vanish = MathUtils.clamp((e.totalPathLen - e.pathProgress) / 1.3f, 0.05f, 1f);
            float lifeScale = Math.min(appear, vanish);
            if (lifeScale < 1f) e.renderInstance.transform.scale(lifeScale, lifeScale, lifeScale);

            // tinte verdoso según los stacks de veneno
            ColorAttribute bodyColor = (ColorAttribute) e.renderInstance.materials.first().get(ColorAttribute.Diffuse);
            if (bodyColor != null) {
                tmpC.set(e.def.color());
                if (!"meadow".equals(world.biome.id)) tmpC.lerp(world.biome.enemyTint(), 0.4f);
                // élite dorado: baño de oro pulsante para que se vea a golpe de ojo
                if (e.crystalElite) {
                    float pulse = 0.55f + 0.15f * MathUtils.sin(stateTime * 5f);
                    tmpC.lerp(ELITE_TINT, pulse);
                }
                if (e.poisonStacks > 0) tmpC.lerp(POISON_TINT, 0.11f * e.poisonStacks);
                bodyColor.color.set(tmpC);
            }
            // oculto (arbustos, jefe enterrado): semitransparente para que se
            // entienda por qué los tanques no le disparan
            for (int m = 0; m < e.renderInstance.materials.size; m++) {
                com.badlogic.gdx.graphics.g3d.Material mat = e.renderInstance.materials.get(m);
                BlendingAttribute ba = (BlendingAttribute) mat.get(BlendingAttribute.Type);
                if (e.isHidden()) {
                    if (ba == null) {
                        ba = new BlendingAttribute(0.32f);
                        mat.set(ba);
                    }
                    ba.opacity = 0.32f;
                } else if (ba != null) {
                    // revelado por el radar: vuelve casi opaco (y targeteable)
                    ba.opacity = e.hidden ? 0.75f : 1f;
                }
            }
            renderList.add(e.renderInstance);

            if (com.pcdc.td.ui.Quality.high) {
                ModelInstance shadow = obtainBeam(modelFactory.shadow());
                float s = e.def.size * (e.isFlying() ? 0.7f : 1.05f);
                shadow.transform.setToTranslation(e.position.x, 0.07f, e.position.z).scale(s, 1f, s);
                tintInstance(shadow, Color.BLACK, 0.3f); // el pool puede traerla aclarada
                renderList.add(shadow);
            }

            // UNIDADES INSIGNIA con aura: anillo del tamaño REAL de su efecto,
            // para saber a quién apagar primero y desde dónde
            Color auraTint = specialAuraTint(e.special);
            if (auraTint != null) {
                ModelInstance ring = obtainBeam(modelFactory.cellHighlight());
                float rs = specialAuraRadius(e) * 2f
                    * (1f + 0.04f * MathUtils.sin(stateTime * 3f));
                ring.transform.setToTranslation(e.position.x, 0.075f, e.position.z)
                    .rotate(Vector3.Y, stateTime * 12f).scale(rs, 1f, rs);
                tintInstance(ring, auraTint, 0.16f);
                renderList.add(ring);
            }
            // EMBESTIDOR lanzado: polvareda tras él, se ve venir el topetazo
            if (e.charging && com.pcdc.td.ui.Quality.high) emitChargeDust(e);

            // JEFE CARGANDO: anillo de aviso que se cierra hacia él. Cuando
            // se cierra, descarga: da tiempo a reaccionar y se entiende qué pasó
            if (e.casting) {
                float k = MathUtils.clamp(e.castProgress / com.pcdc.td.world.Enemy.CAST_TIME, 0f, 1f);
                ModelInstance warn = obtainBeam(modelFactory.cellHighlight());
                float rs = 7.2f * (1f - k) + 1.2f;
                warn.transform.setToTranslation(e.position.x, 0.08f, e.position.z)
                    .rotate(Vector3.Y, stateTime * 90f).scale(rs, 1f, rs);
                tintInstance(warn, CAST_TINT, 0.18f + 0.32f * k);
                renderList.add(warn);
                // y el propio jefe se pone al rojo mientras carga
                if (bodyColor != null) {
                    tmpC.set(bodyColor.color).lerp(CAST_TINT, 0.35f + 0.35f * k);
                    bodyColor.color.set(tmpC);
                }
            }

            // EL CORRUPTOR arrastra un anillo de corrupción pulsante
            if ("overlord".equals(e.def.id)) {
                ModelInstance ring = obtainBeam(modelFactory.cellHighlight());
                float rs = 3.1f * (1f + 0.06f * MathUtils.sin(stateTime * 2.2f));
                ring.transform.setToTranslation(e.position.x, 0.076f, e.position.z)
                    .rotate(Vector3.Y, stateTime * 18f).scale(rs, 1f, rs);
                tintInstance(ring, OVERLORD_TINT, 0.26f);
                renderList.add(ring);
            }
        }

        // proyectiles: arco para los de splash, bala alargada para los rápidos, estela para todos
        for (int i = 0; i < world.projectiles.size; i++) {
            Projectile p = world.projectiles.get(i);
            if (p.renderInstance == null || !p.def.id.equals(p.renderKey)) {
                p.renderInstance = new ModelInstance(modelFactory.projectile(p.def));
                p.renderKey = p.def.id;
            }
            boolean splash = p.def.splashRadius > 0f;
            float y = p.position.y;
            if (splash) {
                y += MathUtils.sin(p.flightProgress * MathUtils.PI) * Math.min(1.5f, p.initialDist * 0.3f);
            }
            if (p.def.projectileSpeed >= 12f) {
                float yaw = MathUtils.atan2Deg360(p.dir.x, p.dir.z);
                float h = (float) Math.sqrt(p.dir.x * p.dir.x + p.dir.z * p.dir.z);
                float pitch = -MathUtils.atan2Deg360(p.dir.y, Math.max(0.0001f, h));
                p.renderInstance.transform.setToTranslation(p.position.x, y, p.position.z)
                    .rotate(Vector3.Y, yaw).rotate(Vector3.X, pitch).scale(0.7f, 0.7f, 2.6f);
            } else {
                p.renderInstance.transform.setToTranslation(p.position.x, y, p.position.z)
                    .rotate(Vector3.Y, stateTime * 260f);
            }
            renderList.add(p.renderInstance);

            // sombra del obús en el suelo mientras vuela en arco
            if (splash) {
                ModelInstance shadow = obtainBeam(modelFactory.shadow());
                shadow.transform.setToTranslation(p.position.x, 0.075f, p.position.z)
                    .scale(0.45f, 1f, 0.45f);
                renderList.add(shadow);
            }

            if (com.pcdc.td.ui.Quality.high) {
                for (int g = 1; g <= 2; g++) {
                    ModelInstance ghost = obtainBeam(modelFactory.projectileTrail(p.def));
                    float gs = (splash ? 0.2f : 0.12f) * (1f - g * 0.28f);
                    ghost.transform.setToTranslation(
                            p.position.x - p.dir.x * 0.24f * g,
                            y - p.dir.y * 0.24f * g,
                            p.position.z - p.dir.z * 0.24f * g)
                        .scale(gs, gs, gs);
                    tintInstance(ghost, p.def.color(), 0.5f - 0.18f * g);
                    renderList.add(ghost);
                }
            }
        }

        // vida ambiental del bioma
        if (com.pcdc.td.ui.Quality.high) updateAmbient();

        // partículas de llama/escarcha
        for (int i = puffs.size - 1; i >= 0; i--) {
            Puff puff = puffs.get(i);
            puff.life -= lastDelta;
            if (puff.life <= 0f) {
                puffs.removeIndex(i);
                puffPool.free(puff);
                continue;
            }
            puff.x += puff.vx * lastDelta;
            puff.y += puff.vy * lastDelta;
            puff.z += puff.vz * lastDelta;
            float lifePct = puff.life / puff.maxLife;
            ModelInstance inst = obtainBeam(modelFactory.fxSphere());
            float s = puff.size * (1f + 2.4f * (1f - lifePct));
            inst.transform.setToTranslation(puff.x, puff.y, puff.z).scale(s, s, s);
            float alpha = 0.75f;
            switch (puff.kind) {
                case Puff.FIRE -> tmpC.set(FIRE_START).lerp(FIRE_END, 1f - lifePct);
                case Puff.DUST -> {
                    tmpC.set(DUST_START).lerp(DUST_END, 1f - lifePct);
                    alpha = 0.42f;
                }
                case Puff.SPLASH -> {
                    // la corona se abre a lo ancho y se aplasta
                    tmpC.set(SPLASH_TINT);
                    alpha = 0.5f;
                    inst.transform.setToTranslation(puff.x, puff.y, puff.z)
                        .scale(s * 1.6f, s * 0.35f, s * 1.6f);
                }
                case Puff.POISON -> {
                    tmpC.set(POISON_TINT).lerp(POISON_DARK, 1f - lifePct);
                    alpha = 0.55f;
                }
                default -> tmpC.set(FROST_START).lerp(FROST_END, 1f - lifePct);
            }
            tintInstance(inst, tmpC, alpha * lifePct);
            renderList.add(inst);
        }

        // efectos: tracers, impactos y muertes
        for (int i = 0; i < world.fx.size; i++) {
            Fx f = world.fx.get(i);
            float lifePct = f.ttl / f.maxTtl;
            switch (f.type) {
                case TRACER: {
                    // los tracers siempre salen de un tanque: llevan su pintura
                    fxTmpColor.set(f.color);
                    com.pcdc.td.render.TowerSkins.fxTint(
                        com.pcdc.td.render.ModelFactory.activeSkin, fxTmpColor);
                    if (f.jagged && com.pcdc.td.ui.Quality.high) {
                        // relámpago: quebrado en segmentos con desvíos que
                        // cambian CADA frame — el parpadeo es lo que lo vende.
                        // En calidad baja cae al trazo recto (8 calls → 1)
                        final int segs = 4;
                        float px = -(f.to.z - f.from.z), pz = f.to.x - f.from.x;
                        float plen = (float) Math.sqrt(px * px + pz * pz);
                        if (plen > 0.0001f) {
                            px /= plen;
                            pz /= plen;
                        }
                        tmpV.set(f.from);
                        for (int s2 = 1; s2 <= segs; s2++) {
                            tmpV2.set(f.from).lerp(f.to, s2 / (float) segs);
                            if (s2 < segs) {
                                float off = MathUtils.random(-0.24f, 0.24f);
                                tmpV2.add(px * off, MathUtils.random(-0.14f, 0.14f), pz * off);
                            }
                            ModelInstance seg = obtainBeam(modelFactory.beam());
                            setBeamTransform(seg, tmpV, tmpV2, 0.06f);
                            tintInstance(seg, fxTmpColor, 1f * lifePct);
                            renderList.add(seg);
                            // núcleo blanco fino: brillo de descarga
                            ModelInstance core = obtainBeam(modelFactory.beam());
                            setBeamTransform(core, tmpV, tmpV2, 0.024f);
                            tintInstance(core, Color.WHITE, 0.95f * lifePct);
                            renderList.add(core);
                            tmpV.set(tmpV2);
                        }
                        break;
                    }
                    ModelInstance tracer = obtainBeam(modelFactory.beam());
                    setBeamTransform(tracer, f.from, f.to, 0.04f);
                    tintInstance(tracer, fxTmpColor, 0.9f * lifePct);
                    renderList.add(tracer);
                    // fogonazo en la boca del cañón
                    ModelInstance flash = obtainBeam(modelFactory.fxSphere());
                    float fs = 0.3f * lifePct;
                    flash.transform.setToTranslation(f.from).scale(fs, fs, fs);
                    tintInstance(flash, Color.valueOf("fff0a0"), 0.85f * lifePct);
                    renderList.add(flash);
                    break;
                }
                case HIT: {
                    ModelInstance hit = obtainBeam(modelFactory.fxSphere());
                    float hs = f.size * (1.3f - lifePct);
                    hit.transform.setToTranslation(f.from).scale(hs, hs, hs);
                    tintInstance(hit, f.color, 0.8f * lifePct);
                    renderList.add(hit);
                    break;
                }
                case DEATH: {
                    ModelInstance death = obtainBeam(modelFactory.fxSphere());
                    float ds = f.size * (1.5f - lifePct);
                    death.transform.setToTranslation(f.from).scale(ds, ds, ds);
                    tintInstance(death, f.color, 0.65f * lifePct);
                    renderList.add(death);
                    break;
                }
                case TRACKS: {
                    // dos rodadas paralelas orientadas a la marcha, se desvanecen
                    for (int side = -1; side <= 1; side += 2) {
                        ModelInstance tread = obtainBeam(modelFactory.cellHighlight());
                        float yawRad = f.yawDeg * MathUtils.degreesToRadians;
                        float ox = MathUtils.cos(yawRad) * 0.26f * side;
                        float oz = -MathUtils.sin(yawRad) * 0.26f * side;
                        tread.transform.setToTranslation(f.from.x + ox, 0.082f, f.from.z + oz)
                            .rotate(Vector3.Y, f.yawDeg).scale(0.13f, 1f, 0.44f);
                        tintInstance(tread, TRACKS_TINT, 0.28f * lifePct);
                        renderList.add(tread);
                    }
                    break;
                }
                case SCORCH: {
                    // quemadura rasante: se aclara hasta desaparecer
                    ModelInstance mark = obtainBeam(modelFactory.cellHighlight());
                    float s = f.size * 1.05f;
                    mark.transform.setToTranslation(f.from.x, 0.085f, f.from.z).scale(s, 1f, s);
                    tintInstance(mark, SCORCH_TINT, 0.3f * Math.min(1f, lifePct * 1.6f));
                    renderList.add(mark);
                    break;
                }
                case WRECK: {
                    // chatarra: se derrumba de lado, se hunde y se apaga
                    com.pcdc.td.data.EnemyDef def = game.catalogs.enemy(f.modelId);
                    if (def == null) break;
                    ModelInstance wreck = obtainBeam(modelFactory.enemy(def, world.biome));
                    float fall = Math.min(1f, (1f - lifePct) * 4f);
                    wreck.transform
                        .setToTranslation(f.from.x, f.size * 0.4f - 0.12f * (1f - lifePct), f.from.z)
                        .rotate(Vector3.Y, f.yawDeg)
                        .rotate(Vector3.X, 74f * fall)
                        .scale(0.95f, 0.95f, 0.95f);
                    tintInstance(wreck, WRECK_TINT, 0.85f * Math.min(1f, lifePct * 2.2f));
                    renderList.add(wreck);
                    break;
                }
                case METEOR: {
                    float t = 1f - lifePct; // 0 al apuntar, 1 al impactar
                    // sombra creciente en el punto de impacto
                    ModelInstance shadow = obtainBeam(modelFactory.cellHighlight());
                    float ss = 0.6f + 2.2f * t;
                    shadow.transform.setToTranslation(f.from.x, 0.09f, f.from.z).scale(ss, 1f, ss);
                    tintInstance(shadow, Color.BLACK, 0.1f + 0.4f * t * t);
                    renderList.add(shadow);
                    // roca en caída diagonal, acelerando (cuadrática)
                    float fall = 1f - t * t;
                    float rx = f.from.x + 3.4f * fall;
                    float ry = 0.35f + 11f * fall;
                    ModelInstance rock = obtainBeam(modelFactory.fxSphere());
                    float rs = 0.5f;
                    rock.transform.setToTranslation(rx, ry, f.from.z).scale(rs, rs, rs);
                    tintInstance(rock, Color.valueOf("6b4a33"), 0.95f);
                    renderList.add(rock);
                    // estela ardiente: esferas rezagadas sobre la trayectoria
                    for (int k = 1; k <= 4; k++) {
                        float back = Math.min(1f, fall + k * 0.07f);
                        ModelInstance ember = obtainBeam(modelFactory.fxSphere());
                        float es = 0.42f - k * 0.08f;
                        ember.transform
                            .setToTranslation(f.from.x + 3.4f * back, 0.35f + 11f * back, f.from.z)
                            .scale(es, es, es);
                        tmpC.set(Color.valueOf("ffb03c")).lerp(Color.valueOf("e0512a"), k / 4f);
                        tintInstance(ember, tmpC, 0.8f - k * 0.15f);
                        renderList.add(ember);
                    }
                    break;
                }
                case EXPLOSION: {
                    if (!f.emberSpawned) {
                        f.emberSpawned = true;
                        emitExplosionEmbers(f);
                    }
                    float t = 1f - lifePct;
                    // bola de fuego: crece rápido, pasa de amarillo al color de la torre
                    ModelInstance ball = obtainBeam(modelFactory.fxSphere());
                    float bs = f.size * (0.4f + 0.8f * (float) Math.sqrt(t));
                    ball.transform.setToTranslation(f.from.x, Math.max(0.3f, f.from.y), f.from.z)
                        .scale(bs, bs * 0.9f, bs);
                    tmpC.set(EXPLO_CORE).lerp(f.color, t * 0.85f);
                    tintInstance(ball, tmpC, 0.85f * lifePct);
                    renderList.add(ball);
                    // destello blanco del primer instante
                    if (lifePct > 0.65f) {
                        ModelInstance core = obtainBeam(modelFactory.fxSphere());
                        float cs = f.size * 0.5f * lifePct;
                        core.transform.setToTranslation(f.from.x, Math.max(0.3f, f.from.y), f.from.z)
                            .scale(cs, cs, cs);
                        tintInstance(core, Color.WHITE, 0.9f * (lifePct - 0.65f) / 0.35f);
                        renderList.add(core);
                    }
                    // onda expansiva rasa que recorre el radio del splash
                    ModelInstance ring = obtainBeam(modelFactory.fxSphere());
                    float rs = 0.3f + f.size * 2.1f * t;
                    ring.transform.setToTranslation(f.from.x, 0.1f, f.from.z)
                        .scale(rs, 0.07f, rs);
                    tintInstance(ring, EXPLO_CORE, 0.55f * lifePct);
                    renderList.add(ring);
                    break;
                }
            }
        }

        // fusiones posibles: las torres compatibles con la seleccionada brillan en dorado
        String selectedId = hud.selectedBuild();
        if (selectedId != null) {
            TowerDef selDef = game.catalogs.tower(selectedId);
            float pulse = 0.55f + 0.3f * MathUtils.sin(stateTime * 6f);
            // con tableros muy poblados, el pilar extra dispara las draw calls:
            // se queda solo el marco del suelo, que ya canta lo suficiente
            boolean pillars = world.towers.size <= 24;
            for (int i = 0; i < world.towers.size; i++) {
                Tower t = world.towers.get(i);
                if (world.fusionAt(t.cellX, t.cellZ, selDef) == null) continue;
                // marco brillante en el suelo + columna de luz sobre la torre
                ModelInstance glow = obtainBeam(modelFactory.cellHighlight());
                glow.transform.setToTranslation(t.position.x, 0.1f, t.position.z)
                    .scale(1.3f, 1f, 1.3f);
                tintInstance(glow, Color.valueOf("fff0a0"), pulse);
                renderList.add(glow);
                if (!pillars) continue;
                ModelInstance pillar = obtainBeam(modelFactory.fxSphere());
                pillar.transform.setToTranslation(t.position.x, 1.15f, t.position.z)
                    .scale(0.3f, 0.5f + 0.12f * MathUtils.sin(stateTime * 6f), 0.3f);
                tintInstance(pillar, Color.valueOf("ffe86a"), pulse * 0.8f);
                renderList.add(pillar);
            }
        }

        // ghost de colocación + rango
        String buildId = hud.selectedBuild();
        if (buildId != null && hoverValid) {
            TowerDef def = game.catalogs.tower(buildId);
            ModelInstance ghost = ghosts.get(def.id);
            if (ghost == null) {
                ghost = new ModelInstance(modelFactory.tower(def));
                for (int m = 0; m < ghost.materials.size; m++) {
                    ghost.materials.get(m).set(new BlendingAttribute(0.55f));
                }
                ghosts.put(def.id, ghost);
            }
            ghost.transform.setToTranslation(hoverCell.x + 0.5f, 0.175f, hoverCell.y + 0.5f);
            renderList.add(ghost);

            rangeDisc.transform.setToTranslation(hoverCell.x + 0.5f, 0.12f, hoverCell.y + 0.5f)
                .scale(def.range, 1f, def.range);
            rangeDiscColor.color.set(Color.WHITE);
            renderList.add(rangeDisc);
        } else if (selectedPlaced != null) {
            rangeDisc.transform.setToTranslation(selectedPlaced.position.x, 0.12f, selectedPlaced.position.z)
                .scale(selectedPlaced.def.range, 1f, selectedPlaced.def.range);
            rangeDiscColor.color.set(Color.WHITE);
            renderList.add(rangeDisc);
        }

        // mientras eliges dónde construir: el alcance de los colocados, tenue y
        // del color de cada tanque. Con MUCHOS tanques solo los cercanos al
        // cursor: cada disco es una draw call y el tablero tardío se llenaba
        if (buildId != null) {
            boolean many = world.towers.size > 24;
            // con muchos tanques y el cursor fuera del tablero, ni un disco
            if (!many || hoverValid) {
            for (int i = 0; i < world.towers.size; i++) {
                Tower t = world.towers.get(i);
                if (many) {
                    float dx = t.position.x - (hoverCell.x + 0.5f);
                    float dz = t.position.z - (hoverCell.y + 0.5f);
                    if (dx * dx + dz * dz > 49f) continue; // a más de 7 celdas
                }
                float r = world.rangeFor(t);
                ModelInstance disc = obtainBeam(modelFactory.rangeDisc());
                disc.transform.setToTranslation(t.position.x, 0.105f, t.position.z)
                    .scale(r, 1f, r);
                tintInstance(disc, t.def.color(), 0.10f);
                renderList.add(disc);
            }
            }
        }

        if (highlightVisible) renderList.add(highlight);
    }

    // ---- clima ----

    /** Luz del ciclo día/noche antes de aplicarle el clima. */
    private final Color baseAmbient = new Color(), baseSun = new Color();
    /** Hora del día con la que arranca este nivel (0 amanecer, 1 anochecer). */
    private float dayPhase;
    private boolean dayLightReady;
    /** Relámpagos de la tormenta: cuenta atrás al siguiente y brillo del actual. */
    private float lightningWait = 5f, lightningFlash;
    private final Color weatherAmbient = new Color(), weatherSun = new Color();

    /**
     * Clima con presencia real: oscurece y tiñe la luz del bioma, y en la
     * tormenta añade relámpagos que iluminan el tablero un instante.
     */
    private void updateWeather(float delta) {
        weatherAmbient.set(baseAmbient);
        weatherSun.set(baseSun);
        String b = world.biome.id;

        if ("storm".equals(b)) {
            // cielo plomizo y frío; las rachas lo cierran todavía más
            float gloom = world.mechanics.gustActive ? 0.62f : 0.74f;
            weatherAmbient.mul(gloom, gloom, gloom * 1.12f, 1f);
            weatherSun.mul(gloom + 0.06f, gloom + 0.08f, gloom + 0.18f, 1f);

            lightningWait -= delta;
            if (lightningWait <= 0f) {
                lightningWait = MathUtils.random(7f, 15f);
                lightningFlash = 1f;
                game.sounds.playSfx("explosion");
            }
            if (lightningFlash > 0f) {
                lightningFlash = Math.max(0f, lightningFlash - delta * 4.5f);
                // el fogonazo cae rápido: brilla y se apaga
                float f = lightningFlash * lightningFlash;
                weatherAmbient.add(0.75f * f, 0.78f * f, 0.95f * f, 0f);
                weatherSun.add(0.6f * f, 0.62f * f, 0.8f * f, 0f);
            }
        } else if ("snow".equals(b)) {
            // ventisca: enfría y desatura, sin blanquear tanto que se pierda
            // el trazado del camino (la nieve arrastrada ya hace el efecto)
            weatherAmbient.lerp(0.9f, 0.94f, 1f, 1f, 0.18f);
            weatherSun.lerp(0.93f, 0.96f, 1f, 1f, 0.15f);
        } else if ("swamp".equals(b)) {
            // bruma verdosa que apaga la luz
            weatherAmbient.mul(0.86f, 0.94f, 0.8f, 1f);
            weatherSun.mul(0.88f, 0.96f, 0.82f, 1f);
        }
        renderer.setLighting(weatherAmbient, weatherSun);
    }

    /** Empuje lateral de la ventisca: sopla a rachas en vez de constante. */
    private float blizzardWind() {
        return -1.6f - 1.4f * MathUtils.sin(stateTime * 0.35f);
    }

    /** Corona de agua donde revienta una gota contra el suelo (no en todas). */
    private void splash(float x, float z) {
        if (puffs.size > 60 || MathUtils.random() > 0.5f) return;
        Puff puff = puffPool.obtain();
        puff.x = x;
        puff.y = 0.06f;
        puff.z = z;
        puff.vx = MathUtils.random(-0.12f, 0.12f);
        puff.vy = MathUtils.random(0.25f, 0.5f);
        puff.vz = MathUtils.random(-0.12f, 0.12f);
        puff.maxLife = puff.life = MathUtils.random(0.16f, 0.26f);
        puff.size = MathUtils.random(0.035f, 0.06f);
        puff.kind = Puff.SPLASH;
        puffs.add(puff);
    }

    /** Vida ambiental por bioma: nieve, lluvia, luciérnagas, ascuas, pájaros... */
    private void updateAmbient() {
        String b = world.biome.id;
        float w = world.map.width, h = world.map.height;
        // topes ajustados al coste: cada partícula es una llamada de dibujo y la
        // tormenta a tope se comía ~170 (medido con -Dtd.profile)
        int cap;
        switch (b) {
            case "snow": cap = 45; break;  // ventisca
            case "storm": cap = 55; break; // aguacero
            case "swamp": cap = 22; break; // luciérnagas + jirones de bruma
            case "meadow": cap = 3; break;
            default: cap = 14; break;
        }

        // la lluvia y la nieve caen a mantas: varias por frame y sin sorteo
        boolean downpour = "storm".equals(b) || "snow".equals(b);
        int perFrame = downpour ? 2 : 1;
        float spawnChance = downpour ? 1f : "meadow".equals(b) ? 0.01f : 0.35f;
        for (int n = 0; n < perFrame; n++) {
        // spawn probabilístico hasta el tope
        if (ambient.size < cap && MathUtils.random() < spawnChance) {
            Amb a = ambientPool.obtain();
            a.phase = MathUtils.random(0f, 6.28f);
            a.mist = false;
            switch (b) {
                case "meadow": // pájaro cruzando en lo alto
                    a.x = -3f;
                    a.y = MathUtils.random(2.6f, 3.4f);
                    a.z = MathUtils.random(0f, h);
                    a.vx = MathUtils.random(1.6f, 2.4f);
                    a.vy = 0f;
                    a.vz = MathUtils.random(-0.3f, 0.3f);
                    a.maxLife = (w + 6f) / a.vx;
                    a.size = 0.09f;
                    break;
                case "snow": // copo arrastrado por la ventisca
                    a.x = MathUtils.random(-3f, w + 1f);
                    a.y = MathUtils.random(1.5f, 3.5f);
                    a.z = MathUtils.random(-1f, h + 1f);
                    a.vx = blizzardWind() + MathUtils.random(-0.3f, 0.3f);
                    a.vy = -MathUtils.random(0.5f, 1.1f);
                    a.vz = MathUtils.random(-0.3f, 0.3f);
                    a.maxLife = 6f;
                    a.size = MathUtils.random(0.035f, 0.07f);
                    break;
                case "storm": // gota de lluvia (se inclina con las rachas)
                    a.x = MathUtils.random(-2f, w + 1f);
                    a.y = 3.5f;
                    a.z = MathUtils.random(-1f, h + 1f);
                    a.vx = world.mechanics.gustActive ? 4.5f : 1.2f;
                    a.vy = -11f;
                    a.vz = 0f;
                    a.maxLife = 0.42f;
                    a.size = 0.022f;
                    break;
                case "volcano": // ascua ascendente
                case "corrupt":
                case "ruins":
                    a.x = MathUtils.random(0f, w);
                    a.y = 0.1f;
                    a.z = MathUtils.random(0f, h);
                    a.vx = MathUtils.random(-0.1f, 0.1f);
                    a.vy = MathUtils.random(0.35f, 0.7f);
                    a.vz = MathUtils.random(-0.1f, 0.1f);
                    a.maxLife = MathUtils.random(1.8f, 3f);
                    a.size = MathUtils.random(0.03f, 0.06f);
                    break;
                case "forest": // hoja cayendo con vaivén
                    a.x = MathUtils.random(0f, w);
                    a.y = MathUtils.random(2f, 3f);
                    a.z = MathUtils.random(0f, h);
                    a.vx = 0f;
                    a.vy = -MathUtils.random(0.35f, 0.55f);
                    a.vz = 0f;
                    a.maxLife = 6f;
                    a.size = MathUtils.random(0.05f, 0.08f);
                    break;
                case "crystal": // destello sobre el suelo
                    a.x = MathUtils.random(0f, w);
                    a.y = MathUtils.random(0.3f, 1f);
                    a.z = MathUtils.random(0f, h);
                    a.vx = a.vz = 0f;
                    a.vy = 0.12f;
                    a.maxLife = MathUtils.random(1.2f, 2f);
                    a.size = MathUtils.random(0.03f, 0.05f);
                    break;
                default: // desierto/pantano: motas a la deriva / luciérnagas
                    // en el pantano, 2 de cada 3 son jirones de bruma rasante
                    a.mist = "swamp".equals(b) && MathUtils.random() < 0.66f;
                    a.x = MathUtils.random(-1f, w + 1f);
                    a.y = a.mist ? MathUtils.random(0.12f, 0.3f) : MathUtils.random(0.4f, 1.3f);
                    a.z = MathUtils.random(-1f, h + 1f);
                    a.vx = "desert".equals(b) ? MathUtils.random(0.8f, 1.4f)
                        : a.mist ? MathUtils.random(0.06f, 0.2f) : MathUtils.random(-0.25f, 0.25f);
                    a.vy = 0f;
                    a.vz = a.mist ? MathUtils.random(-0.05f, 0.05f) : MathUtils.random(-0.2f, 0.2f);
                    a.maxLife = a.mist ? MathUtils.random(7f, 11f) : MathUtils.random(3f, 5f);
                    a.size = a.mist ? MathUtils.random(1.1f, 2.2f) : "swamp".equals(b) ? 0.045f : 0.05f;
                    break;
            }
            a.life = a.maxLife;
            ambient.add(a);
        }
        }

        // color por bioma
        switch (b) {
            case "meadow": ambColor.set(0.95f, 0.95f, 0.98f, 1f); break;
            // azulado: el blanco puro se perdía sobre el suelo nevado
            case "snow": ambColor.set(0.78f, 0.88f, 1f, 1f); break;
            case "storm": ambColor.set(0.62f, 0.74f, 0.95f, 1f); break;
            case "volcano": ambColor.set(1f, 0.55f, 0.18f, 1f); break;
            case "forest": ambColor.set(0.45f, 0.75f, 0.3f, 1f); break;
            case "swamp": ambColor.set(0.85f, 1f, 0.45f, 1f); break;
            case "crystal": ambColor.set(0.62f, 0.9f, 1f, 1f); break;
            case "ruins": ambColor.set(0.78f, 0.5f, 0.95f, 1f); break;
            case "corrupt": ambColor.set(0.9f, 0.35f, 0.75f, 1f); break;
            default: ambColor.set(0.9f, 0.82f, 0.6f, 1f); break;
        }

        for (int i = ambient.size - 1; i >= 0; i--) {
            Amb a = ambient.get(i);
            a.life -= lastDelta;
            if (a.life <= 0f || a.y < 0.05f) {
                // la gota que toca suelo deja su salpicadura
                if ("storm".equals(b) && a.y < 0.05f) splash(a.x, a.z);
                ambient.removeIndex(i);
                ambientPool.free(a);
                continue;
            }
            a.x += a.vx * lastDelta;
            a.y += a.vy * lastDelta;
            a.z += a.vz * lastDelta;
            // vaivenes: hojas y luciérnagas serpentean, los pájaros aletean
            if ("forest".equals(b) || "swamp".equals(b)) {
                a.x += MathUtils.sin(stateTime * 2.2f + a.phase) * 0.5f * lastDelta;
                a.z += MathUtils.cos(stateTime * 1.8f + a.phase) * 0.4f * lastDelta;
            }
            float fade = Math.min(1f, Math.min(a.life, a.maxLife - a.life) * 2.5f);
            if ("swamp".equals(b) || "crystal".equals(b)) {
                fade *= 0.6f + 0.4f * MathUtils.sin(stateTime * 5f + a.phase); // parpadeo
            }

            if ("storm".equals(b) || "snow".equals(b)) {
                // trazo inclinado hacia donde va: lluvia larga, nieve corta.
                // Con puntos, la nieve blanca desaparecía sobre el suelo nevado.
                boolean rain = "storm".equals(b);
                ModelInstance drop = obtainBeam(modelFactory.beam());
                float slant = MathUtils.atan2Deg360(a.vx, -a.vy);
                drop.transform.setToTranslation(a.x, a.y, a.z)
                    .rotate(Vector3.Z, -slant)
                    .rotate(Vector3.X, 90f)
                    .scale(a.size, a.size, rain ? 0.62f : 0.3f);
                tintInstance(drop, ambColor, (rain ? 0.42f : 0.75f) * fade);
                renderList.add(drop);
            } else if (a.mist) {
                // jirón de bruma: losa rasante, muy tenue, que se desliza
                ModelInstance veil = obtainBeam(modelFactory.cellHighlight());
                float pulse = 0.85f + 0.15f * MathUtils.sin(stateTime * 0.7f + a.phase);
                veil.transform.setToTranslation(a.x, a.y, a.z)
                    .scale(a.size * pulse, 1f, a.size * 0.75f * pulse);
                tintInstance(veil, MIST_TINT, 0.12f * fade);
                renderList.add(veil);
            } else {
                ModelInstance dot = obtainBeam(modelFactory.fxSphere());
                float sx = a.size, sy = a.size, sz = a.size;
                if ("meadow".equals(b)) {
                    // pájaro: silueta alargada que "aletea" en ancho
                    sx = a.size * (2.2f + 1.4f * MathUtils.sin(stateTime * 9f + a.phase));
                    sy = a.size * 0.5f;
                }
                dot.transform.setToTranslation(a.x, a.y, a.z).scale(sx, sy, sz);
                tintInstance(dot, ambColor, ("snow".equals(b) ? 0.8f : 0.65f) * fade);
                renderList.add(dot);
            }
        }
    }

    /** Próxima oleada del sin fin que ofrece carta (10, 20, 30...). */
    private int nextBoonWave = 10;
    private boolean boonOpen;

    /**
     * SIN FIN: cada diez oleadas se para el reloj y se eligen mejoras. Es lo
     * que le da forma propia a cada partida: antes el modo era la campaña sin
     * final, con las mismas oleadas cada vez más grandes.
     */
    private void checkEndlessBoon() {
        if (!world.survival || boonOpen || world.state != GameWorld.State.RUNNING) return;
        if (world.waves.displayWave() < nextBoonWave) return;
        nextBoonWave += 10;
        showBoonChoice();
    }

    private void showBoonChoice() {
        // tres cartas al azar de las que quedan por coger
        com.badlogic.gdx.utils.Array<com.pcdc.td.world.EndlessBoon> pool =
            com.pcdc.td.world.EndlessBoon.all();
        for (int i = pool.size - 1; i >= 0; i--) {
            if (world.boonsTaken.contains(pool.get(i).id, false)) pool.removeIndex(i);
        }
        if (pool.size == 0) return;
        pool.shuffle();
        int count = Math.min(3, pool.size);

        queueTip("endless_boons");
        boonOpen = true;
        paused = true;
        com.badlogic.gdx.scenes.scene2d.ui.Table panel = com.pcdc.td.ui.Widgets.modal(hud.stage, game.skin,
            com.pcdc.td.ui.Lang.get("boon.title"));
        panel.add(new com.badlogic.gdx.scenes.scene2d.ui.Label(
            com.pcdc.td.ui.Lang.get("boon.subtitle"), game.skin, "dim")).padBottom(4).row();
        for (int i = 0; i < count; i++) {
            final com.pcdc.td.world.EndlessBoon boon = pool.get(i);
            com.badlogic.gdx.scenes.scene2d.ui.TextButton card =
                new com.badlogic.gdx.scenes.scene2d.ui.TextButton(
                    com.pcdc.td.ui.Lang.get("boon." + boon.id) + "\n"
                    + com.pcdc.td.ui.Lang.get("boondesc." + boon.id), game.skin);
            card.getLabel().setWrap(true);
            card.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
            card.getLabelCell().pad(4).width(com.pcdc.td.ui.UiScale.compact() ? 280 : 340);
            card.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    game.sounds.playSfx("buy");
                    boon.apply(world);
                    world.boonsTaken.add(boon.id);
                    hud.setNotice(com.pcdc.td.ui.Lang.get("boon." + boon.id));
                    panel.getParent().remove();
                    boonOpen = false;
                    paused = false;
                }
            });
            panel.add(card).padTop(4).row();
        }
    }

    /** Latido de las últimas vidas: cuenta atrás del siguiente golpe. */
    private float heartbeatTimer;
    private boolean tensionOn;

    /**
     * Con tres vidas o menos la música se agacha y entra un latido cada vez
     * más rápido. Es puro tacto: el número de vidas ya estaba en la barra,
     * pero un final apurado no se SENTÍA.
     */
    private void updateTension(float delta) {
        boolean tense = world.state == GameWorld.State.RUNNING
            && world.lives <= 3 && world.lives > 0 && world.waves.started;
        if (tense != tensionOn) {
            tensionOn = tense;
            game.sounds.setMusicDuck(tense ? 0.45f : 1f);
            heartbeatTimer = 0f;
        }
        if (!tense) return;
        heartbeatTimer -= delta;
        if (heartbeatTimer <= 0f) {
            // con una sola vida el pulso se acelera
            heartbeatTimer = world.lives <= 1 ? 0.85f : world.lives == 2 ? 1.15f : 1.45f;
            game.sounds.playSfx("heartbeat");
        }
    }

    /**
     * Deterioro visible de la fortaleza: el estandarte se encoge y se descuelga
     * y la piedra se ensucia según caen las vidas. Se ve de un vistazo cómo va
     * la partida sin leer el número.
     */
    private void updateGateDamage() {
        float health = world.startLives > 0
            ? MathUtils.clamp(world.lives / (float) world.startLives, 0f, 1f) : 1f;
        float soot = 0.55f + 0.45f * health;
        // la piedra se rompe por tramos: intacta, tocada y en ruinas
        int tier = health > 0.66f ? 0 : health > 0.33f ? 1 : 2;
        if (tier != gateDamageTier) {
            gateDamageTier = tier;
            rebuildGoalGates(tier);
        }
        int c = 0; // recorre baseGateColors en el mismo orden en que se llenó
        for (int i = 0; i < goalGates.size; i++) {
            ModelInstance gate = goalGates.get(i);
            Node banner = gate.getNode("banner");
            if (banner != null) {
                // se descuelga hasta 70° y se encoge a la mitad: jirones
                banner.rotation.setFromAxis(1f, 0f, 0f, -70f * (1f - health));
                float shrink = 0.5f + 0.5f * health;
                banner.scale.set(shrink, shrink, 1f);
            }
            gate.calculateTransforms();
            for (int m = 0; m < gate.materials.size; m++, c++) {
                Color base = c < baseGateColors.size ? baseGateColors.get(c) : null;
                if (base == null) continue;
                ColorAttribute attr =
                    (ColorAttribute) gate.materials.get(m).get(ColorAttribute.Diffuse);
                if (attr != null) attr.color.set(base).mul(soot, soot, soot, 1f);
            }
        }
    }

    /** Colores originales de los materiales de las fortalezas (para no oscurecer en cascada). */
    private final Array<Color> baseGateColors = new Array<>();

    /**
     * Cambia las fortalezas por su versión dañada. Se sustituye la instancia
     * entera (el modelo cachea por bioma+estrellas+daño) conservando su
     * posición, y se relee la paleta base para que el tizne siga cuadrando.
     */
    private void rebuildGoalGates(int tier) {
        com.badlogic.gdx.graphics.g3d.Model model =
            modelFactory.goalGate(world.biome, biomeStarTier(), tier);
        baseGateColors.clear();
        for (int i = 0; i < goalGates.size; i++) {
            ModelInstance old = goalGates.get(i);
            ModelInstance fresh = new ModelInstance(model);
            fresh.transform.set(goalGateTransforms.get(i));
            staticOverlays.removeValue(old, true);
            staticOverlays.add(fresh);
            goalGates.set(i, fresh);
            for (int m = 0; m < fresh.materials.size; m++) {
                ColorAttribute attr =
                    (ColorAttribute) fresh.materials.get(m).get(ColorAttribute.Diffuse);
                baseGateColors.add(attr == null ? null : new Color(attr.color));
            }
        }
        if (tier > 0) jolt(0.5f, 220); // la muralla cede: se nota
    }

    /** Sacudida de cámara + vibración, salvo que el jugador las haya desactivado. */
    private void jolt(float duration, int vibrateMs) {
        if (!com.pcdc.td.ui.Quality.shake) return;
        cameraController.shake(duration);
        if (vibrateMs > 0) Gdx.input.vibrate(vibrateMs);
    }

    /** Polvo levantado por las orugas cuando el tanque gira la torreta. */
    /** Salva de celebración: chispas doradas que suben desde el cañón. */
    private void emitVictorySalvo(Tower t) {
        if (puffs.size > 200) return;
        game.sounds.playSfx("shoot_light"); // pop suave (con throttling propio)
        for (int n = 0; n < 3; n++) {
            Puff puff = puffPool.obtain();
            puff.x = t.position.x + MathUtils.random(-0.1f, 0.1f);
            puff.y = 0.7f;
            puff.z = t.position.z + MathUtils.random(-0.1f, 0.1f);
            puff.vx = MathUtils.random(-0.5f, 0.5f);
            puff.vy = MathUtils.random(2.2f, 3.4f);
            puff.vz = MathUtils.random(-0.5f, 0.5f);
            puff.maxLife = puff.life = MathUtils.random(0.5f, 0.8f);
            puff.size = MathUtils.random(0.06f, 0.1f);
            puff.kind = Puff.FIRE;
            puffs.add(puff);
        }
    }

    /** Polvareda tras las orugas de un tanque en traslado. */
    private void emitDriveDust(Tower t) {
        if (puffs.size > 200) return;
        float yawRad = t.driveYawDeg * MathUtils.degreesToRadians;
        float backX = -MathUtils.sin(yawRad), backZ = -MathUtils.cos(yawRad);
        for (int n = 0; n < 2; n++) {
            Puff puff = puffPool.obtain();
            float side = MathUtils.random(-0.34f, 0.34f);
            puff.x = t.position.x + backX * 0.45f - backZ * side;
            puff.y = MathUtils.random(0.05f, 0.16f);
            puff.z = t.position.z + backZ * 0.45f + backX * side;
            puff.vx = backX * MathUtils.random(0.4f, 0.9f) + MathUtils.random(-0.2f, 0.2f);
            puff.vy = MathUtils.random(0.2f, 0.5f);
            puff.vz = backZ * MathUtils.random(0.4f, 0.9f) + MathUtils.random(-0.2f, 0.2f);
            puff.maxLife = puff.life = MathUtils.random(0.5f, 0.9f);
            puff.size = MathUtils.random(0.09f, 0.16f);
            puff.kind = Puff.DUST;
            puffs.add(puff);
        }
    }

    private static final Color PORTAL_IN = Color.valueOf("c86ae8");
    private static final Color PORTAL_OUT = Color.valueOf("6ae8c8");

    /**
     * Portales de las ruinas: aro de ENTRADA (violeta) y de SALIDA (verde) con
     * galones recorriendo el salto, para que se vea de dónde a dónde van. La
     * mecánica ya existía pero era invisible.
     */
    private void assemblePortals() {
        com.pcdc.td.world.BiomeMechanics m = world.mechanics;
        portalRing(m.portalAPos, m.portalAYaw, PORTAL_IN);
        portalRing(m.portalBPos, m.portalBYaw, PORTAL_OUT);

        // tres galones viajando de la entrada a la salida, en bucle
        for (int i = 0; i < 3; i++) {
            float t = (stateTime * 0.45f + i / 3f) % 1f;
            ModelInstance arrow = obtainBeam(modelFactory.routeArrow());
            tmpV.set(m.portalAPos).lerp(m.portalBPos, t);
            float yaw = MathUtils.atan2Deg360(
                m.portalBPos.x - m.portalAPos.x, m.portalBPos.z - m.portalAPos.z);
            arrow.transform.setToTranslation(tmpV.x, 0.75f, tmpV.z)
                .rotate(Vector3.Y, yaw).scale(0.7f, 0.7f, 0.7f);
            // se desvanece al salir y al llegar, como un destello en tránsito
            tmpC.set(PORTAL_IN).lerp(PORTAL_OUT, t);
            tintInstance(arrow, tmpC, 0.75f * Math.min(1f, Math.min(t, 1f - t) * 6f));
            renderList.add(arrow);
        }
    }

    /**
     * Aro de portal PLANTADO CRUZANDO LA CALZADA (girando sobre sí mismo no
     * tenía sentido: es una puerta, no un molinillo). Se ensancha para cubrir
     * las dos celdas de ancho del camino.
     */
    private void portalRing(Vector3 at, float yaw, Color tint) {
        ModelInstance ring = obtainBeam(modelFactory.portalRing());
        float pulse = 1f + 0.04f * MathUtils.sin(stateTime * 3f);
        ring.transform.setToTranslation(at.x, 0.1f, at.z)
            .rotate(Vector3.Y, yaw)
            .scale(1.55f * pulse, pulse, pulse);
        tintInstance(ring, tint, 0.85f);
        renderList.add(ring);

        ModelInstance disc = obtainBeam(modelFactory.rangeDisc());
        float ds = 1.5f * pulse;
        disc.transform.setToTranslation(at.x, 0.085f, at.z).scale(ds, 1f, ds);
        tintInstance(disc, tint, 0.22f);
        renderList.add(disc);
    }

    /** Altura desde la que se suelta el tanque y punto en que se pliega la cúpula. */
    private static final float DROP_HEIGHT = 4.2f;

    /**
     * Reparto aéreo de un tanque recién comprado: el helicóptero lo suelta
     * sobre su casilla y se aleja, y el tanque baja en paracaídas. Devuelve la
     * altura extra a la que está el tanque en este instante (0 = en el suelo).
     */
    private float assembleAirdrop(Tower t) {
        float p = t.deployProgress();
        if (p >= 1f) return 0f;
        // rumbo propio por casilla: no entran todos por el mismo lado
        float ang = ((t.cellX * 47 + t.cellZ * 89) % 360) * MathUtils.degreesToRadians;
        float dx = MathUtils.sin(ang), dz = MathUtils.cos(ang);

        // caída frenada: cae rápido al principio y se posa suave
        float y = DROP_HEIGHT * (float) Math.pow(1f - p, 1.7f);

        // helicóptero: suelta sobre la casilla y se marcha ganando altura
        ModelInstance heli = obtainBeam(modelFactory.helicopter());
        float travel = p * 15f;
        heli.transform.setToTranslation(
                t.position.x + dx * travel,
                DROP_HEIGHT + 1.2f + p * 1.6f + MathUtils.sin(stateTime * 5f) * 0.05f,
                t.position.z + dz * travel)
            .rotate(Vector3.Y, MathUtils.atan2Deg360(dx, dz))
            .scale(0.85f, 0.85f, 0.85f);
        Node rotor = heli.getNode("rotor");
        if (rotor != null) {
            rotor.rotation.setFromAxis(0f, 1f, 0f, stateTime * 1500f);
            heli.calculateTransforms();
        }
        renderList.add(heli);

        // cúpula: se abre de golpe al soltar y se pliega al tocar suelo
        float open = Math.min(1f, p / 0.12f);
        float collapse = p > 0.86f ? Math.max(0f, 1f - (p - 0.86f) / 0.14f) : 1f;
        float cs = 0.6f * open * collapse;
        if (cs > 0.03f) {
            ModelInstance chute = obtainBeam(modelFactory.parachute());
            // la cúpula va sobre las cuerdas, que cuelgan hasta el tanque
            chute.transform.setToTranslation(t.position.x, y + 1.05f * collapse, t.position.z)
                .rotate(Vector3.Z, MathUtils.sin(stateTime * 2.4f + t.cellX) * 7f)
                .scale(cs, cs * (0.55f + 0.45f * collapse), cs);
            renderList.add(chute);
        }
        return y;
    }

    /** Polvareda del embestidor lanzado a la carrera. */
    private void emitChargeDust(Enemy e) {
        if (puffs.size > 200) return;
        float yawRad = e.yawDeg * MathUtils.degreesToRadians;
        Puff puff = puffPool.obtain();
        puff.x = e.position.x - MathUtils.sin(yawRad) * 0.4f;
        puff.y = MathUtils.random(0.05f, 0.2f);
        puff.z = e.position.z - MathUtils.cos(yawRad) * 0.4f;
        puff.vx = MathUtils.random(-0.5f, 0.5f);
        puff.vy = MathUtils.random(0.3f, 0.7f);
        puff.vz = MathUtils.random(-0.5f, 0.5f);
        puff.maxLife = puff.life = MathUtils.random(0.3f, 0.5f);
        puff.size = MathUtils.random(0.08f, 0.14f);
        puff.kind = Puff.DUST;
        puffs.add(puff);
    }

    /** Color del anillo de aura de una unidad insignia, o null si no tiene. */
    private Color specialAuraTint(String special) {
        return switch (special) {
            case "cloak" -> MIST_TINT;
            case "guardaura" -> WARD_TINT;
            case "chillaura" -> FROST_END;
            default -> null;
        };
    }

    /** Radio real del aura, para que el anillo no mienta. */
    private float specialAuraRadius(Enemy e) {
        if ("cloak".equals(e.special)) {
            return e.def.specialPower > 0 ? e.def.specialPower : 2.4f;
        }
        return 2.5f; // resonador y escarchador comparten radio
    }

    private void emitTrackDust(Tower t) {
        if (puffs.size > 200) return;
        Puff puff = puffPool.obtain();
        float side = MathUtils.randomBoolean() ? 0.37f : -0.37f;
        puff.x = t.position.x + side;
        puff.y = 0.06f;
        puff.z = t.position.z + MathUtils.random(-0.35f, 0.35f);
        puff.vx = MathUtils.random(-0.25f, 0.25f);
        puff.vy = MathUtils.random(0.12f, 0.3f);
        puff.vz = MathUtils.random(-0.25f, 0.25f);
        puff.maxLife = puff.life = MathUtils.random(0.35f, 0.6f);
        puff.size = MathUtils.random(0.06f, 0.11f);
        puff.kind = Puff.DUST;
        puffs.add(puff);
    }

    /** Ascuas radiales de una explosión: llamas que salen despedidas y se apagan. */
    private void emitExplosionEmbers(Fx f) {
        int cap = com.pcdc.td.ui.Quality.high ? 200 : 60;
        if (puffs.size > cap) return;
        int count = (com.pcdc.td.ui.Quality.high ? 12 : 7) + (f.fiery ? 4 : 0);
        for (int n = 0; n < count; n++) {
            Puff puff = puffPool.obtain();
            float yaw = MathUtils.random(0f, MathUtils.PI2);
            float speed = MathUtils.random(1.4f, 3.2f) * (0.5f + f.size * 0.4f);
            puff.x = f.from.x;
            puff.y = 0.28f;
            puff.z = f.from.z;
            puff.vx = MathUtils.sin(yaw) * speed;
            puff.vy = MathUtils.random(0.6f, 2.4f);
            puff.vz = MathUtils.cos(yaw) * speed;
            puff.maxLife = puff.life = MathUtils.random(0.3f, 0.55f) * (f.fiery ? 1.35f : 1f);
            puff.size = MathUtils.random(0.09f, 0.17f);
            puff.kind = Puff.FIRE;
            puffs.add(puff);
        }
    }

    private static final Color ZONE_POISON = Color.valueOf("46b428");
    private static final Color ZONE_ICE = Color.valueOf("7fd8e8");
    private static final Color ZONE_NAPALM = Color.valueOf("e05c2a");
    private static final Color POISON_DARK = Color.valueOf("2a6e1a");

    private Color zoneColor(String kind) {
        switch (kind) {
            case "poison": return ZONE_POISON;
            case "napalm": return ZONE_NAPALM;
            default: return ZONE_ICE;
        }
    }

    /** Goteo de partículas de una zona de suelo: burbujas, destellos o llamas. */
    private void emitZoneParticles(com.pcdc.td.world.GroundZone z) {
        int cap = com.pcdc.td.ui.Quality.high ? 200 : 60;
        if (puffs.size > cap) return;
        // goteo suave, no un chorro: una partícula ocasional por zona y frame
        if (MathUtils.random() > (com.pcdc.td.ui.Quality.high ? 0.22f : 0.1f)) return;
        Puff puff = puffPool.obtain();
        float ang = MathUtils.random(MathUtils.PI2);
        float r = z.radius * (float) Math.sqrt(MathUtils.random());
        puff.x = z.position.x + MathUtils.cos(ang) * r;
        puff.z = z.position.z + MathUtils.sin(ang) * r;
        puff.y = 0.12f;
        puff.vx = MathUtils.random(-0.1f, 0.1f);
        puff.vz = MathUtils.random(-0.1f, 0.1f);
        switch (z.kind) {
            case "poison":
                puff.kind = Puff.POISON;
                puff.vy = MathUtils.random(0.3f, 0.6f);
                puff.maxLife = puff.life = MathUtils.random(0.5f, 0.9f);
                puff.size = MathUtils.random(0.07f, 0.13f);
                break;
            case "napalm":
                puff.kind = Puff.FIRE;
                puff.vy = MathUtils.random(0.7f, 1.3f);
                puff.maxLife = puff.life = MathUtils.random(0.4f, 0.7f);
                puff.size = MathUtils.random(0.08f, 0.15f);
                break;
            default: // hielo: destellos que apenas se elevan
                puff.kind = Puff.FROST;
                puff.vy = MathUtils.random(0.1f, 0.25f);
                puff.maxLife = puff.life = MathUtils.random(0.4f, 0.8f);
                puff.size = MathUtils.random(0.05f, 0.1f);
                break;
        }
        puffs.add(puff);
    }

    /** Chispas metálicas de la trituradora mientras corta. */
    private void emitSawSparks(Tower t) {
        int cap = com.pcdc.td.ui.Quality.high ? 200 : 60;
        if (puffs.size > cap || MathUtils.random() > 0.35f) return;
        Puff puff = puffPool.obtain();
        float ang = MathUtils.random(MathUtils.PI2);
        puff.x = t.position.x + MathUtils.cos(ang) * 0.45f;
        puff.z = t.position.z + MathUtils.sin(ang) * 0.45f;
        puff.y = 0.55f;
        puff.vx = MathUtils.cos(ang) * MathUtils.random(1.2f, 2.2f);
        puff.vz = MathUtils.sin(ang) * MathUtils.random(1.2f, 2.2f);
        puff.vy = MathUtils.random(-0.4f, 0.8f);
        puff.maxLife = puff.life = MathUtils.random(0.15f, 0.3f);
        puff.size = MathUtils.random(0.04f, 0.08f);
        puff.kind = Puff.FIRE; // se tiñe de amarillo chispa por su vida corta
        puffs.add(puff);
    }

    /** Emite partículas de fuego (o escarcha) desde la boca de una torre de área. */
    private void emitFlameParticles(Tower t) {
        int cap = com.pcdc.td.ui.Quality.high ? 200 : 60;
        if (puffs.size > cap) return;
        // el chorro se tiñe por lo que escupe: llama, escarcha o ácido
        int kind = Puff.FIRE;
        for (int j = 0; j < t.def.effects.size; j++) {
            String type = t.def.effects.get(j).type;
            if ("slow".equals(type)) kind = Puff.FROST;
            else if ("poison".equals(type)) kind = Puff.POISON;
        }
        Vector3 muzzle = t.muzzle();
        for (int n = 0; n < 3; n++) {
            Puff puff = puffPool.obtain();
            float yawRad = (t.headYawDeg + MathUtils.random(-t.def.coneAngleDeg, t.def.coneAngleDeg) * 0.8f)
                * MathUtils.degreesToRadians;
            float speed = MathUtils.random(2.4f, 3.8f) * (t.def.range / 2.2f);
            puff.x = muzzle.x + MathUtils.sin(yawRad) * 0.3f;
            puff.y = muzzle.y + MathUtils.random(-0.08f, 0.08f);
            puff.z = muzzle.z + MathUtils.cos(yawRad) * 0.3f;
            puff.vx = MathUtils.sin(yawRad) * speed;
            puff.vy = MathUtils.random(0.2f, 0.9f);
            puff.vz = MathUtils.cos(yawRad) * speed;
            puff.maxLife = puff.life = MathUtils.random(0.28f, 0.42f) * (t.def.range / 2.2f);
            puff.size = MathUtils.random(0.09f, 0.16f);
            puff.kind = kind;
            puffs.add(puff);
        }
    }

    private ModelInstance obtainBeam(Model model) {
        // pool de instancias efímeras (rayos, tracers, conos) reutilizadas por frame
        if (beamsUsed < beamPool.size) {
            ModelInstance inst = beamPool.get(beamsUsed);
            if (inst.model != model) {
                inst = new ModelInstance(model);
                beamPool.set(beamsUsed, inst);
            }
            beamsUsed++;
            return inst;
        }
        ModelInstance inst = new ModelInstance(model);
        beamPool.add(inst);
        beamsUsed++;
        return inst;
    }

    private void setBeamTransform(ModelInstance inst, Vector3 from, Vector3 to, float thickness) {
        float dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        float h = (float) Math.sqrt(dx * dx + dz * dz);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float yaw = MathUtils.atan2Deg360(dx, dz);
        float pitch = -MathUtils.atan2Deg360(dy, h);
        inst.transform.setToTranslation(from)
            .rotate(Vector3.Y, yaw)
            .rotate(Vector3.X, pitch)
            .scale(thickness, thickness, len);
    }

    private void tintInstance(ModelInstance inst, Color color, float alpha) {
        ColorAttribute ca = (ColorAttribute) inst.materials.first().get(ColorAttribute.Diffuse);
        if (ca != null) ca.color.set(color);
        BlendingAttribute ba = (BlendingAttribute) inst.materials.first().get(BlendingAttribute.Type);
        if (ba != null) ba.opacity = alpha;
    }

    // ---- GameplayHud.Listener ----

    @Override
    public void onBuildSelected(String towerId) {
        game.sounds.playSfx("click");
        selectedPlaced = null;
        hud.showTowerPanel(null, 0, -1, null);
        hud.showBuildInfo(towerId != null ? buildInfoText(game.catalogs.tower(towerId)) : null);
    }

    /** Ficha de una torre de la barra: nombre, tipo, objetivos, stats y efectos. */
    private String buildInfoText(TowerDef def) {
        StringBuilder sb = new StringBuilder();
        sb.append(com.pcdc.td.ui.Lang.name("tower", def.id, def.name))
            .append("  ·  ").append(def.cost).append('\n');
        sb.append(com.pcdc.td.ui.Lang.get("attack." + def.attackType.toLowerCase()));
        sb.append("  ·  ").append(com.pcdc.td.ui.Lang.get(def.hitsAir ? "targets.both" : "targets.ground"));
        sb.append('\n').append("DMG ").append((int) (def.damage * game.stats.damageMult));
        sb.append("  ·  RNG ").append(String.format(com.pcdc.td.ui.Lang.locale(),
            "%.1f", def.range * game.stats.rangeMult));
        if (def.type() == TowerDef.AttackType.PROJECTILE || def.type() == TowerDef.AttackType.HITSCAN) {
            sb.append("  ·  ").append(String.format(com.pcdc.td.ui.Lang.locale(),
                "%.1f/s", def.fireRate * game.stats.fireRateMult));
        }
        if (def.penetration > 0f) {
            sb.append("  ·  PEN ").append((int) def.penetration);
        }
        boolean firstExtra = true;
        for (int i = 0; i < def.effects.size; i++) {
            sb.append(firstExtra ? "\n" : "  ·  ");
            firstExtra = false;
            sb.append(com.pcdc.td.ui.Lang.get("effect." + def.effects.get(i).type));
        }
        if (def.chainCount > 0) {
            sb.append(firstExtra ? "\n" : "  ·  ");
            firstExtra = false;
            sb.append(com.pcdc.td.ui.Lang.get("effect.chain"));
        }
        if (def.rampPerSecond > 0f) {
            sb.append(firstExtra ? "\n" : "  ·  ");
            firstExtra = false;
            sb.append(com.pcdc.td.ui.Lang.get(
                def.sustainedRamp ? "effect.ramp_kept" : "effect.ramp"));
        }
        if (def.pullDistance > 0f) {
            sb.append(firstExtra ? "\n" : "  ·  ");
            firstExtra = false;
            sb.append(com.pcdc.td.ui.Lang.get("manual.pull"));
        }
        if (def.pulseInterval > 0f) {
            sb.append(firstExtra ? "\n" : "  ·  ");
            firstExtra = false;
            sb.append(com.pcdc.td.ui.Lang.get("effect.pulse"));
        }
        if (def.pierce) {
            sb.append(firstExtra ? "\n" : "  ·  ");
            firstExtra = false;
            sb.append(com.pcdc.td.ui.Lang.get("effect.pierce"));
        }
        if (def.groundDuration > 0f) {
            sb.append(firstExtra ? "\n" : "  ·  ");
            firstExtra = false;
            sb.append(com.pcdc.td.ui.Lang.get("effect.ground"));
        }
        if (!def.zoneKind.isEmpty()) {
            sb.append(firstExtra ? "\n" : "  ·  ");
            sb.append(com.pcdc.td.ui.Lang.get("effect.zone_" + def.zoneKind));
        }
        return sb.toString();
    }

    @Override
    public void onCallWave() {
        game.sounds.playSfx("click");
        world.callNextWave();
        tutorialEvent("callwave");
    }

    @Override
    public void onRotateCamera() {
        game.sounds.playSfx("click");
        cameraController.rotate90();
    }

    @Override
    public void onSpeedChanged(float multiplier) {
        game.sounds.playSfx("click");
        speedMultiplier = multiplier;
        Gdx.app.getPreferences("td-settings").putFloat("gameSpeed", multiplier).flush();
    }

    @Override
    public void onCycleTargeting() {
        if (selectedPlaced == null) return;
        game.sounds.playSfx("click");
        // el modo AIRE solo se ofrece a quien puede disparar hacia arriba
        int modes = selectedPlaced.def.hitsAir ? Tower.TARGET_MODES : Tower.TARGET_MODES - 1;
        selectedPlaced.targeting = (selectedPlaced.targeting + 1) % modes;
        refreshTowerPanel();
    }

    @Override
    public void onUpgrade() {
        if (selectedPlaced == null) return;
        if (world.tryUpgrade(selectedPlaced)) {
            refreshTowerPanel();
            tutorialEvent("upgrade");
        } else {
            game.sounds.playSfx("denied");
        }
    }

    private void refreshTowerPanel() {
        hud.showTowerPanel(selectedPlaced, world.sellRefund(selectedPlaced),
            world.upgradeCost(selectedPlaced), towerStats(selectedPlaced));
    }

    /** ¿Puede construirse este tanque en este nivel (lista del nivel o árbol)? */
    private boolean isTowerAllowed(String id) {
        if (!world.survival && level.allowedTowers.size > 0) {
            return level.allowedTowers.contains(id, false);
        }
        return game.stats.towerAvailable(id);
    }

    /** Datos estructurados del panel: stats, líneas de estado y recetas. */
    private com.pcdc.td.ui.GameplayHud.PanelInfo towerStats(Tower t) {
        com.pcdc.td.ui.GameplayHud.PanelInfo info = new com.pcdc.td.ui.GameplayHud.PanelInfo();
        info.dmg = world.damageFor(t);
        info.rng = world.rangeFor(t);
        info.rate = world.fireRateFor(t);
        info.pen = world.penFor(t);
        info.dps = t.dps();
        info.kills = t.kills;
        // qué se lleva por el oro de Mejorar: se mide con un tanque fantasma
        // del nivel siguiente, igual que la comparativa de fusión
        if (world.upgradeCost(t) >= 0) {
            Tower ghost = new Tower(t.cellX, t.cellZ, t.def, 0);
            ghost.level = t.level + 1;
            info.nextDmg = world.damageFor(ghost);
            info.nextRng = world.rangeFor(ghost);
            info.nextRate = world.fireRateFor(ghost);
        }

        StringBuilder extra = new StringBuilder();
        if (t.driving()) extra.append(com.pcdc.td.ui.Lang.get("hud.driving"));
        if (world.underCommandAura(t)) {
            if (extra.length() > 0) extra.append('\n');
            extra.append(com.pcdc.td.ui.Lang.get("hud.boosted"));
        }
        // las fusiones heredan la mitad de las mejoras de sus dos padres
        com.pcdc.td.data.FusionDef parents = game.catalogs.fusionByResult(t.def.id);
        if (parents != null) {
            if (extra.length() > 0) extra.append('\n');
            extra.append(com.pcdc.td.ui.Lang.fmt("hud.inherit",
                com.pcdc.td.ui.Lang.name("tower", parents.a, parents.a),
                com.pcdc.td.ui.Lang.name("tower", parents.b, parents.b)));
        }
        info.extra = extra.length() > 0 ? extra.toString() : null;

        // recetas desbloqueadas que parten de esta torre → chips tocables
        for (com.pcdc.td.data.FusionDef f : game.catalogs.fusions) {
            String partner = f.a.equals(t.def.id) ? f.b : f.b.equals(t.def.id) ? f.a : null;
            if (partner == null) continue;
            if (!game.stats.unlockedFusions.contains(f.result)) continue;
            if (!isTowerAllowed(partner)) continue;
            com.pcdc.td.ui.GameplayHud.FusionOption op =
                new com.pcdc.td.ui.GameplayHud.FusionOption();
            op.partnerId = partner;
            op.resultName = com.pcdc.td.ui.Lang.name("tower", f.result, f.result);
            info.fusions.add(op);
        }
        return info;
    }

    @Override
    public void onTutorialDismissed() {
        game.sounds.playSfx("click");
        tutorialIndex++;
        applyTutorialStep();
    }

    @Override
    public void onBuildDragMoved(String towerId, float screenX, float screenY) {
        updateHover(screenX, screenY);
    }

    @Override
    public void onBuildDragDropped(String towerId, float screenX, float screenY) {
        if (world.state != GameWorld.State.RUNNING) return;
        if (!picker.pick(renderer.camera, world.map, screenX, screenY, pickedCell)) {
            // soltó fuera del tablero: se mantiene la selección para el flujo por toque
            highlightVisible = false;
            return;
        }
        TowerDef def = game.catalogs.tower(towerId);
        boolean isFusion = world.fusionAt(pickedCell.x, pickedCell.y, def) != null;
        if (world.tryPlace(pickedCell.x, pickedCell.y, def)) {
            tutorialEvent(isFusion ? "fusion" : "build");
            if (!isFusion) lastPlacedTower = world.towerAt(pickedCell.x, pickedCell.y);
            afterPlace(def);
            hud.setSelectedBuild(null);
            hud.showBuildInfo(null);
        } else {
            game.sounds.playSfx("denied");
        }
        highlightVisible = false;
        hoverValid = false;
    }

    @Override
    public void onExit() {
        game.sounds.playSfx("click");
        // salir ya no cuesta la partida: se guarda para poder continuarla
        if (canCheckpoint()) saveCheckpoint();
        game.setScreen(new LevelSelectScreen(game));
        dispose();
    }

    @Override
    public boolean canResumeLater() {
        return canCheckpoint();
    }

    @Override
    public void onRestart() {
        game.sounds.playSfx("click");
        game.saveManager.clearCheckpoint();
        // mismo trato que Reintentar en resultados: colocación inicial conservada
        requestRetryLoadout();
        game.setScreen(new GameplayScreen(game, level.id, world.survival, plusMode, null));
        dispose();
    }

    @Override
    public void onUndoPlace() {
        if (lastPlacedTower == null || !world.undoPlace(lastPlacedTower)) {
            game.sounds.playSfx("denied");
            return;
        }
        if (selectedPlaced == lastPlacedTower) deselectPlaced();
        lastPlacedTower = null;
        game.sounds.playSfx("sell");
    }

    @Override
    public void onMove() {
        if (selectedPlaced == null) return;
        if (!world.relocateAllowed() || selectedPlaced.driving()) {
            game.sounds.playSfx("denied");
            return;
        }
        game.sounds.playSfx("click");
        movingTower = selectedPlaced;
        hud.setNotice(com.pcdc.td.ui.Lang.get("hud.move_hint"));
    }

    @Override
    public void onSell() {
        if (selectedPlaced != null) {
            world.sell(selectedPlaced);
            selectedPlaced = null;
            hud.showTowerPanel(null, 0, -1, null);
            tutorialEvent("sell");
        }
    }

    // ---- CameraController.TapListener ----

    @Override
    public void onTap(float screenX, float screenY) {
        if (world.state != GameWorld.State.RUNNING) return;
        if (cameraController.isCinematic()) {
            cameraController.stopCinematic();
            return;
        }
        tutorialEvent("tap");

        if (!picker.pick(renderer.camera, world.map, screenX, screenY, pickedCell)) {
            deselectPlaced();
            return;
        }

        // meteorito apuntado: cae donde toques
        if (aimingMeteor) {
            if (world.castMeteor(pickedCell.x + 0.5f, pickedCell.y + 0.5f)) {
                jolt(0.3f, 0);
            }
            aimingMeteor = false;
            hud.setAbilityAiming(0, false);
            return;
        }

        // reubicación armada: este toque intenta mover el tanque
        if (movingTower != null) {
            Tower moving = movingTower;
            movingTower = null;
            hud.setNotice(null);
            if (world.tryRelocate(moving, pickedCell.x, pickedCell.y)) {
                selectedPlaced = moving;
                refreshTowerPanel();
            } else {
                game.sounds.playSfx("denied");
                // sin ruta libre: decir POR QUÉ, o parece que el botón falla
                if (world.lastRelocateBlocked) {
                    hud.setNotice(com.pcdc.td.ui.Lang.get("hud.move_blocked"));
                }
            }
            return;
        }

        String buildId = hud.selectedBuild();
        if (buildId != null) {
            TowerDef def = game.catalogs.tower(buildId);
            boolean isFusion = world.fusionAt(pickedCell.x, pickedCell.y, def) != null;
            if (world.tryPlace(pickedCell.x, pickedCell.y, def)) {
                tutorialEvent(isFusion ? "fusion" : "build");
                if (!isFusion) lastPlacedTower = world.towerAt(pickedCell.x, pickedCell.y);
                afterPlace(def);
                hud.setSelectedBuild(null); // colocar deselecciona la torreta
                hud.showBuildInfo(null);
            } else {
                game.sounds.playSfx("denied");
            }
            updateHover(screenX, screenY);
        } else {
            Tower t = world.towerAt(pickedCell.x, pickedCell.y);
            selectedPlaced = t;
            hud.showTowerPanel(t, t != null ? world.sellRefund(t) : 0,
                t != null ? world.upgradeCost(t) : -1, t != null ? towerStats(t) : null);
            // sin tanque en la celda: ¿ha tocado un enemigo? Carta informativa
            if (t == null) {
                Enemy tapped = nearestEnemyTo(pickedCell.x + 0.5f, pickedCell.y + 0.5f);
                if (tapped != null) {
                    hud.showBuildInfo(enemyCardText(tapped));
                    enemyCardTimer = 5f;
                } else if (enemyCardTimer > 0f) {
                    enemyCardTimer = 0f;
                    hud.showBuildInfo(null);
                }
            }
        }
    }

    /** Carta de enemigo: se cierra sola a los pocos segundos. */
    private float enemyCardTimer;
    /** Segundos de tablero limpio antes de que el AUTO llame a la siguiente. */
    private float autoWaveCalm;

    private Enemy nearestEnemyTo(float x, float z) {
        Enemy best = null;
        float bestD2 = 0.9f * 0.9f;
        for (int i = 0; i < world.enemies.size; i++) {
            Enemy e = world.enemies.get(i);
            if (!e.alive) continue;
            float dx = e.position.x - x, dz = e.position.z - z;
            float d2 = dx * dx + dz * dz;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = e;
            }
        }
        return best;
    }

    /** Nombre, vida, armadura, rasgo y cómo contrarrestarlo. */
    private String enemyCardText(Enemy e) {
        StringBuilder sb = new StringBuilder();
        sb.append(com.pcdc.td.ui.Lang.name("enemy", e.def.id, e.def.id));
        if (e.crystalElite) sb.append(" (").append(com.pcdc.td.ui.Lang.get("card.elite")).append(')');
        sb.append(String.format(com.pcdc.td.ui.Lang.locale(), "  ·  %s %.0f/%.0f",
            com.pcdc.td.ui.Lang.get("card.hp"), e.hp, e.maxHp));
        if (e.armor() > 0f) sb.append("  ·  ARM ").append((int) e.armor());
        if (!"none".equals(e.trait)) {
            sb.append('\n').append(com.pcdc.td.ui.Lang.get("trait." + e.trait));
        }
        // el truco de la unidad insignia y el oficio mandan sobre el consejo
        // genérico: es lo que hay que saber ya
        if (!"none".equals(e.special)) {
            sb.append('\n').append(com.pcdc.td.ui.Lang.get("special." + e.special));
        } else if (!"none".equals(e.def.role)) {
            sb.append('\n').append(com.pcdc.td.ui.Lang.get("card." + e.def.role + "_hint"));
        } else if (e.armor() > 0f) {
            sb.append('\n').append(com.pcdc.td.ui.Lang.get("card.armor_hint"));
        } else if (e.isFlying()) {
            sb.append('\n').append(com.pcdc.td.ui.Lang.get("card.air_hint"));
        }
        return sb.toString();
    }

    /** Chip de la previa tocado: carta del enemigo ANTES de que salga del portal. */
    @Override
    public void onPreviewEnemyTapped(String enemyId) {
        com.pcdc.td.data.EnemyDef def = game.catalogs.enemy(enemyId);
        if (def == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(com.pcdc.td.ui.Lang.name("enemy", def.id, def.id));
        sb.append(String.format(com.pcdc.td.ui.Lang.locale(), "  ·  %s %.0f",
            com.pcdc.td.ui.Lang.get("card.hp"), def.hp * world.previewHpMult()));
        if (def.armor > 0f) sb.append("  ·  ARM ").append((int) def.armor);
        if (!"none".equals(def.role)) {
            sb.append('\n').append(com.pcdc.td.ui.Lang.get("card." + def.role + "_hint"));
        } else if (def.armor > 0f) {
            sb.append('\n').append(com.pcdc.td.ui.Lang.get("card.armor_hint"));
        } else if (def.flying) {
            sb.append('\n').append(com.pcdc.td.ui.Lang.get("card.air_hint"));
        }
        hud.showBuildInfo(sb.toString());
        enemyCardTimer = 5f;
    }

    @Override
    public void onPointerMoved(float screenX, float screenY) {
        updateHover(screenX, screenY);
    }

    @Override
    public void onPauseChanged(boolean isPaused) {
        // una pausa por perder el foco no la ha pulsado nadie: no suena
        if (!autoPausing) game.sounds.playSfx("click");
        this.paused = isPaused;
        // guardar al pausar cubre el cierre de la app en Android (y salir al menú)
        if (isPaused && canCheckpoint()) saveCheckpoint();
    }

    /** Al ir a segundo plano (Android) se abre el panel de pausa. */
    @Override
    public void pause() {
        autoPause();
    }

    /** Pausa automática por pérdida de foco; no hace nada si ya está pausado. */
    private boolean autoPausing;

    public void autoPause() {
        if (world.state == GameWorld.State.RUNNING && !hud.isPaused()) {
            autoPausing = true;
            hud.setPaused(true);
            autoPausing = false;
        }
    }

    @Override
    public void onAbility(int index) {
        // sin cargas el botón recarga en vez de lanzar
        if (world.abilityCharges[index] <= 0) {
            if (world.buyAbilityCharge(index)) {
                hud.update(world); // el sonido de compra lo emite el mundo
            } else {
                game.sounds.playSfx("denied");
            }
            return;
        }
        game.sounds.playSfx("click");
        queueTip("ability_ammo");
        switch (index) {
            case 0:
                // el meteorito se lanza tocando el mapa
                aimingMeteor = !aimingMeteor;
                hud.setAbilityAiming(0, aimingMeteor);
                break;
            case 1:
                world.castFrost();
                break;
            case 2:
                world.castGoldRush();
                break;
        }
    }

    private void deselectPlaced() {
        selectedPlaced = null;
        hud.showTowerPanel(null, 0, -1, null);
    }

    private void updateHover(float screenX, float screenY) {
        hoverValid = picker.pick(renderer.camera, world.map, screenX, screenY, hoverCell);
        highlightVisible = hoverValid;
        if (!hoverValid) return;

        String buildId = hud.selectedBuild();
        if (buildId != null) {
            TowerDef def = game.catalogs.tower(buildId);
            boolean affordable = world.gold >= def.cost;
            com.pcdc.td.data.FusionDef recipe = world.fusionAt(hoverCell.x, hoverCell.y, def);
            if (recipe != null && affordable) {
                highlightColor.color.set(Color.valueOf("ffd54a")); // fusión disponible
                // ficha comparativa: qué gana el tanque si confirmas la fusión
                showFusionCompare(world.towerAt(hoverCell.x, hoverCell.y), recipe);
            } else {
                if (fusionCompareShown) {
                    fusionCompareShown = false;
                    hud.showBuildInfo(buildInfoText(def)); // vuelve la ficha normal
                }
                if (world.canBuildAt(hoverCell.x, hoverCell.y) && affordable) {
                    highlightColor.color.set(Color.valueOf("7dff7d"));
                } else {
                    highlightColor.color.set(Color.valueOf("ff6d6d"));
                }
            }
        } else {
            highlightColor.color.set(Color.valueOf("ffffff"));
        }
        highlight.transform.setToTranslation(hoverCell.x + 0.5f, 0.08f, hoverCell.y + 0.5f);
    }

    private boolean fusionCompareShown;

    /**
     * Ficha comparativa de fusión: stats del tanque actual → los del resultado
     * (con las mejoras del árbol aplicadas, vía un Tower temporal).
     */
    private void showFusionCompare(Tower existing, com.pcdc.td.data.FusionDef recipe) {
        fusionCompareShown = true;
        TowerDef fused = game.catalogs.tower(recipe.result);
        Tower ghost = new Tower(existing.cellX, existing.cellZ, fused, 0);
        ghost.level = existing.level; // la fusión conserva el nivel pagado
        StringBuilder sb = new StringBuilder();
        sb.append(com.pcdc.td.ui.Lang.get("hud.fusion_preview")).append(' ')
            .append(com.pcdc.td.ui.Lang.name("tower", fused.id, fused.name)).append('\n');
        sb.append(String.format(com.pcdc.td.ui.Lang.locale(),
            "DMG %.0f > %.0f  ·  RNG %.1f > %.1f  ·  CAD %.1f > %.1f",
            world.damageFor(existing), world.damageFor(ghost),
            world.rangeFor(existing), world.rangeFor(ghost),
            world.fireRateFor(existing), world.fireRateFor(ghost)));
        boolean extras = false;
        StringBuilder line = new StringBuilder();
        if (world.penFor(ghost) > world.penFor(existing)) {
            line.append("PEN ").append((int) world.penFor(existing))
                .append(" > ").append((int) world.penFor(ghost));
            extras = true;
        }
        if (fused.splashRadius > 0f && existing.def.splashRadius <= 0f) {
            if (extras) line.append("  ·  ");
            line.append(com.pcdc.td.ui.Lang.get("hud.gains_splash"));
            extras = true;
        }
        for (int i = 0; i < fused.effects.size; i++) {
            if (extras) line.append("  ·  ");
            line.append(com.pcdc.td.ui.Lang.get("effect." + fused.effects.get(i).type));
            extras = true;
        }
        if (extras) sb.append('\n').append(line);
        hud.showBuildInfo(sb.toString());
    }

    @Override
    public void resize(int width, int height) {
        renderer.resize(width, height);
        hud.resize(width, height);
    }

    @Override
    public void hide() {
        game.sounds.setMusicDuck(1f);
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        modelFactory.dispose();
        renderer.dispose();
        healthBars.dispose();
        floatingText.dispose();
        hud.dispose();
        iconRenderer.dispose();
    }
}
