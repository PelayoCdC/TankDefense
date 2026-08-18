package com.pcdc.td.world;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.pcdc.td.data.TowerDef;

/** Proyectil con persecución simple del objetivo. Pooled. */
public class Projectile implements Pool.Poolable {

    private static final float HIT_DISTANCE = 0.25f;

    public TowerDef def;
    public final Vector3 position = new Vector3();
    final Vector3 aim = new Vector3();
    private final Vector3 tmp = new Vector3();

    public Enemy target;
    /** Torre que lo disparó (atribución de bajas). */
    public Tower source;
    public float damage;
    public boolean alive;
    private float life;

    /** Dirección de vuelo normalizada (para estelas y balas alargadas). */
    public final Vector3 dir = new Vector3(0f, 0f, 1f);
    /** Distancia inicial al objetivo y progreso 0..1 (para el arco del mortero). */
    public float initialDist = 1f;
    public float flightProgress;

    /** Perforante: vuela recto y va anotando a quién ya ha cortado. */
    public boolean piercing;
    private float traveled, maxTravel;
    public final com.badlogic.gdx.utils.Array<Enemy> pierced =
        new com.badlogic.gdx.utils.Array<>(false, 8);

    public ModelInstance renderInstance;
    public String renderKey;

    public void init(TowerDef def, Vector3 from, Enemy target, float damage) {
        this.def = def;
        this.position.set(from);
        this.target = target;
        this.damage = damage;
        this.alive = true;
        this.life = 4f;
        this.aim.set(target.position.x, target.centerY(), target.position.z);
        this.initialDist = Math.max(0.001f, position.dst(aim));
        this.flightProgress = 0f;
        this.dir.set(aim).sub(position).nor();
        this.piercing = false;
        this.traveled = 0f;
        this.maxTravel = 0f;
        this.pierced.clear();
    }

    /**
     * Disco de sierra: sale en la dirección del objetivo pero NO lo persigue;
     * sigue recto hasta agotar su alcance, cortando a todo el que cruce.
     */
    public void initPiercing(TowerDef def, Vector3 from, Enemy target, float damage, float reach) {
        init(def, from, target, damage);
        this.piercing = true;
        this.target = null; // ya no persigue a nadie
        this.maxTravel = reach;
        this.initialDist = Math.max(0.001f, reach);
    }

    /** @return true si ha impactado este paso. */
    public boolean update(float dt) {
        if (!alive) return false;
        if (piercing) {
            // vuelo recto: el daño lo aplica GameWorld a quien atraviesa
            float step = def.projectileSpeed * dt;
            position.mulAdd(dir, step);
            traveled += step;
            flightProgress = Math.min(1f, traveled / initialDist);
            if (traveled >= maxTravel) alive = false;
            return false;
        }
        if (target != null && target.alive) {
            aim.set(target.position.x, target.centerY(), target.position.z);
        }
        tmp.set(aim).sub(position);
        float dist = tmp.len();
        flightProgress = Math.min(1f, Math.max(0f, 1f - dist / initialDist));
        float step = def.projectileSpeed * dt;
        if (dist <= step || dist < HIT_DISTANCE) {
            position.set(aim);
            alive = false;
            return true;
        }
        tmp.scl(1f / dist);
        dir.set(tmp);
        position.mulAdd(tmp, step);
        life -= dt;
        if (life <= 0f) alive = false;
        return false;
    }

    @Override
    public void reset() {
        def = null;
        target = null;
        source = null;
        piercing = false;
        pierced.clear();
        // renderInstance/renderKey se conservan (ver Enemy.reset)
    }
}
