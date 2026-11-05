package com.theslave.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3fc;
import static com.theslave.client.LatigoForma.Punto;

/** Comprueba las coordenadas empaquetadas y los vertices emitidos por el renderer real. */
public final class LatigoTest {
	private record Datos(double[][] points, double[][] handle) { }
	private record Vertice(double x, double y, double z) { }
	private static int comprobaciones;

	private static void exigir(boolean condicion, String mensaje) {
		comprobaciones++;
		if (!condicion) throw new AssertionError(mensaje);
	}

	private static double distancia(Punto a, Punto b) {
		return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
	}

	public static void main(String[] args) throws Exception {
		// Usa los decodificadores de Minecraft, no solo un parser JSON generico.
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		net.minecraft.client.renderer.item.ItemModels.bootstrap();
		net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties.bootstrap();
		try (var reader = new InputStreamReader(LatigoTest.class.getResourceAsStream("/assets/theslave/items/latigo.json"), StandardCharsets.UTF_8)) {
			var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().get("model");
			exigir(net.minecraft.client.renderer.item.ItemModels.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).isSuccess(), "Definicion de item incompatible con Minecraft");
		}
		for (String nombre : new String[]{"latigo", "latigo_icon"}) {
			try (var reader = new InputStreamReader(LatigoTest.class.getResourceAsStream("/assets/theslave/models/item/" + nombre + ".json"), StandardCharsets.UTF_8)) {
				exigir(net.minecraft.client.resources.model.cuboid.CuboidModel.fromStream(reader) != null, "Modelo incompatible con Minecraft");
			}
		}
		Datos datos;
		try (var stream = LatigoTest.class.getResourceAsStream("/assets/theslave/latigo_geometry.json")) {
			if (stream == null) throw new AssertionError("No hay geometria empaquetada");
			datos = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Datos.class);
		}
		Punto[] reposo = new Punto[datos.points.length];
		for (int i = 0; i < reposo.length; i++) {
			double[] p = datos.points[i];
			reposo[i] = new Punto(p[0], p[1], p[2], p[3]);
			exigir(p[0] > 0 && p[0] < 1 && p[1] > 0 && p[1] < 1 && p[3] > 0, "Coil fuera del lienzo");
			if (i > 0) exigir(p[3] < reposo[i - 1].radio(), "La cuerda no se afina");
		}
		exigir(LatigoForma.animar(reposo, 0, 0, 0, -1) == reposo, "Reposo distinto");
		exigir(LatigoForma.animar(reposo, 18, 0, 0, -1) == reposo, "No recupera exactamente la espiral");
		exigir(LatigoForma.despliegue(9) == 1, "El chasquido no coincide con el despliegue maximo");
		for (double t = 0; t <= 18; t += 0.125) {
			Punto[] actual = LatigoForma.animar(reposo, t, -0.25, 0.15, -1);
			exigir(actual.length == reposo.length && actual[0].equals(reposo[0]), "Cuerda duplicada o separada del mango");
			for (int i = 1; i < actual.length; i++) {
				exigir(Double.isFinite(actual[i].x()) && Double.isFinite(actual[i].y()) && Double.isFinite(actual[i].z()), "Coordenada no finita");
				exigir(Math.abs(distancia(actual[i - 1], actual[i]) - distancia(reposo[i - 1], reposo[i])) < 1e-9, "Cambia la longitud de un segmento");
				exigir(actual[i].radio() == reposo[i].radio(), "Cambia el grosor al animar");
			}
		}
		Punto[] extendido = LatigoForma.animar(reposo, 9, -0.25, 0.15, -1);
		exigir(distancia(extendido[0], extendido[extendido.length - 1]) > 2, "La cuerda no se despliega");
		// Ejecuta el mismo emisor de vertices usado en Minecraft, tambien en mitad del retorno.
		for (double tick : new double[]{0, 4.5, 7, 9, 12, 16, 18}) {
			verificarMalla(LatigoForma.animar(reposo, tick, -0.25, 0.15, -1));
		}
		System.out.println("Latigo: " + comprobaciones + " comprobaciones correctas (forma, longitud, retorno y malla).");
	}

	private static void verificarMalla(Punto[] puntos) throws Exception {
		List<Vertice> vertices = new ArrayList<>();
		VertexConsumer buffer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
				new Class<?>[]{VertexConsumer.class}, (proxy, metodo, args) -> {
			if (metodo.getName().equals("addVertex")) {
				Vector3fc p = (Vector3fc) args[1];
				vertices.add(new Vertice(p.x(), p.y(), p.z()));
			} else if (metodo.getName().equals("setNormal")) {
				Vector3fc n = (Vector3fc) args[1];
				exigir(Float.isFinite(n.x()) && Math.abs(n.length() - 1) < 1e-4, "Normal invalida");
			} else if (metodo.getName().equals("setUv")) {
				float u = (float) args[0], v = (float) args[1];
				exigir(u >= 0 && u <= 1 && v >= 0 && v <= 1, "UV fuera de la textura");
			}
			return proxy;
		});
		var tubo = LatigoRenderer.class.getDeclaredMethod("tubo", Punto[].class, PoseStack.Pose.class,
				VertexConsumer.class, int.class, int.class, float.class, float.class);
		tubo.setAccessible(true);
		tubo.invoke(null, puntos, new PoseStack().last(), buffer, 0, 0, 0.5f, 1f);
		exigir(vertices.size() == ((puntos.length - 1) * 8 + 16) * 4, "Faltan caras o tapas");
		for (var v : vertices) exigir(Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z), "Vertice invalido");
	}
}
