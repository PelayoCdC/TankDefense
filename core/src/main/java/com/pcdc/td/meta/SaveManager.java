package com.pcdc.td.meta;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

/** Guardado local con escritura atómica (tmp + rename). */
public class SaveManager {

    private final Json json = new Json();

    /**
     * Partida de PRUEBAS: cualquier flag -Dtd.* (nivel forzado, tanques
     * automáticos, fusiones abiertas, escala de UI...) cambia el guardado a un
     * fichero aparte. Sin esto, una prueba podía terminar un nivel y escribir
     * recompensas —o un checkpoint— en el perfil real del jugador, que es
     * exactamente lo que pasó varias veces durante el desarrollo.
     */
    public static final boolean DEBUG_PROFILE = detectDebugFlags();

    private static boolean detectDebugFlags() {
        try {
            for (Object key : System.getProperties().keySet()) {
                String k = String.valueOf(key);
                // los flags del simulador son de herramientas sin perfil
                if (k.startsWith("td.") && !k.startsWith("td.sim.")) return true;
            }
        } catch (SecurityException ignored) {
            // sin acceso a las propiedades: se asume partida normal
        }
        return false;
    }

    /** Nombre base del guardado: el de pruebas nunca pisa al del jugador. */
    private static String baseName() {
        return DEBUG_PROFILE ? "save-debug.json" : "save.json";
    }

    public SaveManager() {
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);
    }

    /**
     * Android: almacenamiento interno de la app. Desktop: carpeta del usuario
     * (el working dir del run de desktop es assets/, que no debe ensuciarse).
     */
    private FileHandle saveFile(String suffix) {
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            return Gdx.files.local("save/" + baseName() + suffix);
        }
        return Gdx.files.external(".towerdefense/" + baseName() + suffix);
    }

    /**
     * Carga el perfil. Si el fichero principal falta o está corrupto se
     * intenta la COPIA DE RESPALDO antes de rendirse: perder la partida en
     * silencio por un guardado a medias es el peor fallo posible.
     */
    public PlayerProfile load() {
        PlayerProfile profile = tryLoad(saveFile(""));
        if (profile != null) return profile;
        profile = tryLoad(saveFile(".bak"));
        if (profile != null) {
            Gdx.app.error("SaveManager", "guardado principal ilegible: restaurado el respaldo");
            save(profile); // deja el principal sano otra vez
            return profile;
        }
        return new PlayerProfile();
    }

    /** @return el perfil del fichero, o null si no existe o no se puede leer. */
    private PlayerProfile tryLoad(FileHandle file) {
        try {
            if (!file.exists()) return null;
            PlayerProfile profile = json.fromJson(PlayerProfile.class, file);
            // un JSON vacío deserializa a un perfil sin niveles: no vale como bueno
            return profile != null && profile.levels != null ? profile : null;
        } catch (Exception e) {
            Gdx.app.error("SaveManager", "guardado ilegible: " + file.name(), e);
            return null;
        }
    }

    // ---- checkpoint de partida a mitad de nivel ----

    private FileHandle checkpointFile() {
        String name = DEBUG_PROFILE ? "checkpoint-debug.json" : "checkpoint.json";
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            return Gdx.files.local("save/" + name);
        }
        return Gdx.files.external(".towerdefense/" + name);
    }

    public void saveCheckpoint(Checkpoint cp) {
        try {
            Json j = new Json(com.badlogic.gdx.utils.JsonWriter.OutputType.json);
            j.setElementType(Checkpoint.class, "towers", Checkpoint.TowerState.class);
            FileHandle f = checkpointFile();
            f.parent().mkdirs();
            f.writeString(j.toJson(cp), false, "UTF-8");
        } catch (Exception e) {
            Gdx.app.error("SaveManager", "checkpoint", e);
        }
    }

    /** @return el checkpoint pendiente, o null. */
    public Checkpoint loadCheckpoint() {
        try {
            FileHandle f = checkpointFile();
            if (!f.exists()) return null;
            Json j = new Json();
            j.setIgnoreUnknownFields(true);
            j.setElementType(Checkpoint.class, "towers", Checkpoint.TowerState.class);
            return j.fromJson(Checkpoint.class, f);
        } catch (Exception e) {
            Gdx.app.error("SaveManager", "checkpoint corrupto", e);
            return null;
        }
    }

    public void clearCheckpoint() {
        try {
            checkpointFile().delete();
        } catch (Exception ignored) {
        }
    }

    public void save(PlayerProfile profile) {
        try {
            FileHandle tmp = saveFile(".tmp");
            tmp.parent().mkdirs();
            tmp.writeString(json.prettyPrint(profile), false, "UTF-8");
            // el guardado anterior pasa a ser el respaldo, PERO solo si se puede
            // leer: copiar un fichero corrupto encima destruiría la única copia
            // buena que queda (pasó al probar la recuperación)
            FileHandle current = saveFile("");
            if (current.exists() && tryLoad(current) != null) {
                try {
                    current.copyTo(saveFile(".bak"));
                } catch (Exception e) {
                    Gdx.app.error("SaveManager", "no se pudo respaldar", e);
                }
            }
            java.io.File target = saveFile("").file();
            if (target.exists() && !target.delete()) {
                Gdx.app.error("SaveManager", "no se pudo reemplazar el guardado anterior");
            }
            if (!tmp.file().renameTo(target)) {
                // último recurso: escritura directa
                saveFile("").writeString(json.prettyPrint(profile), false, "UTF-8");
            }
        } catch (Exception e) {
            Gdx.app.error("SaveManager", "no se pudo guardar", e);
        }
    }
}
