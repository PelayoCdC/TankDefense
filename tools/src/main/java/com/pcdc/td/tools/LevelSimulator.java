package com.pcdc.td.tools;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import com.pcdc.td.data.BuildingDef;
import com.pcdc.td.data.Catalogs;
import com.pcdc.td.data.LevelDef;
import com.pcdc.td.data.TowerDef;
import com.pcdc.td.data.UpgradeNodeDef;
import com.pcdc.td.meta.MetaEffects;
import com.pcdc.td.meta.PlayerProfile;
import com.pcdc.td.meta.StatBlock;
import com.pcdc.td.world.CellType;
import com.pcdc.td.world.GameWorld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Simula los 100 niveles sin gráficos con una IA sencilla de construcción,
 * para detectar niveles imposibles o triviales. Uso: LevelSimulator <assetsDir>
 */
public class LevelSimulator {

    /** Orden de preferencia de compra de la IA. */
    private static final String[] BUY_PRIORITY = {
        "machinegun", "cannon", "tesla", "mortar", "sniper", "laser",
        "slow", "poison", "flamethrower", "saw"
    };

    public static void main(String[] args) throws IOException {
        Path assets = Paths.get(args.length > 0 ? args[0] : "assets");
        Catalogs catalogs = new Catalogs();
        catalogs.loadFromStrings(
            read(assets, "data/towers.json"),
            read(assets, "data/enemies.json"),
            read(assets, "data/fusions.json"),
            read(assets, "data/biomes.json"),
            read(assets, "data/upgrades.json"),
            read(assets, "data/buildings.json"));
        computeMaxStats(catalogs);
        System.out.printf("Meta al máximo: daño x%.2f cadencia x%.2f alcance x%.2f "
                + "oro x%.2f oro inicial +%d vidas +%d%n",
            maxStats.damageMult, maxStats.fireRateMult, maxStats.rangeMult,
            maxStats.goldPerKillMult, maxStats.startGoldFlat, maxStats.livesFlat);
        if (fullMeta) System.out.println("(modo completista: árbol completo en todos los niveles)");

        if (endlessMode) {
            runEndless(assets, catalogs);
            return;
        }

        int wins = 0, losses = 0;
        float totalWinTime = 0f;
        List<Integer> lost = new ArrayList<>();
        List<Integer> flawless = new ArrayList<>();
        for (int id = 1; id <= 100; id++) {
            Path levelFile = assets.resolve(String.format("levels/level_%03d.json", id));
            if (!Files.exists(levelFile)) continue;
            LevelDef level = Catalogs.newJson().fromJson(LevelDef.class, read(assets,
                String.format("levels/level_%03d.json", id)));

            Result r = simulate(level, catalogs);
            if (r.won) {
                wins++;
                totalWinTime += r.time;
                if (r.livesLeft == r.livesStart) flawless.add(id);
            } else {
                losses++;
                lost.add(id);
            }
            System.out.printf("nivel %3d [%s]: %s vidas %d/%d torres %d t=%.0fs%n",
                id, level.biome, r.won ? "GANA " : "PIERDE", r.livesLeft, r.livesStart, r.towersBuilt, r.time);
        }
        System.out.println("== Resumen: " + wins + " ganados, " + losses + " perdidos ==");
        if (wins > 0) {
            System.out.printf("Duración media de victoria: %.0fs%n", totalWinTime / wins);
        }
        System.out.println("Perdidos: " + lost);
        System.out.println("Sin perder vidas: " + flawless.size() + " niveles");
    }

    private static String read(Path assets, String rel) throws IOException {
        return Files.readString(assets.resolve(rel), StandardCharsets.UTF_8);
    }

    /** -Pendless: mide hasta dónde llega el modo Sin fin de cada bioma. */
    private static final boolean endlessMode = Boolean.getBoolean("td.sim.endless");

    /**
     * El Sin fin no tiene victoria: se juega hasta que caen las vidas. Interesa
     * la OLEADA alcanzada (la dificultad debe frenar al jugador) y los
     * CRISTALES que paga (no puede convertirse en una máquina de farmeo).
     */
    private static void runEndless(Path assets, Catalogs catalogs) throws IOException {
        System.out.println("== Modo SIN FIN (último nivel de cada bioma) ==");
        int totalCrystals = 0;
        for (int biome = 0; biome < 10; biome++) {
            int id = biome * 5 + 5;
            Path file = assets.resolve(String.format("levels/level_%03d.json", id));
            if (!Files.exists(file)) continue;
            LevelDef level = Catalogs.newJson().fromJson(LevelDef.class,
                read(assets, String.format("levels/level_%03d.json", id)));

            StatBlock stats = expectedStats(50, catalogs); // se juega al final
            GameWorld world = new GameWorld(level, catalogs, stats, true);
            EndlessResult r = playEndless(world);
            // misma fórmula que GameplayScreen al cerrar una partida sin fin
            int crystals = r.wave * (2 + biome);
            totalCrystals += crystals;
            System.out.printf("sin fin %-8s (nivel %2d): oleada %2d  tanques %2d  t=%.0fs  → %d cristales%n",
                level.biome, id, r.wave, r.towers, r.time, crystals);
        }
        System.out.println("Cristales por una vuelta completa a los 10 biomas: " + totalCrystals);
    }

    private static class EndlessResult {
        int wave, towers;
        float time;
    }

    private static EndlessResult playEndless(GameWorld world) {
        List<GridPoint2> candidates = buildCandidates(world);
        Array<TowerDef> allowed = new Array<>();
        for (String pref : BUY_PRIORITY) {
            if (world.stats.towerAvailable(pref)) allowed.add(world.catalogs.tower(pref));
        }
        EndlessResult r = new EndlessResult();
        if (allowed.isEmpty()) return r;

        world.callNextWave();
        float t = 0f, buyTimer = 0f;
        int buyIndex = 0;
        final float STEP = 1f / 30f;
        // tope de seguridad: si a la oleada 80 sigue vivo, el modo no frena
        while (world.state == GameWorld.State.RUNNING && t < 9000f && world.waves.displayWave() < 80) {
            world.step(STEP);
            t += STEP;
            buyTimer += STEP;
            if (buyTimer >= 0.7f) {
                buyTimer = 0f;
                buyIndex = buyStep(world, allowed, candidates, buyIndex);
            }
        }
        r.wave = world.waves.displayWave();
        r.towers = world.towers.size;
        r.time = t;
        return r;
    }

    static class Result {
        boolean won;
        int livesLeft, livesStart, towersBuilt;
        float time;
    }

    /** -Dtd.sim.plus=true simula el modo Nivel+ (vida de enemigos x1.3). */
    private static final boolean plusMode = Boolean.getBoolean("td.sim.plus");

    static Result simulate(LevelDef level, Catalogs catalogs) {
        StatBlock stats = expectedStats(level.id, catalogs);
        GameWorld world = new GameWorld(level, catalogs, stats, false,
            plusMode ? 1.3f : 1f);

        List<GridPoint2> candidates = buildCandidates(world);

        // lista explícita del nivel (tutoriales) o, sin lista, lo desbloqueado en el árbol
        Array<TowerDef> allowed = new Array<>();
        for (String pref : BUY_PRIORITY) {
            boolean ok = level.allowedTowers.size > 0
                ? level.allowedTowers.contains(pref, false)
                : stats.towerAvailable(pref);
            if (ok) allowed.add(catalogs.tower(pref));
        }

        Result r = new Result();
        r.livesStart = world.lives;
        if (allowed.isEmpty()) return r;

        // la primera oleada arranca a petición del jugador; la IA empieza ya
        world.callNextWave();

        float t = 0f;
        float buyTimer = 0f;
        int buyIndex = 0;
        final float STEP = 1f / 30f;
        while (world.state == GameWorld.State.RUNNING && t < 3000f) {
            world.step(STEP);
            t += STEP;
            buyTimer += STEP;
            if (buyTimer >= 0.7f) {
                buyTimer = 0f;
                int before = world.towers.size;
                buyIndex = buyStep(world, allowed, candidates, buyIndex);
                if (world.towers.size > before) r.towersBuilt++;
            }
        }
        r.won = world.state == GameWorld.State.VICTORY;
        r.livesLeft = world.lives;
        r.time = t;
        return r;
    }

    /** Celdas construibles ordenadas por cercanía al camino (radio 2). */
    private static List<GridPoint2> buildCandidates(GameWorld world) {
        List<GridPoint2> candidates = new ArrayList<>();
        for (int z = 0; z < world.map.height; z++) {
            for (int x = 0; x < world.map.width; x++) {
                if (world.map.get(x, z) == CellType.GROUND && pathScore(world, x, z) > 0) {
                    candidates.add(new GridPoint2(x, z));
                }
            }
        }
        candidates.sort(Comparator.comparingInt((GridPoint2 c) -> -pathScore(world, c.x, c.y)));
        return candidates;
    }

    /**
     * Un tic de compra de la IA: coloca el siguiente tanque de la rotación o,
     * si no hay hueco junto al camino, mejora el que más daño lleva hecho.
     * @return el índice de rotación actualizado.
     */
    private static int buyStep(GameWorld world, Array<TowerDef> allowed,
                               List<GridPoint2> candidates, int buyIndex) {
        TowerDef def = allowed.get(buyIndex % allowed.size);
        if (world.gold < def.cost) {
            // si la preferida no se puede pagar, cae a la más barata de las 3 primeras
            def = null;
            for (int a = 0; a < Math.min(3, allowed.size); a++) {
                TowerDef option = allowed.get(a);
                if (world.gold >= option.cost && (def == null || option.cost < def.cost)) def = option;
            }
        }
        if (def == null) return buyIndex;

        boolean placed = false;
        for (GridPoint2 c : candidates) {
            if (world.canBuildAt(c.x, c.y)) {
                if (world.tryPlace(c.x, c.y, def)) {
                    buyIndex++;
                    placed = true;
                }
                break;
            }
        }
        if (!placed) {
            com.pcdc.td.world.Tower best = null;
            for (com.pcdc.td.world.Tower tw : world.towers) {
                int cost = world.upgradeCost(tw);
                if (cost >= 0 && world.gold >= cost
                    && (best == null || tw.damageDealt > best.damageDealt)) best = tw;
            }
            if (best != null) world.tryUpgrade(best);
        }
        return buyIndex;
    }

    private static int pathScore(GameWorld world, int x, int z) {
        int score = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (world.map.inBounds(x + dx, z + dz)
                    && world.map.get(x + dx, z + dz) == CellType.PATH) {
                    int dist = Math.max(Math.abs(dx), Math.abs(dz));
                    score += dist <= 1 ? 3 : 1;
                }
            }
        }
        return score;
    }

    /**
     * Poder meta del jugador al llegar al nivel L. Se calcula A PARTIR DEL ÁRBOL
     * REAL (assets/data/upgrades.json y buildings.json), no de una curva
     * inventada: se supone que el jugador va comprando por orden de coste y que
     * lo tiene todo hacia el nivel FULL_TREE_LEVEL.
     *
     * -Dtd.sim.meta=full fuerza el árbol completo en todos los niveles: sirve
     * para medir al jugador completista, que es quien nota si el juego se
     * queda corto de dificultad.
     */
    private static boolean fullMeta = "full".equals(System.getProperty("td.sim.meta"));
    /** Bonus del árbol completo + edificios al máximo; se rellena al cargar. */
    private static StatBlock maxStats = new StatBlock();
    private static int totalTreeCost;

    static void computeMaxStats(Catalogs catalogs) {
        PlayerProfile profile = new PlayerProfile();
        totalTreeCost = 0;
        for (UpgradeNodeDef node : catalogs.upgrades) {
            profile.upgrades.add(node.id);
            totalTreeCost += node.cost;
        }
        for (BuildingDef b : catalogs.buildings) profile.buildings.put(b.id, b.levels.size);
        maxStats = MetaEffects.compute(profile, catalogs);
    }

    /**
     * Compra REAL simulada: con un presupuesto de cristales proporcional al
     * nivel, la IA compra nodos del árbol de barato a caro respetando los
     * requisitos, y niveles de edificio con las medallas esperadas. Así los
     * desbloqueos de torres y los bonus por torre entran en la simulación
     * igual que le pasarían a un jugador.
     */
    private static StatBlock expectedStats(int level, Catalogs catalogs) {
        if (fullMeta) return maxStats;
        // presupuesto = ingreso REAL esperado hasta este nivel: metaReward a ~2
        // estrellas + firstClear de los niveles anteriores (85% invertido).
        // Antes se interpolaba sobre el coste del árbol y regalaba de más.
        int income = 0;
        for (int l = 1; l < level; l++) {
            income += DifficultyCurve.metaReward(l) + DifficultyCurve.firstClearBonus(l);
        }
        int budget = Math.round(income * 0.85f);
        PlayerProfile p = new PlayerProfile();
        boolean progress = true;
        while (progress) {
            progress = false;
            UpgradeNodeDef cheapest = null;
            for (UpgradeNodeDef node : catalogs.upgrades) {
                if (p.hasUpgrade(node.id) || node.cost > budget) continue;
                boolean met = true;
                for (String req : node.requires) {
                    if (!p.hasUpgrade(req)) {
                        met = false;
                        break;
                    }
                }
                if (!met) continue;
                if (cheapest == null || node.cost < cheapest.cost) cheapest = node;
            }
            if (cheapest != null) {
                p.upgrades.add(cheapest.id);
                budget -= cheapest.cost;
                progress = true;
            }
        }
        // medallas: ~2,2 estrellas de media por nivel completado
        int medals = Math.round(2.2f * (level - 1));
        boolean built = true;
        while (built) {
            built = false;
            BuildingDef best = null;
            int bestCost = Integer.MAX_VALUE;
            for (BuildingDef b : catalogs.buildings) {
                int lvl = p.buildingLevel(b.id);
                if (lvl >= b.levels.size) continue;
                int cost = b.levels.get(lvl).cost;
                if (cost <= medals && cost < bestCost) {
                    best = b;
                    bestCost = cost;
                }
            }
            if (best != null) {
                p.buildings.put(best.id, p.buildingLevel(best.id) + 1);
                medals -= bestCost;
                built = true;
            }
        }
        return MetaEffects.compute(p, catalogs);
    }
}
