package com.pcdc.td.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.pcdc.td.data.Balance;
import com.pcdc.td.data.Catalogs;
import com.pcdc.td.data.EnemyDef;
import com.pcdc.td.data.FusionDef;
import com.pcdc.td.data.LevelDef;
import com.pcdc.td.data.TowerDef;

/**
 * Simulación completa de un nivel, sin nada de render. Se avanza con step()
 * a timestep fijo desde GameplayScreen.
 */
public class GameWorld {

    public static final float STEP = 1f / 60f;

    public enum State { RUNNING, VICTORY, DEFEAT }

    public final LevelDef level;
    public final Catalogs catalogs;
    public final GridMap map;
    public final WaveManager waves;
    public final com.pcdc.td.data.BiomeDef biome;
    public final BiomeMechanics mechanics;

    public final Array<Enemy> enemies = new Array<>(false, 64);
    public final Array<Tower> towers = new Array<>();
    public final Array<Projectile> projectiles = new Array<>(false, 128);
    public final Array<Fx> fx = new Array<>(false, 32);
    /** Zonas persistentes en el suelo (charcas, hielo, napalm). */
    public final Array<GroundZone> zones = new Array<>(false, 16);
    /** Eventos de audio del paso actual; los consume y limpia la capa de render. */
    public final Array<String> soundEvents = new Array<>(false, 32);

    private void sound(String id) {
        if (soundEvents.size < 32) soundEvents.add(id);
    }

    /**
     * Modificadores DE ESTA PARTIDA (cartas del modo sin fin). No tocan el
     * perfil ni el StatBlock global: mueren con el nivel.
     */
    public float runDamageMult = 1f, runRangeMult = 1f, runGoldMult = 1f;
    public float runEffectMult = 1f, runPen = 0f, runUpgradeDiscount = 0f;
    public float runEnemyArmorBonus = 0f, runEnemySpeedMult = 1f;
    /** Cartas ya elegidas (para no repetirlas en la siguiente ronda). */
    public final Array<String> boonsTaken = new Array<>();

    public int gold;
    public int lives;
    public final int startLives;
    public State state = State.RUNNING;

    private final float hpMult, goldMult;
    /** Nivel+ activo: los jefes ganan un afijo aleatorio. */
    private final boolean plusMode;
    /** Polvo del asalto a la muralla (roca del bioma aclarada). */
    private final com.badlogic.gdx.graphics.Color wallDustColor;
    private static final String[] AFFIXES = {"armored", "swift", "regen", "iron"};
    private final Tower[] towerGrid;
    private final Pool<Enemy> enemyPool = new Pool<Enemy>() {
        @Override
        protected Enemy newObject() {
            return new Enemy();
        }
    };
    private final Pool<Projectile> projectilePool = new Pool<Projectile>() {
        @Override
        protected Projectile newObject() {
            return new Projectile();
        }
    };
    private final Pool<Fx> fxPool = new Pool<Fx>() {
        @Override
        protected Fx newObject() {
            return new Fx();
        }
    };
    private final Pool<GroundZone> zonePool = new Pool<GroundZone>() {
        @Override
        protected GroundZone newObject() {
            return new GroundZone();
        }
    };
    private final Vector3 tmp = new Vector3();
    private final Vector3 tmp2 = new Vector3();

    public final com.pcdc.td.meta.StatBlock stats;
    /** Modo sin fin: oleadas generadas proceduralmente, sin victoria. */
    public final boolean survival;
    private float survivalScale = 1f;

    // contadores para el resumen, los logros y los retos
    public int totalKills;
    public int fusionsMade;
    public int towersSold;
    /** Habilidades lanzadas y máximo de tanques a la vez (retos del nivel). */
    public int abilitiesUsed;
    public int peakTowers;
    /** Contadores para los logros: élites y campeones abatidos, sinergias. */
    public int elitesKilled;
    public int championsKilled;
    public int synergiesTriggered;

    // habilidades activas: meteorito, escarcha, fiebre del oro
    public static final float[] ABILITY_COOLDOWN = {45f, 60f, 90f};
    public final float[] abilityCooldown = new float[3];
    /** Cargas de cada habilidad: se gastan al usarla y se recompran con oro. */
    public static final int[] ABILITY_START_CHARGES = {2, 2, 1};
    public static final int[] ABILITY_RELOAD_COST = {120, 150, 200};
    public final int[] abilityCharges = ABILITY_START_CHARGES.clone();

    /** Recarga una habilidad pagando oro; false si no llega o ya está llena. */
    public boolean buyAbilityCharge(int index) {
        if (abilityCharges[index] >= ABILITY_START_CHARGES[index]) return false;
        int cost = ABILITY_RELOAD_COST[index];
        if (gold < cost) return false;
        gold -= cost;
        abilityCharges[index]++;
        sound("buy");
        return true;
    }
    private float goldRushTimer;
    private float radarPingTimer;
    // caída escénica del jefe final
    private float overlordFxTimer;
    private int overlordFxLeft;
    private final Vector3 overlordFxPos = new Vector3();
    /** La pantalla lo consume: sacudida larga al caer el Corruptor. */
    public boolean overlordDied;
    private static final com.pcdc.td.data.EffectDef FROST_EFFECT = new com.pcdc.td.data.EffectDef();

    static {
        FROST_EFFECT.type = "slow";
        FROST_EFFECT.factor = 0.35f;
        FROST_EFFECT.duration = 5f;
    }

    public GameWorld(LevelDef level, Catalogs catalogs, com.pcdc.td.meta.StatBlock stats) {
        this(level, catalogs, stats, false, 1f);
    }

    public GameWorld(LevelDef level, Catalogs catalogs, com.pcdc.td.meta.StatBlock stats, boolean survival) {
        this(level, catalogs, stats, survival, 1f);
    }

    public GameWorld(LevelDef level, Catalogs catalogs, com.pcdc.td.meta.StatBlock stats,
                     boolean survival, float difficultyMult) {
        this.level = level;
        this.catalogs = catalogs;
        this.stats = stats;
        this.survival = survival;
        this.map = buildMap(level);
        this.waves = new WaveManager(level, survival);
        this.gold = level.startGold + stats.startGoldFlat;
        // modo relajado: +50% de vidas y nada más (el resto del equilibrio,
        // idéntico); los resultados lo marcan para no falsear las estrellas
        int baseLives = level.lives + stats.livesFlat;
        this.lives = this.startLives = com.pcdc.td.ui.Quality.relaxed
            ? Math.round(baseLives * 1.5f) : baseLives;
        this.hpMult = Balance.enemyHpMult(level.id) * difficultyMult;
        this.plusMode = difficultyMult > 1.001f;
        this.goldMult = Balance.goldMult(level.id) * stats.goldPerKillMult;
        this.towerGrid = new Tower[map.width * map.height];
        this.biome = catalogs.biome(level.biome);
        this.wallDustColor = new com.badlogic.gdx.graphics.Color(biome.rock())
            .lerp(com.badlogic.gdx.graphics.Color.WHITE, 0.3f);
        this.mechanics = new BiomeMechanics(this, biome, level.id);
    }

    private static GridMap buildMap(LevelDef level) {
        GridMap map = new GridMap(level.gridWidth, level.gridHeight);
        for (LevelDef.PathDef p : level.paths) map.addPath(p.waypoints);
        for (int[] c : level.blockedCells) map.block(c[0], c[1]);
        return map;
    }

    public void step(float dt) {
        if (state != State.RUNNING) {
            // EPÍLOGO: sin esto, la traca del Corruptor moría muda (VICTORY se
            // declara en el mismo step que su muerte y ya no se procesaba) y
            // los fx en vuelo se congelaban en pantalla durante el desfile
            stepOverlordFireworks(dt);
            decayFx(dt);
            return;
        }

        waves.update(dt, this);
        mechanics.step(dt);

        for (int i = 0; i < abilityCooldown.length; i++) {
            abilityCooldown[i] = Math.max(0f, abilityCooldown[i] - dt);
        }
        if (goldRushTimer > 0f) goldRushTimer -= dt;

        // aura de élites (tierras corruptas): buff temporal de velocidad a cercanos
        if ("elite".equals(biome.trait)) {
            for (int i = 0; i < enemies.size; i++) enemies.get(i).auraSpeedMult = 1f;
            float boost = biome.traitPower > 0 ? biome.traitPower : 1.2f;
            for (int i = 0; i < enemies.size; i++) {
                Enemy elite = enemies.get(i);
                if (!elite.eliteAura || !elite.alive) continue;
                for (int j = 0; j < enemies.size; j++) {
                    Enemy other = enemies.get(j);
                    if (other != elite && other.alive && other.position.dst2(elite.position) < 2.25f) {
                        other.auraSpeedMult = boost;
                    }
                }
            }
        }

        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.update(dt);

            // habilidades de jefe que necesitan el mundo (aliados, esbirros, fx)
            if (e.alive && !"none".equals(e.bossAbility)) {
                float healEvery = e.phase == 2 ? 4f : 6f;
                if ("healpulse".equals(e.bossAbility) && e.abilityTimer >= healEvery) {
                    e.abilityTimer -= healEvery;
                    healPulse(e);
                } else if ("summon".equals(e.bossAbility) && !e.abilityUsed
                    && e.hp < e.maxHp * 0.5f) {
                    e.abilityUsed = true;
                    spawnMinions(e, "fast", 3);
                }
                // guion del jefe FINAL: refuerzos al 66% y blindados al 33%
                if ("overlord".equals(e.bossAbility)) {
                    if (e.summonStage == 0 && e.hp < e.maxHp * 0.66f) {
                        e.summonStage = 1;
                        spawnMinions(e, "fast", 4);
                    } else if (e.summonStage == 1 && e.hp < e.maxHp * 0.33f) {
                        e.summonStage = 2;
                        spawnMinions(e, "tank", 2);
                    }
                }
                if (e.phaseJustChanged) {
                    e.phaseJustChanged = false;
                    bossPhaseEvent = true;
                    Fx burst = fxPool.obtain();
                    tmp.set(e.position.x, e.centerY(), e.position.z);
                    burst.initDeath(tmp, com.badlogic.gdx.graphics.Color.valueOf("ff5a2a"), 2.6f);
                    fx.add(burst);
                    sound("fusion");
                }
                // en fase 2 el jefe sabotea un tanque cercano cada pocos segundos
                if (e.phase == 2) {
                    e.sabotageTimer += dt;
                    // si no tiene ningún tanque a tiro sigue cargado y saltará
                    // en cuanto se acerque a uno (si no, casi nunca acertaba)
                    if (e.sabotageTimer >= SABOTAGE_EVERY && sabotageNearbyTower(e)) {
                        e.sabotageTimer = 0f;
                    }
                }
                // descarga del golpe cargado: onda que apaga los tanques
                if (e.castReleased) {
                    e.castReleased = false;
                    bossSlam(e);
                }
                if (e.justBlinked) {
                    e.justBlinked = false;
                    Fx f = fxPool.obtain();
                    tmp.set(e.position.x, e.centerY(), e.position.z);
                    f.initDeath(tmp, com.badlogic.gdx.graphics.Color.valueOf("c88ae8"), 1.4f);
                    fx.add(f);
                    sound("fusion");
                }
            }

            // tropa con oficio propio: sanador, zapador y portador
            if (e.alive && !"none".equals(e.def.role)) stepEnemyRole(e, dt);
            // unidad insignia del bioma: trucos que necesitan el mundo
            if (e.alive && !"none".equals(e.special)) stepEnemySpecial(e, dt);
            if (e.healFlash > 0f) e.healFlash -= dt;
            if (e.pullCooldown > 0f) e.pullCooldown -= dt;

            if (e.reachedEnd) {
                lives -= e.def.livesCost;
                // quién se cuela: la pantalla de derrota lo convierte en consejo
                leaksByType.getAndIncrement(e.def.id, 0, 1);
                // polvareda del impacto contra la muralla: el golpe se VE donde duele
                Fx dust = fxPool.obtain();
                tmp.set(e.position.x, Math.max(0.4f, e.centerY() * 0.6f), e.position.z);
                dust.initDeath(tmp, wallDustColor, 0.8f + e.def.size * 0.4f);
                fx.add(dust);
                enemies.removeIndex(i);
                enemyPool.free(e);
            } else if (!e.alive) {
                int reward = Math.round((goldRushTimer > 0f ? e.goldReward * 1.5f : e.goldReward)
                    * runGoldMult);
                gold += reward;
                goldEarnedThisWave += reward;
                totalKills++;
                if (e.lastHitTower != null) e.lastHitTower.kills++;
                // el jefe FINAL cae: cadena de explosiones escalonadas
                if ("overlord".equals(e.def.id)) {
                    overlordFxLeft = 7;
                    overlordFxTimer = 0f;
                    overlordFxPos.set(e.position);
                    overlordDied = true;
                    sound("firework");
                }
                if ("champion".equals(e.def.id)) championsKilled++;
                if (e.crystalElite) {
                    elitesKilled++;
                    crystalsEarned += e.crystalReward;
                    Fx ct = fxPool.obtain();
                    tmp.set(e.position.x, e.centerY() + 0.5f, e.position.z);
                    ct.initCrystal(tmp, e.crystalReward);
                    fx.add(ct);
                }
                sound("death");
                addDeathFx(e);
                if ("split".equals(e.trait) && !e.splitChild) spawnSplitChildren(e);
                if ("carrier".equals(e.def.role)) dropCargo(e);
                if ("meltdown".equals(e.special)) meltdown(e);
                if ("deathburst".equals(e.bossAbility)) spawnMinions(e, "fast", 3);
                enemies.removeIndex(i);
                enemyPool.free(e);
            }
        }

        // referencia al jefe con aura gélida (ralentiza torres cercanas)
        frostBoss = null;
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (e.alive && "frostaura".equals(e.bossAbility)) {
                frostBoss = e;
                break;
            }
        }

        stepSpecialAuras();

        resolveEnemyCollisions();

        // el radar ilumina: los ocultos en su radio vuelven a ser targeteables
        // y quedan MARCADOS (+10% daño). Se recalcula antes de que disparen.
        for (int i = 0; i < enemies.size; i++) enemies.get(i).revealed = false;
        if (radarTower != null && !radarTower.driving()) {
            radarPingTimer -= dt;
            if (radarPingTimer <= 0f) {
                radarPingTimer = 4f; // blip sutil de sonar mientras vigila
                sound("radar_ping");
            }
            float r = rangeFor(radarTower);
            float r2 = r * r;
            for (int i = 0; i < enemies.size; i++) {
                Enemy e = enemies.get(i);
                if (!e.alive) continue;
                float dx = e.position.x - radarTower.position.x;
                float dz = e.position.z - radarTower.position.z;
                if (dx * dx + dz * dz <= r2) e.revealed = true;
            }
        }

        for (int i = 0; i < towers.size; i++) {
            towers.get(i).update(dt, this);
        }

        for (int i = projectiles.size - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            boolean hit = p.update(dt);
            if (p.piercing) pierceHits(p);
            if (hit) projectileHit(p);
            if (!p.alive) {
                projectiles.removeIndex(i);
                projectilePool.free(p);
            }
        }

        if (meteorTimer > 0f) {
            meteorTimer -= dt;
            if (meteorTimer <= 0f) meteorImpact();
        }

        // zonas de suelo: aplican los efectos de su torre a quien las pisa
        for (int i = zones.size - 1; i >= 0; i--) {
            GroundZone z = zones.get(i);
            boolean apply = z.tick(dt);
            if (apply) {
                float r2 = z.radius * z.radius;
                for (int j = 0; j < enemies.size; j++) {
                    Enemy e = enemies.get(j);
                    if (!e.alive || e.isFlying()) continue;
                    float dx = e.position.x - z.position.x;
                    float dz = e.position.z - z.position.z;
                    if (dx * dx + dz * dz > r2) continue;
                    if (z.source != null) e.lastHitTower = z.source;
                    float zonePower = effectPowerFor(z.def);
                    for (int k = 0; k < z.def.effects.size; k++) {
                        e.applyEffect(z.def.effects.get(k), zonePower);
                    }
                }
            }
            if (z.ttl <= 0f) {
                zones.removeIndex(i);
                zonePool.free(z);
            }
        }

        stepOverlordFireworks(dt);
        decayFx(dt);

        checkWaveCleared();

        if (lives <= 0) {
            lives = 0;
            state = State.DEFEAT;
        } else if (!survival && waves.allSpawned() && enemies.size == 0) {
            state = State.VICTORY;
        }
    }

    /** Fuegos artificiales escalonados de la caída del Corruptor. */
    private void stepOverlordFireworks(float dt) {
        if (overlordFxLeft <= 0) return;
        overlordFxTimer -= dt;
        if (overlordFxTimer > 0f) return;
        overlordFxTimer = 0.18f;
        overlordFxLeft--;
        Fx boom = fxPool.obtain();
        tmp.set(overlordFxPos.x + MathUtils.random(-1.4f, 1.4f), 0.4f,
            overlordFxPos.z + MathUtils.random(-1.4f, 1.4f));
        boom.initExplosion(tmp, Color.valueOf("c86ae8"),
            MathUtils.random(1.1f, 1.9f), overlordFxLeft % 2 == 0);
        fx.add(boom);
        sound("explosion");
    }

    /** Decaimiento de los efectos visuales (también durante el epílogo). */
    private void decayFx(float dt) {
        for (int i = fx.size - 1; i >= 0; i--) {
            Fx f = fx.get(i);
            f.ttl -= dt;
            if (f.ttl <= 0f) {
                fx.removeIndex(i);
                fxPool.free(f);
            }
        }
    }

    /** Separación física simple: los enemigos se empujan y no se atraviesan. */
    private void resolveEnemyCollisions() {
        for (int i = 0; i < enemies.size; i++) {
            Enemy a = enemies.get(i);
            if (!a.alive) continue;
            for (int j = i + 1; j < enemies.size; j++) {
                Enemy b = enemies.get(j);
                if (!b.alive || a.isFlying() != b.isFlying()) continue;
                float dx = b.position.x - a.position.x;
                float dz = b.position.z - a.position.z;
                float minDist = a.radius() + b.radius();
                float dist2 = dx * dx + dz * dz;
                if (dist2 >= minDist * minDist || dist2 < 0.0001f) continue;
                float dist = (float) Math.sqrt(dist2);
                float push = (minDist - dist) * 0.5f;
                float nx = dx / dist, nz = dz / dist;
                a.position.add(-nx * push, 0f, -nz * push);
                b.position.add(nx * push, 0f, nz * push);
            }
        }
    }

    // ---- habilidades activas ----

    public boolean goldRushActive() {
        return goldRushTimer > 0f;
    }

    /** Segundos que tarda la roca en caer desde que se apunta. */
    public static final float METEOR_FALL_TIME = 0.9f;
    private float meteorTimer;
    private float meteorX, meteorZ;

    /** Meteorito en el punto tocado: la roca cae y el daño llega al impactar. */
    public boolean castMeteor(float wx, float wz) {
        if (abilityCooldown[0] > 0f || abilityCharges[0] <= 0) return false;
        abilityCooldown[0] = ABILITY_COOLDOWN[0];
        abilityCharges[0]--;
        abilitiesUsed++;
        meteorTimer = METEOR_FALL_TIME;
        meteorX = wx;
        meteorZ = wz;
        Fx f = fxPool.obtain();
        tmp.set(wx, 0.1f, wz);
        f.initMeteor(tmp, METEOR_FALL_TIME);
        fx.add(f);
        return true;
    }

    /** La roca toca el suelo: daño en área escalado al nivel. */
    private void meteorImpact() {
        float dmg = 55f * Balance.enemyHpMult(level.id);
        float r2 = 1.7f * 1.7f;
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (!e.alive) continue;
            float dx = e.position.x - meteorX, dz = e.position.z - meteorZ;
            if (dx * dx + dz * dz > r2) continue;
            e.lastHitTower = null;
            e.damage(dmg);
        }
        tmp.set(meteorX, 0.4f, meteorZ);
        Fx boom = fxPool.obtain();
        boom.initExplosion(tmp, com.badlogic.gdx.graphics.Color.valueOf("ff8a2a"), 1.7f, true);
        fx.add(boom);
        Fx scorch = fxPool.obtain();
        scorch.initScorch(tmp, 1.7f);
        fx.add(scorch);
        sound("explosion");
    }

    /** Escarcha global: ralentiza a todos los enemigos vivos. */
    public boolean castFrost() {
        if (abilityCooldown[1] > 0f || abilityCharges[1] <= 0) return false;
        abilityCooldown[1] = ABILITY_COOLDOWN[1];
        abilityCharges[1]--;
        abilitiesUsed++;
        for (int i = 0; i < enemies.size; i++) {
            if (enemies.get(i).alive) enemies.get(i).applyEffect(FROST_EFFECT);
        }
        sound("fusion");
        return true;
    }

    /** Fiebre del oro: +50% oro por baja durante 10 s. */
    public boolean castGoldRush() {
        if (abilityCooldown[2] > 0f || abilityCharges[2] <= 0) return false;
        abilityCooldown[2] = ABILITY_COOLDOWN[2];
        abilityCharges[2]--;
        abilitiesUsed++;
        goldRushTimer = 10f;
        sound("sell");
        return true;
    }

    /** Recoloca una torre al reanudar una partida guardada (sin coste ni animación). */
    public void restoreTower(int x, int z, TowerDef def, int invested, int targeting, int level) {
        if (!canBuildAt(x, z)) return;
        Tower t = new Tower(x, z, def, invested);
        t.targeting = targeting;
        t.level = Math.max(1, Math.min(Balance.TOWER_MAX_LEVEL, level));
        t.age = 10f;
        t.airdrop = false; // ya estaba desplegado antes de guardar
        towers.add(t);
        towerGrid[z * map.width + x] = t;
        if (def.unique) setUnique(t);
        peakTowers = Math.max(peakTowers, towers.size);
    }

    /** Registra un único (tanque de mando o radar) en su ranura. */
    private void setUnique(Tower t) {
        if ("command".equals(t.def.id)) commandTower = t;
        else if ("radar".equals(t.def.id)) radarTower = t;
    }

    /** ¿Ya hay colocado un único de este tipo? */
    public Tower uniquePlaced(String id) {
        if ("command".equals(id)) return commandTower;
        if ("radar".equals(id)) return radarTower;
        return null;
    }

    /** El jefe vivo con más vida (para la barra del HUD), o null. */
    public Enemy strongestBoss() {
        Enemy best = null;
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            boolean bossLike = "boss".equals(e.def.id) || "overlord".equals(e.def.id);
            if (e.alive && bossLike && (best == null || e.hp > best.hp)) best = e;
        }
        return best;
    }

    // ---- construcción, fusión y venta ----

    public Tower towerAt(int x, int z) {
        return map.inBounds(x, z) ? towerGrid[z * map.width + x] : null;
    }

    public boolean canBuildAt(int x, int z) {
        return map.isBuildable(x, z) && towerAt(x, z) == null && !mechanics.blocksBuild(x, z);
    }

    // ---- oleada impecable ----

    /** Vidas al arrancar la oleada en curso, para saber si se cerró sin fugas. */
    private int livesAtWaveStart;
    private boolean waveInProgress;
    /**
     * Oro ganado con bajas (y bonus de oleada limpia) desde que empezó la oleada
     * en curso. Al guardar a media oleada se descuenta: la partida se reanuda
     * relanzando esa oleada, así que si no, cobrarías dos veces por los mismos
     * enemigos.
     */
    public int goldEarnedThisWave;
    /** Oro de la última oleada impecable; la pantalla lo consume para el aviso. */
    public int perfectWaveGold;
    /** Cristales soltados por élites dorados; se abonan al perfil al terminar. */
    public int crystalsEarned;
    /** Fugas por tipo de enemigo: materia prima del diagnóstico de derrota. */
    public final com.badlogic.gdx.utils.ObjectIntMap<String> leaksByType =
        new com.badlogic.gdx.utils.ObjectIntMap<>();

    /**
     * Racha de oleadas impecables consecutivas. Multiplica el bonus (x1 → x1.5
     * → x2 y sigue) y se rompe en cuanto se cuela un solo enemigo: le da
     * tensión a los tramos en los que ya vas cómodo.
     */
    public int perfectStreak;
    /** Multiplicador que se aplicará a la próxima oleada limpia. */
    public float streakMult() {
        return Math.min(3f, 1f + 0.5f * perfectStreak);
    }

    /** Cierra la oleada en curso y abona el bonus si no se coló nadie. */
    private void checkWaveCleared() {
        if (!waveInProgress || waves.spawning || enemies.size > 0) return;
        waveInProgress = false;
        if (lives < livesAtWaveStart) {
            perfectStreak = 0;
            return;
        }
        int bonus = Math.round(Balance.perfectWaveBonus(waves.displayWave()) * streakMult());
        perfectStreak++;
        gold += bonus;
        goldEarnedThisWave += bonus;
        perfectWaveGold = bonus;
        // el "+oro" sale flotando sobre la fortaleza, que es donde miraba el
        // jugador esperando la fuga que no llegó. El último waypoint queda fuera
        // del tablero (extensión de entrada/salida): el penúltimo sí se ve
        Array<Vector3> path = map.worldPaths.first();
        Fx text = fxPool.obtain();
        tmp.set(path.get(Math.max(0, path.size - 2))).y = 1.4f;
        text.initGold(tmp, bonus);
        fx.add(text);
        sound("sell");
    }

    /** Notificado por WaveManager al arrancar cada oleada. */
    void onWaveStart() {
        sound("wave");
        // aviso sonoro extra si la oleada llega con evento
        if (!waves.currentEvent.isEmpty()) sound("event_alert");
        livesAtWaveStart = lives;
        goldEarnedThisWave = 0;
        waveInProgress = true;
        // Sin fin: la vida crece de forma ACELERADA. Con un escalado lineal la
        // partida no terminaba nunca en los biomas suaves (medido: oleada 80+),
        // y eso convertía el modo en una máquina de farmear cristales.
        if (survival) {
            float w = Math.max(0, waves.waveIndex);
            survivalScale = 1f + 0.06f * w + 0.009f * w * w;
        }
        mechanics.onWaveStart();
        openPathsForWave(waves.displayWave());
    }

    // ---- stats efectivas de torre (def × mecánicas de bioma) ----

    /** El tanque de mando colocado (único por partida), o null. */
    public Tower commandTower;
    /** El tanque radar colocado (único), o null: revela y marca ocultos. */
    public Tower radarTower;

    // ---- rutas selladas ----

    /** Oleada (1-based) en la que se derrumba el sello de la segunda ruta. */
    public static final int PATH_OPEN_WAVE = 3;
    /** Cuántas rutas están abiertas ahora mismo. */
    public int openPaths = 1;
    /** true el frame en que se abre una ruta: la pantalla lo usa para el aviso. */
    public boolean pathJustOpened;

    /** Abre la ruta sellada si toca; la llama el WaveManager al arrancar la oleada. */
    void openPathsForWave(int waveNumber) {
        if (openPaths >= map.worldPaths.size) return;
        if (waveNumber < PATH_OPEN_WAVE) return;
        openPaths = map.worldPaths.size;
        pathJustOpened = true;
        sound("explosion");
    }

    /** true si t está en una de las 8 celdas contiguas al tanque de mando. */
    public boolean underCommandAura(Tower t) {
        if (commandTower == null || commandTower == t) return false;
        return Math.abs(t.cellX - commandTower.cellX) <= 1
            && Math.abs(t.cellZ - commandTower.cellZ) <= 1;
    }

    /**
     * Bonus por-torre del árbol: las FUSIONES heredan además la MITAD de los
     * nodos de sus dos tanques padre — invertir en la rama del cañón mejora
     * también a la artillería que nace de él, sin duplicar el poder.
     */
    private float perTowerBonus(com.badlogic.gdx.utils.ObjectFloatMap<String> map, String id) {
        float v = map.get(id, 0f);
        com.pcdc.td.data.FusionDef f = catalogs.fusionByResult(id);
        if (f != null) v += 0.5f * (map.get(f.a, 0f) + map.get(f.b, 0f));
        return v;
    }

    /** PEN efectiva de la torre (def + nodos del árbol, con herencia de fusión). */
    public float penFor(Tower t) {
        return t.def.penetration + runPen + stats.penFlat
            + perTowerBonus(stats.towerPen, t.def.id);
    }

    /** Potencia de los efectos de estado que aplica esta def (1 = sin bonus). */
    float effectPowerFor(com.pcdc.td.data.TowerDef def) {
        return runEffectMult * (1f + perTowerBonus(stats.towerEffect, def.id));
    }

    /** Saltos de cadena efectivos (Tesla/Prisma). */
    int chainCountFor(Tower t) {
        return t.def.chainCount + Math.round(perTowerBonus(stats.towerChain, t.def.id));
    }

    /** Radio de explosión efectivo del proyectil de esta torre. */
    float splashFor(Tower source, com.pcdc.td.data.TowerDef def) {
        float mult = source != null ? 1f + perTowerBonus(stats.towerSplash, source.def.id) : 1f;
        return def.splashRadius * mult;
    }

    /** Multiplicador de velocidad de rampa del rayo (láser y derivados). */
    public float rampMultFor(Tower t) {
        return 1f + perTowerBonus(stats.towerRamp, t.def.id);
    }

    public float rangeFor(Tower t) {
        float aura = underCommandAura(t) ? 1f + commandTower.def.auraRangeAdd : 1f;
        return runRangeMult * t.def.range * mechanics.rangeMult(t) * stats.rangeMult
            * (1f + perTowerBonus(stats.towerRange, t.def.id))
            * Balance.upgradeRangeMult(t.level) * aura;
    }

    /** Jefe con aura gélida activo, o null (lo consulta fireRateFor). */
    private Enemy frostBoss;

    public float fireRateFor(Tower t) {
        float mult = 1f;
        if (frostBoss != null
            && frostBoss.position.dst2(t.position.x, frostBoss.position.y, t.position.z) < 6.25f) {
            mult = 0.72f; // el frío del jefe de nieve entumece las torres cercanas
        }
        // escarchadores de la nieve: el mismo frío, en tropa de a pie
        for (int i = 0; i < chillers.size; i++) {
            Enemy c = chillers.get(i);
            if (c.position.dst2(t.position.x, c.position.y, t.position.z) < 6.25f) {
                mult = Math.min(mult, c.def.specialPower > 0 ? c.def.specialPower : 0.7f);
                break;
            }
        }
        return t.def.fireRate * mechanics.fireRateMult(t) * stats.fireRateMult
            * (1f + perTowerBonus(stats.towerRate, t.def.id)) * mult;
    }

    /** Pulso del jefe de pantano: cura un 8% a los aliados cercanos. */
    // --- tropa con rol propio ---
    private static final float HEAL_EVERY = 2f, HEAL_RANGE2 = 2.2f * 2.2f;
    private static final float SAPPER_EVERY = 7f;

    /**
     * Oficios de tropa: el SANADOR cura a los suyos (hay que priorizarlo o la
     * oleada se vuelve esponja), el ZAPADOR apaga tanques al pasar y el
     * PORTADOR suelta tropa al caer (ver dropCargo).
     */
    private void stepEnemyRole(Enemy e, float dt) {
        e.roleTimer += dt;
        switch (e.def.role) {
            case "healer":
                if (e.roleTimer >= HEAL_EVERY) {
                    e.roleTimer = 0f;
                    healAround(e);
                }
                break;
            case "sapper":
                // como el sabotaje del jefe: la carga NO se gasta si no había
                // ningún tanque a tiro, así salta en cuanto se acerca a uno
                if (e.roleTimer >= SAPPER_EVERY && sabotageNearbyTower(e)) {
                    e.roleTimer = 0f;
                }
                break;
            default:
                break;
        }
    }

    // --- unidades insignia de bioma ---

    /** Escarchadores vivos: entumecen a los tanques cercanos (ver fireRateFor). */
    private final Array<Enemy> chillers = new Array<>(false, 4);

    /**
     * Auras de las unidades insignia, recalculadas cada paso ANTES de que las
     * torres busquen objetivo: la BRUMA oculta a los suyos, el RESONADOR les
     * presta armadura y el ESCARCHADOR entumece a los tanques de al lado.
     * Todas se anulan solas al morir su portador: son objetivos prioritarios.
     */
    private void stepSpecialAuras() {
        boolean any = false;
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            e.cloaked = false;
            e.auraArmor = 0f;
            if (e.alive && ("cloak".equals(e.special) || "guardaura".equals(e.special)
                || "chillaura".equals(e.special))) {
                any = true;
            }
        }
        chillers.clear();
        if (!any) return;

        for (int i = 0; i < enemies.size; i++) {
            Enemy carrier = enemies.get(i);
            if (!carrier.alive) continue;
            switch (carrier.special) {
                case "chillaura":
                    chillers.add(carrier);
                    break;
                case "cloak": {
                    // la bruma NO se oculta a sí misma: hay que poder matarla
                    float r = carrier.def.specialPower > 0 ? carrier.def.specialPower : 2.4f;
                    float r2 = r * r;
                    for (int j = 0; j < enemies.size; j++) {
                        Enemy o = enemies.get(j);
                        if (o == carrier || !o.alive || o.isFlying()) continue;
                        if (o.position.dst2(carrier.position) <= r2) o.cloaked = true;
                    }
                    break;
                }
                case "guardaura": {
                    float armor = carrier.def.specialPower > 0 ? carrier.def.specialPower : 6f;
                    for (int j = 0; j < enemies.size; j++) {
                        Enemy o = enemies.get(j);
                        if (o == carrier || !o.alive) continue;
                        if (o.position.dst2(carrier.position) <= 6.25f) {
                            o.auraArmor = Math.max(o.auraArmor, armor);
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }

    /** Cada cuánto pulsa el escudo del guardián y radios de los trucos. */
    private static final float WARD_RANGE2 = 2.5f * 2.5f, THUNDER_RANGE2 = 4.5f * 4.5f;
    private static final float MELTDOWN_RANGE2 = 1.9f * 1.9f;

    /**
     * Trucos con temporizador de las unidades insignia: el GUARDIÁN escuda a
     * los suyos, el ACÓLITO salta camino adelante llevándose a los cercanos y
     * el INVOCADOR fulmina un tanque a distancia.
     */
    private void stepEnemySpecial(Enemy e, float dt) {
        float period = e.def.specialInterval;
        switch (e.special) {
            case "wardaura":
                e.specialTimer += dt;
                if (e.specialTimer >= (period > 0 ? period : 4f)) {
                    e.specialTimer = 0f;
                    wardAround(e);
                }
                break;
            case "warp":
                e.specialTimer += dt;
                if (e.specialTimer >= (period > 0 ? period : 7f)) {
                    e.specialTimer = 0f;
                    warpGroup(e);
                }
                break;
            case "thunder":
                e.specialTimer += dt;
                // como el sabotaje: la carga no se gasta si no había tanque a
                // tiro, así cae en cuanto entra en alcance
                if (e.specialTimer >= (period > 0 ? period : 6f) && strikeTower(e)) {
                    e.specialTimer = 0f;
                }
                break;
            default:
                break;
        }
    }

    /** Guardián del bosque: reparte escudo de savia a los aliados heridos. */
    private void wardAround(Enemy warden) {
        boolean warded = false;
        float pct = warden.def.specialPower > 0 ? warden.def.specialPower : 0.25f;
        for (int i = 0; i < enemies.size; i++) {
            Enemy o = enemies.get(i);
            if (o == warden || !o.alive || o.def.livesCost >= 3) continue;
            if (o.position.dst2(warden.position) > WARD_RANGE2) continue;
            float shield = o.maxHp * pct;
            if (o.shieldHp >= shield) continue;
            o.shieldMax = Math.max(o.shieldMax, shield);
            o.shieldHp = shield;
            warded = true;
        }
        if (!warded) return;
        warden.healFlash = 0.4f;
        Fx f = fxPool.obtain();
        tmp.set(warden.position.x, warden.centerY(), warden.position.z);
        f.initDeath(tmp, com.badlogic.gdx.graphics.Color.valueOf("8ae0a0"), 1.8f);
        fx.add(f);
    }

    /** Acólito de las ruinas: se lleva por delante un tramo del camino. */
    private void warpGroup(Enemy acolyte) {
        float dist = acolyte.def.specialPower > 0 ? acolyte.def.specialPower : 2.2f;
        for (int i = 0; i < enemies.size; i++) {
            Enemy o = enemies.get(i);
            if (!o.alive || o.isFlying() || o.def.livesCost >= 3) continue;
            if (o != acolyte && o.position.dst2(acolyte.position) > 4f) continue;
            tmp.set(o.position.x, o.centerY(), o.position.z);
            o.warpForward(dist);
            Fx f = fxPool.obtain();
            f.initDeath(tmp, com.badlogic.gdx.graphics.Color.valueOf("c88ae8"), 1.2f);
            fx.add(f);
        }
        sound("fusion");
    }

    /** Invocador de rayos: fulmina y apaga un tanque a distancia. */
    private boolean strikeTower(Enemy caster) {
        Tower pick = null;
        int seen = 0;
        for (int i = 0; i < towers.size; i++) {
            Tower t = towers.get(i);
            if (t.disabledTimer > 0f) continue;
            float dx = t.position.x - caster.position.x;
            float dz = t.position.z - caster.position.z;
            if (dx * dx + dz * dz > THUNDER_RANGE2) continue;
            seen++;
            if (MathUtils.random(seen - 1) == 0) pick = t;
        }
        if (pick == null) return false;
        pick.disabledTimer = caster.def.specialPower > 0 ? caster.def.specialPower : 2.5f;
        Fx bolt = fxPool.obtain();
        tmp.set(caster.position.x, caster.centerY(), caster.position.z);
        tmp2.set(pick.position.x, 0.7f, pick.position.z);
        bolt.initTracer(tmp, tmp2, com.badlogic.gdx.graphics.Color.valueOf("9ad8ff"));
        bolt.jagged = true;
        bolt.maxTtl = bolt.ttl = 0.22f;
        fx.add(bolt);
        sound("zap");
        return true;
    }

    /** Radio y apagón del golpe cargado del jefe. */
    private static final float SLAM_RANGE2 = 3.6f * 3.6f, SLAM_DISABLE = 3.5f;

    /**
     * El jefe descarga: onda expansiva que APAGA los tanques cercanos unos
     * segundos. Se ha telegrafiado 1,3 s antes, y durante la carga el jefe se
     * queda quieto: el trato es "te hago daño, pero te doy una ventana".
     */
    private void bossSlam(Enemy boss) {
        for (int i = 0; i < towers.size; i++) {
            Tower t = towers.get(i);
            float dx = t.position.x - boss.position.x;
            float dz = t.position.z - boss.position.z;
            if (dx * dx + dz * dz > SLAM_RANGE2) continue;
            t.disabledTimer = Math.max(t.disabledTimer, SLAM_DISABLE);
        }
        Fx wave = fxPool.obtain();
        tmp.set(boss.position.x, 0.35f, boss.position.z);
        wave.initExplosion(tmp, com.badlogic.gdx.graphics.Color.valueOf("ff7a4a"), 3.4f, true);
        fx.add(wave);
        sound("explosion");
        bossSlamEvent = true;
    }

    /** La pantalla lo consume para sacudir la cámara con la onda. */
    public boolean bossSlamEvent;

    /** Destello del salto por un portal de las ruinas (entrada y salida). */
    void portalFlash(Vector3 at, boolean exit) {
        Fx f = fxPool.obtain();
        tmp.set(at.x, 0.5f, at.z);
        f.initDeath(tmp, com.badlogic.gdx.graphics.Color.valueOf(exit ? "6ae8c8" : "c86ae8"), 1.6f);
        fx.add(f);
        if (exit) sound("fusion"); // un solo sonido por salto
    }

    /** Núcleo de magma: al caer estalla y apaga los tanques de al lado. */
    private void meltdown(Enemy e) {
        for (int i = 0; i < towers.size; i++) {
            Tower t = towers.get(i);
            float dx = t.position.x - e.position.x;
            float dz = t.position.z - e.position.z;
            if (dx * dx + dz * dz > MELTDOWN_RANGE2) continue;
            t.disabledTimer = Math.max(t.disabledTimer,
                e.def.specialPower > 0 ? e.def.specialPower : 3f);
        }
        Fx boom = fxPool.obtain();
        tmp.set(e.position.x, 0.4f, e.position.z);
        boom.initExplosion(tmp, com.badlogic.gdx.graphics.Color.valueOf("ff7a2a"), 1.6f, true);
        fx.add(boom);
        Fx scorch = fxPool.obtain();
        scorch.initScorch(tmp, 1.6f);
        fx.add(scorch);
        sound("explosion");
    }

    /** Cura del sanador a los heridos de alrededor. */
    private void healAround(Enemy healer) {
        boolean healed = false;
        for (int i = 0; i < enemies.size; i++) {
            Enemy o = enemies.get(i);
            if (o == healer || !o.alive || o.hp >= o.maxHp) continue;
            // los sanadores NO se curan entre sí: en grupo se volvían un bloque
            // inmortal que ninguna cantidad de daño razonable rompía. Tampoco
            // curan a jefes ni campeones: con esas pozas de vida la pelea no
            // avanzaba y el jugador no tiene forma de saber por qué
            if ("healer".equals(o.def.role) || o.def.livesCost >= 3) continue;
            if (o.position.dst2(healer.position) > HEAL_RANGE2) continue;
            o.hp = Math.min(o.maxHp, o.hp + o.maxHp * 0.03f);
            healed = true;
        }
        if (!healed) return;
        healer.healFlash = 0.4f;
        Fx f = fxPool.obtain();
        tmp.set(healer.position.x, healer.centerY(), healer.position.z);
        f.initDeath(tmp, com.badlogic.gdx.graphics.Color.valueOf("6ae87d"), 1.5f);
        fx.add(f);
    }

    /**
     * El portador cae y suelta su tropa. Aterriza en el punto del camino de
     * tierra más cercano a donde cayó (vuela en recta, así que su posición
     * puede estar en mitad del campo): matarlo tarde deja tropa muy adelantada.
     */
    private void dropCargo(Enemy carrier) {
        Array<Vector3> ground = map.worldPaths.get(Math.min(carrier.pathNo, map.worldPaths.size - 1));
        // TOPE: la tropa no puede aterrizar más adelante de lo que el propio
        // portador ha volado. Buscando solo el waypoint MÁS CERCANO, en un
        // camino en U el punto de al lado podía ser el tramo final y la tropa
        // caía a las puertas de la fortaleza (visto en partida).
        float flownPct = carrier.totalPathLen > 0.01f
            ? Math.min(1f, carrier.pathProgress / carrier.totalPathLen) : 0f;
        float groundLen = 0f;
        for (int i = 1; i < ground.size; i++) groundLen += ground.get(i).dst(ground.get(i - 1));
        float maxProgress = groundLen * flownPct;

        int bestIdx = 1;
        float bestD2 = Float.MAX_VALUE, progressToBest = 0f, walked = 0f;
        for (int i = 1; i < ground.size; i++) {
            Vector3 wp = ground.get(i);
            walked += wp.dst(ground.get(i - 1));
            if (walked > maxProgress) break;
            float dx = wp.x - carrier.position.x;
            float dz = wp.z - carrier.position.z;
            float d2 = dx * dx + dz * dz;
            if (d2 < bestD2) {
                bestD2 = d2;
                bestIdx = i;
                progressToBest = walked;
            }
        }
        for (int c = 0; c < 2; c++) {
            Enemy troop = enemyPool.obtain();
            troop.init(catalogs.enemy("fast"), ground, hpMult * survivalScale,
                goldMult * (0.6f + 0.4f * survivalScale));
            troop.initAtWaypoint(ground.get(bestIdx), bestIdx, progressToBest, (c - 0.5f) * 0.6f);
            enemies.add(troop);
        }
        Fx f = fxPool.obtain();
        tmp.set(carrier.position.x, carrier.centerY(), carrier.position.z);
        f.initDeath(tmp, com.badlogic.gdx.graphics.Color.valueOf("c8a8ff"), 2f);
        fx.add(f);
        sound("explosion");
    }

    /**
     * Tirón del arpón. Los pesos pesados se resisten: sin esto un solo arpón
     * dejaba clavado en el sitio a un tanque (0.78 de velocidad) para siempre.
     */
    public void pullBack(Enemy e, float distance) {
        // TOPE POR ENEMIGO: da igual cuántos arpones le enganchen a la vez,
        // solo un tirón cada 1.2 s. Sin esto, dos arpones ya empujaban al
        // enemigo más rápido de lo que camina y lo devolvían al portal: visto
        // en pruebas, la oleada entera se quedaba clavada en la entrada.
        if (e.pullCooldown > 0f) return;
        // Los JEFES se plantan: con 0,3 de resistencia y un tirón cada 1,2 s, un
        // solo garfio glacial (2,2 de arrastre) los movía 0,55 u/s hacia atrás,
        // casi su velocidad entera: se quedaban clavados en el sitio para
        // siempre. Ahora el arpón sobre un jefe es un respiro (~20% de su
        // avance), no una cadena perpetua.
        boolean bossLike = e.def.livesCost >= 3;
        boolean heavy = e.def.armor >= 5f;
        e.pullCooldown = bossLike ? 2.4f : heavy ? 1.6f : 1.2f;
        float resist = bossLike ? 0.15f : heavy ? 0.6f : 1f;
        e.pullBack(distance * resist);
    }

    private void healPulse(Enemy boss) {
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (e == boss || !e.alive) continue;
            if (e.position.dst2(boss.position) > 9f) continue;
            e.hp = Math.min(e.maxHp, e.hp + e.maxHp * 0.08f);
        }
        Fx f = fxPool.obtain();
        tmp.set(boss.position.x, boss.centerY(), boss.position.z);
        f.initDeath(tmp, com.badlogic.gdx.graphics.Color.valueOf("6ae87d"), 2.2f);
        fx.add(f);
    }

    public float damageFor(Tower t) {
        float aura = underCommandAura(t)
            ? 1f + commandTower.def.auraDamageAdd + stats.auraDamageAdd : 1f;
        return t.def.damage * runDamageMult * mechanics.damageMult(t) * stats.damageMult
            * (1f + perTowerBonus(stats.towerDamage, t.def.id))
            * Balance.upgradeDamageMult(t.level) * aura;
    }

    /** Coste de subir la torre al siguiente nivel, o -1 si ya está al máximo. */
    public int upgradeCost(Tower t) {
        if (t.level >= Balance.TOWER_MAX_LEVEL) return -1;
        return Math.round(Balance.upgradeCost(t.def.cost, t.level) * (1f - runUpgradeDiscount));
    }

    /** Sube la torre de nivel in situ si hay oro. */
    public boolean tryUpgrade(Tower t) {
        int cost = upgradeCost(t);
        if (cost < 0 || gold < cost) return false;
        gold -= cost;
        t.level++;
        t.invested += cost;
        Fx burst = fxPool.obtain();
        tmp.set(t.position.x, 0.6f, t.position.z);
        burst.initDeath(tmp, com.badlogic.gdx.graphics.Color.valueOf("ffd54a"), 1.4f);
        fx.add(burst);
        sound("fusion");
        return true;
    }

    /** Receta aplicable (y desbloqueada) si se coloca esta torre sobre la celda, o null. */
    public FusionDef fusionAt(int x, int z, TowerDef def) {
        Tower existing = towerAt(x, z);
        if (existing == null || existing.driving()) return null; // aún de viaje
        FusionDef recipe = catalogs.findFusion(existing.def.id, def.id);
        if (recipe == null || !stats.unlockedFusions.contains(recipe.result)) return null;
        return recipe;
    }

    /**
     * Coloca (celda libre) o fusiona (celda con torre y receta). Cobra def.cost.
     *
     * @return true si se hizo algo.
     */
    public boolean tryPlace(int x, int z, TowerDef def) {
        if (gold < def.cost) return false;
        if (def.unique && uniquePlaced(def.id) != null) return false; // uno por partida
        Tower existing = towerAt(x, z);
        if (existing != null) {
            FusionDef recipe = fusionAt(x, z, def);
            if (recipe == null) return false;
            gold -= def.cost;
            TowerDef fused = catalogs.tower(recipe.result);
            Tower merged = new Tower(x, z, fused, existing.invested + def.cost);
            merged.level = existing.level; // la fusión conserva las mejoras pagadas
            merged.airdrop = false; // se funde en el sitio: no lo trae el helicóptero
            towers.removeValue(existing, true);
            towers.add(merged);
            towerGrid[z * map.width + x] = merged;
            fusionsMade++;
            // estallido de fusión
            Fx burst = fxPool.obtain();
            tmp.set(merged.position.x, 0.6f, merged.position.z);
            burst.initDeath(tmp, fused.color(), 1.7f);
            fx.add(burst);
            sound("fusion");
            return true;
        }
        if (!canBuildAt(x, z)) return false;
        gold -= def.cost;
        Tower t = new Tower(x, z, def, def.cost);
        towers.add(t);
        towerGrid[z * map.width + x] = t;
        if (def.unique) setUnique(t);
        peakTowers = Math.max(peakTowers, towers.size);
        // lo trae un helicóptero: el polvo y el golpe llegan al aterrizar (los
        // pone la capa de render), aquí solo suenan las palas acercándose
        sound("chopper");
        return true;
    }

    public int sellRefund(Tower t) {
        // antes de arrancar la primera oleada, recolocar es gratis: reintegro total
        if (!waves.started) return t.invested;
        float refund = Math.min(0.95f, Balance.SELL_REFUND + stats.sellRefundAdd);
        return Math.round(t.invested * refund);
    }

    /** ¿Se puede reubicar? Solo requiere el nodo Logística: se conduce en caliente. */
    public boolean relocateAllowed() {
        return stats.canRelocate;
    }

    /**
     * Manda un tanque conduciendo a una celda libre (gratis, incluso a media
     * oleada): la contrapartida es el viaje, durante el que no dispara.
     */
    public boolean tryRelocate(Tower t, int x, int z) {
        lastRelocateBlocked = false;
        if (!relocateAllowed() || !canBuildAt(x, z) || t.driving()) return false;
        // RUTA por casillas esquivando a los demás tanques: antes viajaba en
        // línea recta y los atravesaba (y también las rocas). Si no hay paso,
        // el movimiento simplemente no se permite.
        Array<Vector3> route = findDriveRoute(t, x, z);
        if (route == null) {
            lastRelocateBlocked = true;
            return false;
        }
        towerGrid[t.cellZ * map.width + t.cellX] = null;
        t.startDrive(x, z, route);
        towerGrid[z * map.width + x] = t;
        sound("build");
        return true;
    }

    /** El último traslado falló porque no había ruta libre (lo lee el HUD). */
    public boolean lastRelocateBlocked;

    /**
     * Camino más corto de casilla a casilla para el traslado (BFS a 4
     * direcciones). Puede cruzar la calzada —es un tanque— pero NO atraviesa
     * rocas ni a otros tanques.
     * @return centros de casilla hasta el destino, o null si no hay paso.
     */
    private Array<Vector3> findDriveRoute(Tower t, int destX, int destZ) {
        int w = map.width, h = map.height;
        int start = t.cellZ * w + t.cellX, goal = destZ * w + destX;
        int[] cameFrom = new int[w * h];
        java.util.Arrays.fill(cameFrom, -2);
        com.badlogic.gdx.utils.IntArray queue = new com.badlogic.gdx.utils.IntArray();
        queue.add(start);
        cameFrom[start] = -1;
        int[] dx = {1, -1, 0, 0}, dz = {0, 0, 1, -1};
        boolean found = false;
        for (int head = 0; head < queue.size && !found; head++) {
            int cur = queue.get(head);
            int cx = cur % w, cz = cur / w;
            for (int d = 0; d < 4; d++) {
                int nx = cx + dx[d], nz = cz + dz[d];
                if (!map.inBounds(nx, nz)) continue;
                int ni = nz * w + nx;
                if (cameFrom[ni] != -2) continue;
                if (map.get(nx, nz) == CellType.BLOCKED) continue;
                Tower other = towerAt(nx, nz);
                if (other != null && other != t) continue;
                cameFrom[ni] = cur;
                if (ni == goal) {
                    found = true;
                    break;
                }
                queue.add(ni);
            }
        }
        if (!found) return null;
        Array<Vector3> route = new Array<>();
        for (int cur = goal; cur != start; cur = cameFrom[cur]) {
            route.insert(0, GridMap.cellCenter(cur % w, cur / w, new Vector3()));
        }
        return route;
    }

    /** Huellas de orugas que se desvanecen (las siembra el tanque al conducir). */
    void addTrackMarks(Vector3 at, float yawDeg) {
        Fx marks = fxPool.obtain();
        tmp.set(at.x, 0f, at.z);
        marks.initTracks(tmp, yawDeg);
        fx.add(marks);
    }

    /**
     * Deshace la última colocación ANTES de arrancar la oleada: reintegro
     * completo y, a diferencia de vender, no cuenta para el reto NO_SELL.
     */
    public boolean undoPlace(Tower t) {
        if (waves.started || !towers.contains(t, true)) return false;
        gold += t.invested;
        towers.removeValue(t, true);
        towerGrid[t.cellZ * map.width + t.cellX] = null;
        if (t == commandTower) commandTower = null;
        if (t == radarTower) radarTower = null;
        return true;
    }

    public void sell(Tower t) {
        gold += sellRefund(t);
        towers.removeValue(t, true);
        towerGrid[t.cellZ * map.width + t.cellX] = null;
        if (t == commandTower) commandTower = null; // se puede volver a colocar
        if (t == radarTower) radarTower = null;
        towersSold++;
        sound("sell");
    }

    /** Llama la siguiente oleada ya; devuelve el bonus de oro obtenido. */
    public int callNextWave() {
        int bonus = Math.round(waves.skipCountdown() * Balance.EARLY_WAVE_GOLD_PER_SECOND
            * (1f + stats.earlyBonusMult));
        gold += bonus;
        return bonus;
    }

    // ---- spawns, targeting y daño ----

    private int spawnCounter;

    /** Vida efectiva con la que spawneará un enemigo ahora mismo (para la carta). */
    public float previewHpMult() {
        return hpMult * survivalScale;
    }

    void spawnEnemy(String enemyId, int pathIndex) {
        EnemyDef def = catalogs.enemy(enemyId);
        Enemy e = enemyPool.obtain();
        int path = Math.min(pathIndex, map.worldPaths.size - 1);
        // mientras la ruta siga sellada, todo entra por la primera
        if (path >= openPaths) path = 0;
        boolean overlord = "overlord".equals(def.id);
        boolean boss = "boss".equals(def.id) || overlord;
        boolean bossFlight = boss && !overlord && "flight".equals(biome.bossAbility);
        // los voladores se saltan el camino: vuelan en línea recta a la salida
        Array<Vector3> route = def.flying || bossFlight
            ? map.flyerPaths.get(path) : map.worldPaths.get(path);
        e.init(def, route, hpMult * survivalScale, goldMult * (0.6f + 0.4f * survivalScale));
        e.pathNo = path; // el portador suelta su tropa sobre ESTE camino de tierra
        if (!"none".equals(biome.trait)) {
            spawnCounter++;
            int eliteEvery = biome.traitCount > 0 ? biome.traitCount : 6;
            e.applyTrait(biome, "elite".equals(biome.trait) && spawnCounter % eliteEvery == 0);
        }
        // CAMPEÓN (mini-jefe de mitad de bioma): hereda la habilidad del jefe
        // en versión previa — salvo volar, que cambiaría su ruta
        boolean champion = "champion".equals(def.id);
        if (champion) {
            sound("sting_champion");
            if (!"flight".equals(biome.bossAbility)) {
                e.bossAbility = biome.bossAbility;
                if ("kingaura".equals(biome.bossAbility)) e.eliteAura = true;
            }
        }
        if (overlord) sound("sting_overlord");

        // cartas del sin fin que refuerzan al enemigo
        e.bonusArmor += runEnemyArmorBonus;
        e.eventSpeedMult *= runEnemySpeedMult;

        // eventos de oleada: enfurecida (rápida y lucrativa) o cargada de botín
        String event = waves.currentEvent;
        if ("enraged".equals(event) && !boss && !champion) {
            e.eventSpeedMult = 1.25f;
            e.goldReward = Math.round(e.goldReward * 1.5f);
        }

        // élite dorado aleatorio (desde el bioma 3): más duro, suelta cristales
        int eliteOdds = "loot".equals(event) ? 6 : 19;
        if (level.id >= 11 && !boss && !champion && MathUtils.random(eliteOdds) == 0) {
            e.crystalElite = true;
            e.maxHp *= 2f;
            e.hp = e.maxHp;
            e.goldReward *= 2;
            e.crystalReward = 1 + level.id / 12;
        }
        if (boss) {
            if (overlord) {
                // el jefe FINAL trae su propio guion, no el del bioma
                e.bossAbility = "overlord";
                e.eliteAura = true; // aura de rey desde el primer segundo
            } else {
                e.bossAbility = biome.bossAbility;
                if ("kingaura".equals(biome.bossAbility)) e.eliteAura = true;
            }
            if (bossFlight) {
                e.forceFlying = true;
                e.position.y = 1.6f;
            }
            // AFIJO en Nivel+: un rasgo extra aleatorio para que rejugar con
            // 3 estrellas no sea calcado; visible en el banner del jefe
            if (plusMode) {
                e.affix = AFFIXES[MathUtils.random(AFFIXES.length - 1)];
                if ("armored".equals(e.affix)) e.bonusArmor = 8f;
                else if ("swift".equals(e.affix)) e.eventSpeedMult *= 1.25f;
            }
        }
        enemies.add(e);
    }

    /** Esbirros invocados por el jefe (summon al 50% / deathburst al morir). */
    private void spawnMinions(Enemy parent, String enemyId, int count) {
        for (int c = 0; c < count; c++) {
            Enemy minion = enemyPool.obtain();
            minion.init(catalogs.enemy(enemyId), parent.route(), hpMult * survivalScale,
                goldMult * (0.6f + 0.4f * survivalScale));
            minion.initAtParent(parent, (c - (count - 1) * 0.5f) * 0.5f);
            enemies.add(minion);
        }
        Fx f = fxPool.obtain();
        tmp.set(parent.position.x, parent.centerY(), parent.position.z);
        f.initDeath(tmp, com.badlogic.gdx.graphics.Color.valueOf("c04ae0"), 1.6f);
        fx.add(f);
        sound("wave");
    }

    private void spawnSplitChildren(Enemy parent) {
        int children = parent.traitCount > 0 ? parent.traitCount : 2;
        for (int c = 0; c < children; c++) {
            Enemy child = enemyPool.obtain();
            // la ruta DEL PADRE: con map.worldPaths.first() un hijo nacido en el
            // segundo camino heredaba el índice de waypoint del padre sobre una
            // ruta distinta y se marchaba en dirección contraria
            child.init(parent.def, parent.route(), hpMult, goldMult);
            child.initAsSplitChild(parent);
            child.pathNo = parent.pathNo;
            child.position.x += (c == 0 ? -0.18f : 0.18f);
            enemies.add(child);
        }
    }

    /** Radio de corte del disco de sierra en vuelo. */
    private static final float PIERCE_RADIUS2 = 0.5f * 0.5f;

    /**
     * LANZASIERRAS: el disco corta a cada enemigo que atraviesa, una sola vez
     * por disco (de ahí la lista pierced), y sigue su camino.
     */
    private void pierceHits(Projectile p) {
        float pen = p.source != null ? penFor(p.source) : p.def.penetration;
        float power = effectPowerFor(p.def);
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (!e.alive || e.isHidden()) continue;
            if (e.isFlying() ? !p.def.hitsAir : !p.def.hitsGround) continue;
            if (p.pierced.contains(e, true)) continue;
            float dx = e.position.x - p.position.x;
            float dz = e.position.z - p.position.z;
            if (dx * dx + dz * dz > PIERCE_RADIUS2) continue;
            p.pierced.add(e);
            e.lastHitTower = p.source;
            if (p.source != null) p.source.damageDealt += p.damage;
            damageText(e, e.hit(p.damage, pen));
            for (int j = 0; j < p.def.effects.size; j++) {
                e.applyEffect(p.def.effects.get(j), power);
            }
            Fx spark = fxPool.obtain();
            tmp.set(e.position.x, e.centerY(), e.position.z);
            spark.initHit(tmp, p.def.color());
            fx.add(spark);
        }
    }

    void spawnProjectile(Tower tower, Enemy target) {
        Projectile p = projectilePool.obtain();
        if (tower.def.pierce) {
            p.initPiercing(tower.def, tower.muzzle(), target, damageFor(tower),
                rangeFor(tower) + 0.6f);
        } else {
            p.init(tower.def, tower.muzzle(), target, damageFor(tower));
        }
        p.source = tower;
        projectiles.add(p);
        sound(tower.def.fireRate >= 3f ? "shoot_light" : "shoot");
    }

    /** Cada cuánto sabotea el jefe en fase 2 y cuánto dura el apagón. */
    private static final float SABOTAGE_EVERY = 7f, SABOTAGE_DURATION = 4f;
    private static final float SABOTAGE_RANGE2 = 5f * 5f;
    /** El jefe ha cambiado de fase: la pantalla lo consume para sacudir y avisar. */
    public boolean bossPhaseEvent;

    /** Apaga unos segundos un tanque al azar cerca del jefe; false si no había ninguno. */
    private boolean sabotageNearbyTower(Enemy boss) {
        Tower pick = null;
        int seen = 0;
        for (int i = 0; i < towers.size; i++) {
            Tower t = towers.get(i);
            if (t.disabledTimer > 0f) continue;
            float dx = t.position.x - boss.position.x;
            float dz = t.position.z - boss.position.z;
            if (dx * dx + dz * dz > SABOTAGE_RANGE2) continue;
            // reservoir sampling: uno al azar sin montar una lista
            seen++;
            if (MathUtils.random(seen - 1) == 0) pick = t;
        }
        if (pick == null) return false;
        pick.disabledTimer = SABOTAGE_DURATION;
        Fx spark = fxPool.obtain();
        tmp.set(pick.position.x, 0.7f, pick.position.z);
        spark.initHit(tmp, com.badlogic.gdx.graphics.Color.valueOf("ff8a3c"));
        fx.add(spark);
        sound("sell");
        return true;
    }

    private void addDeathFx(Enemy e) {
        Fx f = fxPool.obtain();
        tmp.set(e.position.x, e.centerY(), e.position.z);
        f.initDeath(tmp, e.def.color(), e.def.size);
        fx.add(f);

        Fx goldText = fxPool.obtain();
        goldText.initGold(tmp, e.goldReward);
        fx.add(goldText);

        // los de tierra dejan chatarra unos segundos; los voladores estallan y ya
        if (!e.isFlying()) {
            Fx wreck = fxPool.obtain();
            tmp.set(e.position.x, 0f, e.position.z);
            wreck.initWreck(tmp, e.def.id, e.def.size, e.yawDeg);
            fx.add(wreck);
        }
    }

    /**
     * Número de daño flotante: solo golpes gordos (>=30) o jefes/campeones,
     * y con tope de fx — el juice no puede costar rendimiento ni ruido.
     */
    void damageText(Enemy e, float dmg) {
        if (!com.pcdc.td.ui.Quality.damageText) return;
        boolean bossLike = e.def.livesCost >= 3;
        if (dmg < 30f && !bossLike) return;
        if (fx.size > 96) return;
        Fx f = fxPool.obtain();
        tmp.set(e.position.x, e.centerY() + 0.35f, e.position.z);
        f.initDamage(tmp, Math.round(dmg));
        fx.add(f);
    }

    void projectileHit(Projectile p) {
        sound(p.def.splashRadius > 0f ? "explosion" : "hit");
        float splash = splashFor(p.source, p.def);
        float pen = p.source != null ? penFor(p.source) : p.def.penetration;
        float power = effectPowerFor(p.def);
        if (splash > 0f) {
            boolean fiery = false;
            for (int j = 0; j < p.def.effects.size; j++) {
                if ("burn".equals(p.def.effects.get(j).type)) fiery = true;
            }
            Fx boom = fxPool.obtain();
            boom.initExplosion(p.position, p.def.color(), splash, fiery);
            fx.add(boom);
            Fx scorch = fxPool.obtain();
            tmp.set(p.position.x, 0f, p.position.z);
            scorch.initScorch(tmp, splash);
            fx.add(scorch);
        } else {
            Fx f = fxPool.obtain();
            f.initHit(p.position, p.def.color());
            fx.add(f);
        }
        if (splash > 0f) {
            float r2 = splash * splash;
            for (int i = 0; i < enemies.size; i++) {
                Enemy e = enemies.get(i);
                if (!e.alive) continue;
                if (e.isFlying() ? !p.def.hitsAir : !p.def.hitsGround) continue;
                tmp.set(e.position.x, 0f, e.position.z);
                if (tmp.dst2(p.position.x, 0f, p.position.z) > r2) continue;
                if (p.source != null) {
                    e.lastHitTower = p.source;
                    p.source.damageDealt += p.damage;
                }
                damageText(e, e.hit(p.damage, pen));
                for (int j = 0; j < p.def.effects.size; j++) {
                    e.applyEffect(p.def.effects.get(j), power);
                }
            }
        } else if (p.target != null && p.target.alive) {
            if (p.source != null) {
                p.target.lastHitTower = p.source;
                p.source.damageDealt += p.damage;
            }
            damageText(p.target, p.target.hit(p.damage, pen));
            for (int j = 0; j < p.def.effects.size; j++) {
                p.target.applyEffect(p.def.effects.get(j), power);
            }
        }
        // el proyectil deja una zona persistente donde cae (charca, hielo, napalm)
        if (!p.def.zoneKind.isEmpty()) {
            dropZone(p.position, p.def, p.source, p.damage);
        }
    }

    /**
     * Siembra una zona de suelo (o dispara su sinergia si cae sobre otra
     * complementaria). La usan los proyectiles al impactar y el cono del Arco
     * de plasma, que va pintando el suelo por donde barre.
     */
    void dropZone(Vector3 at, TowerDef def, Tower source, float impactDamage) {
        if (zoneSynergy(at, def, source, impactDamage)) return;
        GroundZone z = zonePool.obtain();
        z.init(at, def, source);
        // SINERGIA DE CLIMA: en el volcán el napalm prende de verdad
        if ("volcano".equals(biome.id) && "napalm".equals(z.kind)) {
            z.maxTtl *= 1.5f;
            z.ttl = z.maxTtl;
        }
        zones.add(z);
    }

    /** Consumido por la pantalla: acaba de dispararse una sinergia de zonas. */
    public boolean synergyEvent;

    /**
     * Sinergias de zonas: si la zona que iba a crear este proyectil cae sobre
     * otra de tipo complementario, ambas se consumen en una reacción.
     * napalm+hielo = vapor que ablanda (los enemigos reciben +25% daño 3 s);
     * napalm+veneno = explosión de gas (daño fuerte en el área).
     * @return true si hubo reacción (la zona nueva no llega a crearse).
     */
    private boolean zoneSynergy(Vector3 at, TowerDef def, Tower source, float impactDamage) {
        String nk = def.zoneKind;
        for (int i = zones.size - 1; i >= 0; i--) {
            GroundZone z = zones.get(i);
            float reach = z.radius + def.zoneRadius * 0.6f;
            if (z.position.dst2(at.x, 0f, at.z) > reach * reach) continue;

            boolean steam = ("napalm".equals(nk) && "ice".equals(z.kind))
                || ("ice".equals(nk) && "napalm".equals(z.kind));
            boolean gas = ("napalm".equals(nk) && "poison".equals(z.kind))
                || ("poison".equals(nk) && "napalm".equals(z.kind));
            if (!steam && !gas) continue;

            float radius = Math.max(z.radius, def.zoneRadius) * 1.35f;
            float r2 = radius * radius;
            if (steam) {
                // choque térmico: poco daño, pero ablanda todo lo que envuelve
                for (int j = 0; j < enemies.size; j++) {
                    Enemy e = enemies.get(j);
                    if (!e.alive || e.isFlying()) continue;
                    if (e.position.dst2(z.position.x, 0f, z.position.z) > r2) continue;
                    if (source != null) e.lastHitTower = source;
                    e.damage(15f);
                    e.vulnMult = 1.25f;
                    e.vulnTimer = Math.max(e.vulnTimer, 3f);
                }
                Fx cloud = fxPool.obtain();
                tmp.set(z.position.x, 0.3f, z.position.z);
                cloud.initExplosion(tmp, com.badlogic.gdx.graphics.Color.valueOf("e8f4ff"), radius, false);
                fx.add(cloud);
            } else {
                // explosión de gas: daño alto que escala con ambas torres
                float dmg = 2f * (impactDamage + z.def.damage);
                for (int j = 0; j < enemies.size; j++) {
                    Enemy e = enemies.get(j);
                    if (!e.alive || e.isFlying()) continue;
                    if (e.position.dst2(z.position.x, 0f, z.position.z) > r2) continue;
                    if (source != null) {
                        e.lastHitTower = source;
                        source.damageDealt += dmg;
                    }
                    e.hit(dmg, 10f);
                }
                Fx boom = fxPool.obtain();
                tmp.set(z.position.x, 0.3f, z.position.z);
                boom.initExplosion(tmp, com.badlogic.gdx.graphics.Color.valueOf("9be24a"), radius, true);
                fx.add(boom);
                Fx scorch = fxPool.obtain();
                tmp.set(z.position.x, 0f, z.position.z);
                scorch.initScorch(tmp, radius);
                fx.add(scorch);
            }
            sound("explosion");
            synergyEvent = true;
            synergiesTriggered++;
            zones.removeIndex(i);
            zonePool.free(z);
            return true;
        }
        return false;
    }

    /**
     * Cadena eléctrica (Tesla): desde el objetivo golpeado salta a enemigos
     * cercanos, perdiendo daño en cada salto. Devuelve el número de saltos.
     */
    void chainDamage(Tower tower, Enemy first, float damage) {
        Enemy last = first;
        float dmg = damage;
        float pen = penFor(tower);
        tower.chainTargets.clear();
        for (int c = 0; c < chainCountFor(tower); c++) {
            Enemy next = nearestChainTarget(tower, last, first);
            if (next == null) break;
            dmg *= tower.def.chainFalloff;
            next.lastHitTower = tower;
            tower.damageDealt += dmg;
            damageText(next, next.hit(dmg, pen));
            tower.applyEffects(next, this);
            addArc(last, next, tower.def.color());
            tower.chainTargets.add(next);
            last = next;
        }
    }

    /**
     * Refracción del Prisma (BEAM): el rayo se divide del objetivo hacia los
     * enemigos cercanos, con daño reducido. Se recalcula cada frame.
     */
    void prismDamage(Tower tower, Enemy first, float beamDmgTimesDt, float dt) {
        tower.chainTargets.clear();
        float pen = penFor(tower);
        for (int c = 0; c < chainCountFor(tower); c++) {
            Enemy next = nearestChainTarget(tower, first, first);
            if (next == null) break;
            float dmg = beamDmgTimesDt * tower.def.chainFalloff;
            next.lastHitTower = tower;
            tower.damageDealt += dmg;
            next.hitOverTime(dmg, pen, dt);
            tower.applyEffects(next, this);
            tower.chainTargets.add(next);
        }
    }

    /**
     * SINERGIA DE CLIMA: bajo la lluvia de la tormenta los enemigos mojados
     * conducen — los arcos del Tesla y el Prisma saltan un 50% más lejos.
     */
    float chainRadiusFor(Tower tower) {
        float mult = "storm".equals(biome.id) ? 1.5f : 1f;
        return tower.def.chainRadius * mult;
    }

    /** Enemigo más cercano a "from" al que la cadena aún no ha golpeado. */
    private Enemy nearestChainTarget(Tower tower, Enemy from, Enemy first) {
        float r = chainRadiusFor(tower);
        float r2 = r * r;
        Enemy best = null;
        float bestD2 = Float.MAX_VALUE;
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (!e.alive || e.isHidden() || e == first || !tower.canTarget(e)) continue;
            if (tower.chainTargets.contains(e, true)) continue;
            float dx = e.position.x - from.position.x;
            float dz = e.position.z - from.position.z;
            float d2 = dx * dx + dz * dz;
            if (d2 > r2 || d2 >= bestD2) continue;
            bestD2 = d2;
            best = e;
        }
        return best;
    }

    /** Arco eléctrico entre dos enemigos (sin sonido de disparo propio). */
    private void addArc(Enemy from, Enemy to, Color color) {
        Fx arc = fxPool.obtain();
        tmp.set(from.position.x, from.centerY(), from.position.z);
        tmp2.set(to.position.x, to.centerY(), to.position.z);
        arc.initTracer(tmp, tmp2, color);
        arc.jagged = true;
        fx.add(arc);
    }

    /** Daño continuo del lanzallamas: enemigos en rango dentro del cono del cabezal. */
    void areaDamage(Tower tower, float dt) {
        float range = rangeFor(tower);
        float range2 = range * range;
        // DEMOLEDOR: no muele sin parar, da un golpe seco cada pulseInterval.
        // damage es daño POR PULSO, así que se aplica con hit() (pasa por
        // armadura como un impacto normal), no como daño por tiempo.
        if (tower.def.pulseInterval > 0f) {
            tower.pulseTimer -= dt;
            if (tower.pulseTimer > 0f) return;
            tower.pulseTimer = tower.def.pulseInterval / Math.max(0.2f, fireRateFor(tower) / Math.max(0.01f, tower.def.fireRate));
            boolean any = false;
            float pen = penFor(tower);
            float power = effectPowerFor(tower.def);
            for (int i = 0; i < enemies.size; i++) {
                Enemy e = enemies.get(i);
                if (!e.alive || e.isHidden() || !tower.canTarget(e)) continue;
                float dx = e.position.x - tower.position.x;
                float dz = e.position.z - tower.position.z;
                if (dx * dx + dz * dz > range2) continue;
                float dmg = damageFor(tower);
                e.lastHitTower = tower;
                tower.damageDealt += dmg;
                damageText(e, e.hit(dmg, pen));
                for (int j = 0; j < tower.def.effects.size; j++) {
                    e.applyEffect(tower.def.effects.get(j), power);
                }
                any = true;
            }
            if (any) {
                Fx boom = fxPool.obtain();
                tmp.set(tower.position.x, 0.35f, tower.position.z);
                boom.initExplosion(tmp, tower.def.color(), range * 0.9f, false);
                fx.add(boom);
                sound("explosion");
            }
            return;
        }
        boolean fireTower = false;
        for (int j = 0; j < tower.def.effects.size; j++) {
            if ("burn".equals(tower.def.effects.get(j).type)) fireTower = true;
        }
        Enemy chainSeed = null;
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (!e.alive || e.isHidden() || !tower.canTarget(e)) continue;
            if (fireTower && "igneous".equals(e.trait)) continue; // inmunes al fuego
            float dx = e.position.x - tower.position.x;
            float dz = e.position.z - tower.position.z;
            if (dx * dx + dz * dz > range2) continue;
            float angleTo = MathUtils.atan2Deg360(dx, dz);
            float diff = Math.abs(((angleTo - tower.headYawDeg) % 360f + 540f) % 360f - 180f);
            if (diff > tower.def.coneAngleDeg) continue;
            float dmg = damageFor(tower) * dt;
            e.lastHitTower = tower;
            tower.damageDealt += dmg;
            e.hitOverTime(dmg, penFor(tower), dt);
            float power = effectPowerFor(tower.def);
            for (int j = 0; j < tower.def.effects.size; j++) {
                e.applyEffect(tower.def.effects.get(j), power);
            }
            // GARFIO TRITURADOR: engancha y devuelve al enemigo al centro de
            // las cuchillas (el tope por enemigo de pullBack evita el bucle)
            if (tower.def.pullDistance > 0f) pullBack(e, tower.def.pullDistance);
            if (chainSeed == null) chainSeed = e;
        }
        // ROTOR DE ARCO: el anillo descarga hacia los de fuera del alcance
        if (chainSeed != null && chainCountFor(tower) > 0) {
            prismDamage(tower, chainSeed, damageFor(tower) * dt, dt);
            // el arco visual va throttled: dibujarlo cada frame era un borrón
            if (MathUtils.randomBoolean(0.12f)) {
                for (int c = 0; c < tower.chainTargets.size; c++) {
                    addArc(chainSeed, tower.chainTargets.get(c), tower.def.color());
                }
            }
        }
        // ARCO DE PLASMA: el cono va prendiendo el suelo por donde barre
        if (!tower.def.zoneKind.isEmpty()) {
            tower.zoneTimer -= dt;
            if (tower.zoneTimer <= 0f) {
                tower.zoneTimer = ZONE_PAINT_EVERY;
                float yaw = tower.headYawDeg * MathUtils.degreesToRadians;
                tmp.set(tower.position.x + MathUtils.sin(yaw) * range * 0.62f, 0f,
                    tower.position.z + MathUtils.cos(yaw) * range * 0.62f);
                dropZone(tmp, tower.def, tower, tower.def.damage);
            }
        }
    }

    /** Cada cuánto deja una mancha el cono que pinta el suelo. */
    private static final float ZONE_PAINT_EVERY = 1.1f;

    void addTracer(Vector3 from, Enemy target, Color color) {
        addTracer(from, target, color, "shoot_sniper");
    }

    void addTracer(Vector3 from, Enemy target, Color color, String sfx) {
        sound(sfx);
        Fx f = fxPool.obtain();
        tmp.set(target.position.x, target.centerY(), target.position.z);
        f.initTracer(from, tmp, color);
        if ("zap".equals(sfx)) {
            // el disparo del tesla es un relámpago: quebrado y algo más duradero
            f.jagged = true;
            f.maxTtl = f.ttl = 0.18f;
        }
        fx.add(f);
    }

    /** Enemigo targeteable en rango según la estrategia de la torre. */
    Enemy findTarget(Tower tower) {
        float range = rangeFor(tower);
        float range2 = range * range;
        Enemy best = null;
        float bestScore = 0f;
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (!e.alive || e.isHidden() || !tower.canTarget(e)) continue;
            float dx = e.position.x - tower.position.x;
            float dz = e.position.z - tower.position.z;
            float d2 = dx * dx + dz * dz;
            if (d2 > range2) continue;
            float score;
            switch (tower.targeting) {
                case 1: score = -e.pathProgress; break; // último
                case 2: score = e.hp; break;            // más fuerte
                case 3: score = -d2; break;             // más cercano
                // AIRE: los voladores primero (y entre ellos, el más adelantado);
                // si no hay ninguno a tiro sigue disparando a tierra
                case 4: score = e.pathProgress + (e.isFlying() ? 100000f : 0f); break;
                default: score = e.pathProgress; break; // primero
            }
            if (best == null || score > bestScore) {
                best = e;
                bestScore = score;
            }
        }
        return best;
    }
}
