package com.pcdc.td.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

/** Textos localizados (assets/i18n/<código>.json). */
public final class Lang {

    /** Idiomas soportados: código y nombre en su propia lengua. */
    public static final String[][] AVAILABLE = {
        {"es", "Español"}, {"en", "English"}, {"pt", "Português"}, {"fr", "Français"}
    };

    private static final ObjectMap<String, String> strings = new ObjectMap<>();
    private static String code = "es";

    private Lang() {
    }

    public static void load() {
        code = Gdx.app.getPreferences("td-settings").getString("lang", "es");
        reload();
    }

    public static void setLanguage(String newCode) {
        code = newCode;
        Gdx.app.getPreferences("td-settings").putString("lang", code).flush();
        reload();
    }

    public static String code() {
        return code;
    }

    /**
     * Locale del idioma ACTIVO, para formatear números. Sin esto los decimales
     * seguían al sistema operativo, no al idioma elegido: "RNG 3,4" en un
     * Windows español con el juego en inglés (y al revés en Android).
     */
    public static java.util.Locale locale() {
        switch (code) {
            case "en": return java.util.Locale.ENGLISH;
            case "fr": return java.util.Locale.FRENCH;
            case "pt": return PT_LOCALE;
            default: return ES_LOCALE;
        }
    }

    private static final java.util.Locale PT_LOCALE = new java.util.Locale("pt", "PT");

    private static final java.util.Locale ES_LOCALE = new java.util.Locale("es", "ES");

    private static void reload() {
        strings.clear();
        JsonValue root = new JsonReader().parse(Gdx.files.internal("i18n/" + code + ".json"));
        for (JsonValue e = root.child; e != null; e = e.next) {
            strings.put(e.name, e.asString());
        }
    }

    public static String get(String key) {
        return strings.get(key, key);
    }

    public static String fmt(String key, Object... args) {
        return String.format(locale(), get(key), args);
    }

    /** Nombre localizado de una entidad de datos ("tower.cannon"), con fallback. */
    public static String name(String type, String id, String fallback) {
        return strings.get(type + "." + id, fallback);
    }
}
