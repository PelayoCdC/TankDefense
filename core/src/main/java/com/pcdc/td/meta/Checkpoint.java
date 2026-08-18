package com.pcdc.td.meta;

import com.badlogic.gdx.utils.Array;

/** Estado de una partida a mitad de nivel, guardado al empezar cada oleada. */
public class Checkpoint {

    public static class TowerState {
        public int x, z, targeting, invested;
        /** Nivel de mejora; 0 en checkpoints antiguos (se normaliza a 1 al restaurar). */
        public int level;
        public String def;
    }

    public int levelId;
    public boolean plus;
    /**
     * Partida de SIN FIN. Guardarla exige llevarse también las cartas y sus
     * multiplicadores: sin eso, reanudar devolvía una partida de la oleada 30
     * con la dificultad de la 30 y ninguna de las mejoras acumuladas.
     */
    public boolean survival;
    public Array<String> boons = new Array<>();
    public float runDamageMult = 1f, runRangeMult = 1f, runGoldMult = 1f;
    public float runEffectMult = 1f, runPen = 0f, runUpgradeDiscount = 0f;
    public float runEnemyArmorBonus = 0f, runEnemySpeedMult = 1f;
    /** Oleada a partir de la cual toca ofrecer cartas otra vez. */
    public int nextBoonWave = 10;
    /** Oleada que estaba arrancando (se relanza entera al reanudar). */
    public int waveIndex;
    public int gold;
    public int lives;
    public Array<TowerState> towers = new Array<>();
}
