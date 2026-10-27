package com.pcdc.td.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ConeShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.pcdc.td.data.BiomeDef;
import com.pcdc.td.data.EffectDef;
import com.pcdc.td.data.EnemyDef;
import com.pcdc.td.data.TowerDef;
import com.pcdc.td.world.CellType;
import com.pcdc.td.world.GridMap;

/**
 * Única fuente de "arte" del juego: todos los Models se generan por código con
 * primitivas y quedan cacheados aquí hasta dispose().
 */
public class ModelFactory implements Disposable {

    private static final long ATTRS = Usage.Position | Usage.Normal;

    /**
     * Skin de pintura activa de las torres (perfil del jugador). Fijarla ANTES
     * de crear modelos de torre; cada pantalla crea su propio ModelFactory.
     */
    public static String activeSkin = "classic";

    private final ModelBuilder builder = new ModelBuilder();
    private final com.badlogic.gdx.math.Matrix4 tmpTransform = new com.badlogic.gdx.math.Matrix4();
    private final Array<Model> models = new Array<>();
    private final ObjectMap<String, Model> towerCache = new ObjectMap<>();
    private final ObjectMap<String, Model> enemyCache = new ObjectMap<>();
    private final ObjectMap<String, Model> projectileCache = new ObjectMap<>();
    private Model beamModel, flameConeModel, rangeDiscModel, highlightModel;

    /**
     * Material de EFECTO translúcido: mezcla alfa y NO escribe profundidad.
     * Sin depthMask=false, el cono del lanzallamas o el disco de alcance
     * dejaban su huella en el buffer de profundidad y BORRABAN lo que se
     * dibujara después por detrás (arbustos, lava, cristales).
     */
    private static Material fxMaterial(Color color, float opacity) {
        return new Material(
            ColorAttribute.createDiffuse(color),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, opacity),
            new DepthTestAttribute(GL20.GL_LEQUAL, false));
    }

    // ---- tablero ----

    /** Tablero completo como un único Model con pocas parts (una por material). */
    public Model buildBoard(GridMap map, com.pcdc.td.data.BiomeDef biome, long seed) {
        Color groundA = biome.groundA();
        Color groundB = biome.groundB();
        Color path = biome.path();
        Color rock = biome.rock();
        Color rim = biome.rim();

        builder.begin();

        // ModelBuilder no soporta intercalar parts: cada part se rellena
        // completa antes de crear la siguiente.
        // las cajas se solapan un pelo (1.004): sin ello el redondeo abre costuras
        // negras entre celdas en algunas GPU (visto en Android)
        final float cell = 1.004f;
        MeshPartBuilder pb = builder.part("groundA", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(groundA)));
        for (int z = 0; z < map.height; z++)
            for (int x = 0; x < map.width; x++)
                if (map.get(x, z) != CellType.PATH && ((x + z) & 1) == 0)
                    BoxShapeBuilder.build(pb, x + 0.5f, -0.05f, z + 0.5f, cell, 0.1f, cell);

        pb = builder.part("groundB", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(groundB)));
        for (int z = 0; z < map.height; z++)
            for (int x = 0; x < map.width; x++)
                if (map.get(x, z) != CellType.PATH && ((x + z) & 1) == 1)
                    BoxShapeBuilder.build(pb, x + 0.5f, -0.05f, z + 0.5f, cell, 0.1f, cell);

        pb = builder.part("path", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(path)));
        for (int z = 0; z < map.height; z++)
            for (int x = 0; x < map.width; x++)
                if (map.get(x, z) == CellType.PATH)
                    // camino ligeramente elevado (top en y=0.05)
                    BoxShapeBuilder.build(pb, x + 0.5f, -0.025f, z + 0.5f, cell, 0.15f, cell);
        // tramos de entrada/salida fuera del tablero (los enemigos se anticipan por ahí)
        for (int p = 0; p < map.worldPaths.size; p++) {
            Array<Vector3> wp = map.worldPaths.get(p);
            if (wp.size < 2) continue;
            buildStreetSegment(pb, wp.get(0), wp.get(1));
            buildStreetSegment(pb, wp.get(wp.size - 2), wp.peek());
        }

        pb = builder.part("rock", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(rock)));
        for (int z = 0; z < map.height; z++)
            for (int x = 0; x < map.width; x++)
                if (map.get(x, z) == CellType.BLOCKED)
                    BoxShapeBuilder.build(pb, x + 0.5f, 0.25f, z + 0.5f, 0.8f, 0.5f, 0.8f);

        // marco alrededor del tablero
        pb = builder.part("rim", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(rim)));
        float w = map.width, h = map.height, t = 0.35f, y = 0.06f, hh = 0.22f;
        BoxShapeBuilder.build(pb, w / 2f, y, -t / 2f, w + 2 * t, hh, t);
        BoxShapeBuilder.build(pb, w / 2f, y, h + t / 2f, w + 2 * t, hh, t);
        BoxShapeBuilder.build(pb, -t / 2f, y, h / 2f, t, hh, h);
        BoxShapeBuilder.build(pb, w + t / 2f, y, h / 2f, t, hh, h);

        // terreno exterior: el mundo continúa más allá del tablero jugable
        float ext = 9f;
        Color outsideColor = new Color(groundA).mul(0.75f, 0.75f, 0.78f, 1f);
        pb = builder.part("outside", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(outsideColor)));
        float oy = -0.09f, oh = 0.1f; // top a y=-0.04, un pelo por debajo del tablero
        BoxShapeBuilder.build(pb, w / 2f, oy, -ext / 2f, w + 2 * ext, oh, ext);
        BoxShapeBuilder.build(pb, w / 2f, oy, h + ext / 2f, w + 2 * ext, oh, ext);
        BoxShapeBuilder.build(pb, -ext / 2f, oy, h / 2f, ext, oh, h);
        BoxShapeBuilder.build(pb, w + ext / 2f, oy, h / 2f, ext, oh, h);

        // decoración exterior: árboles/rocas según bioma, con semilla del nivel
        java.util.Random rng = new java.util.Random(seed);
        boolean trees = "meadow".equals(biome.id) || "forest".equals(biome.id) || "swamp".equals(biome.id);
        Color decoColor = new Color(rim).lerp(trees ? Color.FOREST : Color.DARK_GRAY, 0.3f);
        pb = builder.part("deco", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(decoColor)));
        for (int i = 0; i < 26; i++) {
            // posición fuera del tablero, dentro del anillo exterior
            float px, pz;
            int side = rng.nextInt(4);
            float d = 1.2f + rng.nextFloat() * (ext - 2.5f);
            float along = rng.nextFloat();
            switch (side) {
                case 0 -> { px = along * (w + 8) - 4; pz = -d; }
                case 1 -> { px = along * (w + 8) - 4; pz = h + d; }
                case 2 -> { px = -d; pz = along * h; }
                default -> { px = w + d; pz = along * h; }
            }
            float s = 0.5f + rng.nextFloat() * 0.7f;
            if (trees) {
                pb.setVertexTransform(tmpTransform.setToTranslation(px, s * 0.7f - 0.04f, pz));
                ConeShapeBuilder.build(pb, s, s * 1.4f, s, 7);
            } else {
                BoxShapeBuilder.build(pb, px, s * 0.25f - 0.04f, pz, s, s * 0.5f, s * 0.9f);
            }
        }
        pb.setVertexTransform(null);

        return manage(builder.end());
    }

    /** Tramo de calle axis-aligned de 2 de ancho entre dos puntos de la línea central. */
    private static void buildStreetSegment(MeshPartBuilder pb, Vector3 a, Vector3 b) {
        float dx = Math.abs(b.x - a.x);
        float dz = Math.abs(b.z - a.z);
        float cx = (a.x + b.x) / 2f;
        float cz = (a.z + b.z) / 2f;
        if (dx > dz) {
            BoxShapeBuilder.build(pb, cx, -0.025f, cz, dx + 0.004f, 0.15f, 2.004f);
        } else {
            BoxShapeBuilder.build(pb, cx, -0.025f, cz, 2.004f, 0.15f, dz + 0.004f);
        }
    }

    // ---- entidades ----

    /** ¿La torre aplica este estado? Se consulta para decidir los rasgos de
     *  casco de las fusiones, que traducen el efecto a silueta. */
    private static boolean hasEffect(TowerDef def, String type) {
        if (def.effects == null) return false;
        for (EffectDef e : def.effects) {
            if (e != null && type.equals(e.type)) return true;
        }
        return false;
    }

    public Model tower(TowerDef def) {
        return tower(def, 1);
    }

    /** Torre: nodo "base" fijo + nodo "head" que rota hacia el objetivo (cañón hacia +Z).
     *  Los niveles de mejora añaden adornos dorados: banda (nv2) y pilares + remate (nv3). */
    public Model tower(TowerDef def, int level) {
        return tower(def, level, 0);
    }

    /**
     * @param veteran 0 novato, 1 con 25 bajas, 2 con 75: galones pintados en el
     *                casco. No cambia una sola estadística; sirve para que
     *                mires un tanque concreto y sepas que lleva media campaña
     *                contigo.
     */
    public Model tower(TowerDef def, int level, int veteran) {
        String key = def.id + "#" + level + "#" + veteran;
        Model cached = towerCache.get(key);
        if (cached != null) return cached;

        Color headColor = new Color(def.color());
        Color baseColor = new Color(headColor).mul(0.55f, 0.55f, 0.55f, 1f).add(0.18f, 0.17f, 0.16f, 0f);
        TowerSkins.apply(activeSkin, headColor, baseColor);

        builder.begin();

        builder.node().id = "base";
        MeshPartBuilder pb = builder.part("base", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(baseColor)));
        if ("cylinder".equals(def.baseShape)) {
            CylinderShapeBuilder.build(pb, 0.72f, 0.35f, 0.72f, 14);
            // el cilindro se construye centrado: subirlo con un segundo anillo no
            // merece la pena; se compensa en la traslación del nodo del head
        } else {
            BoxShapeBuilder.build(pb, 0f, 0f, 0f, 0.72f, 0.35f, 0.72f);
        }
        // las torres de área llevan depósitos de combustible traseros (silueta
        // propia); la trituradora no, que no quema nada
        if (def.type() == TowerDef.AttackType.AREA && def.coneAngleDeg < 180f) {
            pb.setVertexTransform(tmpTransform.setToTranslation(-0.24f, 0.3f, -0.2f));
            CylinderShapeBuilder.build(pb, 0.2f, 0.32f, 0.2f, 10);
            pb.setVertexTransform(tmpTransform.setToTranslation(0.24f, 0.3f, -0.2f));
            CylinderShapeBuilder.build(pb, 0.2f, 0.32f, 0.2f, 10);
            pb.setVertexTransform(null);
        }
        // ambas bases centradas en el origen; el modelo se coloca a y=0.175 al renderizar

        // ORUGAS: el chasis que convierte cualquier torre en un tanque. Van en su
        // propia part, algo más largas que el casco y con ruedas asomando.
        Color trackColor = new Color(baseColor).mul(0.42f, 0.42f, 0.46f, 1f).add(0.1f, 0.1f, 0.12f, 0f);
        MeshPartBuilder tracks = builder.part("tracks", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(trackColor)));
        for (int side = -1; side <= 1; side += 2) {
            BoxShapeBuilder.build(tracks, side * 0.37f, -0.055f, 0f, 0.17f, 0.24f, 0.88f);
            // ruedas: tres cilindros tumbados que sobresalen un pelo de la oruga
            for (int w = -1; w <= 1; w++) {
                tmpTransform.setToTranslation(side * 0.37f, -0.06f, w * 0.28f)
                    .rotate(Vector3.Z, 90f);
                tracks.setVertexTransform(tmpTransform);
                CylinderShapeBuilder.build(tracks, 0.19f, 0.2f, 0.19f, 8);
            }
            tracks.setVertexTransform(null);
        }

        // el tanque de MANDO lleva mástil con banderín en la popa: se localiza
        // de un vistazo para colocar a su alrededor a los que reciben el aura
        if (def.unique) {
            MeshPartBuilder mast = builder.part("mast", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(Color.valueOf("d8d0c0"))));
            BoxShapeBuilder.build(mast, 0.27f, 0.45f, -0.27f, 0.05f, 0.55f, 0.05f);
            MeshPartBuilder flag = builder.part("flag", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(Color.valueOf("d83848"))));
            BoxShapeBuilder.build(flag, 0.42f, 0.6f, -0.27f, 0.26f, 0.16f, 0.03f);
        }

        // las FUSIONES llevan placa de panza y tacos de color entre las orugas:
        // silueta propia reconocible a cualquier distancia (más las aletas del cabezal)
        if (def.fusionOnly) {
            MeshPartBuilder ped = builder.part("fusion-ped", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(Color.valueOf("2e3540"))));
            BoxShapeBuilder.build(ped, 0f, -0.145f, 0f, 0.56f, 0.06f, 0.98f);
            Color accent = new Color(headColor).lerp(Color.WHITE, 0.35f);
            MeshPartBuilder studs = builder.part("fusion-studs", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(accent)));
            BoxShapeBuilder.build(studs, 0.2f, -0.13f, 0.44f, 0.1f, 0.1f, 0.1f);
            BoxShapeBuilder.build(studs, -0.2f, -0.13f, 0.44f, 0.1f, 0.1f, 0.1f);
            BoxShapeBuilder.build(studs, 0.2f, -0.13f, -0.44f, 0.1f, 0.1f, 0.1f);
            BoxShapeBuilder.build(studs, -0.2f, -0.13f, -0.44f, 0.1f, 0.1f, 0.1f);
            // linaje TESLA: las fusiones que encadenan rayos (o pintan plasma)
            // llevan una antena de bobina en la popa del casco — mástil, dos
            // anillos y orbe, versión mini del cabezal del Tesla. Va en el
            // casco, no en la torreta, para que no gire con el disco de sierra.
            if (def.chainCount > 0 || "plasma_arc".equals(def.id)) {
                BoxShapeBuilder.build(studs, 0.27f, 0.4f, -0.3f, 0.04f, 0.45f, 0.04f);
                tmpTransform.setToTranslation(0.27f, 0.5f, -0.3f);
                studs.setVertexTransform(tmpTransform);
                CylinderShapeBuilder.build(studs, 0.2f, 0.035f, 0.2f, 8);
                tmpTransform.setToTranslation(0.27f, 0.58f, -0.3f);
                studs.setVertexTransform(tmpTransform);
                CylinderShapeBuilder.build(studs, 0.14f, 0.035f, 0.14f, 8);
                tmpTransform.setToTranslation(0.27f, 0.66f, -0.3f);
                studs.setVertexTransform(tmpTransform);
                SphereShapeBuilder.build(studs, 0.11f, 0.11f, 0.11f, 8, 6);
                studs.setVertexTransform(null);
            }

            // RASGOS DE FAMILIA: la fusión se reconoce por lo que hace, no por
            // el color, que la pintura del jugador puede cambiar. Cada rasgo
            // ocupa una esquina distinta del casco para que un tanque con dos
            // efectos (la Plaga es slow+poison) los luzca ambos sin solaparse.
            boolean poison = hasEffect(def, "poison");
            boolean slow = hasEffect(def, "slow");
            boolean burn = hasEffect(def, "burn");
            // lo oscuro de cuba y chimenea comparte una sola part: menos
            // materiales y una part se rellena entera antes de abrir la siguiente
            if (poison || burn) {
                MeshPartBuilder metal = builder.part("trait-metal", GL20.GL_TRIANGLES, ATTRS,
                    new Material(ColorAttribute.createDiffuse(Color.valueOf("2b3138"))));
                if (poison) {
                    // cuba centrada y estrecha: cabe entre los dos depósitos de
                    // combustible que el Aliento cáustico ya lleva en x=±0.24
                    metal.setVertexTransform(tmpTransform.setToTranslation(0f, 0.24f, -0.3f));
                    CylinderShapeBuilder.build(metal, 0.26f, 0.2f, 0.26f, 12);
                }
                if (burn) {
                    // chimenea pegada al eje: más a la izquierda chocaría con el
                    // mástil del adorno de nivel 3, que va en x=-0.26
                    metal.setVertexTransform(tmpTransform.setToTranslation(-0.17f, 0.3f, -0.3f));
                    CylinderShapeBuilder.build(metal, 0.12f, 0.28f, 0.12f, 10);
                }
                metal.setVertexTransform(null);
            }
            if (poison) {
                // tapa de líquido: el verde asomando por la boca de la cuba es lo
                // que se ve desde la cámara cenital, no el bidón
                MeshPartBuilder ooze = builder.part("trait-ooze", GL20.GL_TRIANGLES, ATTRS,
                    new Material(ColorAttribute.createDiffuse(Color.valueOf("7ddc4a"))));
                ooze.setVertexTransform(tmpTransform.setToTranslation(0f, 0.355f, -0.3f));
                CylinderShapeBuilder.build(ooze, 0.2f, 0.03f, 0.2f, 12);
                ooze.setVertexTransform(null);
            }
            if (slow) {
                // carámbanos colgando de AMBOS flancos, no del morro: la cámara
                // gira de 90 en 90 y en cualquiera de las cuatro vistas se ve al
                // menos un lateral, mientras que frente y popa se turnan para
                // esconderse (y el morro encima lo tapa la torreta).
                // x=±0.40 los saca de las planchas de blindaje de nv2 (que llegan
                // a 0.375) y la altura los deja sobre el guardabarros de la oruga,
                // que sube hasta y=0.065: por debajo de ahí no se vería la punta.
                MeshPartBuilder frost = builder.part("trait-frost", GL20.GL_TRIANGLES, ATTRS,
                    new Material(ColorAttribute.createDiffuse(Color.valueOf("dff2ff"))));
                float[] zs = {-0.2f, 0.05f, 0.3f};
                for (int side = -1; side <= 1; side += 2) {
                    for (float z : zs) {
                        tmpTransform.setToTranslation(side * 0.4f, 0.115f, z).rotate(Vector3.X, 180f);
                        frost.setVertexTransform(tmpTransform);
                        ConeShapeBuilder.build(frost, 0.07f, 0.14f, 0.07f, 8);
                    }
                }
                frost.setVertexTransform(null);
            }
            if (burn) {
                // brasa asomando por la boca de la chimenea
                MeshPartBuilder ember = builder.part("trait-ember", GL20.GL_TRIANGLES, ATTRS,
                    new Material(ColorAttribute.createDiffuse(Color.valueOf("ff8a3c"))));
                ember.setVertexTransform(tmpTransform.setToTranslation(-0.17f, 0.44f, -0.3f));
                SphereShapeBuilder.build(ember, 0.11f, 0.11f, 0.11f, 8, 6);
                ember.setVertexTransform(null);
            }
        }

        // adornos de mejora: piezas acoplables que se leen de lejos, no bandas.
        // Nv2 = blindaje lateral atornillado; Nv3 = cargador trasero + antena
        // con banderín del color de la pintura. Cada part se rellena ENTERA
        // antes de crear la siguiente (regla del ModelBuilder).
        if (level >= 2) {
            Color armorColor = new Color(baseColor).mul(0.6f, 0.62f, 0.68f, 1f).add(0.12f, 0.13f, 0.15f, 0f);
            MeshPartBuilder armor = builder.part("armor", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(armorColor)));
            // planchas laterales sobre las orugas
            BoxShapeBuilder.build(armor, 0.34f, 0.1f, 0f, 0.07f, 0.2f, 0.7f);
            BoxShapeBuilder.build(armor, -0.34f, 0.1f, 0f, 0.07f, 0.2f, 0.7f);
            if (level >= 3) {
                // cargador trasero: cajón de munición sobre la popa
                BoxShapeBuilder.build(armor, 0f, 0.24f, -0.3f, 0.34f, 0.16f, 0.18f);
                // mástil de la antena (el banderín va en su propia part de color)
                BoxShapeBuilder.build(armor, -0.26f, 0.42f, -0.28f, 0.035f, 0.5f, 0.035f);
            }
            // remaches en color de la pintura: en su part para que destaquen
            Color accent = new Color(headColor).lerp(Color.WHITE, 0.25f);
            MeshPartBuilder studs = builder.part("armor-accent", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(accent)));
            for (int side = -1; side <= 1; side += 2) {
                BoxShapeBuilder.build(studs, side * 0.38f, 0.1f, 0.24f, 0.035f, 0.07f, 0.07f);
                BoxShapeBuilder.build(studs, side * 0.38f, 0.1f, -0.24f, 0.035f, 0.07f, 0.07f);
            }
            if (level >= 3) {
                // banderín triangular a media asta, del color del cabezal
                BoxShapeBuilder.build(studs, -0.19f, 0.6f, -0.28f, 0.12f, 0.09f, 0.03f);
                BoxShapeBuilder.build(studs, -0.11f, 0.6f, -0.28f, 0.05f, 0.05f, 0.03f);
            }
        }

        // GARFIO TRITURADOR: el único tanque de corte a 360º que además TIRA del
        // enemigo. El carrete de cable va en el CASCO y asomando por la popa
        // (z hasta -0.58): dentro del cabezal el disco de sierra, que es plano y
        // de radio 0.44, lo tapaba entero desde la cámara cenital.
        if (def.pullDistance > 0f && def.type() == TowerDef.AttackType.AREA
            && def.coneAngleDeg >= 180f) {
            MeshPartBuilder reel = builder.part("hook-reel", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(Color.valueOf("3a4148"))));
            // los cilindros nacen sobre Y: tumbarlos sobre X para que el cable
            // se enrolle de lado a lado, como un torno
            reel.setVertexTransform(tmpTransform.setToTranslation(0f, 0.26f, -0.46f)
                .rotate(Vector3.Z, 90f));
            CylinderShapeBuilder.build(reel, 0.24f, 0.3f, 0.24f, 10); // tambor
            reel.setVertexTransform(tmpTransform.setToTranslation(-0.16f, 0.26f, -0.46f)
                .rotate(Vector3.Z, 90f));
            CylinderShapeBuilder.build(reel, 0.3f, 0.03f, 0.3f, 10); // bridas
            reel.setVertexTransform(tmpTransform.setToTranslation(0.16f, 0.26f, -0.46f)
                .rotate(Vector3.Z, 90f));
            CylinderShapeBuilder.build(reel, 0.3f, 0.03f, 0.3f, 10);
            reel.setVertexTransform(null);
        }

        Node head = builder.node();
        head.id = "head";
        head.translation.set(0f, 0.32f, 0f);
        pb = builder.part("head", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(headColor)));
        switch (def.headShape) {
            case "box":
                BoxShapeBuilder.build(pb, 0f, 0f, 0f, 0.36f, 0.27f, 0.36f);
                break;
            case "sphere":
                SphereShapeBuilder.build(pb, 0.4f, 0.4f, 0.4f, 12, 10);
                break;
            case "cone":
                ConeShapeBuilder.build(pb, 0.42f, 0.4f, 0.42f, 12);
                break;
            default:
                CylinderShapeBuilder.build(pb, 0.42f, 0.28f, 0.42f, 14);
                break;
        }
        // GATLING LÁSER: comparte esfera con el láser normal, así que se le
        // añade el tambor de tres lentes que le da nombre — placa frontal que
        // las agrupa y las lentes en triángulo alrededor del eje de disparo
        if ("gatling_laser".equals(def.id)) {
            pb.setVertexTransform(tmpTransform.setToTranslation(0f, 0.04f, 0.24f)
                .rotate(Vector3.X, 90f));
            CylinderShapeBuilder.build(pb, 0.32f, 0.05f, 0.32f, 14); // placa/aro
            for (int l = 0; l < 3; l++) {
                float ang = (90f + l * 120f) * com.badlogic.gdx.math.MathUtils.degreesToRadians;
                float lx = com.badlogic.gdx.math.MathUtils.cos(ang) * 0.1f;
                float ly = com.badlogic.gdx.math.MathUtils.sin(ang) * 0.1f;
                // los cilindros nacen sobre Y: tumbarlos sobre Z para que miren al frente
                pb.setVertexTransform(tmpTransform.setToTranslation(lx, 0.04f + ly, 0.32f)
                    .rotate(Vector3.X, 90f));
                CylinderShapeBuilder.build(pb, 0.11f, 0.3f, 0.11f, 8);
            }
            pb.setVertexTransform(null);
        }
        // cañón hacia +Z; las de área llevan boquilla ancha y corta en vez de tubo
        if ("tesla".equals(def.id)) {
            // bobina: anillos apilados sobre la esfera y orbe en la punta, sin cañón
            for (int r = 0; r < 3; r++) {
                tmpTransform.setToTranslation(0f, 0.18f + r * 0.09f, 0f);
                pb.setVertexTransform(tmpTransform);
                float ring = 0.3f - r * 0.06f;
                CylinderShapeBuilder.build(pb, ring, 0.05f, ring, 10);
            }
            pb.setVertexTransform(tmpTransform.setToTranslation(0f, 0.48f, 0f));
            SphereShapeBuilder.build(pb, 0.17f, 0.17f, 0.17f, 10, 8);
            pb.setVertexTransform(null);
        } else if ("radar".equals(def.id)) {
            // antena parabólica: mástil + panel rectangular inclinado que barre
            CylinderShapeBuilder.build(pb, 0.1f, 0.3f, 0.1f, 8);
            pb.setVertexTransform(tmpTransform.setToTranslation(0f, 0.24f, -0.04f)
                .rotate(Vector3.X, -18f));
            BoxShapeBuilder.build(pb, 0f, 0f, 0f, 0.72f, 0.34f, 0.05f);
            pb.setVertexTransform(tmpTransform.setToTranslation(0f, 0.24f, 0.02f)
                .rotate(Vector3.X, -18f));
            BoxShapeBuilder.build(pb, 0f, 0f, 0f, 0.08f, 0.08f, 0.1f); // emisor
            pb.setVertexTransform(null);
        } else if (def.type() == TowerDef.AttackType.AREA && def.coneAngleDeg >= 180f
            && def.pulseInterval > 0f) {
            // DEMOLEDOR: martillo neumático — cuerpo compresor con asas y un
            // brazo que ADELANTA el pistón hasta asomar por el frontal del
            // casco: así el puntero se ve machacar el suelo (el render hace
            // vibrar el cabezal en vertical al atacar, no girar; el cabezal
            // apunta al enemigo más cercano, o sea el martillo "le busca")
            BoxShapeBuilder.build(pb, 0f, 0.14f, -0.04f, 0.3f, 0.22f, 0.34f); // compresor
            BoxShapeBuilder.build(pb, -0.23f, 0.18f, -0.04f, 0.16f, 0.06f, 0.1f); // asas
            BoxShapeBuilder.build(pb, 0.23f, 0.18f, -0.04f, 0.16f, 0.06f, 0.1f);
            BoxShapeBuilder.build(pb, 0f, 0.1f, 0.22f, 0.12f, 0.1f, 0.3f); // brazo
            pb.setVertexTransform(tmpTransform.setToTranslation(0f, -0.12f, 0.38f));
            CylinderShapeBuilder.build(pb, 0.15f, 0.34f, 0.15f, 10); // pistón
            // puntero: cono invertido que apunta al suelo
            pb.setVertexTransform(tmpTransform.setToTranslation(0f, -0.36f, 0.38f)
                .rotate(Vector3.X, 180f));
            ConeShapeBuilder.build(pb, 0.14f, 0.2f, 0.14f, 8);
            pb.setVertexTransform(null);
        } else if (def.type() == TowerDef.AttackType.AREA && def.coneAngleDeg >= 180f) {
            // cualquier tanque de corte a 360º (trituradora y sus fusiones)
            // disco de sierra PLANO en horizontal, como una mesa de corte:
            // el render lo hace girar sobre el eje Y al atacar
            CylinderShapeBuilder.build(pb, 0.16f, 0.12f, 0.16f, 8); // eje central
            pb.setVertexTransform(tmpTransform.setToTranslation(0f, 0.09f, 0f));
            CylinderShapeBuilder.build(pb, 0.88f, 0.045f, 0.88f, 18); // disco
            pb.setVertexTransform(null);
            for (int t = 0; t < 10; t++) { // dientes: conos apuntando hacia fuera
                pb.setVertexTransform(tmpTransform.setToRotation(Vector3.Y, t * 36f)
                    .translate(0.49f, 0.09f, 0f).rotate(Vector3.Z, -90f));
                ConeShapeBuilder.build(pb, 0.1f, 0.17f, 0.07f, 4);
            }
            pb.setVertexTransform(null);
            // (el carrete del Garfio triturador va en el CASCO, no aquí: bajo el
            // disco quedaba tapado por completo desde la cámara del juego)
        } else if (def.pullDistance > 0f) {
            // ARPÓN: carrete de cable a los lados y un dardo de dos púas
            pb.setVertexTransform(tmpTransform.setToTranslation(-0.2f, 0.06f, -0.05f)
                .rotate(Vector3.Z, 90f));
            CylinderShapeBuilder.build(pb, 0.26f, 0.1f, 0.26f, 10);
            pb.setVertexTransform(tmpTransform.setToTranslation(0.2f, 0.06f, -0.05f)
                .rotate(Vector3.Z, 90f));
            CylinderShapeBuilder.build(pb, 0.26f, 0.1f, 0.26f, 10);
            pb.setVertexTransform(null);
            BoxShapeBuilder.build(pb, 0f, 0.06f, 0.3f, 0.1f, 0.1f, 0.52f); // cable tenso
            BoxShapeBuilder.build(pb, 0f, 0.06f, 0.58f, 0.14f, 0.14f, 0.18f); // cabeza
            BoxShapeBuilder.build(pb, -0.1f, 0.06f, 0.7f, 0.06f, 0.06f, 0.2f); // púas
            BoxShapeBuilder.build(pb, 0.1f, 0.06f, 0.7f, 0.06f, 0.06f, 0.2f);
        } else if (def.type() == TowerDef.AttackType.AREA) {
            BoxShapeBuilder.build(pb, 0f, 0.02f, 0.26f, 0.26f, 0.16f, 0.3f);
            BoxShapeBuilder.build(pb, 0f, 0.02f, 0.42f, 0.34f, 0.22f, 0.08f); // difusor
        } else if ("railgun".equals(def.id)) {
            // CAÑÓN DE RIEL: no dispara por un tubo, sino entre dos raíles. El
            // HUECO central es su seña — va antes que la regla de francotirador
            // porque también es hitscan de largo alcance.
            BoxShapeBuilder.build(pb, 0f, 0.04f, 0f, 0.3f, 0.22f, 0.22f); // acelerador de culata
            BoxShapeBuilder.build(pb, -0.09f, 0.04f, 0.42f, 0.07f, 0.1f, 0.8f);
            BoxShapeBuilder.build(pb, 0.09f, 0.04f, 0.42f, 0.07f, 0.1f, 0.8f);
            // travesaños: sujetan los raíles sin tapar el hueco
            BoxShapeBuilder.build(pb, 0f, 0.04f, 0.2f, 0.25f, 0.05f, 0.07f);
            BoxShapeBuilder.build(pb, 0f, 0.04f, 0.62f, 0.25f, 0.05f, 0.07f);
        } else if (def.type() == TowerDef.AttackType.HITSCAN && def.range >= 5f
            && def.pullDistance <= 0f) {
            // FAMILIA FRANCOTIRADOR: alcance de rifle y disparo instantáneo, sin
            // cable de arpón. Cañón largo y fino más mira telescópica: se
            // distingue del cañón genérico incluso desde la cámara cenital.
            BoxShapeBuilder.build(pb, 0f, 0.04f, 0.42f, 0.11f, 0.11f, 0.78f);
            // los cilindros nacen sobre Y: tumbar la mira sobre el eje Z
            pb.setVertexTransform(tmpTransform.setToTranslation(0.09f, 0.16f, 0.18f)
                .rotate(Vector3.X, 90f));
            CylinderShapeBuilder.build(pb, 0.11f, 0.22f, 0.11f, 8);
            pb.setVertexTransform(null);
            BoxShapeBuilder.build(pb, 0.09f, 0.1f, 0.18f, 0.04f, 0.09f, 0.05f); // soporte de la mira
        } else if (def.twinBarrel) {
            float len = level >= 2 ? 0.68f : 0.58f;
            BoxShapeBuilder.build(pb, -0.12f, 0.04f, 0.36f, 0.11f, 0.11f, len);
            BoxShapeBuilder.build(pb, 0.12f, 0.04f, 0.36f, 0.11f, 0.11f, len);
            if (level >= 2) { // frenos de boca
                BoxShapeBuilder.build(pb, -0.12f, 0.04f, 0.36f + len / 2f, 0.16f, 0.16f, 0.09f);
                BoxShapeBuilder.build(pb, 0.12f, 0.04f, 0.36f + len / 2f, 0.16f, 0.16f, 0.09f);
            }
        } else {
            // el cañón crece con el nivel y estrena freno de boca a partir del 2
            float len = level >= 2 ? 0.64f : 0.5f;
            BoxShapeBuilder.build(pb, 0f, 0.04f, 0.34f, 0.13f, 0.13f, len);
            if (level >= 2) {
                BoxShapeBuilder.build(pb, 0f, 0.04f, 0.34f + len / 2f, 0.19f, 0.19f, 0.1f);
            }
        }
        // aletas laterales de las fusiones (giran con el cabezal)
        if (def.fusionOnly) {
            Color accent = new Color(headColor).lerp(Color.WHITE, 0.35f);
            MeshPartBuilder fins = builder.part("fusion-fins", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(accent)));
            BoxShapeBuilder.build(fins, 0.25f, 0.05f, -0.05f, 0.05f, 0.2f, 0.3f);
            BoxShapeBuilder.build(fins, -0.25f, 0.05f, -0.05f, 0.05f, 0.2f, 0.3f);
        }
        if (veteran > 0) {
            MeshPartBuilder vp = builder.part("vet", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(
                    veteran >= 2 ? Color.valueOf("ffd24a") : Color.valueOf("e8e4d8"))));
            for (int i = 0; i < veteran; i++) {
                // galones PLANOS SOBRE EL CASCO: en el lateral quedaban de
                // espaldas a la cámara cenital y no se veían nunca
                BoxShapeBuilder.build(vp, 0f, 0.205f, -0.24f - i * 0.12f,
                    0.34f, 0.03f, 0.07f);
            }
            vp.setVertexTransform(null);
        }
        return manage(key, towerCache, builder.end());
    }

    /**
     * Monstruo compuesto: cuerpo + cuernos oscuros + ojos blancos, mirando a +Z.
     * Una ÚNICA part con colores de vértice: 1 draw call por enemigo.
     */
    public Model enemy(EnemyDef def, com.pcdc.td.data.BiomeDef biome) {
        String key = def.id + "@" + biome.id;
        Model cached = enemyCache.get(key);
        if (cached != null) return cached;

        Color body = new Color(def.color());
        if (!"meadow".equals(biome.id)) body.lerp(biome.enemyTint(), 0.4f);
        Color dark = new Color(body).mul(0.38f, 0.38f, 0.42f, 1f);
        float s = def.size;

        builder.begin();

        long attrs = ATTRS | Usage.ColorUnpacked;
        MeshPartBuilder pb = builder.part("monster", GL20.GL_TRIANGLES, attrs,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        pb.setColor(body);
        switch (def.shape) {
            case "cone": // rápido: gota con aletas
                pb.setVertexTransform(tmpTransform.setToTranslation(0f, 0f, 0f));
                ConeShapeBuilder.build(pb, s, s * 1.25f, s, 10);
                pb.setVertexTransform(null);
                BoxShapeBuilder.build(pb, -s * 0.5f, -s * 0.25f, -s * 0.2f, s * 0.3f, s * 0.12f, s * 0.5f);
                BoxShapeBuilder.build(pb, s * 0.5f, -s * 0.25f, -s * 0.2f, s * 0.3f, s * 0.12f, s * 0.5f);
                break;
            case "sphere": // volador: bicho con alas
                pb.setVertexTransform(tmpTransform.setToTranslation(0f, 0f, 0f));
                SphereShapeBuilder.build(pb, s, s * 0.9f, s, 12, 10);
                pb.setVertexTransform(null);
                // las alas SOLO si vuela de verdad: un bicho de tierra con alas
                // (el núcleo de magma) se leía como volador y confundía
                if (def.flying) {
                    BoxShapeBuilder.build(pb, -s * 0.72f, s * 0.08f, -s * 0.05f, s * 0.62f, s * 0.07f, s * 0.42f);
                    BoxShapeBuilder.build(pb, s * 0.72f, s * 0.08f, -s * 0.05f, s * 0.62f, s * 0.07f, s * 0.42f);
                }
                break;
            case "cylinder": // jefe: coloso
                pb.setVertexTransform(tmpTransform.setToTranslation(0f, 0f, 0f));
                CylinderShapeBuilder.build(pb, s, s * 1.1f, s, 14);
                pb.setVertexTransform(null);
                BoxShapeBuilder.build(pb, -s * 0.6f, s * 0.28f, 0f, s * 0.34f, s * 0.34f, s * 0.5f);
                BoxShapeBuilder.build(pb, s * 0.6f, s * 0.28f, 0f, s * 0.34f, s * 0.34f, s * 0.5f);
                break;
            default: // normal/tanque: bruto con brazos
                BoxShapeBuilder.build(pb, 0f, 0f, 0f, s, s * 0.85f, s * 0.9f);
                BoxShapeBuilder.build(pb, -s * 0.6f, -s * 0.08f, s * 0.05f, s * 0.22f, s * 0.55f, s * 0.3f);
                BoxShapeBuilder.build(pb, s * 0.6f, -s * 0.08f, s * 0.05f, s * 0.22f, s * 0.55f, s * 0.3f);
                break;
        }

        // cuernos / púas (misma part, color de vértice oscuro)
        pb.setColor(dark);
        if ("sphere".equals(def.shape)) {
            BoxShapeBuilder.build(pb, 0f, s * 0.5f, -s * 0.15f, s * 0.12f, s * 0.3f, s * 0.12f);
        } else if ("cone".equals(def.shape)) {
            BoxShapeBuilder.build(pb, 0f, s * 0.35f, -s * 0.3f, s * 0.12f, s * 0.35f, s * 0.12f);
        } else {
            float hornH = "cylinder".equals(def.shape) ? s * 0.5f : s * 0.34f;
            BoxShapeBuilder.build(pb, -s * 0.26f, s * 0.5f + hornH * 0.3f, 0f, s * 0.14f, hornH, s * 0.14f);
            BoxShapeBuilder.build(pb, s * 0.26f, s * 0.5f + hornH * 0.3f, 0f, s * 0.14f, hornH, s * 0.14f);
        }

        // ojos (misma part, color de vértice claro)
        pb.setColor(Color.valueOf("f5f0e0"));
        if ("cylinder".equals(def.shape)) {
            // jefe: un solo ojo grande
            BoxShapeBuilder.build(pb, 0f, s * 0.22f, s * 0.48f, s * 0.3f, s * 0.2f, s * 0.1f);
        } else {
            float eyeY = "cone".equals(def.shape) ? -s * 0.1f : s * 0.16f;
            BoxShapeBuilder.build(pb, -s * 0.17f, eyeY, s * 0.44f, s * 0.15f, s * 0.15f, s * 0.1f);
            BoxShapeBuilder.build(pb, s * 0.17f, eyeY, s * 0.44f, s * 0.15f, s * 0.15f, s * 0.1f);
        }

        // atuendo del bioma: la pradera va "al natural"; el resto viste a su
        // fauna (misma part, colores de vértice → cero draw calls extra).
        // El Corruptor, el Campeón y la tropa con OFICIO van por libre: su
        // silueta tiene que leerse de un vistazo, manda el rol sobre el bioma.
        if (!"overlord".equals(def.id) && !"champion".equals(def.id)
            && "none".equals(def.role) && "none".equals(def.special)) {
            addBiomeGarb(pb, def, biome, s);
        }

        // UNIDAD INSIGNIA del bioma: silueta propia en vez de atuendo, igual
        // que la tropa con oficio — hay que reconocerla y matarla la primera
        if (!"none".equals(def.special)) addSpecialGarb(pb, def, s);

        // SANADOR: cruz flotante y báculo — el enemigo al que hay que apuntar
        if ("healer".equals(def.role)) {
            pb.setColor(Color.valueOf("d8fff0"));
            BoxShapeBuilder.build(pb, 0f, s * 1.15f, 0f, s * 0.5f, s * 0.16f, s * 0.16f);
            BoxShapeBuilder.build(pb, 0f, s * 1.15f, 0f, s * 0.16f, s * 0.5f, s * 0.16f);
            pb.setColor(Color.valueOf("2a6a44"));
            BoxShapeBuilder.build(pb, s * 0.42f, s * 0.35f, -s * 0.1f, s * 0.09f, s * 1.1f, s * 0.09f);
        }

        // ZAPADOR: mochila de herramientas y dos pinzas que chisporrotean
        if ("sapper".equals(def.role)) {
            pb.setColor(Color.valueOf("3a2c20"));
            BoxShapeBuilder.build(pb, 0f, s * 0.1f, -s * 0.5f, s * 0.5f, s * 0.44f, s * 0.3f);
            pb.setColor(Color.valueOf("c8c8d0"));
            BoxShapeBuilder.build(pb, -s * 0.3f, s * 0.7f, s * 0.1f, s * 0.08f, s * 0.6f, s * 0.08f);
            BoxShapeBuilder.build(pb, s * 0.3f, s * 0.7f, s * 0.1f, s * 0.08f, s * 0.6f, s * 0.08f);
            pb.setColor(Color.valueOf("ffd84a"));
            BoxShapeBuilder.build(pb, -s * 0.3f, s * 1.02f, s * 0.1f, s * 0.15f, s * 0.15f, s * 0.15f);
            BoxShapeBuilder.build(pb, s * 0.3f, s * 1.02f, s * 0.1f, s * 0.15f, s * 0.15f, s * 0.15f);
        }

        // PORTADOR: bodega colgando bajo el casco y rotores a los lados
        if ("carrier".equals(def.role)) {
            pb.setColor(Color.valueOf("4a4058"));
            BoxShapeBuilder.build(pb, 0f, -s * 0.72f, 0f, s * 0.85f, s * 0.5f, s * 0.7f);
            pb.setColor(Color.valueOf("2c2438"));
            BoxShapeBuilder.build(pb, 0f, -s * 0.5f, 0f, s * 0.12f, s * 0.3f, s * 0.12f);
            pb.setColor(Color.valueOf("d0c0e8"));
            BoxShapeBuilder.build(pb, -s * 1.05f, s * 0.3f, 0f, s * 0.7f, s * 0.06f, s * 0.18f);
            BoxShapeBuilder.build(pb, s * 1.05f, s * 0.3f, 0f, s * 0.7f, s * 0.06f, s * 0.18f);
        }

        // CAMPEÓN: cinta dorada y penacho carmesí — mini-jefe reconocible
        if ("champion".equals(def.id)) {
            pb.setColor(Color.valueOf("ffd24a"));
            BoxShapeBuilder.build(pb, 0f, s * 0.44f, 0f, s * 0.54f, s * 0.09f, s * 0.5f);
            pb.setColor(Color.valueOf("d0343c"));
            BoxShapeBuilder.build(pb, 0f, s * 0.6f, s * 0.16f, s * 0.09f, s * 0.26f, s * 0.13f);
            BoxShapeBuilder.build(pb, 0f, s * 0.68f, 0f, s * 0.09f, s * 0.36f, s * 0.15f);
            BoxShapeBuilder.build(pb, 0f, s * 0.6f, -s * 0.16f, s * 0.09f, s * 0.26f, s * 0.13f);
        }

        // EL CORRUPTOR: corona de púas, hombreras y ojo incandescente propio
        if ("overlord".equals(def.id)) {
            pb.setColor(Color.valueOf("2a0f38"));
            for (int i = 0; i < 6; i++) {
                float ang = i * 60f * com.badlogic.gdx.math.MathUtils.degreesToRadians;
                float px = com.badlogic.gdx.math.MathUtils.sin(ang) * s * 0.34f;
                float pz = com.badlogic.gdx.math.MathUtils.cos(ang) * s * 0.34f;
                pb.setVertexTransform(tmpTransform.setToTranslation(px, s * 0.68f, pz));
                ConeShapeBuilder.build(pb, s * 0.14f, s * 0.42f, s * 0.14f, 4);
            }
            pb.setVertexTransform(null);
            pb.setColor(Color.valueOf("1c0a26"));
            BoxShapeBuilder.build(pb, -s * 0.62f, s * 0.42f, 0f, s * 0.3f, s * 0.2f, s * 0.6f);
            BoxShapeBuilder.build(pb, s * 0.62f, s * 0.42f, 0f, s * 0.3f, s * 0.2f, s * 0.6f);
            pb.setColor(Color.valueOf("ff5a3c")); // el ojo arde en naranja
            BoxShapeBuilder.build(pb, 0f, s * 0.22f, s * 0.5f, s * 0.34f, s * 0.22f, s * 0.08f);
        }

        return manage(key, enemyCache, builder.end());
    }

    /**
     * Silueta de las UNIDADES INSIGNIA (una por bioma). Cada truco tiene su
     * seña visible: el que embiste lleva ariete, el que oculta arrastra
     * jirones de bruma, el que escuda luce cornamenta... Misma part, colores
     * fijos: cero draw calls extra.
     */
    private void addSpecialGarb(MeshPartBuilder pb, EnemyDef def, float s) {
        switch (def.special) {
            case "charge": // EMBESTIDOR: ariete frontal y placa de choque
                pb.setColor(Color.valueOf("d8d0c0"));
                pb.setVertexTransform(tmpTransform.setToTranslation(0f, 0f, s * 0.75f)
                    .rotate(Vector3.X, 90f));
                ConeShapeBuilder.build(pb, s * 0.3f, s * 0.5f, s * 0.3f, 6);
                pb.setVertexTransform(null);
                pb.setColor(Color.valueOf("6a5a4a"));
                BoxShapeBuilder.build(pb, 0f, -s * 0.1f, s * 0.5f, s * 0.9f, s * 0.3f, s * 0.12f);
                break;
            case "veil": // ESPEJISMO: faldón de arena y espejismos laterales
                pb.setColor(Color.valueOf("f0e0b8"));
                BoxShapeBuilder.build(pb, 0f, -s * 0.42f, 0f, s * 1.05f, s * 0.06f, s * 1.05f);
                pb.setColor(Color.valueOf("fff4d0"));
                BoxShapeBuilder.build(pb, -s * 0.62f, 0f, 0f, s * 0.05f, s * 0.7f, s * 0.5f);
                BoxShapeBuilder.build(pb, s * 0.62f, 0f, 0f, s * 0.05f, s * 0.7f, s * 0.5f);
                break;
            case "wardaura": // GUARDIÁN: cornamenta ancha y coraza de corteza
                pb.setColor(Color.valueOf("8a6a3c"));
                BoxShapeBuilder.build(pb, 0f, s * 0.85f, 0f, s * 1.2f, s * 0.1f, s * 0.1f);
                BoxShapeBuilder.build(pb, -s * 0.5f, s * 1f, 0f, s * 0.1f, s * 0.34f, s * 0.1f);
                BoxShapeBuilder.build(pb, s * 0.5f, s * 1f, 0f, s * 0.1f, s * 0.34f, s * 0.1f);
                pb.setColor(Color.valueOf("2e5c34"));
                BoxShapeBuilder.build(pb, 0f, 0f, -s * 0.5f, s * 0.9f, s * 0.7f, s * 0.16f);
                break;
            case "chillaura": // ESCARCHADOR: corona de carámbanos
                pb.setColor(Color.valueOf("e8fbff"));
                for (int i = 0; i < 5; i++) {
                    float ang = i * 72f * com.badlogic.gdx.math.MathUtils.degreesToRadians;
                    pb.setVertexTransform(tmpTransform.setToTranslation(
                        com.badlogic.gdx.math.MathUtils.sin(ang) * s * 0.42f, s * 0.62f,
                        com.badlogic.gdx.math.MathUtils.cos(ang) * s * 0.42f));
                    ConeShapeBuilder.build(pb, s * 0.16f, s * 0.5f, s * 0.16f, 4);
                }
                pb.setVertexTransform(null);
                break;
            case "meltdown": // NÚCLEO DE MAGMA: vetas incandescentes y núcleo
                pb.setColor(Color.valueOf("ffc040"));
                BoxShapeBuilder.build(pb, 0f, 0f, 0f, s * 0.62f, s * 0.1f, s * 0.12f);
                BoxShapeBuilder.build(pb, 0f, 0f, 0f, s * 0.12f, s * 0.1f, s * 0.62f);
                pb.setColor(Color.valueOf("fff0a0"));
                BoxShapeBuilder.build(pb, 0f, s * 0.48f, 0f, s * 0.26f, s * 0.26f, s * 0.26f);
                break;
            case "cloak": // BRUMA VIVA: jirones colgando a distintas alturas
                pb.setColor(Color.valueOf("cfe0c0"));
                BoxShapeBuilder.build(pb, 0f, s * 0.2f, 0f, s * 1.5f, s * 0.05f, s * 1.5f);
                BoxShapeBuilder.build(pb, s * 0.15f, s * 0.6f, -s * 0.1f, s * 1.2f, s * 0.05f, s * 1.2f);
                BoxShapeBuilder.build(pb, -s * 0.1f, s * 0.95f, s * 0.1f, s * 0.85f, s * 0.05f, s * 0.85f);
                break;
            case "guardaura": // RESONADOR: tres cristales orbitando
                pb.setColor(Color.valueOf("d8d8ff"));
                for (int i = 0; i < 3; i++) {
                    float ang = i * 120f * com.badlogic.gdx.math.MathUtils.degreesToRadians;
                    pb.setVertexTransform(tmpTransform.setToTranslation(
                        com.badlogic.gdx.math.MathUtils.sin(ang) * s * 0.6f, s * 0.5f,
                        com.badlogic.gdx.math.MathUtils.cos(ang) * s * 0.6f)
                        .rotate(Vector3.Z, 18f));
                    ConeShapeBuilder.build(pb, s * 0.18f, s * 0.62f, s * 0.18f, 5);
                }
                pb.setVertexTransform(null);
                break;
            case "warp": // ACÓLITO: capucha y orbe de salto sobre la cabeza
                pb.setColor(Color.valueOf("3a2450"));
                BoxShapeBuilder.build(pb, 0f, s * 0.5f, -s * 0.06f, s * 0.7f, s * 0.34f, s * 0.7f);
                pb.setColor(Color.valueOf("e0a8ff"));
                pb.setVertexTransform(tmpTransform.setToTranslation(0f, s * 1.05f, 0f));
                SphereShapeBuilder.build(pb, s * 0.3f, s * 0.3f, s * 0.3f, 8, 6);
                pb.setVertexTransform(null);
                break;
            case "thunder": // INVOCADOR: pararrayos y chispa entre las puntas
                pb.setColor(Color.valueOf("c8d8f0"));
                BoxShapeBuilder.build(pb, -s * 0.34f, s * 0.75f, 0f, s * 0.08f, s * 0.6f, s * 0.08f);
                BoxShapeBuilder.build(pb, s * 0.34f, s * 0.75f, 0f, s * 0.08f, s * 0.6f, s * 0.08f);
                pb.setColor(Color.valueOf("fffbc0"));
                BoxShapeBuilder.build(pb, 0f, s * 1.02f, 0f, s * 0.7f, s * 0.07f, s * 0.07f);
                break;
            case "frenzy": // ABOMINACIÓN: brazo desmedido y púas dorsales
                pb.setColor(Color.valueOf("7a2a58"));
                BoxShapeBuilder.build(pb, s * 0.8f, -s * 0.15f, s * 0.1f, s * 0.34f, s * 0.8f, s * 0.4f);
                pb.setColor(Color.valueOf("f0a0d0"));
                for (int i = 0; i < 3; i++) {
                    pb.setVertexTransform(tmpTransform.setToTranslation(
                        0f, s * 0.5f, (i - 1) * s * 0.34f));
                    ConeShapeBuilder.build(pb, s * 0.16f, s * 0.44f, s * 0.16f, 4);
                }
                pb.setVertexTransform(null);
                break;
            default:
                break;
        }
    }

    /**
     * Atrezo por bioma sobre el cuerpo base: cada mundo viste distinto a sus
     * monstruos para que el catálogo no se sienta repetido. Piezas pequeñas en
     * la misma part; los colores son fijos (no del bioma) para que contrasten
     * con el cuerpo ya tintado.
     */
    private void addBiomeGarb(MeshPartBuilder pb, EnemyDef def, com.pcdc.td.data.BiomeDef biome, float s) {
        boolean cone = "cone".equals(def.shape);
        boolean cyl = "cylinder".equals(def.shape);
        boolean twoHorns = !cone && !"sphere".equals(def.shape);
        float top = (cone || cyl) ? s * 0.55f : s * 0.42f;   // cota superior del cuerpo
        float eyeY = cone ? -s * 0.1f : cyl ? s * 0.22f : s * 0.16f;
        float hornTop = cyl ? s * 0.9f : s * 0.76f;
        switch (biome.id) {
            case "desert": // turbante con nudo y fajín; el rápido, cola de escorpión
                // en el cono el turbante se ENROLLA (una caja en el vértice flotaría)
                float turbanY = cone ? s * 0.3f : top + s * 0.1f;
                pb.setColor(Color.valueOf("e8d8a8"));
                BoxShapeBuilder.build(pb, 0f, turbanY, 0f, s * 0.5f, s * 0.16f, s * 0.46f);
                BoxShapeBuilder.build(pb, s * 0.2f, turbanY + s * 0.1f, 0f, s * 0.14f, s * 0.1f, s * 0.14f);
                pb.setColor(Color.valueOf("b0703c"));
                BoxShapeBuilder.build(pb, 0f, -s * 0.05f, s * 0.42f, s * 0.5f, s * 0.13f, s * 0.1f);
                if (cone) {
                    pb.setColor(Color.valueOf("c8a058"));
                    BoxShapeBuilder.build(pb, 0f, s * 0.06f, -s * 0.52f, s * 0.14f, s * 0.3f, s * 0.14f);
                    BoxShapeBuilder.build(pb, 0f, s * 0.28f, -s * 0.64f, s * 0.12f, s * 0.26f, s * 0.12f);
                    pb.setVertexTransform(tmpTransform.setToTranslation(0f, s * 0.5f, -s * 0.64f));
                    ConeShapeBuilder.build(pb, s * 0.14f, s * 0.24f, s * 0.14f, 4);
                    pb.setVertexTransform(null);
                }
                break;
            case "forest": // cornamenta ramificada y musgo en el lomo
                pb.setColor(Color.valueOf("4a3826"));
                if (twoHorns) {
                    BoxShapeBuilder.build(pb, -s * 0.26f, hornTop, 0f, s * 0.32f, s * 0.08f, s * 0.08f);
                    BoxShapeBuilder.build(pb, s * 0.26f, hornTop, 0f, s * 0.32f, s * 0.08f, s * 0.08f);
                } else {
                    // en la punta del cuerno único, no flotando sobre el vértice
                    BoxShapeBuilder.build(pb, 0f, cone ? s * 0.56f : s * 0.62f,
                        cone ? -s * 0.3f : -s * 0.15f, s * 0.34f, s * 0.08f, s * 0.08f);
                }
                pb.setColor(Color.valueOf("3c5a30"));
                BoxShapeBuilder.build(pb, 0f, top + s * 0.02f, -s * 0.08f, s * 0.62f, s * 0.07f, s * 0.5f);
                break;
            case "snow": // lomo de pelaje y carámbanos colgando del pecho
                pb.setColor(Color.valueOf("e8eef5"));
                BoxShapeBuilder.build(pb, 0f, top + s * 0.02f, -s * 0.05f, s * 0.68f, s * 0.13f, s * 0.56f);
                pb.setColor(Color.valueOf("b8dcf0"));
                BoxShapeBuilder.build(pb, -s * 0.15f, -s * 0.28f, s * 0.38f, s * 0.07f, s * 0.26f, s * 0.07f);
                BoxShapeBuilder.build(pb, s * 0.12f, -s * 0.24f, s * 0.38f, s * 0.07f, s * 0.2f, s * 0.07f);
                break;
            case "volcano": // grietas incandescentes y placa de obsidiana
                // amarillo-naranja vivo: sobre el cuerpo ya rojizo el naranja puro se perdía
                pb.setColor(Color.valueOf("ffc040"));
                BoxShapeBuilder.build(pb, 0f, s * 0.02f, s * 0.43f, s * 0.1f, s * 0.36f, s * 0.07f);
                BoxShapeBuilder.build(pb, -s * 0.43f, 0f, s * 0.1f, s * 0.07f, s * 0.28f, s * 0.09f);
                BoxShapeBuilder.build(pb, s * 0.43f, -s * 0.08f, 0f, s * 0.07f, s * 0.24f, s * 0.09f);
                pb.setColor(Color.valueOf("241c20"));
                BoxShapeBuilder.build(pb, 0f, top + s * 0.03f, -s * 0.18f, s * 0.52f, s * 0.1f, s * 0.34f);
                break;
            case "swamp": // seta en el lomo y algas colgando
                pb.setColor(Color.valueOf("d8b060"));
                BoxShapeBuilder.build(pb, s * 0.22f, top + s * 0.09f, -s * 0.1f, s * 0.11f, s * 0.18f, s * 0.11f);
                pb.setColor(Color.valueOf("c85040"));
                pb.setVertexTransform(tmpTransform.setToTranslation(s * 0.22f, top + s * 0.24f, -s * 0.1f));
                ConeShapeBuilder.build(pb, s * 0.42f, s * 0.22f, s * 0.42f, 6);
                pb.setVertexTransform(tmpTransform.setToTranslation(-s * 0.18f, top + s * 0.08f, s * 0.08f));
                ConeShapeBuilder.build(pb, s * 0.2f, s * 0.14f, s * 0.2f, 6);
                pb.setVertexTransform(null);
                pb.setColor(Color.valueOf("5c8050"));
                BoxShapeBuilder.build(pb, -s * 0.45f, -s * 0.12f, s * 0.05f, s * 0.08f, s * 0.34f, s * 0.14f);
                BoxShapeBuilder.build(pb, s * 0.46f, -s * 0.16f, -s * 0.1f, s * 0.08f, s * 0.28f, s * 0.14f);
                break;
            case "crystal": // esquirlas facetadas brotando del lomo
                pb.setColor(Color.valueOf("b8a8f0"));
                pb.setVertexTransform(tmpTransform.setToTranslation(0f, top + s * 0.2f, -s * 0.12f));
                ConeShapeBuilder.build(pb, s * 0.18f, s * 0.5f, s * 0.18f, 4);
                pb.setColor(Color.valueOf("8a7ae0"));
                pb.setVertexTransform(tmpTransform.setToTranslation(-s * 0.24f, top + s * 0.1f, s * 0.05f)
                    .rotate(Vector3.Z, 22f));
                ConeShapeBuilder.build(pb, s * 0.14f, s * 0.36f, s * 0.14f, 4);
                pb.setVertexTransform(tmpTransform.setToTranslation(s * 0.26f, top + s * 0.12f, -s * 0.02f)
                    .rotate(Vector3.Z, -20f));
                ConeShapeBuilder.build(pb, s * 0.15f, s * 0.4f, s * 0.15f, 4);
                pb.setVertexTransform(null);
                break;
            case "ruins": // máscara arcana dorada con ranuras luminosas
                pb.setColor(Color.valueOf("c8a84a"));
                BoxShapeBuilder.build(pb, 0f, eyeY + s * 0.02f, s * 0.5f, s * 0.58f, s * 0.32f, s * 0.09f);
                pb.setColor(Color.valueOf("b06cf0"));
                BoxShapeBuilder.build(pb, -s * 0.14f, eyeY, s * 0.55f, s * 0.13f, s * 0.06f, s * 0.04f);
                BoxShapeBuilder.build(pb, s * 0.14f, eyeY, s * 0.55f, s * 0.13f, s * 0.06f, s * 0.04f);
                break;
            case "storm": // cuerno-pararrayos con punta brillante y remaches
                pb.setColor(Color.valueOf("8a97a8"));
                BoxShapeBuilder.build(pb, 0f, top + s * 0.28f, 0f, s * 0.06f, s * 0.5f, s * 0.06f);
                BoxShapeBuilder.build(pb, -s * 0.4f, s * 0.02f, s * 0.24f, s * 0.08f, s * 0.08f, s * 0.08f);
                BoxShapeBuilder.build(pb, s * 0.4f, s * 0.02f, s * 0.24f, s * 0.08f, s * 0.08f, s * 0.08f);
                pb.setColor(Color.valueOf("d8e4f0"));
                pb.setVertexTransform(tmpTransform.setToTranslation(0f, top + s * 0.56f, 0f));
                SphereShapeBuilder.build(pb, s * 0.14f, s * 0.14f, s * 0.14f, 8, 6);
                pb.setVertexTransform(null);
                break;
            case "corrupt": // púas retorcidas violetas y un tercer ojo
                pb.setColor(Color.valueOf("6a2a8a"));
                pb.setVertexTransform(tmpTransform.setToTranslation(-s * 0.26f, top + s * 0.12f, -s * 0.1f)
                    .rotate(Vector3.Z, 28f));
                ConeShapeBuilder.build(pb, s * 0.14f, s * 0.4f, s * 0.14f, 4);
                pb.setVertexTransform(tmpTransform.setToTranslation(s * 0.26f, top + s * 0.12f, -s * 0.1f)
                    .rotate(Vector3.Z, -28f));
                ConeShapeBuilder.build(pb, s * 0.14f, s * 0.4f, s * 0.14f, 4);
                pb.setVertexTransform(tmpTransform.setToTranslation(0f, top + s * 0.16f, -s * 0.26f)
                    .rotate(Vector3.X, -24f));
                ConeShapeBuilder.build(pb, s * 0.13f, s * 0.36f, s * 0.13f, 4);
                pb.setVertexTransform(null);
                pb.setColor(Color.valueOf("f5f0e0"));
                BoxShapeBuilder.build(pb, 0f, eyeY + s * 0.24f, s * 0.42f, s * 0.11f, s * 0.11f, s * 0.09f);
                break;
            default: // meadow: la base de referencia, sin adornos
                break;
        }
    }

    /** Portal de SPAWN: arco oscuro con brillo violeta (de aquí salen los enemigos). */
    public Model spawnGate() {
        Model cached = towerCache.get("__spawnGate");
        if (cached != null) return cached;
        builder.begin();
        MeshPartBuilder pb = builder.part("frame", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("3a2440"))));
        BoxShapeBuilder.build(pb, -0.95f, 0.65f, 0f, 0.3f, 1.5f, 0.34f);
        BoxShapeBuilder.build(pb, 0.95f, 0.65f, 0f, 0.3f, 1.5f, 0.34f);
        BoxShapeBuilder.build(pb, 0f, 1.5f, 0f, 2.2f, 0.32f, 0.34f);
        BoxShapeBuilder.build(pb, -0.95f, 1.78f, 0f, 0.2f, 0.28f, 0.2f); // pinchos
        BoxShapeBuilder.build(pb, 0.95f, 1.78f, 0f, 0.2f, 0.28f, 0.2f);
        pb = builder.part("glow", GL20.GL_TRIANGLES, ATTRS,
            new Material(
                ColorAttribute.createDiffuse(Color.valueOf("b04ae0")),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.5f)));
        BoxShapeBuilder.build(pb, 0f, 0.68f, 0f, 1.6f, 1.32f, 0.1f);
        return manage("__spawnGate", towerCache, builder.end());
    }

    /**
     * Fortaleza de META: murete con torreones y estandarte (esto defiendes).
     * La piedra y el estandarte toman el color del bioma, así cada mundo tiene
     * su propia base en vez de repetir la misma torre gris diez veces.
     */
    public Model goalGate(BiomeDef biome) {
        return goalGate(biome, 0);
    }

    /**
     * @param starTier 0..3 según las estrellas acumuladas en el bioma: la
     *                 fortaleza gana banderines, filete dorado y remate — la
     *                 progresión se ve en el propio tablero.
     */
    public Model goalGate(BiomeDef biome, int starTier) {
        return goalGate(biome, starTier, 0);
    }

    /**
     * Fortaleza de meta. El grado de DAÑO (0 intacta, 1 tocada, 2 en ruinas)
     * le arranca almenas, abre brecha en el murete y le echa cascotes encima:
     * antes el murete estaba impecable con 20 vidas y con 2, y las vidas solo
     * se veían en la barra de arriba.
     */
    public Model goalGate(BiomeDef biome, int starTier, int damage) {
        String key = "__goalGate_" + biome.id + "_" + starTier + "_" + damage;
        Model cached = towerCache.get(key);
        if (cached != null) return cached;

        // piedra: la roca del bioma aclarada; estandarte: su tinte de enemigos
        Color stone = new Color(biome.rock()).lerp(Color.WHITE, 0.3f);
        Color banner = new Color(biome.enemyTint()).lerp(Color.valueOf("ffd24a"), 0.35f);

        builder.begin();
        builder.node().id = "stone";
        MeshPartBuilder pb = builder.part("stone", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(stone)));
        // el torreón derecho se desmocha cuando la cosa va mal
        BoxShapeBuilder.build(pb, -0.95f, 0.55f, 0f, 0.5f, 1.3f, 0.5f);
        BoxShapeBuilder.build(pb, 0.95f, damage >= 2 ? 0.4f : 0.55f, 0f,
            0.5f, damage >= 2 ? 1f : 1.3f, 0.5f);
        if (damage == 0) {
            BoxShapeBuilder.build(pb, 0f, 0.42f, 0f, 1.5f, 0.95f, 0.34f);
        } else {
            // brecha en el centro del murete: dos tramos en vez de uno
            float side = damage >= 2 ? 0.5f : 0.62f;
            BoxShapeBuilder.build(pb, -0.44f, 0.42f, 0f, side, 0.95f, 0.34f);
            BoxShapeBuilder.build(pb, 0.44f, damage >= 2 ? 0.34f : 0.42f, 0f,
                side, damage >= 2 ? 0.78f : 0.95f, 0.34f);
        }
        // almenas: se pierden con el daño
        BoxShapeBuilder.build(pb, -0.95f, 1.3f, 0f, 0.56f, 0.22f, 0.56f);
        if (damage == 0) BoxShapeBuilder.build(pb, 0.95f, 1.3f, 0f, 0.56f, 0.22f, 0.56f);
        // cascotes al pie: la piedra que ha saltado
        if (damage >= 1) {
            pb.setVertexTransform(tmpTransform.setToTranslation(0.35f, 0.09f, 0.32f)
                .rotate(Vector3.Y, 24f));
            BoxShapeBuilder.build(pb, 0f, 0f, 0f, 0.34f, 0.18f, 0.26f);
            pb.setVertexTransform(tmpTransform.setToTranslation(-0.15f, 0.07f, 0.4f)
                .rotate(Vector3.Y, -38f));
            BoxShapeBuilder.build(pb, 0f, 0f, 0f, 0.26f, 0.14f, 0.2f);
            pb.setVertexTransform(null);
        }
        if (damage >= 2) {
            pb.setVertexTransform(tmpTransform.setToTranslation(0.92f, 0.08f, -0.36f)
                .rotate(Vector3.Y, 52f));
            BoxShapeBuilder.build(pb, 0f, 0f, 0f, 0.4f, 0.16f, 0.3f);
            pb.setVertexTransform(null);
        }
        // mástil: va con la piedra para que el estandarte pueda rasgarse solo
        pb = builder.part("mast", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(stone)));
        BoxShapeBuilder.build(pb, 0f, 1.62f, 0f, 0.08f, 0.55f, 0.08f);

        // nodo aparte: la pantalla lo encoge y lo inclina según las vidas
        Node flag = builder.node();
        flag.id = "banner";
        pb = builder.part("banner", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(banner)));
        if (damage < 2) {
            BoxShapeBuilder.build(pb, 0f, 0.55f, 0.2f, 0.62f, 0.55f, 0.06f); // estandarte
            BoxShapeBuilder.build(pb, 0.2f, 1.78f, 0f, 0.34f, 0.2f, 0.05f); // banderín
        } else {
            BoxShapeBuilder.build(pb, 0f, 0.62f, 0.2f, 0.3f, 0.3f, 0.06f);  // jirón
        }

        // trofeos por estrellas del bioma (nodo aparte: no se rasga con el daño)
        if (starTier > 0) {
            Color gold = Color.valueOf("ffd24a");
            Node decor = builder.node();
            decor.id = "decor";
            pb = builder.part("decor", GL20.GL_TRIANGLES, ATTRS,
                new Material(ColorAttribute.createDiffuse(gold)));
            // 1+: banderines dorados sobre los torreones
            BoxShapeBuilder.build(pb, -0.95f, 1.62f, 0f, 0.05f, 0.42f, 0.05f);
            BoxShapeBuilder.build(pb, -0.83f, 1.72f, 0f, 0.2f, 0.13f, 0.04f);
            BoxShapeBuilder.build(pb, 0.95f, 1.62f, 0f, 0.05f, 0.42f, 0.05f);
            BoxShapeBuilder.build(pb, 1.07f, 1.72f, 0f, 0.2f, 0.13f, 0.04f);
            if (starTier >= 2) {
                // 2: filete dorado a lo largo del murete
                BoxShapeBuilder.build(pb, 0f, 0.93f, 0f, 1.52f, 0.09f, 0.38f);
            }
            if (starTier >= 3) {
                // 3: remate en el mástil (bioma perfecto)
                pb.setVertexTransform(tmpTransform.setToTranslation(0f, 1.98f, 0f));
                SphereShapeBuilder.build(pb, 0.2f, 0.2f, 0.2f, 8, 6);
                pb.setVertexTransform(null);
            }
        }
        return manage(key, towerCache, builder.end());
    }

    /** Escombros que tapian una ruta todavía cerrada (color de roca del bioma). */
    public Model rubble(BiomeDef biome) {
        String key = "__rubble_" + biome.id;
        Model cached = towerCache.get(key);
        if (cached != null) return cached;
        Color rock = new Color(biome.rock());
        builder.begin();
        MeshPartBuilder pb = builder.part("rubble", GL20.GL_TRIANGLES, ATTRS,
            new Material(ColorAttribute.createDiffuse(rock)));
        // bloques irregulares apilados de lado a lado de la calle
        BoxShapeBuilder.build(pb, -0.7f, 0.35f, 0f, 0.7f, 0.7f, 0.5f);
        BoxShapeBuilder.build(pb, 0f, 0.5f, 0.06f, 0.8f, 1f, 0.44f);
        BoxShapeBuilder.build(pb, 0.7f, 0.3f, -0.05f, 0.66f, 0.6f, 0.5f);
        BoxShapeBuilder.build(pb, -0.3f, 0.85f, -0.1f, 0.4f, 0.4f, 0.34f);
        BoxShapeBuilder.build(pb, 0.36f, 0.75f, 0.08f, 0.36f, 0.36f, 0.32f);
        return manage(key, towerCache, builder.end());
    }

    /** Cristal amplificador (bioma cavernas). */
    public Model crystal() {
        Model cached = towerCache.get("__crystal");
        if (cached != null) return cached;
        Model model = builder.createCone(0.5f, 0.9f, 0.5f, 6,
            new Material(
                ColorAttribute.createDiffuse(Color.valueOf("7de8e8")),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.85f)),
            ATTRS);
        return manage("__crystal", towerCache, model);
    }

    /** Disco de sierra volador (proyectil perforante del Lanzasierras). */
    private Model sawDiscModel;

    public Model sawDisc(TowerDef def) {
        if (sawDiscModel != null) return sawDiscModel;
        builder.begin();
        MeshPartBuilder pb = builder.part("disc", GL20.GL_TRIANGLES,
            ATTRS | Usage.ColorUnpacked, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        pb.setColor(Color.valueOf("cfd8de"));
        CylinderShapeBuilder.build(pb, 0.42f, 0.07f, 0.42f, 12);
        pb.setColor(Color.valueOf("8a949c"));
        for (int t = 0; t < 8; t++) { // dientes: puntas cónicas hacia fuera
            pb.setVertexTransform(tmpTransform.setToRotation(Vector3.Y, t * 45f)
                .translate(0.26f, 0f, 0f).rotate(Vector3.Z, -90f));
            ConeShapeBuilder.build(pb, 0.09f, 0.13f, 0.07f, 4);
        }
        pb.setVertexTransform(null);
        sawDiscModel = manage(builder.end());
        return sawDiscModel;
    }

    public Model projectile(TowerDef def) {
        if (def.pierce) return sawDisc(def);
        Model cached = projectileCache.get(def.id);
        if (cached != null) return cached;
        Color c = new Color(def.color()).lerp(Color.WHITE, 0.35f);
        TowerSkins.fxTint(activeSkin, c);
        // los splash llevan proyectil gordo; el resto, bala brillante
        float s = def.splashRadius > 0f ? 0.26f : 0.16f;
        Model model = builder.createSphere(s, s, s, 10, 8,
            new Material(ColorAttribute.createDiffuse(c)), ATTRS);
        return manage(def.id, projectileCache, model);
    }

    /** Esfera translúcida del color de la torre para estelas de proyectil. */
    public Model projectileTrail(TowerDef def) {
        String key = "trail:" + def.id;
        Model cached = projectileCache.get(key);
        if (cached != null) return cached;
        Color c = new Color(def.color()).lerp(Color.WHITE, 0.2f);
        TowerSkins.fxTint(activeSkin, c);
        Model model = builder.createSphere(1f, 1f, 1f, 8, 6,
            new Material(
                ColorAttribute.createDiffuse(c),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.5f)),
            ATTRS);
        return manage(key, projectileCache, model);
    }

    // ---- visuales auxiliares ----

    /** Prisma unitario a lo largo de +Z (z en [0,1]); se escala para rayos y tracers. */
    public Model beam() {
        if (beamModel != null) return beamModel;
        builder.begin();
        MeshPartBuilder pb = builder.part("beam", GL20.GL_TRIANGLES, ATTRS,
            fxMaterial(Color.WHITE, 0.85f));
        BoxShapeBuilder.build(pb, 0f, 0f, 0.5f, 1f, 1f, 1f);
        beamModel = manage(builder.end());
        return beamModel;
    }

    /** Cono unitario translúcido con el vértice en el origen abriéndose hacia +Z. */
    public Model flameCone() {
        if (flameConeModel != null) return flameConeModel;
        builder.begin();
        Node n = builder.node();
        n.rotation.setFromAxis(Vector3.X, -90f);
        n.translation.set(0f, 0f, 0.5f);
        MeshPartBuilder pb = builder.part("cone", GL20.GL_TRIANGLES, ATTRS,
            fxMaterial(Color.valueOf("ff9a3c"), 0.14f));
        ConeShapeBuilder.build(pb, 2f, 1f, 2f, 14);
        flameConeModel = manage(builder.end());
        return flameConeModel;
    }

    /** Disco de radio 1 para visualizar el rango; se escala y tinta por instancia. */
    public Model rangeDisc() {
        if (rangeDiscModel != null) return rangeDiscModel;
        rangeDiscModel = manage(builder.createCylinder(2f, 0.02f, 2f, 32,
            fxMaterial(Color.WHITE, 0.2f), ATTRS));
        return rangeDiscModel;
    }

    private Model moteModel;

    /**
     * Esquirla de luz (bioctaedro): dos pirámides unidas por la base. Se usa
     * para el aura del tanque de mando; una esfera quedaba demasiado sosa.
     */
    public Model mote() {
        if (moteModel != null) return moteModel;
        Material mat = fxMaterial(Color.WHITE, 1f);
        builder.begin();
        MeshPartBuilder pb = builder.part("mote", GL20.GL_TRIANGLES, ATTRS, mat);
        ConeShapeBuilder.build(pb, 1f, 1f, 1f, 4);                       // punta arriba
        pb.setVertexTransform(tmpTransform.setToRotation(Vector3.Z, 180f));
        ConeShapeBuilder.build(pb, 1f, 1f, 1f, 4);                       // punta abajo
        pb.setVertexTransform(null);
        moteModel = manage(builder.end());
        return moteModel;
    }

    private Model fxSphereModel, shadowModel;

    /** Esfera unitaria translúcida para efectos de impacto/muerte (tinte por instancia). */
    public Model fxSphere() {
        if (fxSphereModel != null) return fxSphereModel;
        fxSphereModel = manage(builder.createSphere(1f, 1f, 1f, 10, 8,
            fxMaterial(Color.WHITE, 0.7f), ATTRS));
        return fxSphereModel;
    }

    /** Sombra falsa: quad oscuro translúcido bajo las entidades. */
    public Model shadow() {
        if (shadowModel != null) return shadowModel;
        shadowModel = manage(builder.createBox(1f, 0.01f, 1f,
            fxMaterial(Color.BLACK, 0.3f), ATTRS));
        return shadowModel;
    }

    private Model routeArrowModel;

    /**
     * Punta de flecha plana (cono de 3 lados aplastado) mirando a +Z: recorre el
     * camino los primeros segundos para enseñar por dónde vendrán los enemigos.
     */
    public Model routeArrow() {
        if (routeArrowModel != null) return routeArrowModel;
        Material mat = fxMaterial(Color.WHITE, 0.8f);
        builder.begin();
        MeshPartBuilder pb = builder.part("arrow", GL20.GL_TRIANGLES, ATTRS, mat);
        // cono tumbado: la punta (eje +Y del cono) pasa a señalar +Z
        pb.setVertexTransform(tmpTransform.setToRotation(Vector3.X, 90f));
        ConeShapeBuilder.build(pb, 0.5f, 0.55f, 0.09f, 3);
        pb.setVertexTransform(null);
        routeArrowModel = manage(builder.end());
        return routeArrowModel;
    }

    /** Marcador translúcido de la celda bajo el cursor/dedo. Se tinta por instancia. */
    public Model cellHighlight() {
        if (highlightModel != null) return highlightModel;
        Material mat = fxMaterial(Color.WHITE, 0.45f);
        highlightModel = manage(builder.createBox(0.98f, 0.05f, 0.98f, mat, ATTRS));
        return highlightModel;
    }

    private Model helicopterModel, parachuteModel;

    /**
     * Helicóptero de transporte que trae cada tanque nuevo. Mira a +Z, con el
     * rotor en un nodo aparte ("rotor") para poder girarlo desde el render.
     */
    public Model helicopter() {
        if (helicopterModel != null) return helicopterModel;
        Color body = Color.valueOf("46543c");
        Color dark = Color.valueOf("2a3226");
        builder.begin();

        builder.node().id = "hull";
        MeshPartBuilder pb = builder.part("hull", GL20.GL_TRIANGLES,
            ATTRS | Usage.ColorUnpacked, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        pb.setColor(body);
        BoxShapeBuilder.build(pb, 0f, 0f, 0.1f, 0.62f, 0.5f, 1.15f);   // fuselaje
        BoxShapeBuilder.build(pb, 0f, 0.06f, -0.95f, 0.16f, 0.16f, 1f); // cola
        BoxShapeBuilder.build(pb, 0f, 0.24f, -1.42f, 0.09f, 0.42f, 0.24f); // deriva
        pb.setColor(Color.valueOf("9ad8e8"));
        BoxShapeBuilder.build(pb, 0f, 0.04f, 0.72f, 0.46f, 0.3f, 0.3f); // cabina
        pb.setColor(dark);
        BoxShapeBuilder.build(pb, -0.3f, -0.36f, 0.05f, 0.09f, 0.09f, 1.1f); // patines
        BoxShapeBuilder.build(pb, 0.3f, -0.36f, 0.05f, 0.09f, 0.09f, 1.1f);
        BoxShapeBuilder.build(pb, -0.18f, -0.18f, 0.05f, 0.06f, 0.28f, 0.06f);
        BoxShapeBuilder.build(pb, 0.18f, -0.18f, 0.05f, 0.06f, 0.28f, 0.06f);
        BoxShapeBuilder.build(pb, 0f, 0.28f, 0f, 0.14f, 0.14f, 0.14f);  // mástil
        // rotor de cola (gira con el modelo, no hace falta nodo propio)
        BoxShapeBuilder.build(pb, 0.1f, 0.24f, -1.5f, 0.05f, 0.42f, 0.06f);

        Node rotor = builder.node();
        rotor.id = "rotor";
        rotor.translation.set(0f, 0.38f, 0f);
        MeshPartBuilder rb = builder.part("rotor", GL20.GL_TRIANGLES,
            ATTRS | Usage.ColorUnpacked, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        rb.setColor(dark);
        // palas de 1,8: con 2,6 el aparato ocupaba casi tres casillas y desde
        // cerca parecía un amasijo de tablones
        BoxShapeBuilder.build(rb, 0f, 0f, 0f, 1.8f, 0.04f, 0.14f);
        BoxShapeBuilder.build(rb, 0f, 0f, 0f, 0.14f, 0.04f, 1.8f);

        helicopterModel = manage(builder.end());
        return helicopterModel;
    }

    /** Paracaídas: cúpula, banda de color y cuatro cuerdas. */
    public Model parachute() {
        if (parachuteModel != null) return parachuteModel;
        builder.begin();
        MeshPartBuilder pb = builder.part("chute", GL20.GL_TRIANGLES,
            ATTRS | Usage.ColorUnpacked, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        // la cúpula se sube para que su BORDE quede en y=0: así la banda de
        // color cae en el borde y no en mitad del cono (parecía una seta)
        pb.setColor(Color.valueOf("e8e2d2"));
        pb.setVertexTransform(tmpTransform.setToTranslation(0f, 0.31f, 0f));
        ConeShapeBuilder.build(pb, 1.5f, 0.62f, 1.5f, 10);
        pb.setVertexTransform(null);
        pb.setColor(Color.valueOf("cf5a3c"));
        CylinderShapeBuilder.build(pb, 1.52f, 0.09f, 1.52f, 10);        // banda del borde
        pb.setColor(Color.valueOf("3a3630"));
        for (int i = 0; i < 4; i++) {
            float ang = (45f + i * 90f) * com.badlogic.gdx.math.MathUtils.degreesToRadians;
            BoxShapeBuilder.build(pb,
                com.badlogic.gdx.math.MathUtils.sin(ang) * 0.52f, -0.44f,
                com.badlogic.gdx.math.MathUtils.cos(ang) * 0.52f, 0.035f, 0.88f, 0.035f);
        }
        parachuteModel = manage(builder.end());
        return parachuteModel;
    }

    private Model portalModel;

    /**
     * Portal de las ruinas: aro vertical de 12 segmentos con la membrana
     * dentro. La simulación ya hacía saltar a los enemigos, pero no se dibujaba
     * NADA, así que el jugador no sabía de dónde a dónde iban. Gira sobre su
     * eje en el render, así se lee desde cualquier ángulo de cámara.
     */
    public Model portalRing() {
        if (portalModel != null) return portalModel;
        builder.begin();
        MeshPartBuilder pb = builder.part("portal", GL20.GL_TRIANGLES,
            ATTRS | Usage.ColorUnpacked, fxMaterial(Color.WHITE, 0.9f));
        pb.setColor(Color.WHITE); // el tinte real lo pone cada instancia
        float r = 0.62f, cy = 0.66f;
        for (int i = 0; i < 12; i++) {
            float ang = i * 30f;
            float rad = ang * com.badlogic.gdx.math.MathUtils.degreesToRadians;
            pb.setVertexTransform(tmpTransform.setToTranslation(
                    com.badlogic.gdx.math.MathUtils.sin(rad) * r,
                    cy + com.badlogic.gdx.math.MathUtils.cos(rad) * r, 0f)
                .rotate(Vector3.Z, -ang));
            BoxShapeBuilder.build(pb, 0f, 0f, 0f, 0.2f, 0.11f, 0.12f);
        }
        pb.setVertexTransform(null);
        // membrana: se ve el "otro lado" a través del aro
        pb.setColor(new Color(1f, 1f, 1f, 0.35f));
        BoxShapeBuilder.build(pb, 0f, cy, 0f, 1.05f, 1.05f, 0.04f);
        portalModel = manage(builder.end());
        return portalModel;
    }

    private Model iceModel;

    /**
     * Placa de HIELO de la nieve: como parche translúcido azulado sobre suelo
     * ya blanco no se distinguía nada. Ahora es una losa con marco, grietas
     * claras y dos esquirlas levantadas: se lee como hielo de un vistazo.
     * Una sola part con colores de vértice = un draw call.
     */
    public Model icePatch() {
        if (iceModel != null) return iceModel;
        builder.begin();
        MeshPartBuilder pb = builder.part("ice", GL20.GL_TRIANGLES,
            ATTRS | Usage.ColorUnpacked, fxMaterial(Color.WHITE, 0.92f));

        pb.setColor(Color.valueOf("bfe4f7"));                       // losa helada
        BoxShapeBuilder.build(pb, 0f, 0f, 0f, 0.94f, 0.06f, 0.94f);
        pb.setColor(Color.valueOf("5fa8cc"));                       // marco escarchado
        BoxShapeBuilder.build(pb, 0f, 0.02f, -0.45f, 0.94f, 0.05f, 0.08f);
        BoxShapeBuilder.build(pb, 0f, 0.02f, 0.45f, 0.94f, 0.05f, 0.08f);
        BoxShapeBuilder.build(pb, -0.45f, 0.02f, 0f, 0.08f, 0.05f, 0.94f);
        BoxShapeBuilder.build(pb, 0.45f, 0.02f, 0f, 0.08f, 0.05f, 0.94f);

        pb.setColor(Color.valueOf("f2fbff"));                       // grietas
        float[][] cracks = {{-0.16f, -0.1f, 28f}, {0.2f, 0.14f, -42f}, {0.02f, 0.3f, 68f}};
        for (float[] c : cracks) {
            pb.setVertexTransform(tmpTransform.setToTranslation(c[0], 0.035f, c[1])
                .rotate(Vector3.Y, c[2]));
            BoxShapeBuilder.build(pb, 0f, 0f, 0f, 0.62f, 0.03f, 0.05f);
        }
        pb.setVertexTransform(null);

        pb.setColor(Color.valueOf("9adcf5"));                       // esquirlas levantadas
        float[][] shards = {{-0.28f, 0.26f, 0.2f}, {0.3f, -0.24f, 0.15f}};
        for (float[] s : shards) {
            pb.setVertexTransform(tmpTransform.setToTranslation(s[0], 0.04f, s[1])
                .rotate(Vector3.Z, 14f));
            ConeShapeBuilder.build(pb, s[2], s[2] * 1.6f, s[2], 4);
        }
        pb.setVertexTransform(null);

        iceModel = manage(builder.end());
        return iceModel;
    }

    private Model manage(Model model) {
        models.add(model);
        return model;
    }

    private Model manage(String key, ObjectMap<String, Model> cache, Model model) {
        models.add(model);
        cache.put(key, model);
        return model;
    }

    @Override
    public void dispose() {
        for (Model m : models) m.dispose();
        models.clear();
        towerCache.clear();
        enemyCache.clear();
        projectileCache.clear();
        beamModel = flameConeModel = rangeDiscModel = highlightModel = null;
        fxSphereModel = shadowModel = moteModel = routeArrowModel = iceModel = null;
        helicopterModel = parachuteModel = portalModel = sawDiscModel = null;
    }
}
