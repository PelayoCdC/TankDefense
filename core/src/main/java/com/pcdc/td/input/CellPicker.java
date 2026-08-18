package com.pcdc.td.input;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.pcdc.td.world.GridMap;

/** Convierte coordenadas de pantalla en la celda del grid bajo el puntero. */
public class CellPicker {

    private final Plane groundPlane = new Plane(Vector3.Y, 0f);
    private final Vector3 hit = new Vector3();

    /** @return true si el rayo toca el plano del suelo dentro del tablero. */
    public boolean pick(Camera camera, GridMap map, float screenX, float screenY, GridPoint2 out) {
        Ray ray = camera.getPickRay(screenX, screenY);
        if (!Intersector.intersectRayPlane(ray, groundPlane, hit)) return false;
        int x = (int) Math.floor(hit.x);
        int z = (int) Math.floor(hit.z);
        if (!map.inBounds(x, z)) return false;
        out.set(x, z);
        return true;
    }
}
