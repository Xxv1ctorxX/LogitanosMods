package com.theball.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;

/** Datos de la bola que necesita el render (se copian de la entidad en cada frame). */
public class AluminumBallRenderState extends EntityRenderState {
	public float diameter = 0.6f;
	/** Rotación ya interpolada con el partial tick. */
	public final Quaternionf rotation = new Quaternionf();
}
