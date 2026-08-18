package com.pcdc.td.tools;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.pcdc.td.data.Catalogs;
import com.pcdc.td.data.LevelDef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Genera assets/levels/level_006.json ... level_050.json y levels_index.json.
 * Los niveles 1-5 (tutorial) se escriben a mano y nunca se tocan.
 * Uso: LevelGenerator <assetsDir> [--force]
 * Sin --force no sobrescribe niveles ya existentes (respeta retoques manuales).
 *
 * Los niveles generados NO llevan allowedTowers: lista vacía = todos los
 * tanques base que el jugador haya desbloqueado en el árbol.
 */
public class LevelGenerator {

    private static final int FIRST_GENERATED = 6;
    private static final int LAST_LEVEL = DifficultyCurve.LAST_LEVEL;
    private static final int PER_BIOME = DifficultyCurve.LEVELS_PER_BIOME;

    private static final String[] BIOMES = {
        "meadow", "desert", "forest", "snow", "volcano",
        "swamp", "crystal", "ruins", "storm", "corrupt"
    };
    private static final String[] BIOME_NAMES = {
        "Pradera", "Desierto", "Bosque", "Nieve", "Volcán",
        "Pantano", "Cavernas de cristal", "Ruinas arcanas", "Cielo tormentoso", "Tierras corruptas"
    };
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    /** id, coste de presupuesto, intervalo de spawn, nivel mínimo. */
    private record EnemyPick(String id, float cost, float interval, int minLevel) {
    }

    /** El grueso de la oleada: de aquí sale SIEMPRE el grupo principal. */
    private static final List<EnemyPick> PICKS = List.of(
        new EnemyPick("normal", 10f, 0.8f, 1),
        new EnemyPick("fast", 9f, 0.5f, 1),
        new EnemyPick("tank", 35f, 2.2f, 6),
        new EnemyPick("flyer", 22f, 1.2f, 11)
    );

    /**
     * Tropa con OFICIO (sanador, zapador, portador): entra escalonada para que
     * cada tramo traiga una pregunta nueva (a quién disparo, dónde coloco,
     * cuándo mato), pero siempre como APOYO en grupo secundario y con tope de
     * unidades: repartidos a saco como un tipo más, la campaña simulada se
     * hundía de 42/50 a 37/50.
     */
    private record RolePick(String id, float cost, float interval, int minLevel, int maxCount) {
    }

    private static final List<RolePick> ROLE_PICKS = List.of(
        new RolePick("healer", 46f, 2.5f, 16, 4),
        new RolePick("sapper", 30f, 1.6f, 21, 5),
        new RolePick("carrier", 80f, 3f, 26, 3)
    );

    /**
     * UNIDAD INSIGNIA de cada bioma: solo sale en sus cinco niveles y en el
     * bioma final, donde vuelven todas. El índice es el tier del bioma.
     */
    private record SignaturePick(String id, float cost, float interval, int maxCount) {
    }

    // Los costes van MUY por encima de su vida: estas unidades multiplican la
    // resistencia de toda la oleada (escudos, armadura, ocultación), así que
    // pagarlas a precio de carne de cañón hundía la campaña simulada a 36/50.
    private static final List<SignaturePick> SIGNATURES = List.of(
        new SignaturePick("charger", 32f, 1.4f, 5),      // pradera
        new SignaturePick("mirage", 40f, 1.4f, 4),       // desierto
        new SignaturePick("warden", 70f, 2.2f, 3),       // bosque
        new SignaturePick("chiller", 62f, 2.2f, 3),      // nieve
        new SignaturePick("magma", 44f, 1.8f, 4),        // volcán
        new SignaturePick("bogmist", 64f, 2.2f, 3),      // pantano
        new SignaturePick("resonator", 75f, 2.4f, 3),    // cristal
        new SignaturePick("acolyte", 52f, 2f, 3),        // ruinas
        new SignaturePick("stormcaller", 60f, 2.4f, 3),  // tormenta
        new SignaturePick("abomination", 75f, 3f, 3)     // corruptas
    );

    private static final int LAST_TIER = 9;

    public static void main(String[] args) throws IOException {
        Path assets = Paths.get(args.length > 0 ? args[0] : "assets");
        boolean force = Arrays.asList(args).contains("--force");
        Path levelsDir = assets.resolve("levels");
        Files.createDirectories(levelsDir);

        Json json = Catalogs.newJson();
        json.setOutputType(JsonWriter.OutputType.json);

        int written = 0, skipped = 0;
        for (int id = FIRST_GENERATED; id <= LAST_LEVEL; id++) {
            Path file = levelsDir.resolve(String.format("level_%03d.json", id));
            if (Files.exists(file) && !force) {
                skipped++;
                continue;
            }
            LevelDef level = generateLevel(id);
            Files.writeString(file, json.prettyPrint(level), StandardCharsets.UTF_8);
            written++;
        }
        // la campaña se acortó a 50: borrar los niveles sobrantes de builds anteriores
        int deleted = 0;
        for (int id = LAST_LEVEL + 1; id <= 100; id++) {
            Path file = levelsDir.resolve(String.format("level_%03d.json", id));
            if (Files.deleteIfExists(file)) deleted++;
        }
        System.out.println("Niveles generados: " + written + " (omitidos existentes: " + skipped
            + ", borrados sobrantes: " + deleted + ")");

        writeIndex(levelsDir, assets);
        System.out.println("Índice escrito: " + assets.resolve("levels/levels_index.json"));
    }

    // ---- nivel ----

    /**
     * Semilla revuelta (SplitMix64). Con `new Random(1000 + id)` a pelo, los
     * primeros nextInt(n) de semillas consecutivas salían IGUALES: los niveles
     * 17 a 29 compartían plantilla y el jugador jugaba trece veces el mismo
     * mapa entre nieve, volcán y pantano.
     */
    private static Random rngFor(int id) {
        long z = (1000L + id) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return new Random(z ^ (z >>> 31));
    }

    /** Plantilla elegida para cada nivel, en un pase hacia delante. */
    private static final java.util.Map<Integer, PathGenerator.Template> TEMPLATE_PLAN =
        new java.util.HashMap<>();

    /**
     * Plantilla del nivel, evitando las de los dos niveles anteriores. Se
     * planifica en orden creciente y se memoriza: así el veto mira siempre a
     * la elección REAL del vecino, no a una recalculada sin veto.
     */
    private static PathGenerator.Template templateFor(int id) {
        PathGenerator.Template known = TEMPLATE_PLAN.get(id);
        if (known != null) return known;
        for (int i = FIRST_GENERATED; i <= id; i++) {
            if (TEMPLATE_PLAN.containsKey(i)) continue;
            List<PathGenerator.Template> avoid = new ArrayList<>();
            for (int back = 1; back <= 2; back++) {
                PathGenerator.Template prev = TEMPLATE_PLAN.get(i - back);
                if (prev != null) avoid.add(prev);
            }
            TEMPLATE_PLAN.put(i, PathGenerator.pickTemplate(i,
                DifficultyCurve.gridWidth(i), DifficultyCurve.gridHeight(i), rngFor(i), avoid));
        }
        return TEMPLATE_PLAN.get(id);
    }

    static LevelDef generateLevel(int id) {
        Random rng = rngFor(id);
        int tier = (id - 1) / PER_BIOME; // 0..9

        LevelDef level = new LevelDef();
        level.id = id;
        level.biome = BIOMES[tier];
        level.name = BIOME_NAMES[tier] + " " + ROMAN[(id - 1) % PER_BIOME];
        level.gridWidth = DifficultyCurve.gridWidth(id);
        level.gridHeight = DifficultyCurve.gridHeight(id);
        level.startGold = DifficultyCurve.startGold(id);
        level.lives = DifficultyCurve.lives(id);
        level.rewards = new LevelDef.RewardsDef();
        level.rewards.metaCurrency = DifficultyCurve.metaReward(id);
        level.rewards.firstClearBonus = DifficultyCurve.firstClearBonus(id);

        // caminos (la plantilla se planifica aparte para no repetir vecinos)
        PathGenerator.Template template = templateFor(id);
        List<int[][]> paths = PathGenerator.generate(template, level.gridWidth, level.gridHeight, rng);
        for (int[][] p : paths) {
            LevelDef.PathDef pd = new LevelDef.PathDef();
            pd.waypoints = p;
            level.paths.add(pd);
        }

        // obstáculos decorativos fuera del camino (más en mapas grandes)
        boolean[][] pathCells = PathGenerator.rasterize(paths, level.gridWidth, level.gridHeight);
        int blocks = 2 + rng.nextInt(3) + level.gridWidth / 6;
        List<int[]> blocked = new ArrayList<>();
        for (int tries = 0; tries < 80 && blocked.size() < blocks; tries++) {
            int x = rng.nextInt(level.gridWidth);
            int z = rng.nextInt(level.gridHeight);
            if (pathCells[x][z]) continue;
            boolean dup = blocked.stream().anyMatch(b -> b[0] == x && b[1] == z);
            if (!dup) blocked.add(new int[]{x, z});
        }
        level.blockedCells = blocked.toArray(new int[0][]);

        // sin allowedTowers: los tanques los decide el árbol de mejoras

        // oleadas
        int waves = DifficultyCurve.wavesFor(id);
        boolean bossLevel = id % PER_BIOME == 0;
        for (int w = 1; w <= waves; w++) {
            boolean bossWave = bossLevel && w == waves;
            LevelDef.WaveDef wd = bossWave
                ? buildBossWave(id, w, rng, paths.size())
                : buildWave(id, w, rng, paths.size(), tier);
            // eventos raros: desde el bioma 3 (con arsenal), nunca en las
            // primeras oleadas ni en la del jefe
            if (!bossWave && id >= 11 && w >= 5 && rng.nextFloat() < 0.12f) {
                wd.event = rng.nextBoolean() ? "enraged" : "loot";
            }
            level.waves.add(wd);
        }

        // mini-jefe a mitad del 3er nivel de cada bioma: presenta la habilidad
        // del jefe antes de la pelea de verdad
        if (id % PER_BIOME == 3 && id > 5) {
            LevelDef.WaveDef mid = level.waves.get(waves / 2);
            LevelDef.SpawnGroupDef champ = new LevelDef.SpawnGroupDef();
            champ.enemy = "champion";
            champ.count = 1;
            champ.interval = 3f;
            champ.delay = 2f;
            mid.groups.add(champ);
        }

        checkWinnability(level);
        return level;
    }

    private static LevelDef.WaveDef buildWave(int id, int wave, Random rng, int pathCount, int tier) {
        LevelDef.WaveDef wd = new LevelDef.WaveDef();
        wd.waveDelay = wave == 1 ? 6f : Math.min(12f, 7f + wave * 0.4f);

        float budget = DifficultyCurve.waveBudget(id, wave);
        List<EnemyPick> available = PICKS.stream().filter(p -> id >= p.minLevel()).toList();

        // hasta 3 grupos en oleadas gordas; los tipos caros pesan más al final
        int groupCount = 1;
        if (budget > 120f && rng.nextFloat() < 0.65f) groupCount = 2;
        if (budget > 260f && rng.nextFloat() < 0.5f) groupCount = 3;
        float[] shares = switch (groupCount) {
            case 2 -> new float[]{0.65f, 0.35f};
            case 3 -> new float[]{0.5f, 0.3f, 0.2f};
            default -> new float[]{1f};
        };

        List<RolePick> roles = ROLE_PICKS.stream().filter(p -> id >= p.minLevel()).toList();
        // como mucho UN grupo de apoyo por oleada, y nunca el principal
        int supportGroup = !roles.isEmpty() && groupCount > 1 && rng.nextFloat() < 0.45f
            ? 1 + rng.nextInt(groupCount - 1) : -1;

        // UNIDAD INSIGNIA: la del bioma desde su primera oleada; en el bioma
        // final vuelven todas, una por oleada. Nunca es el grupo principal (el
        // grueso lo sigue poniendo la tropa común) ni coincide con el oficio.
        SignaturePick signature = null;
        int signatureGroup = -1;
        // una de cada cuatro oleadas: es la fauna característica del bioma, no
        // la tropa de siempre; en todas las oleadas dejaba de ser especial
        if (wave >= 2 && rng.nextFloat() < 0.25f) {
            signature = tier == LAST_TIER
                ? SIGNATURES.get(rng.nextInt(SIGNATURES.size()))
                : SIGNATURES.get(tier);
            // en los biomas tempranos casi ninguna oleada llega a dos grupos:
            // exigirlo dejaba a desierto y bosque SIN su unidad insignia
            if (groupCount == 1) {
                groupCount = 2;
                shares = new float[]{0.72f, 0.28f};
            }
            signatureGroup = 1 + rng.nextInt(groupCount - 1);
            if (signatureGroup == supportGroup) supportGroup = -1; // manda la insignia
        }

        for (int g = 0; g < groupCount; g++) {
            LevelDef.SpawnGroupDef group = new LevelDef.SpawnGroupDef();
            if (g == signatureGroup) {
                group.enemy = signature.id();
                group.count = Math.max(2, Math.min(signature.maxCount(),
                    Math.round(budget * shares[g] / signature.cost())));
                group.interval = signature.interval();
            } else if (g == supportGroup) {
                RolePick role = roles.get(rng.nextInt(roles.size()));
                group.enemy = role.id();
                group.count = Math.max(2, Math.min(role.maxCount(),
                    Math.round(budget * shares[g] / role.cost())));
                group.interval = role.interval();
            } else {
                EnemyPick pick = weightedPick(available, wave, rng);
                group.enemy = pick.id();
                group.count = Math.max(3, Math.min(40, Math.round(budget * shares[g] / pick.cost())));
                group.interval = pick.interval();
            }
            group.delay = g == 0 ? 0f : 3f + rng.nextInt(4);
            group.pathIndex = pathCount > 1 ? rng.nextInt(pathCount) : 0;
            wd.groups.add(group);
        }
        return wd;
    }

    private static LevelDef.WaveDef buildBossWave(int id, int wave, Random rng, int pathCount) {
        LevelDef.WaveDef wd = new LevelDef.WaveDef();
        wd.waveDelay = 14f;

        LevelDef.SpawnGroupDef boss = new LevelDef.SpawnGroupDef();
        // el nivel 50 cierra la campaña con EL CORRUPTOR, un jefe único con
        // guion propio (aura de rey, refuerzos al 66% y 33%, sabotaje en fase 2)
        boss.enemy = id == LAST_LEVEL ? "overlord" : "boss";
        boss.count = 1;
        boss.interval = 4f;
        wd.groups.add(boss);

        LevelDef.SpawnGroupDef escort = new LevelDef.SpawnGroupDef();
        escort.enemy = id >= 11 && rng.nextBoolean() ? "flyer" : "normal";
        escort.count = 6 + id / 5;
        escort.interval = 0.8f;
        escort.delay = 3f;
        escort.pathIndex = pathCount > 1 ? rng.nextInt(pathCount) : 0;
        wd.groups.add(escort);

        return wd;
    }

    private static EnemyPick weightedPick(List<EnemyPick> available, int wave, Random rng) {
        // peso: los baratos dominan al principio; los caros ganan peso con las oleadas
        float[] weights = new float[available.size()];
        float total = 0f;
        for (int i = 0; i < available.size(); i++) {
            EnemyPick p = available.get(i);
            float base = 10f / p.cost();
            float lateBias = 1f + wave * 0.1f * (p.cost() / 10f - 1f);
            weights[i] = Math.max(0.1f, base * lateBias);
            total += weights[i];
        }
        float r = rng.nextFloat() * total;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r <= 0f) return available.get(i);
        }
        return available.get(available.size() - 1);
    }

    /** Heurística simple de ganabilidad; imprime avisos, no bloquea. */
    private static void checkWinnability(LevelDef level) {
        // DPS/oro de referencia: cañón (16 daño * 1.1/s por 100 oro), el único
        // tanque garantizado desde el principio
        float dpsPerGold = 16f * 1.1f / 100f;
        float hpMult = com.pcdc.td.data.Balance.enemyHpMult(level.id);
        float goldMult = com.pcdc.td.data.Balance.goldMult(level.id);
        // poder esperado de las mejoras meta acumuladas en este tramo (50 niveles)
        float metaPower = 1f + 0.04f * level.id;
        float income = 0f;
        float totalHp = 0f;
        float totalTime = 0f;
        for (LevelDef.WaveDef w : level.waves) {
            totalTime += w.waveDelay;
            for (LevelDef.SpawnGroupDef g : w.groups) {
                float hp;
                float gold;
                switch (g.enemy) {
                    case "fast" -> { hp = 24f; gold = 5f; }
                    case "tank" -> { hp = 190f; gold = 14f; }
                    case "flyer" -> { hp = 48f; gold = 8f; }
                    case "boss" -> { hp = 900f; gold = 120f; }
                    case "overlord" -> { hp = 2600f; gold = 300f; }
                    case "champion" -> { hp = 420f; gold = 60f; }
                    case "healer" -> { hp = 70f; gold = 12f; }
                    case "sapper" -> { hp = 55f; gold = 10f; }
                    // el portador cuenta también la tropa que suelta al caer
                    case "carrier" -> { hp = 210f + 48f; gold = 26f; }
                    // unidades insignia de bioma
                    case "charger" -> { hp = 90f; gold = 11f; }
                    case "mirage" -> { hp = 80f; gold = 12f; }
                    case "warden" -> { hp = 160f; gold = 18f; }
                    case "chiller" -> { hp = 150f; gold = 17f; }
                    case "magma" -> { hp = 130f; gold = 16f; }
                    case "bogmist" -> { hp = 120f; gold = 16f; }
                    case "resonator" -> { hp = 170f; gold = 19f; }
                    case "acolyte" -> { hp = 140f; gold = 18f; }
                    case "stormcaller" -> { hp = 150f; gold = 22f; }
                    case "abomination" -> { hp = 320f; gold = 26f; }
                    default -> { hp = 42f; gold = 6f; }
                }
                totalHp += hp * hpMult * g.count;
                totalTime += g.count * g.interval;
                income += g.count * gold * goldMult;
            }
        }
        float affordableDps = (level.startGold + income) * dpsPerGold * metaPower;
        float killableHp = affordableDps * totalTime * 0.6f; // exposición parcial en rango
        if (killableHp < totalHp) {
            System.out.printf("AVISO nivel %d: HP total %.0f > capacidad estimada %.0f (ratio %.2f)%n",
                level.id, totalHp, killableHp, killableHp / totalHp);
        }
    }

    // ---- índice ----

    private static void writeIndex(Path levelsDir, Path assets) throws IOException {
        StringBuilder sb = new StringBuilder("{\n\"levels\": [\n");
        JsonReader reader = new JsonReader();
        boolean first = true;
        for (int id = 1; id <= LAST_LEVEL; id++) {
            Path file = levelsDir.resolve(String.format("level_%03d.json", id));
            if (!Files.exists(file)) continue;
            JsonValue v = reader.parse(Files.readString(file, StandardCharsets.UTF_8));
            String name = v.getString("name", "Nivel " + id);
            String biome = v.getString("biome", "meadow");
            if (!first) sb.append(",\n");
            first = false;
            sb.append("{ \"id\": ").append(id)
                .append(", \"name\": \"").append(name)
                .append("\", \"biome\": \"").append(biome)
                .append("\", \"biomeName\": \"").append(biomeDisplay(biome)).append("\" }");
        }
        sb.append("\n]\n}\n");
        Files.writeString(assets.resolve("levels/levels_index.json"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String biomeDisplay(String biome) {
        for (int i = 0; i < BIOMES.length; i++) {
            if (BIOMES[i].equals(biome)) return BIOME_NAMES[i];
        }
        return biome;
    }
}
