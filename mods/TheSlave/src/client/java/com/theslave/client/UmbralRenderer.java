package com.theslave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.theslave.TheSlaveMod;
import com.theslave.entity.UmbralEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.theslave.ritual.AparicionRitual;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Herida opaca de doble cara: oculta el paisaje sin convertir el mundo en otro portal. */
public class UmbralRenderer extends EntityRenderer<UmbralEntity, UmbralRenderer.Estado> {
 public static class Estado extends EntityRenderState {
  public float apertura,yaw,tiempo;public int tipo,variante,variante2;public Vector3f tumba=new Vector3f(),mano=new Vector3f();
  public final ItemStackRenderState contrato=new ItemStackRenderState(),ofrenda=new ItemStackRenderState(),alma=new ItemStackRenderState();
 }
 private final net.minecraft.client.renderer.item.ItemModelResolver objetos;
 private final HumanoidModel<HumanoidRenderState> fantasma;
 public UmbralRenderer(EntityRendererProvider.Context ctx){
  super(ctx);objetos=ctx.getItemModelResolver();fantasma=new HumanoidModel<>(ctx.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER));
 }
 @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(UmbralEntity e,float partial){
  double yaw=Math.toRadians(e.getYRot());
  var fin=e.position().add(-Math.sin(yaw)*5,0,Math.cos(yaw)*5);
  return new net.minecraft.world.phys.AABB(e.position(),fin).inflate(2.1,3.1,2.1);
 }
 @Override public Estado createRenderState(){return new Estado();}
 @Override public void extractRenderState(UmbralEntity e,Estado s,float partial){
  super.extractRenderState(e,s,partial);s.apertura=e.apertura()/100f;s.yaw=e.getYRot();
  s.tipo=e.tipo();s.tiempo=e.tiempo()+partial;s.variante=e.variante();s.variante2=e.variante2();
  s.mano=e.mano().rotateY((float)Math.toRadians(s.yaw));
  objetos.updateForNonLiving(s.contrato,e.contrato(),net.minecraft.world.item.ItemDisplayContext.FIXED,e);
  objetos.updateForNonLiving(s.ofrenda,e.ofrenda(),net.minecraft.world.item.ItemDisplayContext.FIXED,e);
  objetos.updateForNonLiving(s.alma,e.alma(),net.minecraft.world.item.ItemDisplayContext.FIXED,e);
  // El retorno se presenta ante el altar sobre el alma traida por el jugador, no junto al lecho remoto.
  s.tumba=new Vector3f(0,0,5);
 }
 @Override public void submit(Estado s,PoseStack poses,SubmitNodeCollector collector,CameraRenderState camera){
  super.submit(s,poses,collector,camera);
  poses.pushPose();poses.mulPose(new Matrix4f().rotationY((float)Math.toRadians(-s.yaw)));
  float opening=s.apertura,time=s.ageInTicks;
  int tipo=s.tipo,tick=(int)s.tiempo;Vector3f tumba=new Vector3f(s.tumba);
  collector.submitCustomGeometry(poses,RenderTypes.entitySolid(RitualMesh.SUPERFICIE),(pose,out)->{
   Vector3f center=new Vector3f(0,1.5f,0);
   for(int i=0;i<48 && opening>0;i++){
    double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
    float r=1+(float)Math.sin(i*2.3+time*.025)*.025f;
    Vector3f p=new Vector3f((float)Math.cos(a)*.85f*opening*r,1.5f+(float)Math.sin(a)*1.45f,0);
    Vector3f q=new Vector3f((float)Math.cos(b)*.85f*opening,1.5f+(float)Math.sin(b)*1.45f,0);
    Vector3f outerP=new Vector3f(p.x*1.07f,1.5f+(p.y-1.5f)*1.035f,.015f);
    Vector3f outerQ=new Vector3f(q.x*1.07f,1.5f+(q.y-1.5f)*1.035f,.015f);
    RitualMesh.quad(pose,out,center,p,q,new Vector3f(0,1.5001f,0),0xff08070c,15728880);
    RitualMesh.quad(pose,out,p,outerP,outerQ,q,i%5==0?0xff77717a:0xff493153,15728880);
   }
   int cantidad=tipo==1?4:tipo==2?6:tipo==3?8:0;
   for(int i=0;i<cantidad;i++){
    Vector3f punto;
    if(tipo==2){
     int lado=i<3?-1:1;int j=i%3;
     punto=new Vector3f(lado*(1.4f+j*.14f),0,4.4f+j*.45f);
    } else {
     double ang=i*Math.PI*2/cantidad;
     punto=new Vector3f(tumba).add((float)Math.cos(ang)*1.25f,0,(float)Math.sin(ang)*1.25f);
    }
    int orden=tipo==1?(i%2)*2+i/2:tipo==3?(i+4)%8:i;
    float emerge=Math.clamp((tick-orden*7)/12f,0,1);
    if(emerge<=0)continue;
    float altura=(.24f+(i%3)*.055f)*emerge;
    RitualMesh.beam(pose,out,punto,new Vector3f(punto).add(0,altura,0),.045f,0xff77717a,15728880);
    float inclina=(float)Math.sin(time*.06+i)*.018f;
    Vector3f mecha=new Vector3f(punto).add(0,altura,0);
    RitualMesh.beam(pose,out,mecha,new Vector3f(mecha).add(inclina,.13f,0),.022f,0xff493153,15728880);
    RitualMesh.beam(pose,out,new Vector3f(mecha).add(0,.01f,0),new Vector3f(mecha).add(inclina*.5f,.07f,0),.009f,0xff9aafb8,15728880);
   }
  });
  presentarObjetos(s,poses,collector);
  presentarCuerpo(s,poses,collector);
  manifestaciones(s,poses,collector);
  poses.popPose();
 }

 private void presentarObjetos(Estado s,PoseStack poses,SubmitNodeCollector collector) {
  float visible=AparicionRitual.en(s.tipo,s.tiempo).objetos();
  if(visible<=0)return;
  if(!s.alma.isEmpty()) {
   poses.pushPose();poses.translate(0,.22+Math.sin(s.tiempo*.035)*.018,5);
   poses.rotateDegrees(com.mojang.math.Axis.YP,(float)Math.sin(s.tiempo*.02)*6);
   poses.scale(.34f*visible,.34f*visible,.34f*visible);
   s.alma.submit(poses,collector,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0);poses.popPose();
  }
  poses.pushPose();poses.translate(.19,.99,3);poses.scale(.42f*visible,.42f*visible,.42f*visible);
  s.ofrenda.submit(poses,collector,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0);poses.popPose();
  if(s.contrato.isEmpty())return;
  poses.pushPose();poses.translate(-.22,1.07+Math.sin(s.tiempo*.025)*.015,3);
  poses.rotateDegrees(com.mojang.math.Axis.XP,-74+12*AparicionRitual.suave(s.tiempo,40,100));
  poses.scale(.55f*visible,.55f*visible,.55f*visible);
  s.contrato.submit(poses,collector,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0);poses.popPose();
 }
 private void presentarCuerpo(Estado s,PoseStack poses,SubmitNodeCollector collector) {
  var fase=AparicionRitual.en(s.tipo,s.tiempo);
  if(fase.altura()<=0||s.tipo==2)return;
  int mitades=s.tipo==3?2:1;
  for(int mitad=0;mitad<mitades;mitad++) {
   HumanoidRenderState cuerpo=new HumanoidRenderState();cuerpo.ageInTicks=s.tiempo;cuerpo.scale=1;cuerpo.ageScale=1;
   cuerpo.isCrouching=fase.arrodillado()>.1f;
   fantasma.setupAnim(cuerpo);
   fantasma.head.xRot=.42f*(1-fase.color());
   if(s.tipo==3)fantasma.head.yRot=(float)Math.sin(s.tiempo*.045+mitad*1.3)*.14f*(1-fase.color());
   if(s.tipo==1) {
    fantasma.rightLeg.xRot=-1.1f*fase.arrodillado();fantasma.leftLeg.xRot=-1.1f*fase.arrodillado();
    fantasma.body.xRot=.35f*fase.arrodillado();
   }
   var local=new PoseStack();local.translate(0,-.32f*fase.arrodillado(),0);local.scale(-1,-1,1);local.translate(0,-1.501f,0);
   var planos=new java.util.ArrayList<MallaRecortada.Plano>();
   planos.add(new MallaRecortada.Plano(0,-1,0,2.1f*fase.altura()));planos.add(new MallaRecortada.Plano(0,1,0,0));
   if(mitades==2)planos.add(new MallaRecortada.Plano(mitad==0?-1:1,0,0,0));
   MallaRecortada gris=fase.color()<1?new MallaRecortada(planos.toArray(MallaRecortada.Plano[]::new)):null;
   MallaRecortada piel=fase.color()>0?new MallaRecortada(planos.toArray(MallaRecortada.Plano[]::new)):null;
   if(gris!=null) {fantasma.renderToBuffer(local,gris,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,((int)(220*(1-fase.color()))<<24)|0x77717a);gris.terminar();}
   if(piel!=null) {fantasma.renderToBuffer(local,piel,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,((int)(255*fase.color())<<24)|0xffffff);piel.terminar();}
   poses.pushPose();
   Vector3f punto=s.tipo==0?new Vector3f(0,0,5):new Vector3f(s.tumba);
   if(mitades==2)punto.x+=(mitad==0?-1:1)*fase.separacion();
   poses.translate(punto.x,punto.y,punto.z);
   if(gris!=null)collector.submitCustomGeometry(poses,RenderTypes.entityTranslucent(RitualMesh.SUPERFICIE),gris::dibujar);
   String textura=s.variante2<0?"esclavo_"+s.variante:"esclavo_f_"+s.variante+"_"+s.variante2;
   if(piel!=null)collector.submitCustomGeometry(poses,RenderTypes.entityTranslucent(TheSlaveMod.id("textures/entity/"+textura+".png")),piel::dibujar);
   poses.popPose();
  }
 }
 private void manifestaciones(Estado s,PoseStack poses,SubmitNodeCollector collector) {
  final float tick=s.tiempo;final int tipo=s.tipo;final var fase=AparicionRitual.en(tipo,tick);
  final Vector3f tumba=new Vector3f(s.tumba),mano=new Vector3f(s.mano);
  collector.submitCustomGeometry(poses,RenderTypes.entitySolid(RitualMesh.SUPERFICIE),(pose,out)->{
   Vector3f cuenco=new Vector3f(.19f,.99f,3);
   if(fase.almas()>0)for(int i=0;i<9;i++) {
    float inicio=tipo==0?100:tipo==1?240:tipo==2?180:280,fin=tipo==0?160:tipo==1?320:tipo==2?240:400;
    float viaje=AparicionRitual.suave(tick,inicio+i*2.2f,fin-12+i*.5f);
    Vector3f alma=new Vector3f(cuenco).lerp(mano,viaje).add((float)Math.sin(viaje*Math.PI)*((i%3)-1)*.17f,(float)Math.sin(viaje*Math.PI)*.28f,0);
    int color=switch(i%3){case 0->0xff667c68;case 1->0xff768996;default->0xff705a7c;};
    RitualMesh.beam(pose,out,alma,new Vector3f(alma).add(0,-.12f,0),.035f,0xff17131d,15728880);
    RitualMesh.beam(pose,out,alma,new Vector3f(alma).add(0,.045f,0),.019f,color,15728880);
   }
   if(tipo==0&&tick>=140&&tick<200) {
    Vector3f pie=new Vector3f(-.14f,.91f,3);float alto=.24f;
    RitualMesh.beam(pose,out,pie,new Vector3f(pie).add(0,alto,0),.035f,0xff39323d,15728880);
    RitualMesh.beam(pose,out,new Vector3f(pie).add(0,alto,0),new Vector3f(pie).add(0,alto+.07f,0),.045f,0xff77717a,15728880);
    for(int lado:new int[]{-1,1}) {
     RitualMesh.beam(pose,out,new Vector3f(pie).add(0,.18f,0),new Vector3f(pie).add(lado*.08f,.10f,0),.018f,0xff39323d,15728880);
     RitualMesh.beam(pose,out,new Vector3f(pie).add(0,.08f,0),new Vector3f(pie).add(lado*.035f,0,0),.018f,0xff39323d,15728880);
    }
    RitualMesh.beam(pose,out,new Vector3f(.35f,.9f,3),new Vector3f(.35f,1.1f,3),.025f,0xffb5ab96,15728880);
    RitualMesh.beam(pose,out,new Vector3f(.35f,1.1f,3),new Vector3f(.35f,1.18f,3),.012f,0xff493153,15728880);
   }
   if(tipo==1||tipo==3) {
    float avanza=AparicionRitual.suave(tick,tipo==1?60:80,tipo==1?140:180);
    for(int i=0;i<16*avanza;i++)for(int lado=0;lado<(tipo==3?2:1);lado++) {
     float t=i/16f;
     Vector3f punto=new Vector3f(cuenco).lerp(new Vector3f(tumba).add((lado==0?-1:1)*.28f,.04f,0),t);
     punto.y=Math.max(tumba.y+.025f,.025f);punto.x+=(float)Math.sin(i*1.7)*.035f;
     RitualMesh.beam(pose,out,punto,new Vector3f(punto).add(.035f,0,.025f),.014f,0xff77717a,15728880);
    }
    if(tipo==1&&tick>=240&&tick<360) {
     float venda=AparicionRitual.suave(tick,240,300);
     Vector3f a=new Vector3f(tumba).add(-.28f,.88f,-.14f),b=new Vector3f(tumba).add(.28f,.88f+.08f*venda,-.14f);
     RitualMesh.beam(pose,out,a,new Vector3f(a).lerp(b,venda),.03f,0xffb5ab96,15728880);
    }
    if(tipo==3&&tick>=280)for(int lado:new int[]{-1,1}) {
     Vector3f destino=new Vector3f(tumba).add(lado*fase.separacion(),1.15f,0),anterior=new Vector3f(cuenco);
     float tramo=AparicionRitual.suave(tick,280,380);
     for(int j=1;j<=12*tramo;j++) {
      float t=j/12f;Vector3f punto=new Vector3f(cuenco).lerp(destino,t).add(0,(float)Math.sin(t*Math.PI)*.25f,0);
      RitualMesh.beam(pose,out,anterior,punto,.008f,0xff79548c,15728880);anterior=punto;
     }
     for(int i=0;i<8*tramo;i++) {
      float y=.83f+i*.13f,z=y>1.5f?-.258f:-.14f;
      Vector3f punto=new Vector3f(tumba).add(lado*fase.separacion(),y,z);
      RitualMesh.beam(pose,out,new Vector3f(punto).add(-.035f,0,0),new Vector3f(punto).add(.035f,.03f,0),.006f,0xff79548c,15728880);
     }
    }
   }
  });
 }
}
