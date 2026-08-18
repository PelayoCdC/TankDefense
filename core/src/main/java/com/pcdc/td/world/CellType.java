package com.pcdc.td.world;

/** Tipo de cada celda del grid. 1 unidad de mundo = 1 celda. */
public enum CellType {
    GROUND,   // construible
    PATH,     // camino de enemigos, no construible
    BLOCKED   // obstáculo decorativo, no construible
}
