package com.pcdc.td.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.pcdc.td.world.Enemy;

/** Barras de vida y estados (ralentizado/envenenado) proyectados a pantalla. */
public class HealthBarRenderer implements Disposable {

    // en píxeles de pantalla, escalados por densidad (Android)
    private final float ui = com.pcdc.td.ui.UiScale.value;
    private final float barWidth = 34f * ui;
    private final float barHeight = 5f * ui;
    private static final float Y_OFFSET = 0.8f;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Vector3 tmp = new Vector3();
    private final Color back = Color.valueOf("222222");
    private final Color high = Color.valueOf("58d858");
    private final Color low = Color.valueOf("d8c548");
    private final Color critical = Color.valueOf("d85848");
    // iconos con FORMA distinta además del color (aptos para daltónicos):
    // ralentizado = rombo azul, veneno = círculo verde que se llena con los stacks
    private final Color slowBorder = Color.valueOf("14324a");
    private final Color slowFill = Color.valueOf("6ac8f0");
    private final Color poisonBorder = Color.valueOf("143a14");
    private final Color poisonFill = Color.valueOf("58c838");

    /** Distancia al final del camino a partir de la cual se avisa de la fuga. */
    private static final float LEAK_DISTANCE = 4.5f;
    private final Color leak = Color.valueOf("ff4040");
    private float time;

    public void render(Camera camera, Array<Enemy> enemies, float delta) {
        time += delta;
        boolean any = false;
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (e.alive && (e.hp < e.maxHp || hasStatus(e) || leaking(e))) { any = true; break; }
        }
        if (!any) return;

        shapes.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            boolean damaged = e.hp < e.maxHp;
            boolean leaks = leaking(e);
            if (!e.alive || (!damaged && !hasStatus(e) && !leaks)) continue;
            tmp.set(e.position).add(0f, Y_OFFSET, 0f);
            camera.project(tmp);
            boolean behind = tmp.z < 0f || tmp.z > 1f;
            if (leaks) drawLeakMarker(tmp.x, tmp.y, behind);
            if (behind) continue; // detrás de la cámara

            float x = tmp.x - barWidth / 2f;
            float y = tmp.y;
            if (damaged) {
                float pct = Math.max(0f, e.hp / e.maxHp);
                shapes.setColor(back);
                shapes.rect(x - 1, y - 1, barWidth + 2, barHeight + 2);
                shapes.setColor(pct > 0.5f ? high : pct > 0.25f ? low : critical);
                shapes.rect(x, y, barWidth * pct, barHeight);
            }
            drawStatusIcons(e, tmp.x, y + barHeight + 4f * ui);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private static boolean hasStatus(Enemy e) {
        return e.slowTimer > 0f || e.poisonStacks > 0;
    }

    /** Enemigo a punto de llegar a la base: hay que avisar al jugador. */
    private static boolean leaking(Enemy e) {
        return e.alive && e.totalPathLen > 0f
            && e.totalPathLen - e.pathProgress <= LEAK_DISTANCE;
    }

    /**
     * Punta de flecha roja parpadeante sobre el enemigo. Si está fuera de la
     * vista se pega al borde de la pantalla apuntando hacia dónde se escapa.
     */
    private void drawLeakMarker(float sx, float sy, boolean offCamera) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float margin = 26f * ui;
        boolean outside = offCamera || sx < 0f || sx > w || sy < 0f || sy > h;
        float x = Math.min(Math.max(sx, margin), w - margin);
        float y = Math.min(Math.max(sy, margin), h - margin);
        float size = (outside ? 15f : 10f) * ui;
        // parpadeo: nunca baja del 45% para que no desaparezca del todo
        float pulse = 0.45f + 0.55f * Math.abs((float) Math.sin(time * 5f));
        shapes.setColor(leak.r, leak.g, leak.b, pulse);
        float top = y + size * (outside ? 1.2f : 2.2f);
        shapes.triangle(x - size, top, x + size, top, x, top - size * 1.3f);
    }

    /** Fila de iconos centrada sobre la barra; baseY es el borde inferior. */
    private void drawStatusIcons(Enemy e, float centerX, float baseY) {
        boolean slowed = e.slowTimer > 0f;
        boolean poisoned = e.poisonStacks > 0;
        int count = (slowed ? 1 : 0) + (poisoned ? 1 : 0);
        if (count == 0) return;

        float step = 11f * ui;
        float half = 4.5f * ui;
        float cx = centerX - step * (count - 1) / 2f;
        float cy = baseY + half;

        if (slowed) {
            shapes.setColor(slowBorder);
            shapes.triangle(cx - half, cy, cx + half, cy, cx, cy + half);
            shapes.triangle(cx - half, cy, cx + half, cy, cx, cy - half);
            float in = half * 0.62f;
            shapes.setColor(slowFill);
            shapes.triangle(cx - in, cy, cx + in, cy, cx, cy + in);
            shapes.triangle(cx - in, cy, cx + in, cy, cx, cy - in);
            cx += step;
        }
        if (poisoned) {
            shapes.setColor(poisonBorder);
            shapes.circle(cx, cy, half);
            // el relleno crece con los stacks (1..MAX_POISON_STACKS)
            float in = half * (0.35f + 0.55f * e.poisonStacks / Enemy.MAX_POISON_STACKS);
            shapes.setColor(poisonFill);
            shapes.circle(cx, cy, in);
        }
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
