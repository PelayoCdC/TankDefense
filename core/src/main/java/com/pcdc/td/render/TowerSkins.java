package com.pcdc.td.render;

import com.badlogic.gdx.graphics.Color;

/** Skins de pintura de las torres (estética pura, se compran con medallas). */
public final class TowerSkins {

    public static class Def {
        public final String id;
        public final int cost;

        Def(String id, int cost) {
            this.id = id;
            this.cost = cost;
        }
    }

    public static final Def[] ALL = {
        new Def("classic", 0),
        new Def("onyx", 10),
        new Def("camo", 12),
        new Def("frost", 14),
        new Def("desert", 16),
        new Def("gilded", 20),
        new Def("crimson", 26),
        // trofeo de campaña: no se compra, se GANA derrotando al Corruptor
        new Def("victor", 0),
        // trofeos de logro: tampoco se compran (ver Achievements.SKIN_REWARDS)
        new Def("hunter", 0),
        new Def("alchemist", 0),
        new Def("survivor", 0),
    };

    /** Las pinturas de trofeo no están a la venta: se GANAN jugando. */
    public static boolean isReward(String id) {
        return "victor".equals(id) || "hunter".equals(id)
            || "alchemist".equals(id) || "survivor".equals(id);
    }

    private static final Color ONYX = Color.valueOf("23262e");
    private static final Color FROST = Color.valueOf("9fd8f0");
    private static final Color GOLD = Color.valueOf("e8b23c");
    private static final Color CAMO = Color.valueOf("4a6a2e");
    private static final Color DESERT = Color.valueOf("d6b378");
    private static final Color CRIMSON = Color.valueOf("8e2230");
    private static final Color VICTOR_BASE = Color.valueOf("3a1048");
    private static final Color VICTOR_HEAD = Color.valueOf("ffd24a");
    private static final Color HUNTER_BASE = Color.valueOf("2e3a24");
    private static final Color HUNTER_HEAD = Color.valueOf("c25a2a");
    private static final Color ALCHEMIST_BASE = Color.valueOf("2a1a3c");
    private static final Color ALCHEMIST_HEAD = Color.valueOf("7ae0a0");
    private static final Color SURVIVOR_BASE = Color.valueOf("30302e");
    private static final Color SURVIVOR_HEAD = Color.valueOf("d8d0b0");

    private TowerSkins() {
    }

    public static Def byId(String id) {
        for (Def d : ALL) {
            if (d.id.equals(id)) return d;
        }
        return ALL[0];
    }

    /**
     * Tinte de disparos (proyectiles, rayos, tracers, conos): acerca el color
     * de la torre al de la pintura equipada, más suave que en el casco para
     * que cada tipo de munición siga distinguiéndose.
     */
    public static void fxTint(String skin, Color c) {
        switch (skin) {
            case "onyx" -> c.lerp(ONYX, 0.35f);
            case "frost" -> c.lerp(FROST, 0.4f);
            case "gilded" -> c.lerp(GOLD, 0.4f);
            case "camo" -> c.lerp(CAMO, 0.4f);
            case "desert" -> c.lerp(DESERT, 0.4f);
            case "crimson" -> c.lerp(CRIMSON, 0.4f);
            case "victor" -> c.lerp(VICTOR_HEAD, 0.45f);
            case "hunter" -> c.lerp(HUNTER_HEAD, 0.4f);
            case "alchemist" -> c.lerp(ALCHEMIST_HEAD, 0.45f);
            case "survivor" -> c.lerp(SURVIVOR_HEAD, 0.35f);
            default -> { }
        }
    }

    /** Transforma in situ los colores de cabezal y base según la skin activa. */
    public static void apply(String skin, Color head, Color base) {
        switch (skin) {
            case "onyx":
                // cuerpo oscuro elegante, el cabezal conserva un matiz del color original
                base.lerp(ONYX, 0.8f);
                head.lerp(ONYX, 0.45f);
                break;
            case "frost":
                base.lerp(FROST, 0.55f);
                head.lerp(FROST, 0.3f);
                break;
            case "gilded":
                base.lerp(GOLD, 0.7f);
                head.lerp(GOLD, 0.35f);
                break;
            case "camo":
                base.lerp(CAMO, 0.8f);
                head.lerp(CAMO, 0.5f);
                break;
            case "desert":
                base.lerp(DESERT, 0.78f);
                head.lerp(DESERT, 0.45f);
                break;
            case "crimson":
                base.lerp(CRIMSON, 0.75f);
                head.lerp(CRIMSON, 0.4f);
                break;
            case "victor":
                // el trofeo del Corruptor: casco púrpura corrupto, torreta dorada
                base.lerp(VICTOR_BASE, 0.85f);
                head.lerp(VICTOR_HEAD, 0.6f);
                break;
            case "hunter": // 5.000 bajas: verde de caza con torreta de óxido
                base.lerp(HUNTER_BASE, 0.8f);
                head.lerp(HUNTER_HEAD, 0.55f);
                break;
            case "alchemist": // 25 fusiones: violeta de laboratorio y verde reactivo
                base.lerp(ALCHEMIST_BASE, 0.82f);
                head.lerp(ALCHEMIST_HEAD, 0.55f);
                break;
            case "survivor": // oleada 40 en Sin fin: gris curtido y hueso
                base.lerp(SURVIVOR_BASE, 0.8f);
                head.lerp(SURVIVOR_HEAD, 0.5f);
                break;
            default:
                break;
        }
    }
}
