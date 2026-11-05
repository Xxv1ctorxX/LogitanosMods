package com.theslave.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class EsclavoRenderState extends HumanoidRenderState {
	public boolean cadena;
    public boolean retirado;
	public double anclaX, anclaY, anclaZ;
	public int variante;
	public int variante2 = -1;
    public int fusionLado;
    public float fusionTick, fusionYaw;
    public float centroX,centroY,centroZ;
}
