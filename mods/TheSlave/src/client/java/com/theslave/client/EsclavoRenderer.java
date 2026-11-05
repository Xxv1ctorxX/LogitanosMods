package com.theslave.client;

import com.theslave.TheSlaveMod;
import com.theslave.entity.EsclavoEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;

public class EsclavoRenderer extends HumanoidMobRenderer<EsclavoEntity, EsclavoRenderState, HumanoidModel<EsclavoRenderState>> {
	private static final Identifier[] SKINS = {
			TheSlaveMod.id("textures/entity/esclavo_0.png"),
			TheSlaveMod.id("textures/entity/esclavo_1.png"),
			TheSlaveMod.id("textures/entity/esclavo_2.png") };

	private static final Identifier[] FUSIONADOS = new Identifier[9];

	static {
		for (int a = 0; a < 3; a++) {
			for (int b = 0; b < 3; b++) {
				FUSIONADOS[a * 3 + b] = TheSlaveMod.id("textures/entity/esclavo_f_" + a + "_" + b + ".png");
			}
		}
	}

	public EsclavoRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new HumanoidArmorLayer<>(this,
				ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), HumanoidModel::new),
				ctx.getEquipmentRenderer()));
	}

	@Override
	public EsclavoRenderState createRenderState() {
		return new EsclavoRenderState();
	}

	@Override
	public void extractRenderState(EsclavoEntity e, EsclavoRenderState s, float partial) {
		super.extractRenderState(e, s, partial);
		s.cadena = e.tieneCadena();
        s.retirado=e.ocultoPorCustodia();
		s.anclaX=e.anclaCadena().getX()+0.5-s.x;
		s.anclaY=e.anclaCadena().getY()+0.3-s.y;
		s.anclaZ=e.anclaCadena().getZ()+0.5-s.z;
		s.variante = e.variante();
		s.variante2 = e.variante2();
        s.fusionLado=e.fusionLado(); s.fusionTick=e.fusionTick()+partial;
        var frente=e.fusionFrente(); s.fusionYaw=frente.getOpposite().toYRot();
        s.centroX=(float)s.anclaX+frente.getStepX()*2;
        s.centroY=(float)s.anclaY-0.3f+0.5f;
        s.centroZ=(float)s.anclaZ+frente.getStepZ()*2;
	}

 @Override
 public void submit(EsclavoRenderState s, com.mojang.blaze3d.vertex.PoseStack poses,
   net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera) {
  if(s.retirado)return;
  var fusion=com.theslave.ritual.CoreografiaFusion.en(s.fusionTick);
  if(s.fusionLado!=0&&fusion.activa()) submitMitad(s,fusion,poses,collector);
  else super.submit(s,poses,collector,camera);
  if(!s.cadena)return;
  org.joml.Vector3f anchor=new org.joml.Vector3f((float)s.anclaX,(float)s.anclaY,(float)s.anclaZ);
  final org.joml.Vector3f desplazamiento=s.fusionLado==0?new org.joml.Vector3f():
   new org.joml.Vector3f(s.centroX*fusion.convergencia(),s.centroY*fusion.convergencia()-0.5f*fusion.descenso(),s.centroZ*fusion.convergencia());
  int light=s.lightCoords;
  collector.submitCustomGeometry(poses,net.minecraft.client.renderer.rendertype.RenderTypes.entitySolid(RitualMesh.SUPERFICIE),(pose,out)->{
   RitualMesh.chain(pose,out,anchor,new org.joml.Vector3f(desplazamiento).add(0,1.48f,0),light);
   for(int i=0;i<16;i++){
    double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
    RitualMesh.beam(pose,out,new org.joml.Vector3f((float)Math.cos(a)*.15f,1.48f,(float)Math.sin(a)*.15f).add(desplazamiento),
     new org.joml.Vector3f((float)Math.cos(b)*.15f,1.48f,(float)Math.sin(b)*.15f).add(desplazamiento),.022f,0xff39323d,light);
   }
  });
 }

 private void submitMitad(EsclavoRenderState s,com.theslave.ritual.CoreografiaFusion fusion,
   com.mojang.blaze3d.vertex.PoseStack poses,net.minecraft.client.renderer.SubmitNodeCollector collector) {
  // Las islas UV finales conservan la izquierda anatomica de A y la derecha de B.
  float signo=s.fusionLado==1?-1:1;
  var malla=new MallaRecortada(new MallaRecortada.Plano(signo,0,0,fusion.resto()));
  var local=new com.mojang.blaze3d.vertex.PoseStack();
  local.scale(-s.scale,-s.scale,s.scale); scale(s,local); local.translate(0,-1.501f,0);
  s.walkAnimationSpeed=0;s.walkAnimationPos=0;s.yRot=0;s.xRot=0;
  model.setupAnim(s);model.renderToBuffer(local,malla,s.lightCoords,getOverlayCoords(s,0),-1);malla.terminar();
  poses.pushPose();
  poses.translate(s.centroX*fusion.convergencia(),s.centroY*fusion.convergencia()-0.5f*fusion.descenso(),s.centroZ*fusion.convergencia());
  float orientacion=net.minecraft.util.Mth.rotLerp(1-fusion.resto(),s.bodyRot,s.fusionYaw);
  poses.rotateDegrees(com.mojang.math.Axis.YP,180-orientacion);
  collector.submitCustomGeometry(poses,net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(getTextureLocation(s)),malla::dibujar);
  if(s.fusionLado==2&&fusion.costura()>0) {
   final float avance=fusion.costura();final int light=s.lightCoords;
   collector.submitCustomGeometry(poses,net.minecraft.client.renderer.rendertype.RenderTypes.entitySolid(RitualMesh.SUPERFICIE),(pose,out)->{
    // Hilo visible en frente y espalda, sin sustituir la geometria por particulas.
    for(float cara:new float[]{-1,1})for(int i=0;i<13*avance;i++) {
     float y=0.78f+i*0.09f,z=cara*(y>1.5f?0.258f:0.133f);
     RitualMesh.beam(pose,out,new org.joml.Vector3f(-0.045f,y,z),new org.joml.Vector3f(0.045f,y+0.035f,z),0.009f,0xff39283e,light);
     RitualMesh.beam(pose,out,new org.joml.Vector3f(-0.022f,y,z*1.02f),new org.joml.Vector3f(0.022f,y+0.02f,z*1.02f),0.004f,0xff79548c,light);
    }
   });
  }
  poses.popPose();
 }

    @Override
    protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(EsclavoEntity entity, float partial) {
        var body=super.getBoundingBoxForCulling(entity,partial);
        return entity.tieneCadena()?body.minmax(new net.minecraft.world.phys.AABB(entity.anclaCadena()).inflate(3)):body;
    }

	@Override
	public Identifier getTextureLocation(EsclavoRenderState s) {
		if (s.variante2 >= 0) {
			return FUSIONADOS[Math.floorMod(s.variante, 3) * 3 + Math.floorMod(s.variante2, 3)];
		}
		return SKINS[Math.floorMod(s.variante, SKINS.length)];
	}
}
