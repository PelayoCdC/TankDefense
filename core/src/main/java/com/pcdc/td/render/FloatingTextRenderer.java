package com.pcdc.td.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.StringBuilder;
import com.pcdc.td.world.Fx;

/** Textos flotantes en pantalla ("+oro" al morir un enemigo). */
public class FloatingTextRenderer implements Disposable {

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font;
    private final Vector3 tmp = new Vector3();
    private final StringBuilder sb = new StringBuilder();

    private final float uiScale = com.pcdc.td.ui.UiScale.value;

    public FloatingTextRenderer() {
        font = com.pcdc.td.ui.Fonts.pixels(16f * uiScale);
    }

    public void render(Camera camera, Array<Fx> fx) {
        boolean any = false;
        for (int i = 0; i < fx.size; i++) {
            if (fx.get(i).type == Fx.Type.GOLD) { any = true; break; }
        }
        if (!any) return;

        batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.begin();
        for (int i = 0; i < fx.size; i++) {
            Fx f = fx.get(i);
            if (f.type != Fx.Type.GOLD) continue;
            float progress = 1f - f.ttl / f.maxTtl;
            tmp.set(f.from).add(0f, 0.4f, 0f);
            camera.project(tmp);
            if (tmp.z < 0f || tmp.z > 1f) continue;
            sb.setLength(0);
            float alpha = Math.min(1f, 2f * (1f - progress));
            if (f.dmgText) {
                // número de daño: sube menos y en tono cálido, para no
                // confundirse con las recompensas
                sb.append("-").append(f.goldAmount);
                font.setColor(1f, 0.72f, 0.6f, alpha);
                font.draw(batch, sb, tmp.x - 8f * uiScale, tmp.y + (16f + progress * 26f) * uiScale);
                continue;
            }
            sb.append("+").append(f.goldAmount);
            if (f.crystal) font.setColor(0.44f, 0.78f, 0.94f, alpha);
            else font.setColor(1f, 0.84f, 0.3f, alpha);
            font.draw(batch, sb, tmp.x - 10f * uiScale, tmp.y + (22f + progress * 42f) * uiScale);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
