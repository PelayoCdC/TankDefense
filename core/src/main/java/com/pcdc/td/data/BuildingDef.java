package com.pcdc.td.data;

import com.badlogic.gdx.utils.Array;

/** Edificio de la base (buildings.json), con niveles comprables. */
public class BuildingDef {

    public static class LevelDefEntry {
        public int cost;
        /** stat: metaIncomeFlat | upgradeDiscount | fireRateMult | damageMult | startGoldFlat */
        public String stat;
        public float add;
    }

    /**
     * DOCTRINA: dos maneras excluyentes de usar el edificio. Con doctrinas, los
     * niveles comprados aplican la que esté elegida en vez del stat fijo, así
     * que el edificio deja de ser "sube un número" y pasa a ser una decisión.
     */
    public static class DoctrineDef {
        public String id;
        public String stat;
        public float add;
    }

    public String id;
    public String name;
    public String desc = "";
    public Array<LevelDefEntry> levels = new Array<>();
    public Array<DoctrineDef> doctrines = new Array<>();
}
