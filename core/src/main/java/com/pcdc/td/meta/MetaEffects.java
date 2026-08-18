package com.pcdc.td.meta;

import com.pcdc.td.data.BuildingDef;
import com.pcdc.td.data.Catalogs;
import com.pcdc.td.data.UpgradeNodeDef;

/** Agrega mejoras compradas y edificios en un StatBlock. Único punto de aplicación de bonus. */
public final class MetaEffects {

    private MetaEffects() {
    }

    public static StatBlock compute(PlayerProfile profile, Catalogs catalogs) {
        StatBlock stats = new StatBlock();

        for (UpgradeNodeDef node : catalogs.upgrades) {
            if (!profile.hasUpgrade(node.id)) continue;
            apply(stats, node.effect.stat, node.effect.add, node.effect.value);
        }

        for (BuildingDef building : catalogs.buildings) {
            int level = profile.buildingLevel(building.id);
            // con doctrina elegida, cada nivel comprado aplica ESA, no la fija
            BuildingDef.DoctrineDef doctrine = null;
            if (building.doctrines.size > 0) {
                String chosen = profile.doctrines.get(building.id);
                for (BuildingDef.DoctrineDef d : building.doctrines) {
                    if (d.id.equals(chosen)) doctrine = d;
                }
                if (doctrine == null) doctrine = building.doctrines.first();
            }
            for (int i = 0; i < level && i < building.levels.size; i++) {
                BuildingDef.LevelDefEntry entry = building.levels.get(i);
                if (doctrine != null) apply(stats, doctrine.stat, doctrine.add, null);
                else apply(stats, entry.stat, entry.add, null);
            }
        }

        // -Dtd.allfusions=true desbloquea recetas, tanques y nodos QoL (solo pruebas)
        if (Boolean.getBoolean("td.allfusions")) {
            for (com.pcdc.td.data.FusionDef f : catalogs.fusions) {
                stats.unlockedFusions.add(f.result);
            }
            for (com.pcdc.td.data.TowerDef t : catalogs.towers) {
                if (!t.fusionOnly) stats.unlockedTowers.add(t.id);
            }
            stats.canRelocate = true;
            stats.directFusion = true;
        }
        return stats;
    }

    private static void apply(StatBlock stats, String stat, float add, String value) {
        switch (stat) {
            case "towerDamage" -> {
                if (value != null) stats.towerDamage.getAndIncrement(value, 0f, add);
            }
            case "towerRate" -> {
                if (value != null) stats.towerRate.getAndIncrement(value, 0f, add);
            }
            case "towerRange" -> {
                if (value != null) stats.towerRange.getAndIncrement(value, 0f, add);
            }
            case "towerEffect" -> {
                if (value != null) stats.towerEffect.getAndIncrement(value, 0f, add);
            }
            case "towerPen" -> {
                if (value != null) stats.towerPen.getAndIncrement(value, 0f, add);
            }
            case "towerChain" -> {
                if (value != null) stats.towerChain.getAndIncrement(value, 0f, add);
            }
            case "towerSplash" -> {
                if (value != null) stats.towerSplash.getAndIncrement(value, 0f, add);
            }
            case "towerRamp" -> {
                if (value != null) stats.towerRamp.getAndIncrement(value, 0f, add);
            }
            case "auraDamage" -> stats.auraDamageAdd += add;
            case "unlockRelocate" -> stats.canRelocate = true;
            case "unlockDirectFusion" -> stats.directFusion = true;
            case "damageMult" -> stats.damageMult += add;
            case "fireRateMult" -> stats.fireRateMult += add;
            case "rangeMult" -> stats.rangeMult += add;
            case "goldPerKillMult" -> stats.goldPerKillMult += add;
            case "startGoldFlat" -> stats.startGoldFlat += Math.round(add);
            case "livesFlat" -> stats.livesFlat += Math.round(add);
            case "metaIncomeFlat" -> stats.metaIncomeFlat += Math.round(add);
            case "upgradeDiscount" -> stats.upgradeDiscount += add;
            case "sellRefundAdd" -> stats.sellRefundAdd += add;
            case "penFlat" -> stats.penFlat += add;
            case "earlyBonusMult" -> stats.earlyBonusMult += add;
            case "unlockFusion" -> {
                if (value != null) stats.unlockedFusions.add(value);
            }
            case "unlockTower" -> {
                if (value != null) stats.unlockedTowers.add(value);
            }
            case "unlockAbility" -> {
                if (value != null) stats.unlockedAbilities.add(value);
            }
        }
    }

    /** Coste efectivo de un nodo del árbol con el descuento del laboratorio. */
    public static int upgradeCost(UpgradeNodeDef node, StatBlock stats) {
        return Math.round(node.cost * (1f - Math.min(0.5f, stats.upgradeDiscount)));
    }
}
