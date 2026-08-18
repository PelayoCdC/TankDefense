package com.pcdc.td.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pcdc.td.data.BiomeDef;
import com.pcdc.td.data.Catalogs;
import com.pcdc.td.data.EnemyDef;
import com.pcdc.td.render.ModelFactory;

/**
 * Herramienta de verificación: renderiza el catálogo completo de enemigos
 * (filas = biomas, columnas = tipos) a un único PNG para revisar de un vistazo
 * el atuendo por bioma. No forma parte del juego.
 * Ejecutar con gradlew :lwjgl3:enemyGallery -Pout=<ruta.png> (workingDir = assets).
 */
public class EnemyGalleryTool extends ApplicationAdapter {

    private static final int CELL = 150;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setInitialVisible(false);
        cfg.setWindowedMode(64, 64);
        cfg.disableAudio(true);
        cfg.setTitle("EnemyGalleryTool");
        new Lwjgl3Application(new EnemyGalleryTool(), cfg);
    }

    @Override
    public void create() {
        Catalogs catalogs = new Catalogs();
        catalogs.load();
        ModelFactory factory = new ModelFactory();
        ModelBatch batch = new ModelBatch();
        Environment env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.64f, 1f));
        env.add(new DirectionalLight().set(0.95f, 0.92f, 0.86f, -0.4f, -0.8f, -0.3f));

        int cols = catalogs.enemies.size, rows = catalogs.biomes.size;
        Pixmap sheet = new Pixmap(cols * CELL, rows * CELL, Pixmap.Format.RGBA8888);
        sheet.setColor(0.09f, 0.12f, 0.16f, 1f);
        sheet.fill();

        for (int r = 0; r < rows; r++) {
            BiomeDef biome = catalogs.biomes.get(r);
            for (int c = 0; c < cols; c++) {
                EnemyDef def = catalogs.enemies.get(c);
                // cámara frontal-lateral, encuadre según el tamaño del bicho
                PerspectiveCamera cam = new PerspectiveCamera(38, CELL, CELL);
                float d = 1.35f + def.size * 1.15f;
                cam.position.set(d * 0.62f, d * 0.72f, d);
                cam.lookAt(0f, def.size * 0.28f, 0f);
                cam.near = 0.1f;
                cam.far = 12f;
                cam.update();

                FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, CELL, CELL, true);
                fbo.begin();
                ScreenUtils.clear(0f, 0f, 0f, 0f, true);
                batch.begin(cam);
                batch.render(new ModelInstance(factory.enemy(def, biome)), env);
                batch.end();
                Pixmap cell = ScreenUtils.getFrameBufferPixmap(0, 0, CELL, CELL);
                fbo.end();
                fbo.dispose();

                // el framebuffer llega volteado: copiar fila a fila invirtiendo Y
                for (int y = 0; y < CELL; y++) {
                    for (int x = 0; x < CELL; x++) {
                        int px = cell.getPixel(x, CELL - 1 - y);
                        if ((px & 0xff) != 0) sheet.drawPixel(c * CELL + x, r * CELL + y, px);
                    }
                }
                cell.dispose();
            }
        }

        String out = System.getProperty("td.galleryOut", "enemy-gallery.png");
        PixmapIO.writePNG(Gdx.files.absolute(out), sheet);
        sheet.dispose();
        batch.dispose();
        factory.dispose();
        Gdx.app.log("EnemyGalleryTool", "galeria escrita en " + out);
        Gdx.app.exit();
    }
}
