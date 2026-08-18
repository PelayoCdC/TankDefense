package com.pcdc.td.data;

import com.badlogic.gdx.graphics.Color;

/** Definición de un bioma (assets/data/biomes.json): paleta, mecánica y rasgo. */
public class BiomeDef {

    public String id;
    public String name;

    // paleta del tablero
    public String groundA = "#4d7c3a";
    public String groundB = "#558544";
    public String path = "#c2a86b";
    public String rock = "#6b6f75";
    public String rim = "#3a5c2c";
    /** Tinte que se mezcla con el color base de los enemigos (skin del bioma). */
    public String enemyTint = "#ffffff";
    /** Iluminación de la escena. */
    public String ambientLight = "#8c8c94";
    public String sunLight = "#d9d4c7";

    /**
     * Mecánica del mapa: none | sandstorm | bushes | ice | lava | fog |
     * crystals | portals | wind | combo (combina dos según el nivel).
     */
    public String mechanic = "none";
    public float mechanicPower = 0f;    // intensidad (p.ej. multiplicador)
    public float mechanicInterval = 0f; // periodo en segundos
    public float mechanicDuration = 0f; // duración del efecto
    public int mechanicCount = 0;       // nº de celdas afectadas

    /**
     * Habilidad especial del jefe del bioma: none | enrage | sandveil | regrowth |
     * frostaura | deathburst | healpulse | summon | blink | flight | kingaura.
     */
    public String bossAbility = "none";

    /**
     * Rasgo de los enemigos: none | sprint | regen | icearmor | igneous |
     * slowresist | split | shield | evasion | elite.
     */
    public String trait = "none";
    public float traitPower = 0f;
    public float traitInterval = 0f;
    public int traitCount = 0;

    private transient Color groundAC, groundBC, pathC, rockC, rimC, enemyTintC, ambientC, sunC;

    public Color ambientLight() { return ambientC == null ? ambientC = parse(ambientLight) : ambientC; }
    public Color sunLight() { return sunC == null ? sunC = parse(sunLight) : sunC; }

    public Color groundA() { return groundAC == null ? groundAC = parse(groundA) : groundAC; }
    public Color groundB() { return groundBC == null ? groundBC = parse(groundB) : groundBC; }
    public Color path() { return pathC == null ? pathC = parse(path) : pathC; }
    public Color rock() { return rockC == null ? rockC = parse(rock) : rockC; }
    public Color rim() { return rimC == null ? rimC = parse(rim) : rimC; }
    public Color enemyTint() { return enemyTintC == null ? enemyTintC = parse(enemyTint) : enemyTintC; }

    private static Color parse(String hex) {
        return Color.valueOf(hex.replace("#", ""));
    }
}
