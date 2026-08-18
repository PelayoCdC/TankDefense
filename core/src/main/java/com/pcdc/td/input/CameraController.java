package com.pcdc.td.input;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Cámara con vista lateral (no en rombo) con pan (arrastre) y zoom
 * (pinch / rueda). rotate90() gira la vista alrededor del tablero con
 * animación; el tap se delega en un listener para el picking.
 * Registrar en el InputMultiplexer: new GestureDetector(this) y también this.
 * Llamar a update(delta) cada frame.
 */
public class CameraController extends InputAdapter implements GestureDetector.GestureListener {

    public interface TapListener {
        void onTap(float screenX, float screenY);

        /** Movimiento sin pulsar (hover, solo desktop). */
        void onPointerMoved(float screenX, float screenY);
    }

    private static final float PITCH_DEG = 54f;   // inclinación desde la horizontal

    private final PerspectiveCamera camera;
    private final Vector3 target = new Vector3();
    private float distance = 14f;
    private final float minDistance, maxDistance;

    /** Yaw en pasos de 90°: 0 = mirando desde el sur hacia el norte. */
    private float yawDeg = 0f;
    private float yawTargetDeg = 0f;

    private final float minX, maxX, minZ, maxZ;
    private TapListener tapListener;

    private final Vector3 dir = new Vector3();
    private final Vector3 right = new Vector3();
    private final Vector3 forward = new Vector3();
    private float pinchBaseDistance = -1f;
    private float lastInitialPinch = -1f;

    public CameraController(PerspectiveCamera camera, float boardWidth, float boardHeight) {
        this.camera = camera;
        // el pan queda acotado a la zona central: en mapas pequeños apenas se mueve
        float marginX = boardWidth * 0.22f;
        float marginZ = boardHeight * 0.22f;
        this.minX = marginX;
        this.maxX = boardWidth - marginX;
        this.minZ = marginZ;
        this.maxZ = boardHeight - marginZ;
        this.homeX = boardWidth / 2f;
        this.homeZ = boardHeight / 2f;
        target.set(homeX, 0f, homeZ);
        // el zoom máximo encuadra el tablero; no se puede alejar mucho más
        this.maxDistance = Math.max(10f, Math.max(boardWidth, boardHeight) * 1.15f);
        this.minDistance = 6f;
        distance = maxDistance * 0.95f;
        apply();
    }

    public void setTapListener(TapListener listener) {
        this.tapListener = listener;
    }

    /** Gira la vista 90° alrededor del centro (animado en update). */
    public void rotate90() {
        yawTargetDeg += 90f;
    }

    private float shakeTime;

    // barrido cinemático inicial (portal → fortaleza)
    private final Vector3 cineFrom = new Vector3();
    private final Vector3 cineTo = new Vector3();
    private float cineTime = -1f, cineDuration;
    private final float homeX, homeZ;

    /** Sacudida breve de cámara (al perder una vida). */
    public void shake(float duration) {
        shakeTime = duration;
    }

    /** Recorre la cámara de un punto a otro y vuelve al centro. */
    public void cinematic(Vector3 from, Vector3 to, float duration) {
        cineFrom.set(from);
        cineTo.set(to);
        cineDuration = duration;
        cineTime = 0f;
    }

    public boolean isCinematic() {
        return cineTime >= 0f;
    }

    public void stopCinematic() {
        if (cineTime < 0f) return;
        cineTime = -1f;
        target.set(homeX, 0f, homeZ);
        apply();
    }

    /** Animación del giro; llamar cada frame. */
    public void update(float delta) {
        if (cineTime >= 0f) {
            cineTime += delta;
            float p = cineTime / cineDuration;
            if (p >= 1f) {
                stopCinematic();
            } else {
                float eased = MathUtils.sin(p * MathUtils.PI * 0.5f); // ease-out
                target.set(cineFrom).lerp(cineTo, eased);
                apply();
            }
        }
        if (shakeTime > 0f) {
            shakeTime -= delta;
            apply();
        }
        if (Math.abs(yawTargetDeg - yawDeg) > 0.01f) {
            yawDeg = MathUtils.lerp(yawDeg, yawTargetDeg, Math.min(1f, delta * 8f));
            if (Math.abs(yawTargetDeg - yawDeg) < 0.2f) {
                yawDeg = yawTargetDeg;
                // normaliza para no acumular vueltas
                if (yawDeg >= 360f) {
                    yawDeg -= 360f;
                    yawTargetDeg -= 360f;
                }
            }
            apply();
        }
    }

    private void computeDir() {
        float yawRad = yawDeg * MathUtils.degreesToRadians;
        float pitchRad = PITCH_DEG * MathUtils.degreesToRadians;
        float cosP = MathUtils.cos(pitchRad);
        // dir apunta de la cámara hacia el tablero
        dir.set(MathUtils.sin(yawRad) * cosP, -MathUtils.sin(pitchRad), MathUtils.cos(yawRad) * cosP).nor();
    }

    /** Recoloca la cámara según target+distance+yaw. */
    private void apply() {
        computeDir();
        distance = MathUtils.clamp(distance, minDistance, maxDistance);
        target.x = MathUtils.clamp(target.x, minX, maxX);
        target.z = MathUtils.clamp(target.z, minZ, maxZ);
        camera.position.set(target).mulAdd(dir, -distance);
        if (shakeTime > 0f) {
            float mag = shakeTime * 0.55f;
            camera.position.add(
                MathUtils.random(-mag, mag),
                MathUtils.random(-mag, mag) * 0.5f,
                MathUtils.random(-mag, mag));
        }
        camera.direction.set(dir);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    // ---- GestureListener ----

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        computeDir();
        right.set(dir).crs(Vector3.Y).nor();
        forward.set(right).crs(Vector3.Y).nor(); // hacia la cámara en XZ

        float scale = distance * 0.0016f;
        target.mulAdd(right, -deltaX * scale);
        target.mulAdd(forward, -deltaY * scale);
        apply();
        return true;
    }

    @Override
    public boolean zoom(float initialDistance, float currentDistance) {
        if (initialDistance != lastInitialPinch) {
            lastInitialPinch = initialDistance;
            pinchBaseDistance = distance;
        }
        distance = pinchBaseDistance * (initialDistance / currentDistance);
        apply();
        return true;
    }

    @Override
    public boolean tap(float x, float y, int count, int button) {
        if (tapListener != null) tapListener.onTap(x, y);
        return true;
    }

    @Override
    public boolean touchDown(float x, float y, int pointer, int button) {
        return false;
    }

    @Override
    public boolean longPress(float x, float y) {
        return false;
    }

    @Override
    public boolean fling(float velocityX, float velocityY, int button) {
        return false;
    }

    @Override
    public boolean panStop(float x, float y, int pointer, int button) {
        return false;
    }

    @Override
    public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2) {
        return false;
    }

    @Override
    public void pinchStop() {
        lastInitialPinch = -1f;
    }

    // ---- InputProcessor (desktop) ----

    @Override
    public boolean scrolled(float amountX, float amountY) {
        distance *= (1f + amountY * 0.09f);
        apply();
        return true;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        if (tapListener != null) tapListener.onPointerMoved(screenX, screenY);
        return false;
    }
}
