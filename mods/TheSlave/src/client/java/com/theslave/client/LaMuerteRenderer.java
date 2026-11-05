package com.theslave.client;

import com.theslave.TheSlaveMod;
import com.theslave.entity.LaMuerteEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class LaMuerteRenderer extends HumanoidMobRenderer<LaMuerteEntity, LaMuerteRenderer.Estado, HumanoidModel<LaMuerteRenderer.Estado>> {
	private static final Identifier SKIN = TheSlaveMod.id("textures/entity/la_muerte.png");
    public static class Estado extends HumanoidRenderState { public int presencia,tipo; public float nx,nz,distancia,tiempo; }

	public LaMuerteRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.0f);
	}

	@Override
	public Estado createRenderState() {
		return new Estado();
	}

    @Override
    public void extractRenderState(LaMuerteEntity entity, Estado state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.presencia = entity.presencia();
        state.tipo=entity.tipo();state.tiempo=entity.tiempo()+partial;
        state.nx=entity.frentePortal().getStepX(); state.nz=entity.frentePortal().getStepZ();
        state.distancia=(float)((state.x-entity.portal().getX()-0.5)*state.nx+(state.z-entity.portal().getZ()-0.5)*state.nz);
    }

    @Override
    public void submit(Estado state, com.mojang.blaze3d.vertex.PoseStack poses,
            net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        if(state.presencia<=0||state.isInvisibleToPlayer)return;
        // Captura en coordenadas locales de entidad, antes de aplicar la vista de la camara.
        var local=new com.mojang.blaze3d.vertex.PoseStack();
        local.scale(state.scale,state.scale,state.scale);
        setupRotations(state,local,state.bodyRot,state.scale);
        local.scale(-1,-1,1); scale(state,local); local.translate(0,-1.501f,0);
        var malla=new MallaRecortada(new MallaRecortada.Plano(state.nx,0,state.nz,state.distancia));
        model.setupAnim(state);
        var gesto=com.theslave.ritual.GestosMuerte.en(state.tipo,state.tiempo);
        model.head.xRot=gesto.cabeza();model.rightArm.xRot=gesto.derechaX();model.rightArm.zRot=gesto.derechaZ();
        model.leftArm.xRot=gesto.izquierdaX();model.leftArm.zRot=gesto.izquierdaZ();
        model.renderToBuffer(local,malla,state.lightCoords,getOverlayCoords(state,0),getModelTint(state));
        malla.terminar();
        collector.submitCustomGeometry(poses,net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(SKIN),malla::dibujar);
    }

    @Override
    protected int getModelTint(Estado state) {
        return (state.presencia * 255 / 100) << 24 | 0xFFFFFF;
    }

    @Override
    protected net.minecraft.client.renderer.rendertype.RenderType getRenderType(Estado state, boolean visible, boolean translucent, boolean glowing) {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(SKIN);
    }

	@Override
	public Identifier getTextureLocation(Estado s) {
		return SKIN;
	}
}
