package com.pcdc.td.data;

/** Efecto de estado que aplica una torre: slow, poison o burn. */
public class EffectDef {
    public String type;        // "slow" | "poison" | "burn"
    public float factor = 1f;  // slow: multiplicador de velocidad (0.5 = mitad)
    public float duration = 2f;
    public float dps = 0f;     // poison/burn: daño por segundo
}
