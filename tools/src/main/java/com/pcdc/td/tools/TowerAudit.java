package com.pcdc.td.tools;

import com.pcdc.td.data.Catalogs;
import com.pcdc.td.data.EffectDef;
import com.pcdc.td.data.EnemyDef;
import com.pcdc.td.data.FusionDef;
import com.pcdc.td.data.TowerDef;
import com.pcdc.td.world.Enemy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Auditoría de daño del catálogo de tanques: pone a todos en la misma unidad
 * (daño por segundo y por 100 de oro) para detectar los que se salen de la
 * banda. No toca nada: solo imprime.
 *
 * Uso: gradlew :tools:audit
 *
 * Los supuestos están explícitos en las constantes de abajo; lo que importa no
 * es el número absoluto sino la COMPARACIÓN entre tanques con el mismo rasero.
 */
public class TowerAudit {

    /** Enemigos de referencia: sin blindaje, blindado (tanque) y jefe. */
    private static final float ARMOR_LIGHT = 0f, ARMOR_TANK = 6f, ARMOR_BOSS = 8f;

    /** Segundos de fuego sostenido sobre el mismo objetivo que se suponen. */
    private static final float SUSTAIN = 6f;

    /**
     * Objetivos simultáneos estimados por tipo de ataque. La ruta tiene dos
     * celdas de ancho y los enemigos ~0,5 de radio: un radio de salpicadura de
     * 1,2 pilla unos 3.
     */
    private static final float CROWD_CAP = 6f;

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

        List<Row> rows = new ArrayList<>();
        for (TowerDef def : catalogs.towers) rows.add(measure(def, catalogs));

        System.out.println("== BASE (se compran en la barra) ==");
        print(rows.stream().filter(r -> !r.def.fusionOnly && !r.def.unique).toList());
        System.out.println();
        System.out.println("== FUSIONES ==");
        print(rows.stream().filter(r -> r.def.fusionOnly).toList());
        System.out.println();
        System.out.println("== ÚNICOS ==");
        print(rows.stream().filter(r -> r.def.unique).toList());

        System.out.println();
        System.out.println("== ¿COMPENSA CADA FUSIÓN? ==");
        System.out.println("Una fusión no se juzga por oro (el tanque barato siempre gana ahí),");
        System.out.println("sino por CASILLA: dos tanques pasan a ocupar una sola, y las casillas");
        System.out.println("buenas junto al camino son el recurso escaso. Un ratio < 1 significa");
        System.out.println("que dejar los dos padres sueltos da más daño que fusionarlos.");
        System.out.printf("%-18s %-26s %9s %9s %6s %6s%n",
            "fusión", "padres", "dps/celda", "mejor p.", "ratio", "oro");
        int weak = 0;
        for (FusionDef f : catalogs.fusions) {
            Row r = find(rows, f.result);
            Row a = find(rows, f.a), b = find(rows, f.b);
            if (r == null || a == null || b == null) continue;
            float best = Math.max(a.perCell(), b.perCell());
            float ratio = r.perCell() / best;
            float goldRatio = 100f * r.value() / Math.max(a.value(), b.value());
            System.out.printf("%-18s %-26s %9.1f %9.1f %6.2f %5.0f%%%s%n",
                r.def.id, f.a + " + " + f.b, r.perCell(), best, ratio, goldRatio,
                ratio < 1f ? "  <-- NO COMPENSA" : ratio > 2.2f ? "  <-- SE DISPARA" : "");
            if (ratio < 1f || ratio > 2.2f) weak++;
        }
        System.out.println();
        System.out.println("Fusiones fuera de banda (1,0-2,2 por casilla): " + weak);

        System.out.println();
        System.out.println("== BLINDAJE: cuánto daño CONSERVA cada tanque contra ARM 6 y ARM 8 ==");
        System.out.printf("%-18s %6s %6s%n", "tanque", "ARM6", "ARM8");
        List<Row> byArmor = new ArrayList<>(rows);
        byArmor.sort(Comparator.comparingDouble(r -> r.dpsTank / Math.max(0.01f, r.dpsLight)));
        for (Row r : byArmor) {
            if (r.dpsLight <= 0f) continue;
            System.out.printf("%-18s %5.0f%% %5.0f%%%n", r.def.id,
                100f * r.dpsTank / r.dpsLight, 100f * r.dpsBoss / r.dpsLight);
        }
    }

    private static void print(List<Row> rows) {
        System.out.printf("%-18s %5s %7s %8s %6s %8s %8s %9s  %s%n",
            "tanque", "oro", "dps", "dps ARM6", "multi", "dps mult",
            "daño/paso", "paso/100", "notas");
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingDouble(r -> -r.perPass() / r.def.cost));
        for (Row r : sorted) {
            System.out.printf("%-18s %5d %7.1f %8.1f %6.1f %8.1f %8.0f %9.1f  %s%n",
                r.def.id, r.def.cost, r.dpsLight, r.dpsTank, r.targets,
                r.dpsCrowd, r.perPass(), 100f * r.perPass() / r.def.cost, r.notes);
        }
    }

    private static Row find(List<Row> rows, String id) {
        for (Row r : rows) {
            if (r.def.id.equals(id)) return r;
        }
        return null;
    }

    private static class Row {
        TowerDef def;
        float dpsLight, dpsTank, dpsBoss, targets, dpsCrowd;
        String notes = "";

        /**
         * Daño total que le mete a UN grupo que pasa por delante. El dps por sí
         * solo miente con los tanques de corto alcance: el enemigo camina a ~1
         * u/s, así que uno de alcance 1,5 lo tiene a tiro 3 s y uno de alcance
         * 3 lo tiene 6 s. Ignorarlo hacía parecer rota a la Trituradora, y al
         * subirle el precio la campaña simulada perdía hasta un tutorial.
         */
        float perPass() {
            float exposure = Math.min(2f * def.range, 10f);
            return dpsCrowd * exposure;
        }

        /** Daño puesto en UNA casilla: mitad objetivo único, mitad multitud. */
        float perCell() {
            return dpsLight * 0.5f + dpsCrowd * 0.5f;
        }

        /** Lo mismo, por cada 100 de oro. */
        float value() {
            return 100f * perCell() / def.cost;
        }
    }

    private static Row measure(TowerDef def, Catalogs catalogs) {
        Row r = new Row();
        r.def = def;
        r.dpsLight = dpsAgainst(def, ARMOR_LIGHT);
        r.dpsTank = dpsAgainst(def, ARMOR_TANK);
        r.dpsBoss = dpsAgainst(def, ARMOR_BOSS);
        r.targets = targets(def);
        r.dpsCrowd = r.dpsLight * r.targets;

        StringBuilder n = new StringBuilder();
        n.append(def.hitsAir ? "aire " : "tierra ");
        if (def.splashRadius > 0f) n.append("área ");
        if (def.chainCount > 0) n.append("cadena ");
        if (!def.zoneKind.isEmpty()) n.append("zona ");
        if (def.pulseInterval > 0f) n.append("pulso ");
        if (def.pierce) n.append("perfora ");
        if (def.pullDistance > 0f) n.append("tira ");
        if (def.groundDuration > 0f) n.append("derriba ");
        if (def.sustainedRamp) n.append("rampa-fija ");
        for (int i = 0; i < def.effects.size; i++) {
            String t = def.effects.get(i).type;
            if ("slow".equals(t)) n.append("frena ");
        }
        r.notes = n.toString().trim();
        return r;
    }

    /**
     * Daño por segundo sostenido contra un enemigo con esa armadura. El daño
     * por tiempo (veneno/quemadura) NO pasa por la armadura, por diseño.
     */
    private static float dpsAgainst(TowerDef def, float armor) {
        float direct;
        switch (def.type()) {
            case PROJECTILE:
            case HITSCAN: {
                float perHit = afterArmor(def.damage, armor, def.penetration);
                direct = perHit * def.fireRate;
                break;
            }
            case AREA: {
                if (def.pulseInterval > 0f) {
                    // DEMOLEDOR: golpe seco cada intervalo, con armadura de impacto
                    direct = afterArmor(def.damage, armor, def.penetration) / def.pulseInterval;
                } else {
                    // el daño es por segundo y la armadura se descuenta como dps
                    direct = Math.max(def.damage * 0.2f,
                        def.damage - Math.max(0f, armor - def.penetration));
                }
                break;
            }
            case BEAM: {
                float avg = averageRamp(def);
                float base = def.damage * avg;
                direct = Math.max(base * 0.2f, base - Math.max(0f, armor - def.penetration));
                break;
            }
            default:
                direct = 0f;
        }
        return direct + dotDps(def);
    }

    private static float afterArmor(float damage, float armor, float pen) {
        return Math.max(damage * 0.2f, damage - Math.max(0f, armor - pen));
    }

    /** Multiplicador medio de la rampa del rayo durante SUSTAIN segundos. */
    private static float averageRamp(TowerDef def) {
        if (def.rampPerSecond <= 0f) return 1f;
        float t = 0f, sum = 0f;
        float step = 0.1f;
        // el gatling conserva revoluciones al cambiar de objetivo: se supone
        // que ya llega caliente a la mitad del tramo
        float start = def.sustainedRamp ? (1f + def.maxRampMult) * 0.5f : 1f;
        float mult = start;
        while (t < SUSTAIN) {
            mult = Math.min(def.maxRampMult, mult + def.rampPerSecond * step);
            sum += mult * step;
            t += step;
        }
        return sum / SUSTAIN;
    }

    /**
     * Daño por segundo del veneno y la quemadura una vez saturados. El veneno
     * acumula con rendimientos decrecientes (ver Enemy.MAX_POISON_STACKS).
     */
    private static float dotDps(TowerDef def) {
        float poison = 0f, burn = 0f;
        for (int i = 0; i < def.effects.size; i++) {
            EffectDef e = def.effects.get(i);
            if ("poison".equals(e.type)) poison = Math.max(poison, e.dps * poisonStackMult());
            if ("burn".equals(e.type)) burn = Math.max(burn, e.dps);
        }
        return poison + burn;
    }

    private static float poisonStackMult() {
        // mismos pesos que Enemy.POISON_STACK_WEIGHT
        float[] w = {1f, 0.6f, 0.4f, 0.25f};
        float sum = 0f;
        for (int i = 0; i < Math.min(w.length, Enemy.MAX_POISON_STACKS); i++) sum += w[i];
        return sum;
    }

    /** Objetivos simultáneos estimados. */
    private static float targets(TowerDef def) {
        float t = 1f;
        if (def.splashRadius > 0f) t = 1f + 1.6f * def.splashRadius;
        if (def.chainCount > 0) {
            float chain = 1f, factor = 1f;
            for (int c = 0; c < def.chainCount; c++) {
                factor *= def.chainFalloff;
                chain += factor;
            }
            t = Math.max(t, chain);
        }
        if (def.pierce) t = Math.max(t, 3f); // corta la fila entera de la calzada
        if (def.type() == TowerDef.AttackType.AREA) {
            // cono estrecho: los que van en fila; anillo de 360º: los de
            // alrededor. Si además encadena, los saltos SUMAN (el rotor de arco
            // golpea el anillo y salta fuera), no se solapan
            float ring = def.coneAngleDeg >= 180f ? 3f : 2f;
            t = ring + (def.chainCount > 0 ? t - 1f : 0f);
        }
        if (!def.zoneKind.isEmpty()) t += 0.8f; // la mancha sigue castigando al grupo
        return Math.min(CROWD_CAP, t);
    }

    private static String read(Path assets, String rel) throws IOException {
        return Files.readString(assets.resolve(rel), StandardCharsets.UTF_8);
    }
}
