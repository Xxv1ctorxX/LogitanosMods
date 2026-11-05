package com.theslave.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadataNode;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static com.theslave.client.LatigoForma.Punto;

/** Previsualizacion de los vertices reales; no es una captura dentro de Minecraft. */
public final class LatigoPreview {
	private record Datos(double[][] points, double[][] handle) { }
	private record Cara(List<Vector3f> vertices, boolean mango) { }
	private static Punto[] puntos(double[][] a) {
		Punto[] p = new Punto[a.length];
		for (int i = 0; i < a.length; i++) p[i] = new Punto(a[i][0], a[i][1], a[i][2], a[i][3]);
		return p;
	}

	private static List<Cara> malla(Punto[] p, boolean mango) throws Exception {
		List<Vector3f> vertices = new ArrayList<>();
		VertexConsumer buffer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
				new Class<?>[]{VertexConsumer.class}, (proxy, metodo, args) -> {
			if (metodo.getName().equals("addVertex")) vertices.add(new Vector3f((Vector3fc) args[1]));
			return proxy;
		});
		var metodo = LatigoRenderer.class.getDeclaredMethod("tubo", Punto[].class, PoseStack.Pose.class,
				VertexConsumer.class, int.class, int.class, float.class, float.class);
		metodo.setAccessible(true);
		metodo.invoke(null, p, new PoseStack().last(), buffer, 0, 0, 0f, 1f);
		List<Cara> caras = new ArrayList<>();
		for (int i = 0; i < vertices.size(); i += 4) caras.add(new Cara(vertices.subList(i, i + 4), mango));
		return caras;
	}

	private static double px(Vector3f p) { return p.x * 0.90 + p.z * 0.44; }
	private static double py(Vector3f p) { return -p.y * 0.96 + p.z * 0.25; }
	private static double profundidad(Cara c) {
		return c.vertices.stream().mapToDouble(p -> p.z * 0.90 - p.x * 0.44 + p.y * 0.25).average().orElse(0);
	}

	private static BufferedImage render(List<Cara> caras, double x0, double y0, double escala) {
		BufferedImage pequeno = new BufferedImage(240, 200, BufferedImage.TYPE_INT_RGB);
		var g = pequeno.createGraphics();
		g.setColor(new Color(35, 37, 40)); g.fillRect(0, 0, 240, 200);
		List<Cara> ordenadas = new ArrayList<>(caras);
		ordenadas.sort(Comparator.comparingDouble(LatigoPreview::profundidad).reversed());
		int i = 0;
		for (Cara c : ordenadas) {
			Polygon poligono = new Polygon();
			for (Vector3f p : c.vertices) poligono.addPoint((int) Math.round((px(p) - x0) * escala), (int) Math.round((py(p) - y0) * escala));
			Vector3f a = c.vertices.get(0), b = c.vertices.get(1), d = c.vertices.get(3);
			Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a));
			float brillo = normal.lengthSquared() < 1e-10 ? 0.7f : 0.65f + 0.35f * Math.abs(normal.normalize().dot(new Vector3f(-0.3f, 0.6f, -0.74f)));
			boolean trenza = (i++ / 8) % 2 == 0;
			int r = c.mango ? 100 : trenza ? 155 : 120, verde = c.mango ? 68 : trenza ? 99 : 74, azul = c.mango ? 39 : trenza ? 56 : 41;
			g.setColor(new Color((int) (r * brillo), (int) (verde * brillo), (int) (azul * brillo)));
			g.fillPolygon(poligono);
		}
		g.dispose();
		BufferedImage grande = new BufferedImage(720, 600, BufferedImage.TYPE_INT_RGB);
		var gg = grande.createGraphics(); gg.drawImage(pequeno, 0, 0, 720, 600, null); gg.dispose();
		return grande;
	}

	public static void main(String[] args) throws Exception {
		Path salida = Path.of("build", "previews"); Files.createDirectories(salida);
		Datos datos;
		try (var s = LatigoPreview.class.getResourceAsStream("/assets/theslave/latigo_geometry.json")) {
			datos = new Gson().fromJson(new InputStreamReader(s, StandardCharsets.UTF_8), Datos.class);
		}
		Punto[] reposo = puntos(datos.points);
		List<Cara> mango = malla(puntos(datos.handle), true);
		List<List<Cara>> frames = new ArrayList<>();
		for (int i = 0; i <= 48; i++) {
			var caras = malla(LatigoForma.animar(reposo, i * 18.0 / 48, -0.25, 0.15, -1), false);
			caras.addAll(mango); frames.add(caras);
		}
		double xmin = Double.POSITIVE_INFINITY, xmax = Double.NEGATIVE_INFINITY, ymin = xmin, ymax = xmax;
		for (var frame : frames) for (var c : frame) for (var p : c.vertices) {
			xmin = Math.min(xmin, px(p)); xmax = Math.max(xmax, px(p));
			ymin = Math.min(ymin, py(p)); ymax = Math.max(ymax, py(p));
		}
		double escala = Math.min(210 / (xmax - xmin), 170 / (ymax - ymin));
		double x0 = (xmin + xmax) / 2 - 120 / escala, y0 = (ymin + ymax) / 2 - 100 / escala;
		double sx0 = Double.POSITIVE_INFINITY, sx1 = Double.NEGATIVE_INFINITY, sy0 = sx0, sy1 = sx1;
		for (var c : frames.get(0)) for (var p : c.vertices) {
			sx0 = Math.min(sx0, px(p)); sx1 = Math.max(sx1, px(p));
			sy0 = Math.min(sy0, py(p)); sy1 = Math.max(sy1, py(p));
		}
		double estatica = Math.min(210 / (sx1 - sx0), 170 / (sy1 - sy0));
		ImageIO.write(render(frames.get(0), (sx0 + sx1) / 2 - 120 / estatica,
				(sy0 + sy1) / 2 - 100 / estatica, estatica), "png", salida.resolve("latigo-3d.png").toFile());
		var escritor = ImageIO.getImageWritersByFormatName("gif").next();
		try (var output = ImageIO.createImageOutputStream(salida.resolve("latigo-animation.gif").toFile())) {
			escritor.setOutput(output); escritor.prepareWriteSequence(null);
			for (int i = 0; i < frames.size(); i++) {
				BufferedImage image = render(frames.get(i), x0, y0, escala);
				var metadata = escritor.getDefaultImageMetadata(javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image), null);
				String formato = metadata.getNativeMetadataFormatName();
				var root = (IIOMetadataNode) metadata.getAsTree(formato);
				var control = new IIOMetadataNode("GraphicControlExtension");
				control.setAttribute("disposalMethod", "none"); control.setAttribute("userInputFlag", "FALSE");
				control.setAttribute("transparentColorFlag", "FALSE"); control.setAttribute("transparentColorIndex", "0");
				control.setAttribute("delayTime", i == 0 || i == frames.size() - 1 ? "65" : "3"); root.appendChild(control);
				if (i == 0) {
					var extensiones = new IIOMetadataNode("ApplicationExtensions"); var loop = new IIOMetadataNode("ApplicationExtension");
					loop.setAttribute("applicationID", "NETSCAPE"); loop.setAttribute("authenticationCode", "2.0");
					loop.setUserObject(new byte[]{1, 0, 0}); extensiones.appendChild(loop); root.appendChild(extensiones);
				}
				metadata.setFromTree(formato, root);
				escritor.writeToSequence(new IIOImage(image, null, metadata), null);
			}
			escritor.endWriteSequence();
		} finally { escritor.dispose(); }
		System.out.println("Previsualizaciones guardadas en " + salida.toAbsolutePath());
	}
}
