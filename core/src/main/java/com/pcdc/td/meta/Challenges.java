package com.pcdc.td.meta;

import com.pcdc.td.world.GameWorld;

/**
 * Retos opcionales de cada nivel: tres condiciones que se evalúan al ganar y
 * que dan cristales la primera vez que se cumplen. No cambian la partida, solo
 * dan motivos para volver a niveles ya completados.
 */
public final class Challenges {

    public static final int NO_SELL = 1;
    public static final int NO_ABILITY = 2;
    public static final int FEW_TANKS = 4;

    /** Ids en el orden en que se muestran (para las claves de idioma). */
    public static final String[] IDS = {"nosell", "noability", "fewtanks"};
    public static final int[] FLAGS = {NO_SELL, NO_ABILITY, FEW_TANKS};

    /** Cristales por cada reto conseguido por primera vez. */
    public static final int REWARD = 15;

    private Challenges() {
    }

    /**
     * Tope de tanques del reto FEW_TANKS: escala con el nivel, porque con los
     * mapas y oleadas del final de campaña un tope fijo de 6 era imposible.
     */
    public static int maxTanks(int levelId) {
        return 6 + levelId / 5;
    }

    /** Máscara de retos cumplidos en esta partida. */
    public static int evaluate(GameWorld world) {
        int done = 0;
        if (world.towersSold == 0) done |= NO_SELL;
        if (world.abilitiesUsed == 0) done |= NO_ABILITY;
        if (world.peakTowers <= maxTanks(world.level.id)) done |= FEW_TANKS;
        return done;
    }

    public static int count(int mask) {
        return Integer.bitCount(mask & (NO_SELL | NO_ABILITY | FEW_TANKS));
    }
}
