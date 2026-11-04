package com.theball.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;

/** Datos de la portería que necesita el render. */
public class GoalRenderState extends EntityRenderState {
	public Direction facing = Direction.NORTH;
}
