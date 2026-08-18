package com.pcdc.td.data;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;

/** Definición de una torre (catálogo towers.json). */
public class TowerDef {

    public enum AttackType { PROJECTILE, HITSCAN, AREA, BEAM }

    public String id;
    public String name;
    public int cost;
    /** PROJECTILE/HITSCAN: daño por disparo. AREA/BEAM: daño por segundo. */
    public float damage;
    public float range;
    /** Disparos por segundo (PROJECTILE/HITSCAN). */
    public float fireRate = 1f;
    public float projectileSpeed = 10f;
    public String attackType = "projectile";
    public boolean hitsGround = true;
    public boolean hitsAir = false;
    public float splashRadius = 0f;
    /** Penetración: puntos de armadura enemiga que este tanque ignora. */
    public float penetration = 0f;
    /** AREA: semiángulo del cono en grados. */
    public float coneAngleDeg = 45f;
    /** BEAM: crecimiento del multiplicador de daño por segundo sobre el mismo objetivo. */
    public float rampPerSecond = 0f;
    public float maxRampMult = 1f;
    /**
     * BEAM: el giro NO se pierde al cambiar de objetivo (láser gatling). Sin
     * esto un rayo solo rinde contra un objetivo gordo; con esto rinde contra
     * multitudes, que es lo que aporta la metralleta a la fusión.
     */
    public boolean sustainedRamp = false;
    /** Cadena (Tesla/Prisma): saltos extra desde el objetivo a enemigos cercanos. */
    public int chainCount = 0;
    public float chainRadius = 2f;
    /** Fracción del daño que conserva cada salto de la cadena. */
    public float chainFalloff = 0.6f;
    /** Zona persistente que deja el proyectil al impactar: "", poison, ice o napalm. */
    public String zoneKind = "";
    public float zoneRadius = 1.3f;
    public float zoneDuration = 4f;
    public Array<EffectDef> effects = new Array<>();
    public String baseShape = "box";     // box | cylinder
    public String headShape = "cylinder"; // box | cylinder | sphere | cone
    /** Dos tubos en paralelo en vez del cañón único (artillería). */
    public boolean twinBarrel = false;
    public String color = "#c0762a";
    /** true si solo se obtiene por fusión (no aparece en la barra de construcción). */
    public boolean fusionOnly = false;
    /** Solo se puede tener UNO por partida y hay que desbloquearlo en el árbol. */
    public boolean unique = false;
    /** Bonus que este tanque otorga a los tanques de las 8 celdas contiguas. */
    public float auraDamageAdd = 0f;
    public float auraRangeAdd = 0f;
    /** Arpón: unidades de camino que arrastra HACIA ATRÁS al objetivo. */
    public float pullDistance = 0f;
    /** Segundos que un volador alcanzado queda EN TIERRA (arpón de red). */
    public float groundDuration = 0f;
    /**
     * AREA: en vez de daño continuo, un GOLPE seco cada N segundos a todo lo
     * que tenga en el anillo (Demoledor). El campo damage pasa a ser daño por
     * pulso, no por segundo.
     */
    public float pulseInterval = 0f;
    /**
     * PROJECTILE: el proyectil no persigue, vuela RECTO y atraviesa a todos los
     * enemigos que pilla por el camino (Lanzasierras). Alcance = range.
     */
    public boolean pierce = false;

    private transient AttackType typeCache;
    private transient Color colorCache;

    public AttackType type() {
        if (typeCache == null) typeCache = AttackType.valueOf(attackType.toUpperCase());
        return typeCache;
    }

    public Color color() {
        if (colorCache == null) colorCache = Color.valueOf(color.replace("#", ""));
        return colorCache;
    }
}
