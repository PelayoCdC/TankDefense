package com.pcdc.td.data;

/** Receta de fusión: construir 'b' encima de 'a' (o al revés) produce 'result'. */
public class FusionDef {
    public String a;
    public String b;
    public String result;

    public boolean matches(String towerA, String towerB) {
        return (a.equals(towerA) && b.equals(towerB)) || (a.equals(towerB) && b.equals(towerA));
    }
}
