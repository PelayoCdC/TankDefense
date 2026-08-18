package com.pcdc.td.meta;

import com.badlogic.gdx.utils.Array;

/** Resultado de una partida, para la pantalla de resultados. */
public class LevelSummary {
    public boolean victory;
    public int stars;
    public int crystals;
    public int medals;
    public int kills;
    public String mvpName;
    public boolean survival;
    /** Cartas cogidas en el sin fin (se listan en resultados). */
    public final com.badlogic.gdx.utils.Array<String> boons = new com.badlogic.gdx.utils.Array<>();
    /** Hitos del sin fin alcanzados por primera vez (oleada, cristales). */
    public final com.badlogic.gdx.utils.Array<int[]> milestones = new com.badlogic.gdx.utils.Array<>();
    /** Se jugó en Nivel+ (Reintentar debe conservar el modo). */
    public boolean plus;
    public int waveReached;
    public int bestWave;
    /** Líneas de logros desbloqueados, ya formateadas. */
    public final Array<String> achievements = new Array<>();
    /** Daño agregado por tipo de torre, ordenado de mayor a menor. */
    public final Array<TowerDamage> towerDamage = new Array<>();
    /** Retos cumplidos en esta partida y los que además eran nuevos. */
    public int challengesDone;
    public int challengesNew;
    /** Primera victoria sobre el Corruptor: resultados de gala. */
    public boolean campaignComplete;
    /** Diagnóstico de derrota: id del enemigo que más se coló y cuántos. */
    public String leakTopEnemy;
    public int leakTopCount;
    public int leakTotal;
    /** Tanques en pie al perder (para distinguir "pocos" de "mal elegidos"). */
    public int towersAtEnd;

    public static class TowerDamage {
        public String name;
        public float damage;
    }
}
