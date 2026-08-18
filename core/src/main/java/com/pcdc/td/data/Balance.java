package com.pcdc.td.data;

/** Fórmulas de escalado centralizadas. Cambiar aquí y regenerar niveles. */
public final class Balance {

    private Balance() {
    }

    public static final float SELL_REFUND = 0.7f;
    /** Oro extra por segundo restante al llamar la oleada antes de tiempo. */
    public static final float EARLY_WAVE_GOLD_PER_SECOND = 2f;

    /** Oro por cerrar una oleada sin perder ninguna vida (crece con la oleada). */
    public static int perfectWaveBonus(int waveNumber) {
        return 8 + 2 * Math.min(20, Math.max(1, waveNumber));
    }

    /** Niveles de torre: nivel 1 de serie, mejorable in situ hasta el 3. */
    public static final int TOWER_MAX_LEVEL = 3;

    /** Coste de subir DESDE el nivel dado (proporción del coste base). */
    public static int upgradeCost(int baseCost, int level) {
        return Math.round(baseCost * (level == 1 ? 0.9f : 1.5f));
    }

    /** ×1 / ×1.4 / ×1.8 de daño según nivel. */
    public static float upgradeDamageMult(int level) {
        return 1f + 0.4f * (level - 1);
    }

    /** +6% de alcance por nivel. */
    public static float upgradeRangeMult(int level) {
        return 1f + 0.06f * (level - 1);
    }

    /**
     * Multiplicador de HP de enemigos según nivel (polinómico, no exponencial).
     * Campaña de 50 niveles: la dificultad visible la lleva la CANTIDAD de
     * enemigos (DifficultyCurve.waveBudget); esta curva acompaña suave hasta
     * ~x5.6 en el nivel 50.
     */
    public static float enemyHpMult(int level) {
        float l = level - 1;
        return 1f + 0.035f * l + 0.0018f * l * l;
    }

    /**
     * Multiplicador del oro por kill según nivel. Muy sublineal respecto al HP:
     * como ahora la CANTIDAD de enemigos también escala, si el oro siguiera al
     * HP la economía tardía se desbordaría (doble escalado).
     */
    public static float goldMult(int level) {
        return 0.9f + 0.1f * enemyHpMult(level);
    }

    /** Estrellas al ganar: 3 sin perder vidas, 2 con >=60%, 1 en otro caso. */
    public static int stars(int livesLeft, int livesStart) {
        if (livesLeft >= livesStart) return 3;
        if (livesLeft >= livesStart * 0.6f) return 2;
        return 1;
    }

    /** Moneda meta ganada al completar un nivel con ese número de estrellas. */
    public static int metaReward(int baseReward, int stars) {
        return Math.round(baseReward * (0.5f + 0.5f * stars / 3f));
    }
}
