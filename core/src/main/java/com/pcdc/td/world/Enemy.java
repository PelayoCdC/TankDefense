package com.pcdc.td.world;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.pcdc.td.data.BiomeDef;
import com.pcdc.td.data.EffectDef;
import com.pcdc.td.data.EnemyDef;

/** Enemigo que avanza por los waypoints del camino. Pooled. */
public class Enemy implements Pool.Poolable {

    public EnemyDef def;
    public final Vector3 position = new Vector3();
    public float hp, maxHp;
    public int goldReward;
    /** Distancia recorrida sobre el camino; usado por el targeting "first". */
    public float pathProgress;
    public float yawDeg;
    public boolean alive;
    public boolean reachedEnd;
    /** Asalto final: al llegar a la muralla se frena, toma impulso y embiste. */
    public boolean assaulting;
    public float assaultTimer;
    public static final float ASSAULT_WINDUP = 0.32f, ASSAULT_LUNGE = 0.18f;

    // efectos de estado
    public float slowFactor = 1f, slowTimer;
    /**
     * El veneno ACUMULA stacks, pero con RENDIMIENTOS DECRECIENTES: el primero
     * vale entero y los siguientes cada vez menos (total x2,25 en vez de x5).
     * Sumando cinco stacks completos, y como el daño por tiempo no pasa por la
     * armadura, el veneno era la mejor arma del juego contra cualquier cosa.
     */
    public static final int MAX_POISON_STACKS = 4;
    private static final float[] POISON_STACK_WEIGHT = {1f, 0.6f, 0.4f, 0.25f};
    public int poisonStacks;
    public float poisonDps, poisonTimer;
    public float burnDps, burnTimer;

    // rasgo del bioma
    public String trait = "none";
    public float traitPower, traitInterval;
    public int traitCount;
    public boolean hidden;          // arbustos: no targeteable
    /** El tanque radar lo tiene iluminado: targeteable aunque esté oculto. */
    public boolean revealed;
    public boolean teleported;      // portales: ya usó el suyo
    public boolean splitChild;      // no vuelve a dividirse
    public boolean eliteAura;       // élite de tierras corruptas
    /** Élite dorado aleatorio: más vida y suelta cristales al morir. */
    public boolean crystalElite;
    public int crystalReward;
    public float auraSpeedMult = 1f; // buff recibido de un élite (reset por step)
    /** Multiplicador de la oleada "enfurecida" (fijo desde el spawn). */
    public float eventSpeedMult = 1f;
    /** Afijo de jefe en Nivel+ ("none"/"armored"/"swift"/"regen"/"iron"). */
    public String affix = "none";
    public float bonusArmor;
    /** Temporizador del rol propio (cura del sanador, sabotaje del zapador). */
    public float roleTimer;
    /** Índice del camino por el que entró (el portador suelta tropa sobre él). */
    public int pathNo;
    /** Pulso visual del sanador: segundos restantes de destello. */
    public float healFlash;
    /** Enfriamiento del arpón SOBRE ESTE enemigo (ver GameWorld.pullBack). */
    public float pullCooldown;
    /** Vulnerabilidad temporal (choque térmico): multiplica el daño recibido. */
    public float vulnMult = 1f;
    public float vulnTimer;
    public boolean sprinting;
    public int armorHits;
    public float shieldHp, shieldMax;
    public boolean evadeReady;
    private float sprintTimer, timeSinceDamage, evadeTimer;

    // --- unidad insignia del bioma (EnemyDef.special) ---
    public String special = "none";
    /** Temporizador del truco propio (embestida, velo, salto, rayo...). */
    public float specialTimer;
    /** Embestidor: tramo de carrera (lo usan la velocidad y el render). */
    public boolean charging;
    /** Oculto por la bruma de un aliado; se recalcula en cada paso. */
    public boolean cloaked;
    /** Armadura prestada por un resonador cercano; se recalcula en cada paso. */
    public float auraArmor;

    // habilidad del jefe del bioma (solo enemigos "boss")
    public String bossAbility = "none";
    public boolean abilityUsed;      // habilidades de un solo uso (summon)
    public float abilityTimer;       // periódicas (sandveil, healpulse)
    /**
     * GOLPE TELEGRAFIADO del jefe: se planta, carga durante CAST_TIME con un
     * aviso bien visible y descarga. Sus habilidades eran automáticas e
     * invisibles: pasaban cosas y no se sabía por qué.
     */
    public boolean casting;
    public float castProgress;
    public float castCooldown = 9f;
    public static final float CAST_TIME = 1.3f;
    /** Lo consume el GameWorld: la carga ha llegado al final. */
    public boolean castReleased;

    /** Fase del jefe: 2 por debajo de la mitad de vida (más rápido y más duro). */
    public int phase = 1;
    /** El GameWorld lo consume para el estallido y el aviso del cambio de fase. */
    public boolean phaseJustChanged;
    /** Temporizador del sabotaje de fase 2. */
    public float sabotageTimer;
    /** Guion del jefe final: invocaciones ya lanzadas (al 66% y al 33%). */
    public int summonStage;
    public boolean forceFlying;      // jefe volador (flight) sin cambiar la def
    public boolean justBlinked;      // el GameWorld lo consume para el Fx
    private int blinkStage;

    private Array<Vector3> waypoints;
    private int wpIndex;
    /** Longitud total de su ruta (para escalar al salir del portal / entrar en meta). */
    public float totalPathLen;

    /** Última torre que le hizo daño directo (atribución de bajas). */
    public Tower lastHitTower;

    /** Solo lo usa la capa de render; la simulación no lo toca. */
    public ModelInstance renderInstance;
    public String renderKey;

    public void init(EnemyDef def, Array<Vector3> waypoints, float hpMult, float goldMult) {
        this.def = def;
        this.waypoints = waypoints;
        this.hp = this.maxHp = def.hp * hpMult;
        this.goldReward = Math.round(def.goldReward * goldMult);
        this.position.set(waypoints.first());
        this.wpIndex = 1;

        this.totalPathLen = 0f;
        for (int i = 1; i < waypoints.size; i++) {
            totalPathLen += waypoints.get(i).dst(waypoints.get(i - 1));
        }

        // dispersión al aparecer: varios enemigos salen a la vez sin solaparse
        if (waypoints.size > 1) {
            float dx = waypoints.get(1).x - position.x;
            float dz = waypoints.get(1).z - position.z;
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len > 0.0001f) {
                dx /= len;
                dz /= len;
                yawDeg = MathUtils.atan2Deg360(dx, dz);
                float side = MathUtils.random(-0.7f, 0.7f);
                float back = MathUtils.random(0f, 0.8f);
                position.add(-dz * side - dx * back, 0f, dx * side - dz * back);
            }
        }
        this.position.y = def.flying ? def.altitude : 0f;
        this.pathProgress = 0f;
        this.alive = true;
        this.reachedEnd = false;
        this.assaulting = false;
        this.assaultTimer = 0f;
        this.slowFactor = 1f;
        this.slowTimer = 0f;
        this.poisonStacks = 0;
        this.poisonDps = this.poisonTimer = 0f;
        this.burnDps = this.burnTimer = 0f;
        this.trait = "none";
        this.traitPower = this.traitInterval = 0f;
        this.traitCount = 0;
        this.hidden = this.teleported = this.splitChild = this.eliteAura = false;
        this.revealed = false;
        this.crystalElite = false;
        this.crystalReward = 0;
        this.auraSpeedMult = 1f;
        this.eventSpeedMult = 1f;
        this.affix = "none";
        this.bonusArmor = 0f;
        this.roleTimer = 0f;
        this.pathNo = 0;
        this.healFlash = 0f;
        this.pullCooldown = 0f;
        this.vulnMult = 1f;
        this.vulnTimer = 0f;
        this.sprinting = false;
        this.armorHits = 0;
        this.shieldHp = this.shieldMax = 0f;
        this.evadeReady = true;
        this.sprintTimer = this.timeSinceDamage = this.evadeTimer = 0f;
        this.special = def.special;
        this.specialTimer = 0f;
        this.groundedTimer = 0f;
        this.charging = this.cloaked = false;
        this.auraArmor = 0f;
        this.bossAbility = "none";
        this.casting = false;
        this.castProgress = 0f;
        this.castCooldown = 9f;
        this.castReleased = false;
        this.abilityUsed = this.justBlinked = false;
        this.abilityTimer = 0f;
        this.phase = 1;
        this.phaseJustChanged = false;
        this.sabotageTimer = 0f;
        this.summonStage = 0;
        this.forceFlying = false;
        this.blinkStage = 0;
    }

    /** Derribado por el arpón de red: camina por tierra unos segundos. */
    public float groundedTimer;

    /** Vuela de verdad (por def o por la habilidad "flight" del jefe). */
    public boolean isFlying() {
        return (def.flying || forceFlying) && groundedTimer <= 0f;
    }

    /** Lo baja al suelo: mientras dure, los antiaéreos dejan de ser necesarios. */
    public void groundFor(float seconds) {
        if (!def.flying && !forceFlying) return;
        groundedTimer = Math.max(groundedTimer, seconds);
    }

    /**
     * Oculto A EFECTOS DE TARGETING: por arbustos/velo propio o por la bruma
     * de un aliado. El radar anula cualquier ocultación.
     */
    public boolean isHidden() {
        return (hidden || cloaked) && !revealed;
    }

    /** Daño de impacto marcado por el radar: +10%. */
    float markedMult() {
        return revealed ? 1.1f : 1f;
    }

    /** Aplica el rasgo del bioma tras init(). */
    public void applyTrait(BiomeDef biome, boolean elite) {
        this.trait = biome.trait;
        this.traitPower = biome.traitPower;
        this.traitInterval = biome.traitInterval;
        this.traitCount = biome.traitCount;
        switch (trait) {
            case "icearmor":
                armorHits = traitCount > 0 ? traitCount : 3;
                break;
            case "igneous":
                goldReward = Math.round(goldReward * (traitPower > 0 ? traitPower : 1.3f));
                break;
            case "shield":
                shieldMax = shieldHp = maxHp * (traitPower > 0 ? traitPower : 0.25f);
                break;
            case "elite":
                eliteAura = elite;
                break;
        }
    }

    /** Reposiciona a un hijo de división en el punto de la muerte del padre. */
    public void initAsSplitChild(Enemy parent) {
        position.set(parent.position);
        wpIndex = parent.wpIndex;
        pathProgress = parent.pathProgress;
        hp = maxHp = parent.maxHp * (parent.traitPower > 0 ? parent.traitPower : 0.3f);
        goldReward = Math.max(1, parent.goldReward / 3);
        splitChild = true;
        trait = parent.trait;
        traitPower = parent.traitPower;
        traitInterval = parent.traitInterval;
        traitCount = parent.traitCount;
    }

    int wpIndex() {
        return wpIndex;
    }

    /** Altura del centro del cuerpo (para apuntado y barras de vida). */
    public float centerY() {
        return position.y + def.size * 0.6f;
    }

    public void teleportTo(Vector3 pos, int newWpIndex, float newProgress) {
        position.x = pos.x;
        position.z = pos.z;
        wpIndex = newWpIndex;
        pathProgress = newProgress;
        teleported = true;
    }

    public void update(float dt) {
        if (!alive) return;

        // estados
        if (slowTimer > 0f) {
            slowTimer -= dt;
            if (slowTimer <= 0f) slowFactor = 1f;
        }
        if (vulnTimer > 0f) {
            vulnTimer -= dt;
            if (vulnTimer <= 0f) vulnMult = 1f;
        }
        // derribado: baja al suelo y vuelve a subir cuando se suelta de la red
        // (durante el asalto manda el picado, no se toca la altura)
        if ((def.flying || forceFlying) && !assaulting) {
            float goal = groundedTimer > 0f ? 0f : def.altitude;
            if (groundedTimer > 0f) groundedTimer -= dt;
            float climb = 2.5f * dt;
            position.y += MathUtils.clamp(goal - position.y, -climb, climb);
        }
        if (poisonTimer > 0f) {
            poisonTimer -= dt;
            damageDot(poisonDps * dt);
            if (poisonTimer <= 0f) {
                poisonStacks = 0;
                poisonDps = 0f;
            }
        }
        if (burnTimer > 0f) {
            burnTimer -= dt;
            damageDot(burnDps * dt);
        }

        // rasgos con lógica por frame
        switch (trait) {
            case "regen":
                if (hp < maxHp) hp = Math.min(maxHp, hp + maxHp * (traitPower > 0 ? traitPower : 0.012f) * dt);
                break;
            case "sprint": {
                float period = traitInterval > 0 ? traitInterval : 4f;
                sprintTimer += dt;
                sprinting = (sprintTimer % period) > period - 1.2f;
                break;
            }
            case "shield": {
                timeSinceDamage += dt;
                float regenAfter = traitInterval > 0 ? traitInterval : 3f;
                if (shieldHp < shieldMax && timeSinceDamage >= regenAfter) shieldHp = shieldMax;
                break;
            }
            case "evasion":
                if (!evadeReady) {
                    evadeTimer -= dt;
                    if (evadeTimer <= 0f) evadeReady = true;
                }
                break;
        }
        if (!alive) return;

        // habilidades del jefe con lógica por frame
        switch (bossAbility) {
            case "sandveil": {
                // se oculta 1.5 s cada 7 s: las torres pierden el objetivo
                // (en fase 2, se esconde más a menudo y más rato)
                abilityTimer += dt;
                float cycle = phase == 2 ? 5f : 7f;
                hidden = (abilityTimer % cycle) > cycle - (phase == 2 ? 2f : 1.5f);
                break;
            }
            case "regrowth":
                // sin recibir daño 3 s, regenera un 3%/s (5% en fase 2)
                if (hp < maxHp && timeSinceDamage >= 3f) {
                    hp = Math.min(maxHp, hp + maxHp * (phase == 2 ? 0.05f : 0.03f) * dt);
                }
                break;
            case "healpulse":
                abilityTimer += dt; // el GameWorld emite el pulso de curación
                break;
        }

        // truco de la unidad insignia del bioma con lógica por frame (los que
        // tocan al mundo — auras, saltos, rayos — los lleva el GameWorld)
        switch (special) {
            case "charge": {
                // embestida a intervalos: se lanza 1,2 s de cada ciclo
                float period = def.specialInterval > 0 ? def.specialInterval : 5f;
                specialTimer += dt;
                charging = (specialTimer % period) > period - 1.2f;
                break;
            }
            case "veil": {
                // espejismo: se difumina y las torres pierden el objetivo
                float period = def.specialInterval > 0 ? def.specialInterval : 5f;
                specialTimer += dt;
                hidden = (specialTimer % period) > period - 1.5f;
                break;
            }
            default:
                break;
        }

        // afijo regenerador (Nivel+): curación constante, sin la pausa de regrowth
        if ("regen".equals(affix) && hp < maxHp) {
            hp = Math.min(maxHp, hp + maxHp * 0.015f * dt);
        }

        // GOLPE CARGADO: mientras carga no avanza (ventana para castigarle)
        if (!"none".equals(bossAbility) && !assaulting) {
            if (casting) {
                castProgress += dt;
                if (castProgress >= CAST_TIME) {
                    casting = false;
                    castProgress = 0f;
                    castReleased = true;
                    castCooldown = phase == 2 ? 7f : 11f;
                }
                return; // plantado mientras carga
            }
            castCooldown -= dt;
            if (castCooldown <= 0f) {
                casting = true;
                castProgress = 0f;
                return;
            }
        }

        // ASALTO A LA FORTALEZA: pausa de impulso y embestida; la vida se
        // pierde al IMPACTO, no desvaneciéndose a mitad del camino extendido
        if (assaulting) {
            assaultTimer += dt;
            if (assaultTimer >= ASSAULT_WINDUP && wpIndex < waypoints.size) {
                Vector3 goal = waypoints.peek();
                float gx = goal.x - position.x;
                float gz = goal.z - position.z;
                float gd = (float) Math.sqrt(gx * gx + gz * gz);
                float lunge = 3.2f * dt;
                if (gd > 0.01f) {
                    position.add(gx / gd * lunge, 0f, gz / gd * lunge);
                    yawDeg = MathUtils.atan2Deg360(gx, gz);
                }
                // los voladores rematan en picado
                if (isFlying()) position.y = Math.max(0.55f, position.y - 3f * dt);
            }
            if (assaultTimer >= ASSAULT_WINDUP + ASSAULT_LUNGE) {
                reachedEnd = true;
                alive = false;
            }
            return;
        }

        float speed = def.speed * slowFactor * auraSpeedMult * eventSpeedMult;
        if (sprinting) speed *= traitPower > 0 ? traitPower : 1.5f;
        if (charging) speed *= def.specialPower > 0 ? def.specialPower : 2.4f;
        // abominación: herida de muerte, se desboca
        if ("frenzy".equals(special) && hp < maxHp * 0.5f) {
            speed *= def.specialPower > 0 ? def.specialPower : 1.5f;
        }
        if ("enrage".equals(bossAbility) && hp < maxHp * 0.3f) speed *= 1.45f;
        if (phase == 2) speed *= 1.18f; // el jefe se desata a media vida

        // avanza hacia el waypoint desde su posición real (las colisiones lo desvían)
        float remaining = speed * dt;
        while (remaining > 0f && wpIndex < waypoints.size) {
            Vector3 wp = waypoints.get(wpIndex);
            float dx = wp.x - position.x;
            float dz = wp.z - position.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.35f) {
                wpIndex++;
                // TIERRA: el penúltimo waypoint ES la muralla → empieza el asalto.
                // La comprobación de DISTANCIA a la meta es imprescindible para
                // los voladores derribados: su ruta solo tiene tres puntos y el
                // del medio cae en mitad del tablero, así que sin ella
                // embestían la muralla desde el centro del mapa.
                if (wpIndex >= waypoints.size - 1 && !isFlying()
                    && position.dst2(waypoints.peek()) < 30f) {
                    assaulting = true;
                    assaultTimer = 0f;
                    remaining = 0f;
                }
                continue;
            }
            // AIRE: su último waypoint está 4.5 u MÁS ALLÁ de la muralla (la
            // extensión del camino); embisten justo antes de sobrevolarla
            if (isFlying() && wpIndex >= waypoints.size - 1
                && dist < GridMap.PATH_EXTENSION + 0.5f) {
                assaulting = true;
                assaultTimer = 0f;
                break;
            }
            float step = Math.min(remaining, dist);
            position.add(dx / dist * step, 0f, dz / dist * step);
            pathProgress += step;
            yawDeg = MathUtils.atan2Deg360(dx, dz);
            remaining -= step;
        }

        if (wpIndex >= waypoints.size) {
            reachedEnd = true;
            alive = false;
        }
    }

    /** Radio de colisión contra otros enemigos. */
    public float radius() {
        return def.size * 0.45f;
    }

    /**
     * Impacto discreto de una torre: la armadura resta (armor - pen) al golpe,
     * con un suelo del 20% del daño original. Los DoT (veneno/quemadura) pasan
     * por damageDot y la ignoran por completo.
     */
    /** @return el daño que atravesó la armadura (para los números flotantes). */
    public float hit(float amount, float penetration) {
        float reduction = Math.max(0f, armor() - penetration);
        float effective = Math.max(amount * 0.2f, amount - reduction);
        damage(effective);
        return effective;
    }

    /**
     * Daño continuo (láser/lanzallamas) ya multiplicado por dt: la armadura se
     * resta como DPS, así los rayos no mueren por aplicar el descuento por frame.
     */
    public void hitOverTime(float amountTimesDt, float penetration, float dt) {
        float reduction = Math.max(0f, armor() - penetration) * dt;
        damage(Math.max(amountTimesDt * 0.2f, amountTimesDt - reduction));
    }

    /** Armadura efectiva actual (incluye la prestada por un resonador). */
    public float armor() {
        return def.armor + bonusArmor + auraArmor;
    }

    /** Daño de impacto (pasa por evasión, escudo y coraza de hielo; no por armadura). */
    public void damage(float amount) {
        if (!alive) return;
        amount *= vulnMult; // choque térmico: vapor que ablanda
        amount *= markedMult(); // marcado por el radar

        if ("evasion".equals(trait) && def.flying && evadeReady) {
            evadeReady = false;
            evadeTimer = traitInterval > 0 ? traitInterval : 4f;
            return;
        }
        if (shieldHp > 0f) {
            timeSinceDamage = 0f;
            shieldHp -= amount;
            if (shieldHp >= 0f) return;
            amount = -shieldHp;
            shieldHp = 0f;
        }
        if (armorHits > 0 && amount >= 5f) {
            armorHits--;
            amount *= traitPower > 0 ? traitPower : 0.15f;
        }
        if (phase == 2) amount *= 0.9f; // blindaje de la segunda fase
        if ("iron".equals(affix)) amount *= 0.85f; // afijo férreo (Nivel+)
        // abominación enfurecida: la carne corrupta encaja peor los golpes
        if ("frenzy".equals(special) && hp < maxHp * 0.5f) amount *= 0.75f;
        timeSinceDamage = 0f;
        hp -= amount;
        if (hp <= 0f) {
            alive = false;
            return;
        }
        // los jefes cambian de fase al bajar de la mitad de vida
        if (phase == 1 && !"none".equals(bossAbility) && hp < maxHp * 0.5f) {
            phase = 2;
            phaseJustChanged = true;
        }
        if ("blink".equals(bossAbility)) {
            // se teletransporta camino adelante al cruzar 2/3 y 1/3 de vida
            if (blinkStage == 0 && hp < maxHp * 0.66f) {
                blinkStage = 1;
                advanceInstant(2.2f);
            } else if (blinkStage == 1 && hp < maxHp * 0.33f) {
                blinkStage = 2;
                advanceInstant(2.2f);
            }
        }
    }

    /** Avance instantáneo por el camino (parpadeo del jefe de ruinas). */
    private void advanceInstant(float dist) {
        float remaining = dist;
        while (remaining > 0f && wpIndex < waypoints.size) {
            Vector3 wp = waypoints.get(wpIndex);
            float dx = wp.x - position.x;
            float dz = wp.z - position.z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < 0.35f) {
                wpIndex++;
                continue;
            }
            float step = Math.min(remaining, d);
            position.add(dx / d * step, 0f, dz / d * step);
            pathProgress += step;
            remaining -= step;
        }
        justBlinked = true;
    }

    /** Salto del acólito de las ruinas: avanza por el camino sin recorrerlo. */
    public void warpForward(float dist) {
        advanceInstant(dist);
        justBlinked = false; // el fx lo lanza quien llama (no es el jefe)
    }

    /** Ruta que sigue este enemigo (para invocar esbirros sobre ella). */
    Array<Vector3> route() {
        return waypoints;
    }

    /**
     * Arpón: devuelve al enemigo camino ATRÁS la distancia dada. Cancela un
     * asalto en curso, así que engancharlo a las puertas de la fortaleza es
     * un rescate legítimo en el último segundo.
     */
    public void pullBack(float distance) {
        if (!alive) return;
        assaulting = false;
        assaultTimer = 0f;
        float remaining = distance;
        while (remaining > 0f && wpIndex > 0) {
            Vector3 prev = waypoints.get(wpIndex - 1);
            float dx = prev.x - position.x;
            float dz = prev.z - position.z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < 0.01f) {
                wpIndex--;
                continue;
            }
            float step = Math.min(remaining, d);
            position.add(dx / d * step, 0f, dz / d * step);
            pathProgress = Math.max(0f, pathProgress - step);
            remaining -= step;
            // si ha llegado al waypoint anterior, ese pasa a ser su destino
            if (step >= d - 0.001f) wpIndex--;
        }
    }

    /** Aterriza la tropa de un portador en un punto concreto de la ruta de tierra. */
    public void initAtWaypoint(Vector3 point, int index, float progress, float sideOffset) {
        position.set(point.x + sideOffset, 0f, point.z);
        wpIndex = index;
        // sin un pathProgress realista el render los haría "emerger" del suelo
        // a mitad de mapa y el targeting "primero" los pondría los últimos
        pathProgress = progress;
    }

    /** Coloca un esbirro invocado junto a su invocador, sobre la misma ruta. */
    public void initAtParent(Enemy parent, float sideOffset) {
        position.set(parent.position);
        float yaw = parent.yawDeg * MathUtils.degreesToRadians;
        position.add(MathUtils.cos(yaw) * sideOffset, 0f, -MathUtils.sin(yaw) * sideOffset);
        position.y = def.flying ? def.altitude : 0f;
        wpIndex = parent.wpIndex;
        pathProgress = parent.pathProgress;
        yawDeg = parent.yawDeg;
    }

    /** Daño por segundo de veneno/quemadura (ignora evasión y armadura, no escudo). */
    private void damageDot(float amount) {
        if (!alive) return;
        if (shieldHp > 0f) {
            timeSinceDamage = 0f;
            shieldHp = Math.max(0f, shieldHp - amount);
            return;
        }
        hp -= amount;
        if (hp <= 0f) alive = false;
    }

    public void applyEffect(EffectDef e) {
        applyEffect(e, 1f);
    }

    /** @param power potencia del árbol (1 = base): escala dps y ralentización. */
    public void applyEffect(EffectDef e, float power) {
        switch (e.type) {
            case "slow": {
                // potencia > 1 = frena más: escala la REDUCCIÓN, no el factor
                float factor = Math.max(0.15f, 1f - (1f - e.factor) * power);
                if ("slowresist".equals(trait)) {
                    float resist = traitPower > 0 ? traitPower : 0.5f;
                    factor = 1f - (1f - factor) * resist;
                }
                slowFactor = factor;
                slowTimer = Math.max(slowTimer, e.duration);
                break;
            }
            case "poison":
                // cada aplicación añade un stack, y cada stack rinde menos que
                // el anterior; la duración se refresca siempre
                if (poisonStacks < MAX_POISON_STACKS) {
                    poisonDps += e.dps * power * POISON_STACK_WEIGHT[poisonStacks];
                    poisonStacks++;
                }
                poisonTimer = Math.max(poisonTimer, e.duration);
                break;
            case "burn":
                if ("igneous".equals(trait)) break; // inmunes al fuego
                burnDps = Math.max(burnDps, e.dps * power);
                burnTimer = Math.max(burnTimer, e.duration);
                break;
        }
    }

    @Override
    public void reset() {
        def = null;
        waypoints = null;
        lastHitTower = null;
        // renderInstance/renderKey se conservan: el pool reutiliza el objeto y
        // la capa de render recrea la instancia solo si cambia el tipo.
    }
}
