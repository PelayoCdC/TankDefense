package com.pcdc.td.meta;

import com.badlogic.gdx.utils.ObjectFloatMap;
import com.badlogic.gdx.utils.ObjectSet;

/** Bonus agregados de mejoras + edificios; los consume GameWorld al iniciar nivel. */
public class StatBlock {

    public static final StatBlock NONE = new StatBlock();

    public float damageMult = 1f;
    public float fireRateMult = 1f;
    public float rangeMult = 1f;
    public float goldPerKillMult = 1f;
    public int startGoldFlat = 0;
    public int livesFlat = 0;
    public int metaIncomeFlat = 0;
    public float upgradeDiscount = 0f;
    /** Aumento del porcentaje de reembolso al vender torres. */
    public float sellRefundAdd = 0f;
    /** Penetración plana para TODOS los tanques (doctrina de la armería). */
    public float penFlat = 0f;
    /** Bonus multiplicador del oro por adelantar oleadas (0 = sin bonus extra). */
    public float earlyBonusMult = 0f;
    /** Torres-resultado de fusión desbloqueadas. */
    public final ObjectSet<String> unlockedFusions = new ObjectSet<>();
    /** Tanques desbloqueados en el árbol (los avanzados y el de mando). */
    public final ObjectSet<String> unlockedTowers = new ObjectSet<>();
    /** Habilidades activas desbloqueadas (meteor, frost, gold). */
    public final ObjectSet<String> unlockedAbilities = new ObjectSet<>();

    /** Bonus específicos por torre (0 = sin bonus; multiplicador = 1 + valor). */
    public final ObjectFloatMap<String> towerDamage = new ObjectFloatMap<>();
    public final ObjectFloatMap<String> towerRate = new ObjectFloatMap<>();
    public final ObjectFloatMap<String> towerRange = new ObjectFloatMap<>();
    /** Potencia de los efectos de estado (veneno/quemadura/ralentización). */
    public final ObjectFloatMap<String> towerEffect = new ObjectFloatMap<>();
    /** PEN plana extra. */
    public final ObjectFloatMap<String> towerPen = new ObjectFloatMap<>();
    /** Saltos de cadena extra (Tesla/Prisma). */
    public final ObjectFloatMap<String> towerChain = new ObjectFloatMap<>();
    /** Radio de explosión (multiplicador = 1 + valor). */
    public final ObjectFloatMap<String> towerSplash = new ObjectFloatMap<>();
    /** Velocidad de rampa del rayo (multiplicador = 1 + valor). */
    public final ObjectFloatMap<String> towerRamp = new ObjectFloatMap<>();
    /** Bonus extra del aura del tanque de mando (se suma a auraDamageAdd). */
    public float auraDamageAdd = 0f;
    /** Nodo "Logística": permite mover tanques entre oleadas. */
    public boolean canRelocate = false;
    /** Nodo "Fabricación avanzada": las fusiones desbloqueadas se compran directas. */
    public boolean directFusion = false;

    /** Tanques base siempre disponibles sin nodo de desbloqueo: SOLO el cañón. */
    public static final ObjectSet<String> DEFAULT_TOWERS = ObjectSet.with("cannon");

    /** ¿Está disponible este tanque base (de serie o desbloqueado)? */
    public boolean towerAvailable(String id) {
        return DEFAULT_TOWERS.contains(id) || unlockedTowers.contains(id);
    }

    public StatBlock() {
        // la receta del tutorial está desbloqueada desde el principio
        unlockedFusions.add("plague");
    }
}
