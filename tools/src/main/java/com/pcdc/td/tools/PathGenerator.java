package com.pcdc.td.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Genera caminos rectilíneos (listas de esquinas) según plantillas. Las
 * esquinas están en coordenadas de INTERSECCIÓN (0..w, 0..h): la calle ocupa
 * las 2 celdas a ambos lados de la línea central (ver GridMap.addPath).
 *
 * Todos los caminos entran y salen por un BORDE del tablero (el portal y la
 * fortaleza se dibujan fuera con PATH_EXTENSION): ninguna plantilla puede
 * terminar en mitad del tablero.
 */
public final class PathGenerator {

    public enum Template { KINKS, SERPENTINE, HORSESHOE, ZIGZAG, LONG_S, COIL, COMB, DOUBLE, DOUBLE_V }

    private PathGenerator() {
    }

    /**
     * Elige plantilla según nivel, tamaño y semilla. Las plantillas grandes
     * (COIL, LONG_S, dobles) solo entran cuando el grid les da espacio, y cada
     * una pesa distinto para que ninguna sature la campaña.
     *
     * @param avoid plantillas de los niveles inmediatamente anteriores: no se
     *              repiten salvo que no quede otra. Sin esto la campaña
     *              encadenaba TRECE niveles seguidos con el mismo trazado.
     */
    public static Template pickTemplate(int level, int w, int h, Random rng, List<Template> avoid) {
        List<Template> options = new ArrayList<>();
        // las básicas siempre disponibles (1 papeleta cada una)
        options.add(Template.KINKS);
        options.add(Template.HORSESHOE);
        options.add(Template.ZIGZAG);
        if (h >= 8) options.add(Template.SERPENTINE);
        if (w >= 11 && h >= 9) options.add(Template.COMB);
        // con alto <10 las tres pasadas de la S se funden en una plaza sin huecos
        if (w >= 12 && h >= 10 && level >= 8) options.add(Template.LONG_S);
        if (w >= 12 && h >= 9 && level >= 13) options.add(Template.COIL);
        if (w >= 10 && h >= 9 && level >= 16) {
            options.add(Template.DOUBLE);
            options.add(Template.DOUBLE_V);
            if (level >= 30) { // en la segunda mitad los dobles pesan más
                options.add(Template.DOUBLE);
                options.add(Template.DOUBLE_V);
            }
        }
        if (avoid != null) {
            List<Template> fresh = new ArrayList<>();
            for (Template t : options) {
                if (!avoid.contains(t)) fresh.add(t);
            }
            if (!fresh.isEmpty()) options = fresh;
        }
        return options.get(rng.nextInt(options.size()));
    }

    public static Template pickTemplate(int level, int w, int h, Random rng) {
        return pickTemplate(level, w, h, rng, null);
    }

    /** @return lista de caminos; cada camino es una lista de esquinas [x,z]. */
    public static List<int[][]> generate(Template template, int w, int h, Random rng) {
        List<int[][]> paths = new ArrayList<>();
        switch (template) {
            case KINKS -> paths.add(kinks(w, h, rng));
            case SERPENTINE -> paths.add(serpentine(w, h, rng));
            case HORSESHOE -> paths.add(horseshoe(w, h, rng));
            case ZIGZAG -> paths.add(zigzag(w, h, rng));
            case LONG_S -> paths.add(longS(w, h, rng));
            case COIL -> paths.add(coil(w, h, rng));
            case COMB -> paths.add(comb(w, h, rng));
            case DOUBLE -> {
                // dos entradas por la izquierda que confluyen en un punto aleatorio
                int zc = h / 2;
                int spread = 2 + rng.nextInt(Math.max(1, h / 2 - 2));
                int zTop = Math.max(1, zc - spread);
                int zBot = Math.min(h - 1, zc + spread);
                int xm1 = w / 3 + rng.nextInt(3) - 1;
                int xm2 = 2 * w / 3 + rng.nextInt(3) - 1;
                paths.add(new int[][]{{0, zTop}, {xm1, zTop}, {xm1, zc}, {w, zc}});
                paths.add(new int[][]{{0, zBot}, {xm2, zBot}, {xm2, zc}, {w, zc}});
            }
            case DOUBLE_V -> {
                // una entrada lateral y otra por el borde superior o inferior
                int zc = 2 + rng.nextInt(Math.max(1, h - 4));
                boolean fromTop = rng.nextBoolean();
                int xv = w / 3 + rng.nextInt(Math.max(1, w / 3));
                paths.add(new int[][]{{0, zc}, {w, zc}});
                paths.add(fromTop
                    ? new int[][]{{xv, 0}, {xv, zc}, {w, zc}}
                    : new int[][]{{xv, h}, {xv, zc}, {w, zc}});
            }
        }
        // espejos: el MISMO volteo para todos los caminos del nivel (si no,
        // los dobles dejarían de confluir). Cuadruplica la variedad gratis:
        // el portal deja de estar siempre a la izquierda.
        boolean flipZ = rng.nextBoolean();
        boolean flipX = rng.nextBoolean();
        for (int[][] path : paths) {
            for (int[] c : path) {
                if (flipZ) c[1] = h - c[1];
                if (flipX) c[0] = w - c[0];
            }
        }
        for (int[][] path : paths) validate(path, w, h);
        return paths;
    }

    private static int[][] kinks(int w, int h, Random rng) {
        List<int[]> pts = new ArrayList<>();
        int z = 2 + rng.nextInt(h - 3);
        add(pts, 0, z);
        int detours = 1 + rng.nextInt(2);
        int x = 2 + rng.nextInt(2);
        for (int d = 0; d < detours && x <= w - 3; d++) {
            add(pts, x, z);
            int newZ;
            do {
                newZ = 1 + rng.nextInt(h - 1);
            } while (Math.abs(newZ - z) < 2);
            z = newZ;
            add(pts, x, z);
            x += 3 + rng.nextInt(Math.max(1, (w - 3 - x) / Math.max(1, detours - d)));
        }
        add(pts, w, z);
        return pts.toArray(new int[0][]);
    }

    private static int[][] serpentine(int w, int h, Random rng) {
        List<int[]> pts = new ArrayList<>();
        int step = 3;
        int z = 1 + rng.nextInt(2);
        boolean rightward = true;
        add(pts, 0, z);
        while (z + step <= h - 1) {
            int xFar = rightward ? w - 2 : 2;
            add(pts, xFar, z);
            z += step;
            add(pts, xFar, z);
            rightward = !rightward;
        }
        add(pts, rightward ? w : 0, z);
        return pts.toArray(new int[0][]);
    }

    private static int[][] horseshoe(int w, int h, Random rng) {
        int z0 = 1 + rng.nextInt(2);
        int z1 = h - 1 - rng.nextInt(2);
        int xr = w - 2 - rng.nextInt(2);
        return new int[][]{{0, z0}, {xr, z0}, {xr, z1}, {0, z1}};
    }

    /** Escalera diagonal: tramos cortos alternando derecha y abajo/arriba. */
    private static int[][] zigzag(int w, int h, Random rng) {
        List<int[]> pts = new ArrayList<>();
        boolean downward = rng.nextBoolean();
        int z = downward ? 1 + rng.nextInt(2) : h - 1 - rng.nextInt(2);
        add(pts, 0, z);
        int x = 0;
        while (x < w - 3) {
            x += 3 + rng.nextInt(2);
            x = Math.min(x, w);
            add(pts, x, z);
            if (x >= w) break;
            int dz = downward ? 3 : -3;
            int nz = z + dz;
            if (nz < 1 || nz > h - 1) {
                downward = !downward; // rebota en el borde y sigue escalonando
                nz = z + (downward ? 3 : -3);
                nz = Math.max(1, Math.min(h - 1, nz));
            }
            if (nz != z) {
                z = nz;
                add(pts, x, z);
            }
        }
        add(pts, w, z);
        return pts.toArray(new int[0][]);
    }

    /** "S" grande que recorre el tablero entero en tres pasadas. */
    private static int[][] longS(int w, int h, Random rng) {
        int zTop = 1 + rng.nextInt(2);
        int zMid = h / 2;
        int zBot = h - 1;
        int xr = w - 2, xl = 2;
        return new int[][]{
            {0, zTop}, {xr, zTop}, {xr, zMid}, {xl, zMid}, {xl, zBot}, {w, zBot}
        };
    }

    /**
     * Bobina: rodea el tablero y sale cruzando por el centro. El cruce con la
     * columna derecha es una intersección visual, los enemigos siguen sus
     * waypoints. Sustituye a la vieja SPIRAL, que terminaba en mitad del mapa.
     */
    private static int[][] coil(int w, int h, Random rng) {
        // los márgenes varían: sin esto la bobina salía CALCADA en cada nivel
        int margin = 1 + rng.nextInt(2);
        int inner = margin + 1 + rng.nextInt(2);
        int exitZ = Math.min(h - 2, inner + 2 + rng.nextInt(2));
        return new int[][]{
            {0, margin}, {w - margin - 1, margin}, {w - margin - 1, h - margin},
            {inner, h - margin}, {inner, exitZ}, {w, exitZ}
        };
    }

    /**
     * Peine: entra por un lado y sube y baja entre púas antes de salir por el
     * otro. Deja bolsas profundas donde el fuego en área luce.
     */
    private static int[][] comb(int w, int h, Random rng) {
        List<int[]> pts = new ArrayList<>();
        int top = 1 + rng.nextInt(2);
        int bottom = h - 1 - rng.nextInt(2);
        int teeth = 2 + rng.nextInt(2);
        int step = Math.max(3, (w - 2) / (teeth + 1));
        int z = top;
        add(pts, 0, z);
        int x = step;
        for (int t = 0; t < teeth && x < w - 2; t++) {
            add(pts, x, z);
            z = (z == top) ? bottom : top;
            add(pts, x, z);
            x += step;
        }
        add(pts, w, z);
        return pts.toArray(new int[0][]);
    }

    private static void add(List<int[]> pts, int x, int z) {
        if (!pts.isEmpty()) {
            int[] last = pts.get(pts.size() - 1);
            if (last[0] == x && last[1] == z) return;
        }
        pts.add(new int[]{x, z});
    }

    private static void validate(int[][] path, int w, int h) {
        for (int i = 0; i < path.length; i++) {
            int x = path[i][0], z = path[i][1];
            if (x < 0 || x > w || z < 0 || z > h) {
                throw new IllegalStateException("Esquina fuera de límites: (" + x + "," + z + ") en grid " + w + "x" + h);
            }
            if (i > 0) {
                boolean sameX = path[i - 1][0] == x;
                boolean sameZ = path[i - 1][1] == z;
                if (sameX == sameZ) {
                    throw new IllegalStateException("Tramo no rectilíneo entre esquinas " + (i - 1) + " y " + i);
                }
            }
        }
    }

    /** Marca las celdas de calle (mismo rasterizado 2-ancho que GridMap.addPath). */
    public static boolean[][] rasterize(List<int[][]> paths, int w, int h) {
        boolean[][] cells = new boolean[w][h];
        for (int[][] path : paths) {
            for (int i = 0; i < path.length - 1; i++) {
                int ax = path[i][0], az = path[i][1];
                int bx = path[i + 1][0], bz = path[i + 1][1];
                if (az == bz) {
                    for (int x = Math.min(ax, bx); x < Math.max(ax, bx); x++) {
                        mark(cells, w, h, x, az - 1);
                        mark(cells, w, h, x, az);
                    }
                } else {
                    for (int z = Math.min(az, bz); z < Math.max(az, bz); z++) {
                        mark(cells, w, h, ax - 1, z);
                        mark(cells, w, h, ax, z);
                    }
                }
            }
            for (int[] c : path) {
                mark(cells, w, h, c[0] - 1, c[1] - 1);
                mark(cells, w, h, c[0], c[1] - 1);
                mark(cells, w, h, c[0] - 1, c[1]);
                mark(cells, w, h, c[0], c[1]);
            }
        }
        return cells;
    }

    private static void mark(boolean[][] cells, int w, int h, int x, int z) {
        if (x >= 0 && x < w && z >= 0 && z < h) cells[x][z] = true;
    }
}
