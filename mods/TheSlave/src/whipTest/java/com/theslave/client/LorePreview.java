package com.theslave.client;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
/** Hoja de modelos reales JSON, render isometrico fuera del juego. */
public final class LorePreview {
 record Face(double[][] p,Color c,double depth){}
 static double px(double[] p){return (p[0]-p[2])*.78;}
 static double py(double[] p){return -p[1]+(p[0]+p[2])*.32;}
 public static void main(String[] args)throws Exception{
  String[] names={"item/relicario_animas","item/ofrenda_muerte","item/tributo_condena","item/sello_almas","item/tributo_doble_condena","block/altar_ceniza","block/urna_animas","block/lampara_animas","item/lecho_ceniza","block/lecho_ceniza_head_vivo","block/lecho_ceniza_head_tumba","block/lecho_ceniza_head_tumba_fusionado","item/alma_vinculada","item/alma_vinculada_fusionada"};
  Path root=Path.of("src/main/resources/assets/theslave");BufferedImage sheet=new BufferedImage(960,((names.length+3)/4)*260,BufferedImage.TYPE_INT_RGB);var canvas=sheet.createGraphics();canvas.setColor(new Color(27,26,31));canvas.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
  int[][][] indices={{{0,0,0},{1,0,0},{1,1,0},{0,1,0}},{{0,0,1},{0,1,1},{1,1,1},{1,0,1}},{{0,1,0},{1,1,0},{1,1,1},{0,1,1}},{{0,0,0},{0,0,1},{1,0,1},{1,0,0}},{{0,0,0},{0,1,0},{0,1,1},{0,0,1}},{{1,0,0},{1,0,1},{1,1,1},{1,1,0}}};String[] directions={"north","south","up","down","west","east"};
  for(int k=0;k<names.length;k++){
   JsonObject model=JsonParser.parseString(Files.readString(root.resolve("models/"+names[k]+".json"))).getAsJsonObject();var textures=model.getAsJsonObject("textures");ArrayList<Face> faces=new ArrayList<>();
   for(var elem:model.getAsJsonArray("elements")){
    var e=elem.getAsJsonObject();double[] a=new double[3],b=new double[3];for(int d=0;d<3;d++){a[d]=e.getAsJsonArray("from").get(d).getAsDouble();b[d]=e.getAsJsonArray("to").get(d).getAsDouble();}
    for(int d=0;d<6;d++){
     var face=e.getAsJsonObject("faces").getAsJsonObject(directions[d]);if(face==null)continue;String tex=face.get("texture").getAsString();while(tex.startsWith("#"))tex=textures.get(tex.substring(1)).getAsString();
     BufferedImage texture=ImageIO.read(root.resolve("textures/"+tex.split(":")[1]+".png").toFile());
     var uv=face.getAsJsonArray("uv");double u=uv==null?8:(uv.get(0).getAsDouble()+uv.get(2).getAsDouble())/2,v=uv==null?8:(uv.get(1).getAsDouble()+uv.get(3).getAsDouble())/2;
     Color base=new Color(texture.getRGB(Math.clamp((int)(u*texture.getWidth()/16),0,texture.getWidth()-1),Math.clamp((int)(v*texture.getHeight()/16),0,texture.getHeight()-1)),true);
     double shade=d==2?1:d==0||d==4?.7:.85;Color color=new Color((int)(base.getRed()*shade),(int)(base.getGreen()*shade),(int)(base.getBlue()*shade));
     double[][] points=new double[4][3];double depth=0;for(int i=0;i<4;i++)for(int j=0;j<3;j++){points[i][j]=indices[d][i][j]==0?a[j]:b[j];depth+=points[i][j]*(j==1?-1:1);}
     faces.add(new Face(points,color,depth));
    }
   }
   faces.sort(Comparator.comparingDouble(Face::depth));BufferedImage tile=new BufferedImage(120,120,BufferedImage.TYPE_INT_RGB);var g=tile.createGraphics();g.setColor(new Color(35,33,39));g.fillRect(0,0,120,120);
   double minX=Double.POSITIVE_INFINITY,maxX=-minX,minY=minX,maxY=-minX;
   for(Face face:faces)for(double[] point:face.p){minX=Math.min(minX,px(point));maxX=Math.max(maxX,px(point));minY=Math.min(minY,py(point));maxY=Math.max(maxY,py(point));}
   double scale=Math.min(104/Math.max(1,maxX-minX),104/Math.max(1,maxY-minY));
   double ox=60-(minX+maxX)*scale/2,oy=60-(minY+maxY)*scale/2;
   for(Face face:faces){Polygon polygon=new Polygon();for(double[] point:face.p)polygon.addPoint((int)(ox+px(point)*scale),(int)(oy+py(point)*scale));g.setColor(face.c);g.fillPolygon(polygon);}g.dispose();
   int x=k%4*240,y=k/4*260;canvas.drawImage(tile,x,y,240,240,null);canvas.setColor(new Color(190,184,190));canvas.drawString(names[k].split("/")[1],x+6,y+253);
  }
  canvas.dispose();Files.createDirectories(Path.of("build/previews"));ImageIO.write(sheet,"png",Path.of("build/previews/lore-models.png").toFile());
 }
}
