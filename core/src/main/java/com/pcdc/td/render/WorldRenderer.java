package com.pcdc.td.render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/** ModelBatch + luces + cámara. Dibuja lo que le pasen, no conoce el juego. */
public class WorldRenderer implements Disposable {

    public final PerspectiveCamera camera;
    private final ModelBatch batch = new ModelBatch();
    private final Environment environment = new Environment();

    private final DirectionalLight sun = new DirectionalLight();

    public WorldRenderer() {
        camera = new PerspectiveCamera(50, 1280, 720);
        camera.near = 0.5f;
        camera.far = 120f;

        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.58f, 1f));
        sun.set(0.85f, 0.83f, 0.78f, -0.35f, -0.85f, -0.25f);
        environment.add(sun);
    }

    /** Iluminación del bioma: ambiente + sol tintados. */
    public void setLighting(com.badlogic.gdx.graphics.Color ambient, com.badlogic.gdx.graphics.Color sunColor) {
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight,
            ambient.r * 0.75f, ambient.g * 0.75f, ambient.b * 0.75f, 1f));
        sun.setColor(sunColor.r, sunColor.g, sunColor.b, 1f);
    }

    /**
     * Dirección del sol. Cada nivel la recibe distinta según su hora del día:
     * los cinco niveles de un bioma compartían iluminación exacta y se veían
     * calcados aunque el trazado cambiara.
     */
    public void setSunDirection(float x, float y, float z) {
        sun.direction.set(x, y, z).nor();
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    public void render(Array<? extends RenderableProvider> providers) {
        batch.begin(camera);
        batch.render(providers, environment);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}
