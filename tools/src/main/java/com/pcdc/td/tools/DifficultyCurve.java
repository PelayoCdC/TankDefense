package com.pcdc.td.tools;

/**
 * Curva de dificultad de los 50 niveles (5 por bioma, jefe en cada 5º).
 * Cambiar aquí y regenerar. La dificultad crece sobre todo en CANTIDAD de
 * enemigos y tamaño del mapa, que es lo que el jugador ve; el multiplicador
 * de HP (Balance.enemyHpMult) acompaña más suave.
 */
public final class DifficultyCurve {

    public static final int LAST_LEVEL = 50;
    public static final int LEVELS_PER_BIOME = 5;

    private DifficultyCurve() {
    }

    public static int wavesFor(int level) {
        return Math.min(22, 6 + level / 3);
    }

    /**
     * Presupuesto de "compra" de enemigos para la oleada w (1-based) del nivel.
     * Crece fuerte por nivel: las oleadas del final del juego son multitudes.
     */
    // OJO: no bajar este presupuesto para compensar enemigos nuevos. El
    // presupuesto también es la ECONOMÍA del jugador (el oro sale de matar):
    // recortarlo un 6-12% hundió la campaña simulada de 40/50 a 36/50.
    public static float waveBudget(int level, int wave) {
        return 24f * (1f + 0.05f * level) * (1f + 0.22f * (wave - 1)) * biomeBudgetFactor(level);
    }

    /**
     * Compensación por la dureza del rasgo de enemigos del bioma: los rasgos
     * que multiplican HP/fugas efectivas (división, escudos) reciben menos
     * presupuesto de oleada.
     */
    public static float biomeBudgetFactor(int level) {
        int tier = (level - 1) / LEVELS_PER_BIOME; // 0..9
        return switch (tier) {
            case 2, 3 -> 0.9f;       // bosque (regen), nieve (coraza)
            case 4, 5 -> 0.92f;      // volcán (lava), pantano (niebla)
            // cristal se quedó como estaba: al corregir el bug de los hijos de
            // la división (se iban en dirección contraria) el bioma subió de
            // dificultad, pero bajarlo a 0.62 no movió el simulador ni un nivel
            // — la pared del bioma es la división, no el tamaño de la oleada
            case 6 -> 0.7f;          // cristal (división)
            case 7 -> 0.68f;         // ruinas (escudo + portales)
            case 8 -> 0.73f;         // tormenta (voladores directos + evasión)
            case 9 -> 0.75f;         // corruptas (élites + combos)
            default -> 1f;
        };
    }

    public static int startGold(int level) {
        return 240 + 12 * level;
    }

    public static int lives(int level) {
        // fijas: con enemigos cada vez más numerosos, bajar vidas además sería doble castigo
        return 20;
    }

    public static int metaReward(int level) {
        return 25 + 8 * level;
    }

    public static int firstClearBonus(int level) {
        return 30 + level;
    }

    /** Ancho del grid: crece de 10 (nivel 6) a 18 (bioma final). */
    public static int gridWidth(int level) {
        return Math.min(18, 10 + (level - 1) / 6);
    }

    /** Alto del grid: crece de 8 a 14. */
    public static int gridHeight(int level) {
        return Math.min(14, 8 + (level - 1) / 8);
    }
}
