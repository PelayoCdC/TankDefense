package com.pcdc.td.world;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.pcdc.td.data.TowerDef;

/**
 * Zona persistente en el suelo dejada por un proyectil (charca tóxica, placa de
 * hielo, napalm). Aplica los efectos de la torre a los enemigos de tierra que
 * la pisan, en ticks espaciados para que el veneno acumule stacks a ritmo justo.
 */
public class GroundZone implements Pool.Poolable {

    /** Cada cuánto se aplican los efectos a quienes están dentro. */
    public static final float TICK = 0.5f;

    public final Vector3 position = new Vector3();
    public float radius;
    public float ttl, maxTtl;
    /** poison | ice | napalm (solo para el render). */
    public String kind = "";
    /** Def de la torre que la creó: aporta efectos y atribución. */
    public TowerDef def;
    public Tower source;
    private float tickTimer;

    public void init(Vector3 at, TowerDef def, Tower source) {
        this.position.set(at.x, 0f, at.z);
        this.def = def;
        this.source = source;
        this.kind = def.zoneKind;
        this.radius = def.zoneRadius;
        this.maxTtl = this.ttl = def.zoneDuration;
        this.tickTimer = 0f; // el primer tick es inmediato
    }

    /** @return true si en este paso toca aplicar efectos. */
    public boolean tick(float dt) {
        ttl -= dt;
        tickTimer -= dt;
        if (tickTimer <= 0f) {
            tickTimer += TICK;
            return true;
        }
        return false;
    }

    @Override
    public void reset() {
        def = null;
        source = null;
    }
}
