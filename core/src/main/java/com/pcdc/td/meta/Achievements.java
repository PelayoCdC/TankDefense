package com.pcdc.td.meta;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/** Logros sencillos: umbral sobre una estadística del perfil, recompensa en cristales. */
public final class Achievements {

    public static final class Def {
        public final String id;
        public final String statKey;
        public final int target;
        public final int reward;

        Def(String id, String statKey, int target, int reward) {
            this.id = id;
            this.statKey = statKey;
            this.target = target;
            this.reward = reward;
        }
    }

    public static final Array<Def> ALL = new Array<>();

    static {
        ALL.add(new Def("kills100", "kills", 100, 30));
        ALL.add(new Def("kills1000", "kills", 1000, 120));
        ALL.add(new Def("kills5000", "kills", 5000, 400));
        ALL.add(new Def("fus5", "fusions", 5, 50));
        ALL.add(new Def("fus25", "fusions", 25, 200));
        ALL.add(new Def("wins10", "wins", 10, 60));
        ALL.add(new Def("wins50", "wins", 50, 300));
        ALL.add(new Def("perfect10", "perfects", 10, 150));
        ALL.add(new Def("perfect25", "perfects", 25, 350));
        ALL.add(new Def("sold20", "sold", 20, 40));
        ALL.add(new Def("wave25", "bestWave", 25, 200));
        ALL.add(new Def("wave40", "bestWave", 40, 400));
        // contenido de las últimas fases: campaña, élites, campeones, sinergias
        ALL.add(new Def("campaign", "campaignDone", 1, 500));
        ALL.add(new Def("elites25", "elites", 25, 150));
        ALL.add(new Def("champions5", "champions", 5, 120));
        ALL.add(new Def("synergy10", "synergies", 10, 100));
    }

    /**
     * Logros que además regalan una PINTURA de trofeo: los cristales se gastan
     * y desaparecen, la pintura se ve en cada partida.
     */
    private static final ObjectMap<String, String> SKIN_REWARDS = new ObjectMap<>();

    static {
        SKIN_REWARDS.put("kills5000", "hunter");
        SKIN_REWARDS.put("fus25", "alchemist");
        SKIN_REWARDS.put("wave40", "survivor");
    }

    /** Pintura que regala este logro, o null. */
    public static String skinReward(String achievementId) {
        return SKIN_REWARDS.get(achievementId);
    }

    private Achievements() {
    }

    /** Reclama los logros recién completados (abona cristales) y los devuelve. */
    public static Array<Def> claim(PlayerProfile profile) {
        Array<Def> unlocked = new Array<>();
        for (Def def : ALL) {
            if (profile.achievements.contains(def.id, false)) continue;
            if (profile.stat(def.statKey) < def.target) continue;
            profile.achievements.add(def.id);
            profile.metaCurrency += def.reward;
            String skin = SKIN_REWARDS.get(def.id);
            if (skin != null && !profile.ownsSkin(skin)) profile.skins.add(skin);
            unlocked.add(def);
        }
        return unlocked;
    }
}
