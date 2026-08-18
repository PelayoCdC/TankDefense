package com.pcdc.td.world;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import com.pcdc.td.data.BiomeDef;

import java.util.Random;

/**
 * Estado y lógica de las mecánicas de mapa de los biomas. Todo el efecto sobre
 * torres/enemigos/proyectiles pasa por los multiplicadores y flags de esta clase.
 */
public class BiomeMechanics {

    private final GameWorld world;
    private final BiomeDef biome;
    private final Random rng;
    private final Array<String> active = new Array<>();

    // celdas especiales (índice z*width+x)
    public final IntSet bushCells = new IntSet();
    public final IntSet iceCells = new IntSet();
    public final IntSet lavaCells = new IntSet();
    public final IntSet fogCells = new IntSet();
    public final IntSet crystalCells = new IntSet();

    // portales (ruinas): salto entre dos puntos del camino
    public boolean hasPortals;
    public final Vector3 portalAPos = new Vector3(), portalBPos = new Vector3();
    /** Rumbo del CAMINO en cada portal: el aro se planta cruzando la calzada. */
    public float portalAYaw, portalBYaw;
    private float portalAProgress, portalBProgress;
    private int portalBWpIndex;

    // tormenta de arena / viento
    private float cycleTimer;
    public boolean stormActive;
    public boolean gustActive;
    private final Vector3 windDir = new Vector3(1f, 0f, 0.3f).nor();

    /** Aviso efímero para el HUD ("¡Tormenta de arena!"). */
    public String notice;
    public float noticeTimer;

    public BiomeMechanics(GameWorld world, BiomeDef biome, int levelId) {
        this.world = world;
        this.biome = biome;
        this.rng = new Random(7000L + levelId);

        if ("combo".equals(biome.mechanic)) {
            // tierras corruptas: combina dos mecánicas de biomas anteriores
            switch (levelId % 4) {
                case 0 -> { active.add("sandstorm"); active.add("fog"); }
                case 1 -> { active.add("ice"); active.add("portals"); }
                case 2 -> { active.add("lava"); active.add("bushes"); }
                default -> { active.add("wind"); active.add("crystals"); }
            }
        } else if (!"none".equals(biome.mechanic)) {
            active.add(biome.mechanic);
        }

        GridMap map = world.map;
        if (active.contains("bushes", false)) pickPathCells(bushCells, count(5), map);
        if (active.contains("ice", false)) pickGroundCells(iceCells, count(8), map);
        if (active.contains("fog", false)) pickFogZone(map);
        if (active.contains("crystals", false)) pickGroundCells(crystalCells, count(4), map);
        if (active.contains("portals", false)) setupPortals(map);
        if (active.contains("lava", false)) rerollLava(map);
    }

    private int count(int fallback) {
        return biome.mechanicCount > 0 ? biome.mechanicCount : fallback;
    }

    public boolean isActive(String mechanic) {
        return active.contains(mechanic, false);
    }

    // ---- setup ----

    private void pickGroundCells(IntSet out, int n, GridMap map) {
        for (int tries = 0; tries < 200 && out.size < n; tries++) {
            int x = rng.nextInt(map.width);
            int z = rng.nextInt(map.height);
            if (map.get(x, z) == CellType.GROUND) out.add(z * map.width + x);
        }
    }

    private void pickPathCells(IntSet out, int n, GridMap map) {
        for (int tries = 0; tries < 200 && out.size < n; tries++) {
            int x = rng.nextInt(map.width);
            int z = rng.nextInt(map.height);
            if (map.get(x, z) == CellType.PATH) out.add(z * map.width + x);
        }
    }

    private void pickFogZone(GridMap map) {
        // niebla en una mitad del mapa (vertical u horizontal)
        boolean vertical = rng.nextBoolean();
        boolean firstHalf = rng.nextBoolean();
        for (int z = 0; z < map.height; z++) {
            for (int x = 0; x < map.width; x++) {
                boolean in = vertical
                    ? (firstHalf ? x < map.width / 2 : x >= map.width / 2)
                    : (firstHalf ? z < map.height / 2 : z >= map.height / 2);
                if (in) fogCells.add(z * map.width + x);
            }
        }
    }

    private void setupPortals(GridMap map) {
        // salto del 25% al 55% del primer camino
        Array<Vector3> path = map.worldPaths.first();
        float total = 0f;
        for (int i = 1; i < path.size; i++) total += path.get(i).dst(path.get(i - 1));
        portalAProgress = total * 0.25f;
        portalBProgress = total * 0.45f;
        portalBWpIndex = locateAt(path, portalBProgress, portalBPos);
        portalBYaw = yawAt(path, portalBWpIndex);
        portalAYaw = yawAt(path, locateAt(path, portalAProgress, portalAPos));
        hasPortals = true;
    }

    /** Rumbo del tramo que termina en ese waypoint (por dónde vienen). */
    private static float yawAt(Array<Vector3> path, int wpIndex) {
        int i = Math.max(1, Math.min(wpIndex, path.size - 1));
        return com.badlogic.gdx.math.MathUtils.atan2Deg360(
            path.get(i).x - path.get(i - 1).x, path.get(i).z - path.get(i - 1).z);
    }

    /** Posición sobre el camino a una distancia dada; devuelve el índice del siguiente waypoint. */
    private static int locateAt(Array<Vector3> path, float distance, Vector3 out) {
        float acc = 0f;
        for (int i = 1; i < path.size; i++) {
            float seg = path.get(i).dst(path.get(i - 1));
            if (acc + seg >= distance) {
                float t = (distance - acc) / seg;
                out.set(path.get(i - 1)).lerp(path.get(i), t);
                return i;
            }
            acc += seg;
        }
        out.set(path.peek());
        return path.size - 1;
    }

    /** Cambia cuando la lava se recoloca, para que el render se refresque. */
    public int lavaVersion;

    void rerollLava(GridMap map) {
        lavaVersion++;
        lavaCells.clear();
        for (int tries = 0; tries < 200 && lavaCells.size < count(6); tries++) {
            int x = rng.nextInt(map.width);
            int z = rng.nextInt(map.height);
            if (map.get(x, z) != CellType.GROUND) continue;
            if (world.towerAt(x, z) != null) continue; // nunca bajo torres
            lavaCells.add(z * map.width + x);
        }
    }

    // ---- por frame ----

    public void step(float dt) {
        if (noticeTimer > 0f) noticeTimer -= dt;

        if (isActive("sandstorm")) {
            cycleTimer += dt;
            boolean wasActive = stormActive;
            float period = biome.mechanicInterval > 0 ? biome.mechanicInterval : 22f;
            float duration = biome.mechanicDuration > 0 ? biome.mechanicDuration : 6f;
            stormActive = (cycleTimer % period) > (period - duration);
            if (stormActive && !wasActive) showNotice("¡Tormenta de arena! Rango reducido");
        }
        if (isActive("wind")) {
            cycleTimer += dt;
            boolean was = gustActive;
            float period = biome.mechanicInterval > 0 ? biome.mechanicInterval : 12f;
            float duration = biome.mechanicDuration > 0 ? biome.mechanicDuration : 5f;
            gustActive = (cycleTimer % period) > (period - duration);
            if (gustActive && !was) showNotice("¡Racha de viento!");
            if (gustActive) {
                float power = biome.mechanicPower > 0 ? biome.mechanicPower : 1.6f;
                for (int i = 0; i < world.projectiles.size; i++) {
                    Projectile p = world.projectiles.get(i);
                    if (p.def.projectileSpeed < 10f) {
                        p.position.mulAdd(windDir, power * dt);
                    }
                }
            }
        }
        if (isActive("bushes")) {
            GridMap map = world.map;
            for (int i = 0; i < world.enemies.size; i++) {
                Enemy e = world.enemies.get(i);
                int cx = (int) Math.floor(e.position.x);
                int cz = (int) Math.floor(e.position.z);
                e.hidden = !e.def.flying && map.inBounds(cx, cz) && bushCells.contains(cz * map.width + cx);
            }
        }
        if (hasPortals) {
            for (int i = 0; i < world.enemies.size; i++) {
                Enemy e = world.enemies.get(i);
                if (!e.teleported && !e.def.flying && e.pathProgress >= portalAProgress && e.pathProgress < portalBProgress) {
                    world.portalFlash(e.position, false); // se lo traga la entrada
                    e.teleportTo(portalBPos, portalBWpIndex, portalBProgress);
                    world.portalFlash(e.position, true);  // y escupe en la salida
                    showNotice("¡Un enemigo ha usado el portal!");
                }
            }
        }
    }

    void onWaveStart() {
        if (isActive("lava")) {
            rerollLava(world.map);
            showNotice("¡La lava cambia de sitio!");
        }
    }

    private void showNotice(String text) {
        notice = text;
        noticeTimer = 2.5f;
    }

    // ---- multiplicadores consultados por las torres ----

    public float rangeMult(Tower t) {
        float m = 1f;
        if (stormActive) m *= biome.mechanicPower > 0 ? biome.mechanicPower : 0.7f;
        if (isActive("fog") && fogCells.contains(t.cellZ * world.map.width + t.cellX)) {
            m *= biome.mechanicPower > 0 ? biome.mechanicPower : 0.7f;
        }
        return m;
    }

    public float fireRateMult(Tower t) {
        if (isActive("ice") && iceCells.contains(t.cellZ * world.map.width + t.cellX)) {
            return biome.mechanicPower > 0 ? biome.mechanicPower : 0.8f;
        }
        return 1f;
    }

    public float damageMult(Tower t) {
        if (!isActive("crystals")) return 1f;
        float bonus = biome.mechanicPower > 0 ? biome.mechanicPower : 0.15f;
        int w = world.map.width;
        boolean adjacent = crystalCells.contains(t.cellZ * w + (t.cellX + 1))
            || crystalCells.contains(t.cellZ * w + (t.cellX - 1))
            || crystalCells.contains((t.cellZ + 1) * w + t.cellX)
            || crystalCells.contains((t.cellZ - 1) * w + t.cellX);
        return adjacent ? 1f + bonus : 1f;
    }

    /** La lava bloquea la construcción en sus celdas. */
    public boolean blocksBuild(int x, int z) {
        return lavaCells.contains(z * world.map.width + x);
    }
}
