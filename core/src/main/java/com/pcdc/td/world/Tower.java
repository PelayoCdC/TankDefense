package com.pcdc.td.world;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.pcdc.td.data.TowerDef;

/** Torre colocada en una celda. El comportamiento depende de def.attackType. */
public class Tower {

    /** Celda actual (mutable: el nodo Logística permite reubicar). */
    public int cellX, cellZ;
    public final Vector3 position = new Vector3();

    public TowerDef def;
    /** Oro total invertido (construcción + fusiones + mejoras); base del reembolso al vender. */
    public int invested;
    /** Nivel de mejora in situ (1..Balance.TOWER_MAX_LEVEL). */
    public int level = 1;

    /**
     * Estrategia de targeting: 0 primero, 1 último, 2 más fuerte, 3 más
     * cercano, 4 aire (voladores primero). Los tanques que no tocan el aire
     * no ofrecen el modo 4: ver GameplayScreen.onCycleTargeting.
     */
    public int targeting;
    public static final int TARGET_MODES = 5;
    public int kills;
    public float damageDealt;
    /** Retroceso visual del cabezal (1 al disparar, decae). */
    public float recoil;
    /** Segundos desde su colocación (animación de brotar del suelo). */
    public float age;
    /** Segundos que le quedan saboteado (no dispara). */
    public float disabledTimer;

    public float headYawDeg;
    public Enemy target;
    /** Enemigos golpeados por la cadena/refracción este disparo o frame (render del Prisma). */
    public final Array<Enemy> chainTargets = new Array<>(false, 4);
    /** Segundos golpeando al mismo objetivo (rampa del láser). */
    public float beamRamp;
    /** true mientras un ataque area/beam está activo (para el render). */
    public boolean attacking;

    private float cooldown;
    /** Cada cuánto se re-evalúa el objetivo aunque el actual siga siendo válido. */
    private static final float RETARGET_EVERY = 0.4f;
    private float retargetTimer;
    private Enemy lastBeamTarget;
    private final Vector3 muzzlePos = new Vector3();

    public ModelInstance renderInstance;
    public String renderKey;
    /** Orientación con la que se dibujó el último frame (polvo de las orugas). */
    public float renderYaw;

    public Tower(int cellX, int cellZ, TowerDef def, int invested) {
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.def = def;
        this.invested = invested;
        GridMap.cellCenter(cellX, cellZ, position);
    }

    public boolean canTarget(Enemy e) {
        return e.isFlying() ? def.hitsAir : def.hitsGround;
    }

    // DPS de los últimos 10 s: cubos de 1 s alimentados con el delta de damageDealt
    private final float[] dpsBuckets = new float[10];
    private int dpsIndex;
    private float dpsBucketTimer, dpsSnapshot;

    /** Daño por segundo medio de los últimos 10 s. */
    public float dps() {
        float sum = 0f;
        for (float b : dpsBuckets) sum += b;
        float window = Math.min(age, 10f);
        return window > 0.5f ? sum / window : 0f;
    }

    /** Punto de salida de disparos/rayos. */
    public Vector3 muzzle() {
        return muzzlePos.set(position.x, 0.62f, position.z);
    }

    /** Segundos desde que lo suelta el helicóptero hasta que toca suelo. */
    public static final float DEPLOY_TIME = 1.3f;
    /**
     * Llega lanzado desde el aire (colocación nueva). Las fusiones y los
     * tanques restaurados de un guardado ya están en el tablero: no caen.
     */
    public boolean airdrop = true;
    /** Lo consume la capa de render para sonar y levantar polvo una sola vez. */
    public boolean landingDone;
    /** Cuenta atrás para la próxima mancha del cono que pinta el suelo. */
    float zoneTimer;
    /** Cuenta atrás del golpe en área del Demoledor. */
    float pulseTimer;

    /** Todavía cayendo en paracaídas: se ve, pero aún no dispara. */
    public boolean deploying() {
        return airdrop && age < DEPLOY_TIME;
    }

    /** 0 recién soltado, 1 en el suelo. */
    public float deployProgress() {
        return airdrop ? Math.min(1f, age / DEPLOY_TIME) : 1f;
    }

    public void update(float dt, GameWorld world) {
        attacking = false;
        cooldown -= dt;
        recoil = Math.max(0f, recoil - dt * 6f);
        age += dt;

        // aún colgando del paracaídas: no apunta ni dispara
        if (deploying()) {
            target = null;
            beamRamp = 0f;
            return;
        }

        // saboteado por un jefe: sigue ahí pero no dispara
        if (disabledTimer > 0f) {
            disabledTimer -= dt;
            target = null;
            beamRamp = 0f;
            return;
        }

        // de viaje a su nueva casilla: conduce, no dispara
        if (updateDrive(dt, world)) return;

        // ventana de DPS: acumula el daño del segundo actual y rota cada 1 s
        dpsBuckets[dpsIndex] += damageDealt - dpsSnapshot;
        dpsSnapshot = damageDealt;
        dpsBucketTimer += dt;
        if (dpsBucketTimer >= 1f) {
            dpsBucketTimer -= 1f;
            dpsIndex = (dpsIndex + 1) % dpsBuckets.length;
            dpsBuckets[dpsIndex] = 0f;
        }

        float range = world.rangeFor(this);
        float range2 = range * range;
        retargetTimer -= dt;
        if (target == null || !target.alive || target.isHidden() || !canTarget(target)
            || target.position.dst2(position.x, target.position.y, position.z) > range2) {
            target = world.findTarget(this);
            retargetTimer = RETARGET_EVERY;
        } else if (retargetTimer <= 0f) {
            // el pool de objetivos cambia: sin esto "más cercano/fuerte/primero" se
            // quedan pegados al objetivo antiguo mientras siga vivo y en alcance
            Enemy best = world.findTarget(this);
            if (best != null) target = best;
            retargetTimer = RETARGET_EVERY;
        }
        if (target == null) {
            // el gatling no se para en seco: sigue girando y baja despacio
            beamRamp = def.sustainedRamp ? Math.max(0f, beamRamp - dt * 1.5f) : 0f;
            lastBeamTarget = null;
            chainTargets.clear();
            trackNearest(world, dt); // sin nadie a tiro, sigue vigilando
            return;
        }

        float dx = target.position.x - position.x;
        float dz = target.position.z - position.z;
        headYawDeg = MathUtils.atan2Deg360(dx, dz);

        switch (def.type()) {
            case PROJECTILE:
                if (cooldown <= 0f) {
                    world.spawnProjectile(this, target);
                    cooldown = 1f / world.fireRateFor(this);
                    recoil = 1f;
                }
                break;
            case HITSCAN:
                if (cooldown <= 0f) {
                    float dmg = world.damageFor(this);
                    target.lastHitTower = this;
                    damageDealt += dmg;
                    world.damageText(target, target.hit(dmg, world.penFor(this)));
                    applyEffects(target, world);
                    world.addTracer(muzzle(), target, def.color(),
                        def.chainCount > 0 ? "zap" : "shoot_sniper");
                    if (def.chainCount > 0) world.chainDamage(this, target, dmg);
                    // ARPÓN: engancha y arrastra al objetivo camino atrás
                    if (def.pullDistance > 0f) world.pullBack(target, def.pullDistance);
                    // ARPÓN DE RED: al volador lo baja a tierra unos segundos
                    if (def.groundDuration > 0f) target.groundFor(def.groundDuration);
                    cooldown = 1f / world.fireRateFor(this);
                    recoil = 1f;
                }
                break;
            case BEAM:
                if (target != lastBeamTarget) {
                    // el láser normal reenfoca desde cero; el gatling conserva
                    // las revoluciones y encadena objetivos sin perder fuelle
                    if (!def.sustainedRamp) beamRamp = 0f;
                    lastBeamTarget = target;
                }
                beamRamp += dt;
                // los nodos de rampa aceleran la subida; el tope no cambia
                rampMult = world.rampMultFor(this);
                float mult = beamMult();
                float beamDmg = world.damageFor(this) * mult * dt;
                target.lastHitTower = this;
                damageDealt += beamDmg;
                target.hitOverTime(beamDmg, world.penFor(this), dt);
                applyEffects(target, world);
                if (def.chainCount > 0) world.prismDamage(this, target, beamDmg, dt);
                attacking = true;
                break;
            case AREA:
                world.areaDamage(this, dt);
                attacking = true;
                break;
        }
    }

    /**
     * Sin objetivo en alcance, el cabezal gira despacio hacia el enemigo más
     * cercano al que podría disparar. No hace daño: solo da sensación de que
     * el tanque está atento en vez de mirar siempre al mismo sitio.
     */
    private void trackNearest(GameWorld world, float dt) {
        Enemy nearest = null;
        float bestDist2 = Float.MAX_VALUE;
        for (int i = 0; i < world.enemies.size; i++) {
            Enemy e = world.enemies.get(i);
            if (!e.alive || e.isHidden() || !canTarget(e)) continue;
            float dx = e.position.x - position.x;
            float dz = e.position.z - position.z;
            float d2 = dx * dx + dz * dz;
            if (d2 < bestDist2) {
                bestDist2 = d2;
                nearest = e;
            }
        }
        if (nearest == null) return;
        float desired = MathUtils.atan2Deg360(
            nearest.position.x - position.x, nearest.position.z - position.z);
        float diff = ((desired - headYawDeg) % 360f + 540f) % 360f - 180f;
        headYawDeg = (headYawDeg + diff * Math.min(1f, dt * 3.5f) + 360f) % 360f;
    }

    void applyEffects(Enemy e, GameWorld world) {
        float power = world.effectPowerFor(def);
        for (int i = 0; i < def.effects.size; i++) {
            e.applyEffect(def.effects.get(i), power);
        }
    }

    /** Multiplicador de rampa del árbol (lo refresca update; 1 sin nodos). */
    private float rampMult = 1f;

    /** Multiplicador actual de la rampa del láser (daño y grosor del rayo). */
    public float beamMult() {
        return Math.min(1f + beamRamp * def.rampPerSecond * rampMult, def.maxRampMult);
    }

    // ---- traslado conduciendo (nodo Logística) ----

    /** Velocidad de crucero al reubicarse, en unidades/segundo. */
    public static final float DRIVE_SPEED = 2.1f;

    private final Vector3 driveFrom = new Vector3();
    private final Vector3 driveTarget = new Vector3();
    private float driveTotal, driveTraveled;
    /**
     * Ruta por casillas hasta el destino (la calcula GameWorld esquivando a
     * los demás tanques). El tanque va tramo a tramo, girando en cada esquina.
     */
    private final Array<Vector3> driveRoute = new Array<>();
    private int driveLeg;
    private float driveDone, driveRouteLen;
    /** Orientación del casco mientras conduce (el render rota el modelo entero). */
    public float driveYawDeg;
    /** Distancia recorrida desde la última huella (lo consume update). */
    private float sinceTrack;

    /** ¿Está de viaje hacia su nueva casilla? No dispara mientras tanto. */
    public boolean driving() {
        return driveLeg < driveRoute.size;
    }

    /** Fracción recorrida del trayecto completo (para efectos). */
    public float driveProgress() {
        if (driveRouteLen <= 0f) return 1f;
        return Math.min(1f, (driveDone + driveTraveled) / driveRouteLen);
    }

    /**
     * Arranca el traslado por la RUTA dada (centros de casilla, del primer
     * paso al destino). La celda de destino se reasigna ya, para que nadie
     * construya allí, pero el tanque tiene que conducir hasta ella rodeando a
     * los demás: antes atravesaba tanques y rocas en línea recta.
     */
    public void startDrive(int x, int z, Array<Vector3> route) {
        this.cellX = x;
        this.cellZ = z;
        driveRoute.clear();
        for (Vector3 wp : route) driveRoute.add(new Vector3(wp));
        driveLeg = 0;
        driveDone = 0f;
        driveRouteLen = 0f;
        Vector3 prev = position;
        for (Vector3 wp : driveRoute) {
            driveRouteLen += prev.dst(wp);
            prev = wp;
        }
        sinceTrack = 0f;
        beginLeg();
        target = null;
        beamRamp = 0f;
    }

    /** Prepara el tramo actual de la ruta. */
    private void beginLeg() {
        if (!driving()) return;
        driveFrom.set(position);
        driveTarget.set(driveRoute.get(driveLeg));
        driveTotal = driveFrom.dst(driveTarget);
        driveTraveled = 0f;
        if (driveTotal > 0.001f) {
            driveYawDeg = MathUtils.atan2Deg360(
                driveTarget.x - driveFrom.x, driveTarget.z - driveFrom.z);
        }
        headYawDeg = driveYawDeg;
    }

    /** Avanza el viaje. @return true si sigue en ruta (y no debe disparar). */
    boolean updateDrive(float dt, GameWorld world) {
        if (!driving()) return false;
        float step = DRIVE_SPEED * dt;
        driveTraveled = Math.min(driveTotal, driveTraveled + step);
        if (driveTotal > 0.001f) {
            position.set(driveFrom).lerp(driveTarget, driveTraveled / driveTotal);
        } else {
            position.set(driveTarget);
        }
        headYawDeg = driveYawDeg;
        // huellas de las orugas cada medio metro largo
        sinceTrack += step;
        if (sinceTrack >= 0.55f) {
            sinceTrack -= 0.55f;
            world.addTrackMarks(position, driveYawDeg);
        }
        // tramo terminado: encara el siguiente (o llega)
        if (driveTraveled >= driveTotal) {
            driveDone += driveTotal;
            driveLeg++;
            beginLeg();
        }
        target = null;
        beamRamp = 0f;
        return true;
    }
}
