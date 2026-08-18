package com.pcdc.td.world;

import com.badlogic.gdx.utils.Array;
import com.pcdc.td.data.LevelDef;

/** Lanza las oleadas definidas en el nivel. */
public class WaveManager {

    private static class GroupState {
        LevelDef.SpawnGroupDef def;
        int spawned;
        float timer;
    }

    private final LevelDef level;
    private final boolean survival;
    private final java.util.Random survivalRng;
    /** Índice de la última oleada lanzada; -1 si no ha empezado ninguna. */
    public int waveIndex = -1;
    public float countdown;
    public boolean spawning;
    /** La primera oleada no arranca hasta que el jugador pulsa "Empezar". */
    public boolean started;
    /** Evento de la oleada en curso ("" | "enraged" | "loot"). */
    public String currentEvent = "";

    private final Array<GroupState> groups = new Array<>();

    public WaveManager(LevelDef level) {
        this(level, false);
    }

    public WaveManager(LevelDef level, boolean survival) {
        this.level = level;
        this.survival = survival;
        this.survivalRng = new java.util.Random(level.id * 977L);
        this.countdown = level.waves.first().waveDelay;
    }

    /** -1 en modo sin fin (no hay total). */
    public int totalWaves() {
        return survival ? -1 : level.waves.size;
    }

    /** La oleada que viene (para la vista previa del HUD), o null si está en curso. */
    public LevelDef.WaveDef upcomingWave() {
        if (!started) return level.waves.first();
        if (!spawning && waveIndex + 1 < level.waves.size) return level.waves.get(waveIndex + 1);
        return null;
    }

    /** Sin fin: fabrica la siguiente oleada procedural y la añade al nivel. */
    private void ensureSurvivalWave() {
        if (!survival || waveIndex + 2 <= level.waves.size) return;
        int w = level.waves.size + 1;
        LevelDef.WaveDef wd = new LevelDef.WaveDef();
        wd.waveDelay = 10f;
        // los biomas con rasgos duros (división, escudos, élites) reciben MENOS
        // enemigos, igual que en campaña: si no, el Sin fin de las corruptas
        // duraba 9 oleadas y el de la pradera más de 70
        float budget = 26f * (1f + 0.02f * level.id) * (1f + 0.13f * w)
            * survivalBiomeFactor((level.id - 1) / 5);

        if (w % 5 == 0) {
            LevelDef.SpawnGroupDef boss = new LevelDef.SpawnGroupDef();
            boss.enemy = "boss";
            boss.count = 1 + w / 15;
            boss.interval = 4f;
            wd.groups.add(boss);
            budget *= 0.5f;
        }
        String[] types = {"normal", "fast", "tank", "flyer"};
        float[] costs = {10f, 9f, 35f, 22f};
        float[] intervals = {0.8f, 0.5f, 2.2f, 1.2f};
        int pick = survivalRng.nextInt(types.length);
        int pick2 = survivalRng.nextInt(types.length);
        LevelDef.SpawnGroupDef g1 = new LevelDef.SpawnGroupDef();
        g1.enemy = types[pick];
        g1.count = Math.max(3, Math.min(30, Math.round(budget * 0.65f / costs[pick])));
        g1.interval = intervals[pick];
        wd.groups.add(g1);
        LevelDef.SpawnGroupDef g2 = new LevelDef.SpawnGroupDef();
        g2.enemy = types[pick2];
        g2.count = Math.max(2, Math.min(30, Math.round(budget * 0.35f / costs[pick2])));
        g2.interval = intervals[pick2];
        g2.delay = 3f + survivalRng.nextInt(4);
        wd.groups.add(g2);

        level.waves.add(wd);
    }

    /** Compensación por dureza del bioma en Sin fin (espeja la de campaña). */
    private static float survivalBiomeFactor(int tier) {
        return switch (tier) {
            case 2, 3 -> 0.9f;
            case 4, 5 -> 0.92f;
            case 6 -> 0.7f;
            case 7 -> 0.55f;   // ruinas: los portales saltan tramos defendidos
            case 8 -> 0.68f;
            case 9 -> 0.55f;
            default -> 1f;
        };
    }

    /** Oleada mostrada en el HUD (1-based; 0 antes de empezar). */
    public int displayWave() {
        return waveIndex + 1;
    }

    /** Tamaño del lote: cuántos salen a la vez en cada tick de spawn. */
    private static int batchFor(LevelDef.SpawnGroupDef def) {
        if (def.batch > 0) return def.batch;
        switch (def.enemy) {
            case "boss": return 1;
            case "tank": return 2;
            case "flyer": return 2;
            default: return 3;
        }
    }

    public void update(float dt, GameWorld world) {
        if (!started) return;
        ensureSurvivalWave();
        if (spawning) {
            boolean pending = false;
            for (int i = 0; i < groups.size; i++) {
                GroupState gs = groups.get(i);
                if (gs.spawned >= gs.def.count) continue;
                gs.timer -= dt;
                while (gs.timer <= 0f && gs.spawned < gs.def.count) {
                    int batch = Math.min(batchFor(gs.def), gs.def.count - gs.spawned);
                    for (int n = 0; n < batch; n++) {
                        world.spawnEnemy(gs.def.enemy, gs.def.pathIndex);
                        gs.spawned++;
                    }
                    // el intervalo escala con el lote: mismo ritmo medio, en ráfagas
                    gs.timer += gs.def.interval * batch;
                }
                if (gs.spawned < gs.def.count) pending = true;
            }
            if (!pending) {
                spawning = false;
                if (waveIndex + 1 < level.waves.size) {
                    countdown = level.waves.get(waveIndex + 1).waveDelay;
                }
            }
        } else if (waveIndex + 1 < level.waves.size) {
            countdown -= dt;
            if (countdown <= 0f) startNextWave(world);
        }
    }

    private void startNextWave(GameWorld world) {
        waveIndex++;
        groups.clear();
        currentEvent = level.waves.get(waveIndex).event == null
            ? "" : level.waves.get(waveIndex).event;
        for (LevelDef.SpawnGroupDef def : level.waves.get(waveIndex).groups) {
            GroupState gs = new GroupState();
            gs.def = def;
            gs.timer = def.delay;
            groups.add(gs);
        }
        spawning = true;
        world.onWaveStart();
    }

    /** Salta la cuenta atrás y devuelve los segundos ahorrados (para el bonus de oro). */
    public float skipCountdown() {
        if (!started) {
            started = true;
            countdown = 0f;
            return 0f; // la primera oleada no da bonus: arranca cuando quiere el jugador
        }
        if (spawning || waveIndex + 1 >= level.waves.size || countdown <= 0f) return 0f;
        float saved = countdown;
        countdown = 0f;
        return saved;
    }

    public boolean canCallEarly() {
        return !started || (!spawning && waveIndex + 1 < level.waves.size);
    }

    /** Reanuda una partida guardada: la oleada indicada se relanza en breve. */
    public void resumeAt(int savedWaveIndex) {
        started = true;
        waveIndex = savedWaveIndex - 1;
        spawning = false;
        countdown = 5f;
    }

    /** Enemigos de la oleada en curso que aún no han salido del portal. */
    public int pendingSpawns() {
        int n = 0;
        for (int i = 0; i < groups.size; i++) {
            GroupState gs = groups.get(i);
            n += Math.max(0, gs.def.count - gs.spawned);
        }
        return n;
    }

    /** Enemigos totales de la oleada en curso (0 antes de la primera). */
    public int waveTotal() {
        int n = 0;
        for (int i = 0; i < groups.size; i++) n += groups.get(i).def.count;
        return n;
    }

    public boolean allSpawned() {
        return waveIndex >= level.waves.size - 1 && !spawning;
    }
}
