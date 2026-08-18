package com.pcdc.td.data;

import com.badlogic.gdx.utils.Array;

/** Definición completa de un nivel (assets/levels/level_XXX.json). */
public class LevelDef {

    public static class PathDef {
        /** Esquinas del camino [[x,z],...]; las celdas intermedias se derivan. */
        public int[][] waypoints;
    }

    public static class SpawnGroupDef {
        public String enemy;
        public int count;
        public float interval = 1f;
        public float delay = 0f;
        public int pathIndex = 0;
        /** Cuántos salen a la vez por tick de spawn; 0 = automático según el tipo. */
        public int batch = 0;
    }

    public static class WaveDef {
        public Array<SpawnGroupDef> groups = new Array<>();
        /** Segundos de preparación antes de esta oleada. */
        public float waveDelay = 8f;
        /** Evento raro de la oleada: "" | "enraged" | "loot". */
        public String event = "";
    }

    public static class RewardsDef {
        public int metaCurrency = 20;
        public int firstClearBonus = 20;
    }

    public static class TutorialStepDef {
        public String message;
        public String messageEn;
        /** Condición de avance: tap | build | fusion | sell | callwave | waveclear */
        public String until = "tap";
    }

    public int id;
    public String name = "";
    public String biome = "meadow";
    public int gridWidth;
    public int gridHeight;
    public int[][] blockedCells = new int[0][0];
    public Array<PathDef> paths = new Array<>();
    public int startGold = 200;
    public int lives = 20;
    public Array<String> allowedTowers = new Array<>();
    public Array<WaveDef> waves = new Array<>();
    public RewardsDef rewards = new RewardsDef();
    /** Pasos de tutorial guiado; vacío en niveles normales. */
    public Array<TutorialStepDef> tutorial = new Array<>();
}
