package com.pcdc.td.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Genera los iconos de la app renderizando un tanque voxel 3D (temática
 * Tank Defense) en una ventana invisible:
 *  - Android: ic_launcher.png legacy + ic_launcher_foreground.png (adaptive) por densidad
 *  - Desktop: assets/icons/iconNN.png (icono de ventana) y lwjgl3/icon.ico (jpackage)
 * Ejecutar con gradlew :lwjgl3:generateIcons (workingDir = assets).
 */
public class IconTool extends ApplicationAdapter {

    private static final int[] LEGACY_SIZES = {48, 72, 96, 144, 192};
    private static final int[] FOREGROUND_SIZES = {108, 162, 216, 324, 432};
    private static final String[] DENSITY_DIRS = {
        "mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"
    };
    private static final int[] WINDOW_SIZES = {16, 32, 64, 128};
    private static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 256};

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setInitialVisible(false);
        cfg.setWindowedMode(64, 64);
        cfg.disableAudio(true);
        cfg.setTitle("IconTool");
        new Lwjgl3Application(new IconTool(), cfg);
    }

    @Override
    public void create() {
        try {
            generate();
            Gdx.app.log("IconTool", "iconos regenerados");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Gdx.app.exit();
        }
    }

    private void generate() throws IOException {
        Pixmap tower = renderTower(512);

        // logotipo del juego: se usa en el menú y, reducido, dentro del icono
        Pixmap wordmark = renderWordmark(1024);
        FileHandle ui = Gdx.files.local("ui");
        ui.mkdirs();
        PixmapIO.writePNG(ui.child("logo.png"), wordmark);

        // legacy / desktop: fondo azul noche con franja de césped y camino
        Pixmap legacy = composeLegacy(tower, wordmark, 512);

        FileHandle res = Gdx.files.local("../android/src/main/res");
        for (int i = 0; i < DENSITY_DIRS.length; i++) {
            FileHandle dir = res.child(DENSITY_DIRS[i]);
            writeScaled(legacy, dir.child("ic_launcher.png"), LEGACY_SIZES[i]);
            // foreground adaptive: todo dentro del 66% central, que es lo que
            // sobrevive al recorte circular de los launchers
            int fgSize = FOREGROUND_SIZES[i];
            Pixmap fg = new Pixmap(fgSize, fgSize, Pixmap.Format.RGBA8888);
            fg.setFilter(Pixmap.Filter.BiLinear);
            int tank = Math.round(fgSize * 0.46f);
            int tankX = (fgSize - tank) / 2;
            int tankY = Math.round(fgSize * 0.17f);
            fg.drawPixmap(tower, 0, 0, tower.getWidth(), tower.getHeight(), tankX, tankY, tank, tank);
            int logoW = Math.round(fgSize * 0.6f);
            int logoH = Math.round(logoW * wordmark.getHeight() / (float) wordmark.getWidth());
            fg.drawPixmap(wordmark, 0, 0, wordmark.getWidth(), wordmark.getHeight(),
                (fgSize - logoW) / 2, Math.round(fgSize * 0.6f), logoW, logoH);
            PixmapIO.writePNG(dir.child("ic_launcher_foreground.png"), fg);
            fg.dispose();
        }

        // icono de ventana desktop (assets/icons, se empaqueta en el jar)
        FileHandle icons = Gdx.files.local("icons");
        icons.mkdirs();
        for (int size : WINDOW_SIZES) {
            writeScaled(legacy, icons.child("icon" + size + ".png"), size);
        }

        // .ico para jpackage (entradas PNG, válidas desde Vista)
        FileHandle ico = Gdx.files.local("../lwjgl3/icon.ico");
        writeIco(ico, legacy);

        legacy.dispose();
        wordmark.dispose();
        tower.dispose();
    }

    /**
     * Logotipo del juego: "TANK" grande sobre "DEFENSE" espaciado, con contorno
     * grueso, sombra proyectada, relleno en degradado y galones angulares.
     *
     * El relleno se pinta en BLANCO puro y el contorno en oscuro; al releer el
     * framebuffer, todo píxel casi blanco se sustituye por el degradado. Así se
     * consigue el degradado sin tocar shaders.
     */
    private Pixmap renderWordmark(int width) {
        int height = Math.round(width * 0.46f);
        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        com.badlogic.gdx.graphics.g2d.SpriteBatch batch =
            new com.badlogic.gdx.graphics.g2d.SpriteBatch();

        BitmapFont big = logoFont(Math.round(width * 0.245f), Math.round(width * 0.019f));
        BitmapFont small = logoFont(Math.round(width * 0.115f), Math.round(width * 0.012f));

        fbo.begin();
        com.badlogic.gdx.utils.ScreenUtils.clear(0f, 0f, 0f, 0f, false);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        batch.begin();
        drawCentered(batch, big, "TANK", width / 2f, height * 0.94f);
        // "DEFENSE" con letras separadas: el aire entre ellas es lo que hace
        // que un logo parezca de juego y no un rótulo cualquiera
        drawSpaced(batch, small, "DEFENSE", width / 2f, height * 0.38f, width * 0.028f);
        batch.end();

        Pixmap raw = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
        fbo.end();
        fbo.dispose();
        batch.dispose();
        big.dispose();
        small.dispose();

        Pixmap logo = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        logo.setBlending(Pixmap.Blending.None);
        // el framebuffer se lee volteado
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int px = raw.getPixel(x, height - 1 - y);
                int a = px & 0xFF;
                if (a == 0) continue;
                int r = (px >>> 24) & 0xFF, g = (px >>> 16) & 0xFF, b = (px >>> 8) & 0xFF;
                if (r > 150 && g > 150 && b > 150) {
                    // relleno: degradado vertical dorado → naranja quemado
                    float t = y / (float) height;
                    float rr = lerp(1f, 0.86f, t), gg = lerp(0.86f, 0.42f, t), bb = lerp(0.33f, 0.12f, t);
                    logo.drawPixel(x, y, rgba(rr, gg, bb, a / 255f));
                } else {
                    logo.drawPixel(x, y, px);
                }
            }
        }
        raw.dispose();
        addLogoAccents(logo);
        return logo;
    }

    /** Fuente del logo: contorno grueso oscuro y sombra proyectada. */
    private BitmapFont logoFont(int size, int border) {
        com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator gen =
            new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator(
                Gdx.files.internal("fonts/Roboto-Regular.ttf"));
        com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter p =
            new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.characters = "TANKDEFS";
        p.color = Color.WHITE;              // marca el relleno para el degradado
        p.borderWidth = border;
        p.borderColor = Color.valueOf("14202c");
        p.borderStraight = false;
        p.shadowOffsetY = Math.max(2, border / 2);
        p.shadowColor = new Color(0f, 0f, 0f, 0.55f);
        p.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        p.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        BitmapFont font = gen.generateFont(p);
        gen.dispose();
        return font;
    }

    private void drawCentered(com.badlogic.gdx.graphics.g2d.SpriteBatch batch,
                              BitmapFont font, String text, float cx, float topY) {
        com.badlogic.gdx.graphics.g2d.GlyphLayout gl =
            new com.badlogic.gdx.graphics.g2d.GlyphLayout(font, text);
        font.draw(batch, text, cx - gl.width / 2f, topY);
    }

    /** Dibuja letra a letra con separación extra (tracking). */
    private void drawSpaced(com.badlogic.gdx.graphics.g2d.SpriteBatch batch,
                            BitmapFont font, String text, float cx, float topY, float extra) {
        com.badlogic.gdx.graphics.g2d.GlyphLayout gl = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
        float total = 0f;
        for (int i = 0; i < text.length(); i++) {
            gl.setText(font, String.valueOf(text.charAt(i)));
            total += gl.width + (i < text.length() - 1 ? extra : 0f);
        }
        float x = cx - total / 2f;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            gl.setText(font, ch);
            font.draw(batch, ch, x, topY);
            x += gl.width + extra;
        }
    }

    /** Galones y filetes: las "formas" que rematan el logotipo. */
    private void addLogoAccents(Pixmap logo) {
        int w = logo.getWidth(), h = logo.getHeight();
        logo.setBlending(Pixmap.Blending.SourceOver);
        int barY = Math.round(h * 0.60f);
        int barH = Math.max(3, Math.round(h * 0.022f));
        // filete que separa las dos palabras, con degradado hacia los extremos
        for (int x = 0; x < w; x++) {
            float d = Math.abs(x - w / 2f) / (w / 2f);
            float a = Math.max(0f, 1f - d * 1.25f);
            if (a <= 0f) continue;
            logo.drawPixel(x, barY, rgba(1f, 0.82f, 0.28f, a));
            for (int k = 1; k < barH; k++) logo.drawPixel(x, barY + k, rgba(0.9f, 0.62f, 0.18f, a));
        }
        // galones a ambos lados del filete
        int cy = barY + barH / 2;
        int cw = Math.round(h * 0.075f);
        for (int s = -1; s <= 1; s += 2) {
            int base = s < 0 ? Math.round(w * 0.085f) : Math.round(w * 0.915f);
            for (int k = 0; k < 2; k++) {
                int ox = base + s * k * (cw + 3);
                logo.setColor(rgba(1f, 0.78f, 0.24f, 1f));
                logo.fillTriangle(ox, cy - cw, ox + s * cw, cy, ox, cy + cw);
                logo.setColor(rgba(0.08f, 0.13f, 0.18f, 1f));
                logo.fillTriangle(ox + s * 4, cy - cw / 2, ox + s * (cw - 2), cy, ox + s * 4, cy + cw / 2);
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.min(1f, Math.max(0f, t));
    }

    private static int rgba(float r, float g, float b, float a) {
        return ((int) (r * 255) << 24) | ((int) (g * 255) << 16) | ((int) (b * 255) << 8) | (int) (a * 255);
    }

    /** Torre 3D sobre fondo transparente, misma cámara que los iconos del HUD. */
    private Pixmap renderTower(int size) {
        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, size, size, true);
        ModelBatch batch = new ModelBatch();
        Environment env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.62f, 0.62f, 0.66f, 1f));
        env.add(new DirectionalLight().set(0.95f, 0.92f, 0.85f, -0.4f, -0.8f, -0.3f));
        PerspectiveCamera cam = new PerspectiveCamera(36, size, size);
        cam.position.set(1.3f, 1.0f, 1.3f);
        cam.lookAt(0f, 0.26f, 0f);
        cam.near = 0.1f;
        cam.far = 10f;
        cam.update();

        com.badlogic.gdx.graphics.g3d.Model tank = buildTank();
        ModelInstance inst = new ModelInstance(tank);
        inst.transform.rotate(com.badlogic.gdx.math.Vector3.Y, 24f); // cañón hacia el frente-derecha
        fbo.begin();
        com.badlogic.gdx.utils.ScreenUtils.clear(0f, 0f, 0f, 0f, true);
        batch.begin(cam);
        batch.render(inst, env);
        batch.end();
        Pixmap pm = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0, size, size);
        fbo.end();
        fbo.dispose();
        batch.dispose();
        tank.dispose();
        // la lectura del framebuffer llega volteada: enderezarla fila a fila
        Pixmap flipped = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            flipped.drawPixmap(pm, 0, y, size, 1, 0, size - 1 - y, size, 1);
        }
        pm.dispose();
        return flipped;
    }

    /** Tanque voxel al estilo del juego: orugas, casco, torreta, cañón y escotilla. */
    private com.badlogic.gdx.graphics.g3d.Model buildTank() {
        com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mb =
            new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
        long attrs = com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal;
        mb.begin();

        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder tracks = mb.part("tracks",
            com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, attrs,
            new com.badlogic.gdx.graphics.g3d.Material(
                ColorAttribute.createDiffuse(Color.valueOf("31363c"))));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
            .build(tracks, -0.31f, 0.11f, 0f, 0.24f, 0.22f, 0.98f);
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
            .build(tracks, 0.31f, 0.11f, 0f, 0.24f, 0.22f, 0.98f);

        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder hull = mb.part("hull",
            com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, attrs,
            new com.badlogic.gdx.graphics.g3d.Material(
                ColorAttribute.createDiffuse(Color.valueOf("5c7a3a"))));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
            .build(hull, 0f, 0.28f, 0f, 0.68f, 0.16f, 0.92f);
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
            .build(hull, 0f, 0.35f, 0.34f, 0.5f, 0.08f, 0.26f); // glacis frontal

        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder turret = mb.part("turret",
            com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, attrs,
            new com.badlogic.gdx.graphics.g3d.Material(
                ColorAttribute.createDiffuse(Color.valueOf("48662e"))));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
            .build(turret, 0f, 0.46f, -0.06f, 0.42f, 0.2f, 0.5f);

        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder gun = mb.part("gun",
            com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, attrs,
            new com.badlogic.gdx.graphics.g3d.Material(
                ColorAttribute.createDiffuse(Color.valueOf("3a4046"))));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
            .build(gun, 0f, 0.48f, 0.45f, 0.09f, 0.09f, 0.55f);
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
            .build(gun, 0f, 0.48f, 0.7f, 0.14f, 0.14f, 0.1f); // freno de boca

        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder trim = mb.part("trim",
            com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, attrs,
            new com.badlogic.gdx.graphics.g3d.Material(
                ColorAttribute.createDiffuse(Color.valueOf("ffd54a"))));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
            .build(trim, -0.1f, 0.58f, -0.12f, 0.16f, 0.05f, 0.16f); // escotilla dorada
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
            .build(trim, 0f, 0.4f, -0.32f, 0.46f, 0.05f, 0.06f);     // franja trasera

        return mb.end();
    }

    /** Fondo azul noche + césped + tanque arriba y el logotipo abajo. */
    private Pixmap composeLegacy(Pixmap tower, Pixmap wordmark, int s) {
        Pixmap out = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        out.setFilter(Pixmap.Filter.BiLinear);
        out.setColor(Color.valueOf("1c2a38"));
        out.fill();
        out.setColor(Color.valueOf("4a7a3c")); // césped
        out.fillRectangle(0, Math.round(s * 0.56f), s, Math.round(s * 0.44f));
        out.setColor(Color.valueOf("d0b070")); // camino
        out.fillRectangle(0, Math.round(s * 0.60f), s, Math.round(s * 0.09f));
        int tank = Math.round(s * 0.72f);
        out.drawPixmap(tower, 0, 0, tower.getWidth(), tower.getHeight(),
            (s - tank) / 2, Math.round(s * -0.06f), tank, tank);
        // franja oscura de fondo para que el logotipo se lea sobre el césped
        out.setColor(rgba(0.06f, 0.1f, 0.14f, 0.72f));
        out.fillRectangle(0, Math.round(s * 0.63f), s, Math.round(s * 0.37f));
        int logoW = Math.round(s * 0.94f);
        int logoH = Math.round(logoW * wordmark.getHeight() / (float) wordmark.getWidth());
        out.drawPixmap(wordmark, 0, 0, wordmark.getWidth(), wordmark.getHeight(),
            (s - logoW) / 2, Math.round(s * 0.99f) - logoH, logoW, logoH);
        return out;
    }

    private void writeScaled(Pixmap src, FileHandle out, int size) {
        Pixmap scaled = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        scaled.setFilter(Pixmap.Filter.BiLinear);
        scaled.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(), 0, 0, size, size);
        PixmapIO.writePNG(out, scaled);
        scaled.dispose();
    }

    /** ICO con entradas PNG (cabecera ICONDIR + ICONDIRENTRY por tamaño). */
    private void writeIco(FileHandle file, Pixmap src) throws IOException {
        byte[][] pngs = new byte[ICO_SIZES.length][];
        for (int i = 0; i < ICO_SIZES.length; i++) {
            Pixmap scaled = new Pixmap(ICO_SIZES[i], ICO_SIZES[i], Pixmap.Format.RGBA8888);
            scaled.setFilter(Pixmap.Filter.BiLinear);
            scaled.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(), 0, 0, ICO_SIZES[i], ICO_SIZES[i]);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PixmapIO.PNG png = new PixmapIO.PNG();
            png.write(bos, scaled);
            png.dispose();
            pngs[i] = bos.toByteArray();
            scaled.dispose();
        }
        try (OutputStream os = file.write(false)) {
            // ICONDIR
            writeShort(os, 0);
            writeShort(os, 1);
            writeShort(os, ICO_SIZES.length);
            int offset = 6 + 16 * ICO_SIZES.length;
            for (int i = 0; i < ICO_SIZES.length; i++) {
                int dim = ICO_SIZES[i] >= 256 ? 0 : ICO_SIZES[i];
                os.write(dim);          // ancho (0 = 256)
                os.write(dim);          // alto
                os.write(0);            // paleta
                os.write(0);            // reservado
                writeShort(os, 1);      // planos
                writeShort(os, 32);     // bits por píxel
                writeInt(os, pngs[i].length);
                writeInt(os, offset);
                offset += pngs[i].length;
            }
            for (byte[] png : pngs) os.write(png);
        }
    }

    private static void writeShort(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
    }

    private static void writeInt(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
        os.write((v >> 16) & 0xFF);
        os.write((v >> 24) & 0xFF);
    }
}
