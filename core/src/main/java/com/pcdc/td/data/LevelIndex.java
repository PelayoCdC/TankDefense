package com.pcdc.td.data;

import com.badlogic.gdx.utils.Array;

/** Índice ligero de niveles (levels_index.json) para no abrir 100 ficheros. */
public class LevelIndex {

    public static class Entry {
        public int id;
        public String name;
        public String biome;
        public String biomeName;
    }

    public Array<Entry> levels = new Array<>();
}
