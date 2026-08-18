package com.pcdc.td.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

/** Efecto visual efímero generado por la simulación (tracer de hitscan, etc.). */
public class Fx implements Pool.Poolable {

    public enum Type { TRACER, HIT, DEATH, GOLD, EXPLOSION, WRECK, SCORCH, METEOR, TRACKS }

    public Type type;
    public final Vector3 from = new Vector3();
    public final Vector3 to = new Vector3();
    public final Color color = new Color(Color.WHITE);
    public float ttl, maxTtl;
    public float size;
    /** EXPLOSION: con llamas (burn) en vez de metralla. */
    public boolean fiery;
    /** WRECK: id del enemigo abatido, para volver a montar su modelo. */
    public String modelId;
    /** WRECK: hacia dónde miraba al caer. */
    public float yawDeg;
    /** EXPLOSION: la pantalla ya emitió las ascuas (una sola vez). */
    public boolean emberSpawned;

    public ModelInstance renderInstance;

    /** TRACER: rayo eléctrico — se dibuja quebrado y parpadeante. */
    public boolean jagged;

    public void initTracer(Vector3 from, Vector3 to, Color color) {
        this.type = Type.TRACER;
        this.from.set(from);
        this.to.set(to);
        this.color.set(color);
        this.jagged = false;
        this.maxTtl = this.ttl = 0.1f;
    }

    public void initHit(Vector3 at, Color color) {
        this.type = Type.HIT;
        this.from.set(at);
        this.color.set(color);
        this.maxTtl = this.ttl = 0.15f;
        this.size = 0.3f;
    }

    public void initDeath(Vector3 at, Color color, float size) {
        this.type = Type.DEATH;
        this.from.set(at);
        this.color.set(color);
        this.maxTtl = this.ttl = 0.3f;
        this.size = size * 1.6f;
    }

    /** Onda expansiva del impacto de un proyectil con splash. */
    public void initExplosion(Vector3 at, Color color, float radius, boolean fiery) {
        this.type = Type.EXPLOSION;
        this.from.set(at);
        this.color.set(color);
        this.size = radius;
        this.fiery = fiery;
        this.emberSpawned = false;
        this.maxTtl = this.ttl = 0.45f;
    }

    /** Roca del meteorito cayendo hacia from (el daño lo aplica GameWorld al expirar). */
    public void initMeteor(Vector3 at, float fallTime) {
        this.type = Type.METEOR;
        this.from.set(at);
        this.maxTtl = this.ttl = fallTime;
    }

    /** Huellas de orugas de un tanque en traslado; se desvanecen solas. */
    public void initTracks(Vector3 at, float yawDeg) {
        this.type = Type.TRACKS;
        this.from.set(at);
        this.yawDeg = yawDeg;
        this.maxTtl = this.ttl = 4.5f;
    }

    /** Quemadura en el suelo donde ha caído un obús; se borra poco a poco. */
    public void initScorch(Vector3 at, float radius) {
        this.type = Type.SCORCH;
        this.from.set(at);
        this.size = radius;
        this.maxTtl = this.ttl = 7f;
    }

    /** Chatarra humeante del enemigo abatido: se inclina, se hunde y se apaga. */
    public void initWreck(Vector3 at, String modelId, float size, float yawDeg) {
        this.type = Type.WRECK;
        this.from.set(at);
        this.modelId = modelId;
        this.size = size;
        this.yawDeg = yawDeg;
        this.maxTtl = this.ttl = 2.4f;
    }

    /** Texto flotante "+oro" en la posición de la muerte. */
    public int goldAmount;
    /** GOLD: es un premio de cristales (élite), se pinta en azul cristal. */
    public boolean crystal;

    public void initGold(Vector3 at, int amount) {
        this.type = Type.GOLD;
        this.from.set(at);
        this.goldAmount = amount;
        this.crystal = false;
        this.maxTtl = this.ttl = 0.9f;
    }

    /** Texto flotante "+cristales" al morir un élite dorado. */
    public void initCrystal(Vector3 at, int amount) {
        this.type = Type.GOLD;
        this.from.set(at);
        this.goldAmount = amount;
        this.crystal = true;
        this.maxTtl = this.ttl = 1.2f;
    }

    /** GOLD: es un número de daño ("-N"), no una recompensa. */
    public boolean dmgText;

    /** Número de daño flotante sobre el enemigo golpeado. */
    public void initDamage(Vector3 at, int amount) {
        this.type = Type.GOLD;
        this.from.set(at);
        this.goldAmount = amount;
        this.dmgText = true;
        this.maxTtl = this.ttl = 0.6f;
    }

    @Override
    public void reset() {
        // renderInstance se conserva
        emberSpawned = false;
        fiery = false;
        crystal = false;
        jagged = false;
        dmgText = false;
    }
}
