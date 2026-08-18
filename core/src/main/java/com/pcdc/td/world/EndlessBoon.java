package com.pcdc.td.world;

import com.badlogic.gdx.utils.Array;

/**
 * Carta que se elige cada diez oleadas en el modo SIN FIN. Unas dan y otras
 * negocian (el enemigo mejora, pero pagas menos o cobras más): así el modo deja
 * de ser "la campaña sin final" y cada partida coge una forma distinta.
 *
 * Todas actúan sobre multiplicadores DE LA PARTIDA (GameWorld.run*), nunca
 * sobre el perfil: al salir del nivel no queda rastro.
 */
public final class EndlessBoon {

    public final String id;
    private final Effect effect;

    private interface Effect {
        void apply(GameWorld world);
    }

    private EndlessBoon(String id, Effect effect) {
        this.id = id;
        this.effect = effect;
    }

    public void apply(GameWorld world) {
        effect.apply(world);
    }

    /** Catálogo completo. Los ids son también las claves de idioma. */
    public static Array<EndlessBoon> all() {
        Array<EndlessBoon> list = new Array<>();
        list.add(new EndlessBoon("gold", w -> w.runGoldMult *= 1.25f));
        list.add(new EndlessBoon("damage", w -> w.runDamageMult *= 1.12f));
        list.add(new EndlessBoon("range", w -> w.runRangeMult *= 1.10f));
        list.add(new EndlessBoon("dot", w -> w.runEffectMult *= 1.30f));
        list.add(new EndlessBoon("pen", w -> w.runPen += 2f));
        list.add(new EndlessBoon("lives", w -> w.lives += 3));
        list.add(new EndlessBoon("cash", w -> w.gold += 500));
        list.add(new EndlessBoon("discount", w -> w.runUpgradeDiscount =
            Math.min(0.5f, w.runUpgradeDiscount + 0.2f)));
        // tratos: el enemigo mejora a cambio de economía
        list.add(new EndlessBoon("armor_trade", w -> {
            w.runEnemyArmorBonus += 2f;
            w.runGoldMult *= 1.5f;
        }));
        list.add(new EndlessBoon("speed_trade", w -> {
            w.runEnemySpeedMult *= 1.15f;
            w.runGoldMult *= 1.35f;
        }));
        return list;
    }
}
