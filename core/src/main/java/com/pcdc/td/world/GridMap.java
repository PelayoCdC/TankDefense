package com.pcdc.td.world;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Grid del nivel. Los caminos se definen por esquinas (waypoints) en
 * coordenadas de INTERSECCIÓN de celdas (rango 0..width / 0..height): la calle
 * ocupa las 2 celdas a ambos lados de la línea central, de modo que caben
 * varios enemigos en paralelo.
 */
public class GridMap {

    public final int width;
    public final int height;
    private final CellType[] cells;

    /** Waypoints de cada camino en coordenadas de mundo (línea central, y=0). */
    public final Array<Array<Vector3>> worldPaths = new Array<>();
    /** Atajo de los voladores por camino: entrada → salida en línea recta. */
    public final Array<Array<Vector3>> flyerPaths = new Array<>();

    public GridMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.cells = new CellType[width * height];
        for (int i = 0; i < cells.length; i++) cells[i] = CellType.GROUND;
    }

    public boolean inBounds(int x, int z) {
        return x >= 0 && x < width && z >= 0 && z < height;
    }

    public CellType get(int x, int z) {
        return cells[z * width + x];
    }

    public void set(int x, int z, CellType type) {
        cells[z * width + x] = type;
    }

    private void markPath(int x, int z) {
        if (inBounds(x, z)) set(x, z, CellType.PATH);
    }

    public boolean isBuildable(int x, int z) {
        return inBounds(x, z) && get(x, z) == CellType.GROUND;
    }

    /**
     * Añade un camino por esquinas [[x,z],...] en coordenadas de intersección;
     * marca una calle de 2 celdas de ancho y guarda la línea central.
     */
    /** Cuánto se prolonga el camino fuera del tablero (para anticipar enemigos). */
    public static final float PATH_EXTENSION = 4.5f;

    public void addPath(int[][] corners) {
        Array<Vector3> world = new Array<>();
        for (int[] c : corners) {
            world.add(new Vector3(c[0], 0f, c[1]));
        }

        // el camino nace y muere en la zona exterior no jugable
        if (world.size >= 2) {
            Vector3 entryDir = new Vector3(world.get(0)).sub(world.get(1)).nor();
            world.insert(0, new Vector3(world.first()).mulAdd(entryDir, PATH_EXTENSION));
            Vector3 exitDir = new Vector3(world.peek()).sub(world.get(world.size - 2)).nor();
            world.add(new Vector3(world.peek()).mulAdd(exitDir, PATH_EXTENSION));
        }
        worldPaths.add(world);

        // los voladores atajan, pero cruzando el interior del tablero: pasan por
        // el punto medio del camino terrestre (la recta pura podía quedar fuera
        // del alcance de todas las torres en rutas en U)
        Array<Vector3> flyer = new Array<>();
        flyer.add(new Vector3(world.first()));
        flyer.add(new Vector3(world.get(world.size / 2)));
        flyer.add(new Vector3(world.peek()));
        flyerPaths.add(flyer);

        for (int i = 0; i < corners.length - 1; i++) {
            int ax = corners[i][0], az = corners[i][1];
            int bx = corners[i + 1][0], bz = corners[i + 1][1];
            if (az == bz) {
                // tramo horizontal: filas z-1 y z
                for (int x = Math.min(ax, bx); x < Math.max(ax, bx); x++) {
                    markPath(x, az - 1);
                    markPath(x, az);
                }
            } else {
                // tramo vertical: columnas x-1 y x
                for (int z = Math.min(az, bz); z < Math.max(az, bz); z++) {
                    markPath(ax - 1, z);
                    markPath(ax, z);
                }
            }
        }
        // esquinas redondeadas: bloque 2x2 en cada waypoint
        for (int[] c : corners) {
            markPath(c[0] - 1, c[1] - 1);
            markPath(c[0], c[1] - 1);
            markPath(c[0] - 1, c[1]);
            markPath(c[0], c[1]);
        }
    }

    public void block(int x, int z) {
        if (inBounds(x, z) && get(x, z) == CellType.GROUND) set(x, z, CellType.BLOCKED);
    }

    /** Centro de la celda en coordenadas de mundo. */
    public static Vector3 cellCenter(int x, int z, Vector3 out) {
        return out.set(x + 0.5f, 0f, z + 0.5f);
    }

    public static GridPoint2 worldToCell(Vector3 world, GridPoint2 out) {
        out.set((int) Math.floor(world.x), (int) Math.floor(world.z));
        return out;
    }
}
