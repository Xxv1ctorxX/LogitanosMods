package com.ludopathyroulette.client;

import com.google.gson.*;
import com.ludopathyroulette.*;
import com.ludopathyroulette.core.WheelMath;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Rueda y bola en 3D con texturas, usando geometría propia compatible con SubmitNodeCollector de 26.3. */
public final class MachineRenderer implements BlockEntityRenderer<RouletteBlockEntity,MachineRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        Direction facing=Direction.NORTH;
        JsonObject view;
        double elapsed;
    }
    private static final int SEG=40;
    public MachineRenderer(BlockEntityRendererProvider.Context c){}
    public State createRenderState(){return new State();}
    public void extractRenderState(RouletteBlockEntity be,State s,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(be,s,partial,camera,overlay);
        s.facing=be.getBlockState().getValue(RouletteBlock.FACING);
        try{s.view=JsonParser.parseString(be.publicJson).getAsJsonObject();s.elapsed=s.view.has("spin")?s.view.getAsJsonObject("spin").get("elapsed").getAsDouble()+Math.min(20,Math.max(0,be.getLevel().getGameTime()-be.snapshotTick))+partial:0;}
        catch(Exception e){s.view=new JsonObject();s.elapsed=0;}
    }
    private static Identifier tex(String n){return RouletteMod.id("textures/block/"+n+".png");}

    /** Plano de la rueda: origen, e1 (ángulo 0), e2 (ángulo +90º, "abajo" en la GUI) y normal de extrusión. */
    private record Frame(double ox,double oy,double oz,double[] e1,double[] e2,double[] n){
        float[] p(double r,double a,double h){
            double c=Math.cos(a)*r,s=Math.sin(a)*r;
            return new float[]{(float)(ox+e1[0]*c+e2[0]*s+n[0]*h),(float)(oy+e1[1]*c+e2[1]*s+n[1]*h),(float)(oz+e1[2]*c+e2[2]*s+n[2]*h)};
        }
        float[] dir(double a){double c=Math.cos(a),s=Math.sin(a);return new float[]{(float)(e1[0]*c+e2[0]*s),(float)(e1[1]*c+e2[1]*s),(float)(e1[2]*c+e2[2]*s)};}
        float[] normal(){return new float[]{(float)n[0],(float)n[1],(float)n[2]};}
    }

    public void submit(State s,PoseStack stack,SubmitNodeCollector collector,CameraRenderState camera){
        if(s.view==null||!s.view.has("rows"))return;
        JsonObject outer=s.view;
        JsonObject view=!outer.has("spin")&&outer.has("lastFrame")?outer.getAsJsonObject("lastFrame"):outer;
        double elapsed=view!=outer?view.getAsJsonObject("spin").get("duration").getAsDouble():s.elapsed;
        boolean casino=view.get("casino").getAsBoolean();
        double rotation=0,ballAngle=0,ballRadius=.8,ballHeight=0;
        int[] weights=WheelCanvas.weights(view);
        if(view.has("spin")){
            JsonObject spin=view.getAsJsonObject("spin");int winner=spin.get("winner").getAsInt(),duration=spin.get("duration").getAsInt();long seed=spin.get("seed").getAsLong();
            if(casino){var b=WheelMath.casino(weights,winner,seed,elapsed,duration);rotation=b.rotor();ballAngle=b.angle();ballRadius=b.radius();ballHeight=b.height();}
            else rotation=WheelMath.fortuna(weights,winner,seed,elapsed,duration);
        }else if(!RouletteClient.options.reducedMotion)rotation=(System.nanoTime()/1e9)*.08;
        final double rot=rotation,bAngle=ballAngle,bRadius=ballRadius,bHeight=ballHeight;
        // Fortuna: disco vertical sobre la cara frontal (-z). Bola: rotor horizontal sobre la caja (hasta y=12/16).
        final double R=casino?.35:.34,T=casino?.045:.05;
        final Frame f=casino
            ?new Frame(0,.255,0,new double[]{-1,0,0},new double[]{0,0,-1},new double[]{0,1,0})
            :new Frame(0,0,-.5,new double[]{-1,0,0},new double[]{0,-1,0},new double[]{0,0,-1});
        JsonArray rows=view.getAsJsonArray("rows");long total=WheelMath.sum(weights);
        boolean open=outer.has("hatch")&&outer.get("hatch").getAsBoolean();
        stack.pushPose();stack.translate(.5,.5,.5);stack.rotateDegrees(Axis.YP,180-s.facing.toYRot());
        // 1) Sectores texturizados en tres bandas radiales: cubo, sector teñido por categoría y aro
        collector.submitCustomGeometry(stack,RenderTypes.entityCutout(tex("wheel_face")),(pose,c)->{
            long before=0;
            for(int i=0;i<weights.length;i++){
                double start=WheelMath.TAU*before/total+rot,end=WheelMath.TAU*(before+weights[i])/total+rot;before+=weights[i];
                int tint=0xff000000|WheelCanvas.color(rows.get(i).getAsJsonObject().get("category").getAsString());
                int n=Math.max(1,(int)Math.ceil((end-start)/.25));
                for(int k=0;k<n;k++){
                    double a0=start+(end-start)*k/n,a1=start+(end-start)*(k+1)/n;float u0=(float)k/n,u1=(float)(k+1)/n;
                    band(pose,c,f,a0,a1,0,R*5/16,T,u0,u1,0,5/16f,0xffffffff);
                    band(pose,c,f,a0,a1,R*5/16,R*12/16,T,u0,u1,5/16f,12/16f,tint);
                    band(pose,c,f,a0,a1,R*12/16,R,T,u0,u1,12/16f,1f,0xffffffff);
                    quad(pose,c,f.p(R,a0,0),f.p(R,a1,0),f.p(R,a1,T),f.p(R,a0,T),f.dir((a0+a1)/2),u0,13/16f,u1,14/16f,0xffffffff);
                }
            }
        });
        // 2) Metal dorado: aro exterior, buje central y clavijas entre sectores
        collector.submitCustomGeometry(stack,RenderTypes.entityCutout(tex("trim")),(pose,c)->{
            double w=.03,H=T+.012,hr=.07,hh=T+.03;
            for(int k=0;k<SEG;k++){
                double a0=WheelMath.TAU*k/SEG,a1=WheelMath.TAU*(k+1)/SEG;float[] d=f.dir((a0+a1)/2);
                quad(pose,c,f.p(R,a0,H),f.p(R,a1,H),f.p(R+w,a1,H),f.p(R+w,a0,H),f.normal(),0,0,1,1,0xffffffff);
                quad(pose,c,f.p(R+w,a0,0),f.p(R+w,a1,0),f.p(R+w,a1,H),f.p(R+w,a0,H),d,0,0,1,1,0xffffffff);
                quad(pose,c,f.p(R,a0,T),f.p(R,a1,T),f.p(R,a1,H),f.p(R,a0,H),neg(d),0,0,1,1,0xffffffff);
                quad(pose,c,f.p(0,a0,hh),f.p(0,a1,hh),f.p(hr,a1,hh),f.p(hr,a0,hh),f.normal(),.2f,.2f,.8f,.8f,0xffffffff);
                quad(pose,c,f.p(hr,a0,T),f.p(hr,a1,T),f.p(hr,a1,hh),f.p(hr,a0,hh),d,0,0,1,1,0xffffffff);
            }
            long before=0;
            for(int i=0;i<weights.length;i++){
                double a=WheelMath.TAU*before/total+rot;before+=weights[i];
                box(pose,c,f.p(R*.96,a,T+.008),f.dir(a),f.dir(a+Math.PI/2),f.normal(),.012f,.012f,.012f);
            }
        });
        // 3) Aguja 3D (fortuna) o bola esférica (ruleta de casino)
        if(casino){
            double br=.024,rr=bRadius*R*.93,h=T+br+bHeight*.12;
            float[] ctr=f.p(rr,bAngle,h);
            collector.submitCustomGeometry(stack,RenderTypes.entityCutout(tex("ball")),(pose,c)->sphere(pose,c,ctr,(float)br));
        }else{
            collector.submitCustomGeometry(stack,RenderTypes.entityCutout(tex("white")),(pose,c)->{
                float zf=-.572f,zb=-.612f,top=.425f,tip=.292f,hw=.036f;
                float[] a={-hw,top,zf},b={hw,top,zf},t={0,tip,zf},a2={-hw,top,zb},b2={hw,top,zb},t2={0,tip,zb};
                quad(pose,c,a,b,t,t,new float[]{0,0,-1},0,0,1,1,0xffd23a3a);
                quad(pose,c,a2,b2,t2,t2,new float[]{0,0,1},0,0,1,1,0xff962626);
                quad(pose,c,a,b,b2,a2,new float[]{0,1,0},0,0,1,1,0xffd23a3a);
                quad(pose,c,a,t,t2,a2,new float[]{-1,-.4f,0},0,0,1,1,0xffb83030);
                quad(pose,c,b,t,t2,b2,new float[]{1,-.4f,0},0,0,1,1,0xffb83030);
            });
        }
        // 4) Puerta lateral y candado
        float y0=casino?-.40f:-.32f,y1=casino?.15f:.32f,ym=(y0+y1)/2;
        collector.submitCustomGeometry(stack,RenderTypes.entityCutout(tex("white")),(pose,c)->{
            float dx=open?.72f:.501f;float[] nx={1,0,0};
            quad(pose,c,new float[]{.501f,y0,-.25f},new float[]{dx,y0,.25f},new float[]{dx,y1,.25f},new float[]{.501f,y1,-.25f},nx,0,0,1,1,0xff343943);
            quad(pose,c,new float[]{dx+.002f,ym-.06f,.08f},new float[]{dx+.002f,ym-.06f,.16f},new float[]{dx+.002f,ym+.06f,.16f},new float[]{dx+.002f,ym+.06f,.08f},nx,0,0,1,1,open?0xff83ad89:0xffc7a956);
        });
        stack.popPose();
    }

    private static float[] neg(float[] v){return new float[]{-v[0],-v[1],-v[2]};}
    private static void band(PoseStack.Pose p,VertexConsumer c,Frame f,double a0,double a1,double r0,double r1,double h,float u0,float u1,float v0,float v1,int color){
        quad(p,c,f.p(r0,a0,h),f.p(r0,a1,h),f.p(r1,a1,h),f.p(r1,a0,h),f.normal(),u0,v0,u1,v1,color);
    }
    /** Caja orientada con semiejes hx/hy/hz sobre ax/ay/az y la textura completa en cada cara. */
    private static void box(PoseStack.Pose p,VertexConsumer c,float[] ctr,float[] ax,float[] ay,float[] az,float hx,float hy,float hz){
        float[][] axes={ax,ay,az};float[] h={hx,hy,hz};
        for(int i=0;i<3;i++)for(int sign=-1;sign<=1;sign+=2){
            float[] u=axes[(i+1)%3],v=axes[(i+2)%3];float hu=h[(i+1)%3],hv=h[(i+2)%3];
            float[] o=new float[3],nrm=new float[3];
            for(int k=0;k<3;k++){nrm[k]=axes[i][k]*sign;o[k]=ctr[k]+nrm[k]*h[i];}
            quad(p,c,pt(o,u,-hu,v,-hv),pt(o,u,hu,v,-hv),pt(o,u,hu,v,hv),pt(o,u,-hu,v,hv),nrm,0,0,1,1,0xffffffff);
        }
    }
    private static float[] pt(float[] o,float[] u,float su,float[] v,float sv){return new float[]{o[0]+u[0]*su+v[0]*sv,o[1]+u[1]*su+v[1]*sv,o[2]+u[2]*su+v[2]*sv};}
    private static void sphere(PoseStack.Pose p,VertexConsumer c,float[] ctr,float r){
        int lat=8,lon=12;
        for(int i=0;i<lat;i++)for(int j=0;j<lon;j++){
            float[][] q=new float[4][];int[][] ij={{i,j},{i,j+1},{i+1,j+1},{i+1,j}};
            for(int k=0;k<4;k++){double phi=Math.PI*ij[k][0]/lat,th=WheelMath.TAU*ij[k][1]/lon;q[k]=new float[]{ctr[0]+(float)(r*Math.sin(phi)*Math.cos(th)),ctr[1]+(float)(r*Math.cos(phi)),ctr[2]+(float)(r*Math.sin(phi)*Math.sin(th))};}
            double phi=Math.PI*(i+.5)/lat,th=WheelMath.TAU*(j+.5)/lon;
            float[] nrm={(float)(Math.sin(phi)*Math.cos(th)),(float)Math.cos(phi),(float)(Math.sin(phi)*Math.sin(th))};
            quad(p,c,q[0],q[1],q[2],q[3],nrm,(float)j/lon,(float)i/lat,(float)(j+1)/lon,(float)(i+1)/lat,0xffffffff);
        }
    }
    /** Quad con UV (u0,v0)→(u1,v1); si el sentido de giro no coincide con la normal se invierte. */
    private static void quad(PoseStack.Pose p,VertexConsumer c,float[] a,float[] b,float[] cc,float[] d,float[] n,float u0,float v0,float u1,float v1,int color){
        float[][] pos={a,b,cc,d};float[][] uv={{u0,v0},{u1,v0},{u1,v1},{u0,v1}};
        float cx=(b[1]-a[1])*(d[2]-a[2])-(b[2]-a[2])*(d[1]-a[1]),cy=(b[2]-a[2])*(d[0]-a[0])-(b[0]-a[0])*(d[2]-a[2]),cz=(b[0]-a[0])*(d[1]-a[1])-(b[1]-a[1])*(d[0]-a[0]);
        int[] order=cx*n[0]+cy*n[1]+cz*n[2]>=0?new int[]{0,1,2,3}:new int[]{0,3,2,1};
        for(int k:order)vertex(p,c,pos[k][0],pos[k][1],pos[k][2],color,uv[k][0],uv[k][1],n);
    }
    private static void vertex(PoseStack.Pose p,VertexConsumer c,float x,float y,float z,int color,float u,float v,float[] n){c.addVertex(p,x,y,z).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(p,n[0],n[1],n[2]);}
    public int getViewDistance(){return 32;}
}
