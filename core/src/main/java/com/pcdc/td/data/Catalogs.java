package com.pcdc.td.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

/** Carga y expone los catálogos JSON compartidos por el juego y el generador. */
public class Catalogs {

    public static class TowerCatalog {
        public Array<TowerDef> towers = new Array<>();
    }

    public static class EnemyCatalog {
        public Array<EnemyDef> enemies = new Array<>();
    }

    public static class FusionCatalog {
        public Array<FusionDef> fusions = new Array<>();
    }

    public static class BiomeCatalog {
        public Array<BiomeDef> biomes = new Array<>();
    }

    public static class UpgradeCatalog {
        public Array<UpgradeNodeDef> nodes = new Array<>();
    }

    public static class BuildingCatalog {
        public Array<BuildingDef> buildings = new Array<>();
    }

    public final Array<TowerDef> towers = new Array<>();
    public final Array<EnemyDef> enemies = new Array<>();
    public final Array<FusionDef> fusions = new Array<>();
    public final Array<BiomeDef> biomes = new Array<>();
    public final Array<UpgradeNodeDef> upgrades = new Array<>();
    public final Array<BuildingDef> buildings = new Array<>();

    private final ObjectMap<String, TowerDef> towersById = new ObjectMap<>();
    private final ObjectMap<String, EnemyDef> enemiesById = new ObjectMap<>();
    private final ObjectMap<String, BiomeDef> biomesById = new ObjectMap<>();

    public static Json newJson() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        json.setElementType(TowerCatalog.class, "towers", TowerDef.class);
        json.setElementType(EnemyCatalog.class, "enemies", EnemyDef.class);
        json.setElementType(FusionCatalog.class, "fusions", FusionDef.class);
        json.setElementType(BiomeCatalog.class, "biomes", BiomeDef.class);
        json.setElementType(UpgradeCatalog.class, "nodes", UpgradeNodeDef.class);
        json.setElementType(UpgradeNodeDef.class, "requires", String.class);
        json.setElementType(BuildingCatalog.class, "buildings", BuildingDef.class);
        json.setElementType(BuildingDef.class, "levels", BuildingDef.LevelDefEntry.class);
        json.setElementType(LevelDef.class, "tutorial", LevelDef.TutorialStepDef.class);
        json.setElementType(TowerDef.class, "effects", EffectDef.class);
        json.setElementType(LevelDef.class, "paths", LevelDef.PathDef.class);
        json.setElementType(LevelDef.class, "waves", LevelDef.WaveDef.class);
        json.setElementType(LevelDef.WaveDef.class, "groups", LevelDef.SpawnGroupDef.class);
        json.setElementType(LevelIndex.class, "levels", LevelIndex.Entry.class);
        return json;
    }

    public void load() {
        loadFromStrings(
            Gdx.files.internal("data/towers.json").readString("UTF-8"),
            Gdx.files.internal("data/enemies.json").readString("UTF-8"),
            Gdx.files.internal("data/fusions.json").readString("UTF-8"),
            Gdx.files.internal("data/biomes.json").readString("UTF-8"),
            Gdx.files.internal("data/upgrades.json").readString("UTF-8"),
            Gdx.files.internal("data/buildings.json").readString("UTF-8"));
        Gdx.app.log("Catalogs", "cargadas " + towers.size + " torres, " + enemies.size
            + " enemigos, " + fusions.size + " fusiones");
    }

    /** Carga desde strings JSON; utilizable sin runtime de libGDX (tools/tests). */
    public void loadFromStrings(String towersJson, String enemiesJson, String fusionsJson,
                                String biomesJson, String upgradesJson, String buildingsJson) {
        Json json = newJson();
        TowerCatalog tc = json.fromJson(TowerCatalog.class, towersJson);
        EnemyCatalog ec = json.fromJson(EnemyCatalog.class, enemiesJson);
        FusionCatalog fc = json.fromJson(FusionCatalog.class, fusionsJson);
        BiomeCatalog bc = json.fromJson(BiomeCatalog.class, biomesJson);
        UpgradeCatalog uc = json.fromJson(UpgradeCatalog.class, upgradesJson);
        BuildingCatalog bdc = json.fromJson(BuildingCatalog.class, buildingsJson);

        upgrades.clear();
        upgrades.addAll(uc.nodes);
        buildings.clear();
        buildings.addAll(bdc.buildings);

        towers.clear();
        towers.addAll(tc.towers);
        enemies.clear();
        enemies.addAll(ec.enemies);
        fusions.clear();
        fusions.addAll(fc.fusions);
        biomes.clear();
        biomes.addAll(bc.biomes);

        towersById.clear();
        for (TowerDef t : towers) towersById.put(t.id, t);
        enemiesById.clear();
        for (EnemyDef e : enemies) enemiesById.put(e.id, e);
        biomesById.clear();
        for (BiomeDef b : biomes) biomesById.put(b.id, b);
    }

    public TowerDef tower(String id) {
        TowerDef def = towersById.get(id);
        if (def == null) throw new IllegalArgumentException("Torre desconocida: " + id);
        return def;
    }

    public EnemyDef enemy(String id) {
        EnemyDef def = enemiesById.get(id);
        if (def == null) throw new IllegalArgumentException("Enemigo desconocido: " + id);
        return def;
    }

    public BiomeDef biome(String id) {
        BiomeDef def = biomesById.get(id);
        return def != null ? def : biomesById.get("meadow");
    }

    /** Receta cuya torre resultado es esta, o null si no es una fusión. */
    public FusionDef fusionByResult(String resultId) {
        for (FusionDef f : fusions) {
            if (f.result.equals(resultId)) return f;
        }
        return null;
    }

    /** Receta que produce el resultado de fusionar estas dos torres, o null. */
    public FusionDef findFusion(String towerA, String towerB) {
        for (FusionDef f : fusions) {
            if (f.matches(towerA, towerB)) return f;
        }
        return null;
    }

    public LevelIndex loadLevelIndex() {
        return newJson().fromJson(LevelIndex.class, Gdx.files.internal("levels/levels_index.json"));
    }

    public LevelDef loadLevel(int id) {
        String path = String.format("levels/level_%03d.json", id);
        return newJson().fromJson(LevelDef.class, Gdx.files.internal(path));
    }
}
