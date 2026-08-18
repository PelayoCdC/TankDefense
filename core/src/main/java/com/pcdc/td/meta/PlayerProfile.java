package com.pcdc.td.meta;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.pcdc.td.data.Balance;

/** Estado persistente del jugador (se serializa tal cual a save.json). */
public class PlayerProfile {

    public static class LevelReward {
        public int crystals;
        public int medals;
    }

    /**
     * 0 por defecto a propósito: Json omite los campos con valor por defecto,
     * así que la versión real (4) siempre queda escrita en el guardado y los
     * ficheros antiguos (sin campo) deserializan a 0 y pasan por migrate().
     */
    public int version = 0;
    public int metaCurrency = 0;
    /** Medallas: 1 por cada estrella NUEVA conseguida. Pagan los edificios de la base. */
    public int medals = 0;
    /** nivel → mejores estrellas conseguidas (1-3). Solo niveles completados. */
    public ObjectIntMap<String> levels = new ObjectIntMap<>();
    /** ids de nodos de mejora comprados. */
    public Array<String> upgrades = new Array<>();
    /** edificio → nivel actual (0 = no construido). */
    public ObjectIntMap<String> buildings = new ObjectIntMap<>();
    /** Doctrina elegida en cada edificio que las tenga (id de edificio -> id). */
    public com.badlogic.gdx.utils.ObjectMap<String, String> doctrines =
        new com.badlogic.gdx.utils.ObjectMap<>();
    public int lastLevelPlayed = 1;
    /** Estadísticas acumuladas (kills, fusions, wins, perfects, sold, bestWave). */
    public ObjectIntMap<String> stats = new ObjectIntMap<>();
    /** Logros ya reclamados. */
    public Array<String> achievements = new Array<>();
    /** bioma → mejor oleada en modo sin fin. */
    public ObjectIntMap<String> survivalBest = new ObjectIntMap<>();
    /** Hitos del sin fin ya cobrados ("<bioma>:<oleada>"), pago único. */
    public com.badlogic.gdx.utils.Array<String> survivalMilestones =
        new com.badlogic.gdx.utils.Array<>();
    /** Skins de torre compradas ("classic" siempre disponible). */
    public Array<String> skins = new Array<>();
    /** Skin de torre equipada. */
    public String activeSkin = "classic";
    /** Biomas cuyo bestiario ya se mostró automáticamente. */
    public Array<String> seenBiomes = new Array<>();
    /** nivel → máscara de retos ya conseguidos (ver meta/Challenges). */
    public ObjectIntMap<String> challenges = new ObjectIntMap<>();
    /** Consejos contextuales ya mostrados (cada mecánica se explica una vez). */
    public Array<String> seenTips = new Array<>();

    /** true la primera vez que se pregunta por ese consejo; lo marca como visto. */
    public boolean markTipSeen(String id) {
        if (seenTips.contains(id, false)) return false;
        seenTips.add(id);
        return true;
    }

    public int challengesFor(int levelId) {
        return challenges.get(String.valueOf(levelId), 0);
    }

    /** Retos conseguidos en todos los niveles (para la pantalla de estadísticas). */
    public int totalChallenges() {
        int total = 0;
        for (ObjectIntMap.Entry<String> e : challenges) total += Challenges.count(e.value);
        return total;
    }

    public boolean ownsSkin(String id) {
        return "classic".equals(id) || skins.contains(id, false);
    }

    public void addStat(String key, int amount) {
        if (amount > 0) stats.getAndIncrement(key, 0, amount);
    }

    public void maxStat(String key, int value) {
        if (value > stats.get(key, 0)) stats.put(key, value);
    }

    public int stat(String key) {
        return stats.get(key, 0);
    }

    public int starsFor(int levelId) {
        return levels.get(String.valueOf(levelId), 0);
    }

    public boolean isCompleted(int levelId) {
        return starsFor(levelId) > 0;
    }

    /** Estrellas acumuladas en toda la campaña (máximo 150 con 50 niveles). */
    public int totalStars() {
        int total = 0;
        for (ObjectIntMap.Entry<String> e : levels) total += e.value;
        return total;
    }

    /**
     * Rango del perfil por estrellas: un título que se ve en el menú, para que
     * las estrellas sirvan para algo más que desbloquear Nivel+.
     * Devuelve la clave i18n ("rank.recruit"...).
     */
    public String rankKey() {
        int s = totalStars();
        if (s >= 140) return "rank.marshal";
        if (s >= 110) return "rank.general";
        if (s >= 80) return "rank.colonel";
        if (s >= 55) return "rank.captain";
        if (s >= 30) return "rank.lieutenant";
        if (s >= 12) return "rank.sergeant";
        return "rank.recruit";
    }

    /** Un nivel está desbloqueado si es el 1 o el anterior está completado. */
    public boolean isUnlocked(int levelId) {
        return levelId <= 1 || isCompleted(levelId - 1);
    }

    public boolean hasUpgrade(String id) {
        return upgrades.contains(id, false);
    }

    public int buildingLevel(String id) {
        return buildings.get(id, 0);
    }

    /** Registra la victoria y abona cristales y medallas (solo estrellas nuevas). */
    public LevelReward completeLevel(int levelId, int stars, int baseReward, int firstClearBonus, int goldmineIncome) {
        int prevStars = starsFor(levelId);
        boolean first = prevStars == 0;
        LevelReward r = new LevelReward();
        r.crystals = Balance.metaReward(baseReward, stars) + goldmineIncome + (first ? firstClearBonus : 0);
        r.medals = Math.max(0, stars - prevStars);
        String key = String.valueOf(levelId);
        levels.put(key, Math.max(prevStars, stars));
        metaCurrency += r.crystals;
        medals += r.medals;
        return r;
    }

    /** Coste base de cada nodo del árbol ANTIGUO (v<=3), para el reembolso de la v4. */
    private static final ObjectIntMap<String> OLD_TREE_COSTS = new ObjectIntMap<>();

    static {
        String[][] old = {
            {"dmg1", "60"}, {"rate1", "100"}, {"dmg2", "140"}, {"rate2", "240"}, {"dmg3", "300"},
            {"fus_artillery", "260"}, {"dmg4", "620"}, {"rate3", "520"}, {"dmg5", "900"},
            {"overcharge", "1400"}, {"eco1", "70"}, {"sell1", "90"}, {"gold1", "120"},
            {"eco2", "240"}, {"gold2", "320"}, {"eco3", "560"}, {"early1", "300"},
            {"eco4", "950"}, {"gold3", "700"}, {"fus_venom", "340"}, {"range1", "110"},
            {"range2", "300"}, {"lives1", "160"}, {"fus_gatling", "420"}, {"lives2", "420"},
            {"range3", "650"}, {"lives3", "800"}, {"fus_railgun", "520"}, {"fortress", "1200"},
            {"ability_meteor", "220"}, {"ability_frost", "400"}, {"ability_gold", "600"},
            {"fus_incendiary", "150"}, {"fus_stinger", "150"}, {"fus_frost", "220"},
            {"masterforge", "750"}, {"warempire", "1600"}, {"unlock_command", "700"},
            // ids del árbol intermedio (fase de desbloqueos): la primera v4 no
            // los conocía y los borró sin reembolso — por eso existe la v5
            {"unlock_poison", "80"}, {"unlock_flamethrower", "160"}, {"unlock_sniper", "260"},
            {"unlock_laser", "380"}, {"unlock_tesla", "450"}, {"unlock_saw", "60"},
            {"saw_dmg", "130"}, {"cannon_dmg", "220"}, {"mg_rate", "320"}, {"tesla_dmg", "550"},
            {"slow_range", "100"}, {"poison_range", "180"}, {"mortar_dmg", "300"},
            {"sniper_rate", "420"}, {"laser_dmg", "550"}, {"relocate", "250"},
            {"direct_fab", "480"}, {"flame_range", "350"}, {"fus_toxic", "300"},
            {"fus_ice", "320"}, {"fus_prism", "550"}
        };
        for (String[] pair : old) OLD_TREE_COSTS.put(pair[0], Integer.parseInt(pair[1]));
    }

    /** Cristales de compensación de la v5 (el respec roto de la v4). */
    public static final int V5_COMPENSATION = 1200;

    /** Migración de guardados antiguos. */
    public boolean migrate() {
        if (version >= 5) return false;
        if (version < 3) {
            // sin medallas: 1 medalla por estrella ya conseguida
            int total = 0;
            for (ObjectIntMap.Entry<String> e : levels.entries()) total += e.value;
            medals += total;
        }
        if (version < 4 && upgrades.size > 0) {
            // v4: árbol rediseñado de arriba abajo → respec con reembolso
            int refund = 0;
            for (String id : upgrades) refund += OLD_TREE_COSTS.get(id, 0);
            metaCurrency += refund;
            upgrades.clear();
        } else if (version == 4) {
            // v5: la migración v4 original vació nodos que no estaban en su
            // tabla (los del árbol intermedio) SIN devolver los cristales.
            // Imposible saber cuáles eran: compensación fija y generosa.
            metaCurrency += V5_COMPENSATION;
        }
        version = 5;
        return true;
    }
}
