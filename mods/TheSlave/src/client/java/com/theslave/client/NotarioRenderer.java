package com.theslave.client;

import com.theslave.TheSlaveMod;
import com.theslave.entity.NotarioEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class NotarioRenderer extends HumanoidMobRenderer<NotarioEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier SKIN = TheSlaveMod.id("textures/entity/notario.png");

	public NotarioRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState s) {
		return SKIN;
	}
}
