package com.pcdc.td.data;

import com.badlogic.gdx.utils.Array;

/** Nodo del árbol de mejoras permanente (upgrades.json). */
public class UpgradeNodeDef {

    public static class EffectRef {
        /**
         * stat: damageMult | fireRateMult | rangeMult | startGoldFlat |
         * goldPerKillMult | livesFlat | unlockFusion
         */
        public String stat;
        public float add = 0f;
        /** Para unlockFusion: id de la torre resultado de la receta. */
        public String value;
    }

    public String id;
    public String name;
    public String desc = "";
    public int cost;
    /** Rama (para el color): attack | economy | utility | fusion */
    public String branch = "attack";
    /** Posición [col, fila] en el lienzo del árbol. */
    public int[] pos = {0, 0};
    public Array<String> requires = new Array<>();
    public EffectRef effect = new EffectRef();
}
