package com.pcdc.td.tools;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.pcdc.td.meta.PlayerProfile;

/** Comprueba que PlayerProfile sobrevive a un ciclo JSON (sin Gdx runtime). */
public class SaveRoundTripCheck {

    public static void main(String[] args) {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);

        PlayerProfile p = new PlayerProfile();
        p.metaCurrency = 123;
        p.levels.put("1", 3);
        p.levels.put("2", 2);
        p.upgrades.add("dmg1");
        p.buildings.put("goldmine", 2);
        p.lastLevelPlayed = 3;

        String s = json.prettyPrint(p);
        System.out.println(s);
        PlayerProfile q = json.fromJson(PlayerProfile.class, s);
        boolean ok = q.metaCurrency == 123
            && q.starsFor(1) == 3 && q.starsFor(2) == 2
            && q.hasUpgrade("dmg1")
            && q.buildingLevel("goldmine") == 2
            && q.lastLevelPlayed == 3;
        System.out.println(ok ? "ROUNDTRIP OK" : "ROUNDTRIP FAILED");
        if (!ok) System.exit(1);
    }
}
