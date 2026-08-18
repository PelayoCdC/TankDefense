package com.pcdc.td.render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pcdc.td.data.TowerDef;

/**
 * Renderiza torres 3D a texturas para usarlas como iconos en la UI (barra del
 * HUD, nodos de fusión del árbol, tarjetas del taller de pintura).
 *
 * Es Disposable y guarda las texturas que crea: la pantalla que lo instancia
 * debe liberarlo en su dispose().
 */
public class TowerIconRenderer implements Disposable {

    private final int size;
    private final Array<Texture> textures = new Array<>();
    private final ModelBatch batch = new ModelBatch();
    private final Environment env = new Environment();
    private final PerspectiveCamera cam;

    public TowerIconRenderer(int size) {
        this.size = size;
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.65f, 0.65f, 0.68f, 1f));
        env.add(new DirectionalLight().set(0.9f, 0.88f, 0.82f, -0.4f, -0.8f, -0.3f));
        cam = new PerspectiveCamera(38, size, size);
        cam.position.set(1.15f, 1.35f, 1.15f);
        cam.lookAt(0f, 0.32f, 0f);
        cam.near = 0.1f;
        cam.far = 10f;
        cam.update();
    }

    /** Icono de una torre usando la skin activa y una ModelFactory ya existente. */
    public TextureRegion render(ModelFactory factory, TowerDef def, int level) {
        return toRegion(new ModelInstance(factory.tower(def, level)));
    }

    /**
     * Icono de una torre con una skin CONCRETA. Usa su propia ModelFactory
     * porque los modelos se cachean por id+nivel: reutilizarla devolvería el
     * modelo construido con la skin anterior.
     */
    public TextureRegion renderWithSkin(TowerDef def, String skinId) {
        String previous = ModelFactory.activeSkin;
        ModelFactory factory = new ModelFactory();
        try {
            ModelFactory.activeSkin = skinId;
            return toRegion(new ModelInstance(factory.tower(def, 1)));
        } finally {
            ModelFactory.activeSkin = previous;
            factory.dispose();
        }
    }

    private TextureRegion toRegion(ModelInstance instance) {
        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, size, size, true);
        fbo.begin();
        ScreenUtils.clear(0f, 0f, 0f, 0f, true);
        batch.begin(cam);
        batch.render(instance, env);
        batch.end();
        Pixmap pm = ScreenUtils.getFrameBufferPixmap(0, 0, size, size);
        fbo.end();
        fbo.dispose();

        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // el Pixmap se queda VIVO: es lo que usa libGDX para recrear la textura
        // si Android destruye el contexto GL en segundo plano
        textures.add(tex);
        TextureRegion region = new TextureRegion(tex);
        region.flip(false, true); // la lectura del framebuffer viene volteada
        return region;
    }

    @Override
    public void dispose() {
        for (Texture t : textures) t.dispose();
        textures.clear();
        batch.dispose();
    }
}
