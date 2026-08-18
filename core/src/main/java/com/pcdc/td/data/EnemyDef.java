package com.pcdc.td.data;

import com.badlogic.gdx.graphics.Color;

/** Definición de un arquetipo de enemigo (catálogo enemies.json). */
public class EnemyDef {
    public String id;
    public float hp;
    public float speed;
    public boolean flying = false;
    public float altitude = 1.4f;
    public int goldReward = 5;
    public int livesCost = 1;
    /** Armadura plana: resta (armor - penetración de la torre) a cada impacto. */
    public float armor = 0f;
    /** Comportamiento propio: none | healer | sapper | carrier. */
    public String role = "none";
    /**
     * Unidad INSIGNIA de un bioma: su id. Solo sale en ese bioma y en el
     * último (las tierras corruptas reúnen a todas). Vacío = tropa común.
     */
    public String biome = "";
    /**
     * Truco de la unidad insignia: charge | veil | wardaura | chillaura |
     * meltdown | cloak | guardaura | warp | thunder | frenzy.
     */
    public String special = "none";
    public float specialPower;
    public float specialInterval;
    public String shape = "box"; // box | cone | sphere | cylinder
    public float size = 0.5f;
    public String color = "#b03a3a";

    private transient Color colorCache;

    public Color color() {
        if (colorCache == null) colorCache = Color.valueOf(color.replace("#", ""));
        return colorCache;
    }
}
