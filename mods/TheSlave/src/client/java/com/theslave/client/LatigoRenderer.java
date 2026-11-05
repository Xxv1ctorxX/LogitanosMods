package com.theslave.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.theslave.TheSlaveMod;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static com.theslave.client.LatigoForma.Punto;

/** Tubo octogonal continuo con textura pixelada; nunca rota cajas a saltos de 22,5 grados. */
public final class LatigoRenderer implements SpecialModelRenderer<LatigoRenderer.Fotograma> {
	private record Geometria(double[][] points, double[][] handle) { }
	public record Fotograma(Punto[] cuerda, double tick) { }
	private static final int LADOS = 8;
	private final Punto[] reposo;
	private final Punto[] mango;
	private final Vector3fc[] limites;

	private LatigoRenderer() {
		try (var stream = LatigoRenderer.class.getResourceAsStream("/assets/theslave/latigo_geometry.json")) {
			if (stream == null) throw new IllegalStateException("Falta latigo_geometry.json");
			Geometria g = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Geometria.class);
			reposo = puntos(g.points);
			mango = puntos(g.handle);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("No se puede cargar la geometria del latigo", e);
		}
		limites = new Vector3fc[]{new Vector3f(0, 0, 0.42f), new Vector3f(1, 1, 0.58f)};
	}

	private static Punto[] puntos(double[][] datos) {
		Punto[] salida = new Punto[datos.length];
		for (int i = 0; i < datos.length; i++) {
			double[] p = datos[i];
			salida[i] = new Punto(p[0], p[1], p[2], p[3]);
		}
		return salida;
	}

	public static void registrar() {
		ModelLoadingPlugin.register(context -> context.modifyItemModelAfterBake().register((original, ctx) -> {
			if (!ctx.itemId().equals(TheSlaveMod.id("latigo"))) return original;
			var baker = ctx.bakingContext().blockModelBaker();
			var base = baker.getModel(TheSlaveMod.id("item/latigo"));
			var propiedades = ModelRenderProperties.fromResolvedModel(baker, base, base.getTopTextureSlots());
			return new Modelo(original, new LatigoRenderer(), propiedades, new Matrix4f(ctx.transformation()));
		}));
	}

	private record Modelo(ItemModel icono, LatigoRenderer renderer, ModelRenderProperties propiedades,
			Matrix4f transformacion) implements ItemModel {
		@Override
		public void update(ItemStackRenderState salida, ItemStack stack, ItemModelResolver resolver,
				ItemDisplayContext contexto, ClientLevel level, ItemOwner owner, int seed) {
			if (contexto == ItemDisplayContext.GUI) {
				icono.update(salida, stack, resolver, contexto, level, owner, seed);
				return;
			}
			double tick = 0;
			boolean mano = contexto.firstPerson() || contexto == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
					|| contexto == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
			if (mano && owner != null && owner.asLivingEntity() != null && level != null) {
				var vivo = owner.asLivingEntity();
				boolean izquierda = vivo.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
				InteractionHand hand = contexto.leftHand() == izquierda ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
				var ataque = LatigoAnimacion.obtener(vivo, hand);
				if (ataque != null) tick = level.getGameTime() - ataque.inicio()
						+ Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			}
			Punto[] cuerda = LatigoForma.animar(renderer.reposo, tick, -0.25, 0.15, -1);
			Fotograma frame = new Fotograma(cuerda, tick);
			var capa = salida.newLayer();
			capa.setExtents(() -> renderer.limites);
			capa.setLocalTransform(transformacion);
			capa.setupSpecialModel(renderer, frame);
			propiedades.applyToLayer(capa, contexto);
			salida.appendModelIdentityElement(this);
			if (tick > 0 && tick < 18) { salida.setAnimated(); salida.appendModelIdentityElement(frame); }
		}
	}

	@Override
	public void submit(Fotograma frame, PoseStack poses, SubmitNodeCollector collector,
			int luz, int overlay, boolean brillo, int outline) {
		poses.pushPose();
		if (frame.tick > 0 && frame.tick < 18) {
			Punto base = mango[0];
			double gesto = frame.tick < 3 ? -LatigoForma.suave(frame.tick / 3) * 12
					: -12 * (1 - LatigoForma.suave((frame.tick - 3) / 6));
			poses.translate(base.x(), base.y(), base.z());
			poses.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees((float) gesto)));
			poses.translate(-base.x(), -base.y(), -base.z());
		}
		var tipo = brillo ? RenderTypes.entitySolidGlint(TheSlaveMod.id("textures/item/latigo_3d.png"))
				: RenderTypes.entitySolid(TheSlaveMod.id("textures/item/latigo_3d.png"));
		collector.submitCustomGeometry(poses, tipo, (pose, buffer) -> {
			tubo(frame.cuerda, pose, buffer, luz, overlay, 0.5f, 1);
			tubo(mango, pose, buffer, luz, overlay, 0, 0.5f);
		});
		poses.popPose();
	}

	/** Marco ortonormal por anillo: superficies con volumen, normales y tapas. */
	private static void tubo(Punto[] puntos, PoseStack.Pose pose, VertexConsumer buffer,
			int luz, int overlay, float u0, float u1) {
		Vector3f[][] vertices = new Vector3f[puntos.length][LADOS];
		Vector3f[][] normales = new Vector3f[puntos.length][LADOS];
		Vector3f anterior = null;
		for (int i = 0; i < puntos.length; i++) {
			Punto a = puntos[Math.max(0, i - 1)], b = puntos[Math.min(puntos.length - 1, i + 1)], p = puntos[i];
			Vector3f eje = new Vector3f((float) (b.x() - a.x()), (float) (b.y() - a.y()), (float) (b.z() - a.z())).normalize();
			Vector3f ref = Math.abs(eje.z) < 0.9 ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
			Vector3f normal = anterior == null ? eje.cross(ref, new Vector3f())
					: new Vector3f(anterior).fma(-anterior.dot(eje), eje);
			if (normal.lengthSquared() < 1e-8f) normal = eje.cross(ref, new Vector3f());
			normal.normalize();
			anterior = normal;
			Vector3f binormal = eje.cross(normal, new Vector3f()).normalize();
			for (int j = 0; j < LADOS; j++) {
				double ang = j * Math.PI * 2 / LADOS;
				Vector3f n = new Vector3f(normal).mul((float) Math.cos(ang)).fma((float) Math.sin(ang), binormal);
				normales[i][j] = n;
				vertices[i][j] = new Vector3f(n).mul((float) p.radio()).add((float) p.x(), (float) p.y(), (float) p.z());
			}
		}
		float avance = 0;
		for (int i = 0; i < puntos.length - 1; i++) {
			Punto a = puntos[i], b = puntos[i + 1];
			float siguiente = avance + (float) Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2)) * 12;
			// Cada segmento repite un pequeno parche de cuero; nunca se sale del atlas.
			float v0 = (avance % 1) * 0.75f, v1 = Math.min(1, v0 + (siguiente - avance));
			for (int j = 0; j < LADOS; j++) {
				int k = (j + 1) % LADOS;
				float uj = u0 + (u1 - u0) * j / LADOS, uk = u0 + (u1 - u0) * (j + 1) / LADOS;
				vertice(pose, buffer, vertices[i][j], normales[i][j], uj, v0, luz, overlay);
				vertice(pose, buffer, vertices[i][k], normales[i][k], uk, v0, luz, overlay);
				vertice(pose, buffer, vertices[i + 1][k], normales[i + 1][k], uk, v1, luz, overlay);
				vertice(pose, buffer, vertices[i + 1][j], normales[i + 1][j], uj, v1, luz, overlay);
			}
			avance = siguiente;
		}
		for (int extremo : new int[]{0, puntos.length - 1}) {
			Punto p = puntos[extremo];
			Vector3f centro = new Vector3f((float) p.x(), (float) p.y(), (float) p.z());
			Punto vecino = puntos[extremo == 0 ? 1 : extremo - 1];
			Vector3f n = new Vector3f((float) (p.x() - vecino.x()), (float) (p.y() - vecino.y()),
					(float) (p.z() - vecino.z())).normalize();
			for (int j = 0; j < LADOS; j++) {
				int a = extremo == 0 ? j : (j + 1) % LADOS, b = extremo == 0 ? (j + 1) % LADOS : j;
				vertice(pose, buffer, centro, n, u0, 0, luz, overlay);
				vertice(pose, buffer, vertices[extremo][b], n, u1, 0, luz, overlay);
				vertice(pose, buffer, vertices[extremo][a], n, u1, 1, luz, overlay);
				vertice(pose, buffer, centro, n, u0, 1, luz, overlay);
			}
		}
	}

	private static void vertice(PoseStack.Pose pose, VertexConsumer buffer, Vector3f p, Vector3f normal,
			float u, float v, int luz, int overlay) {
		buffer.addVertex(pose, p).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(luz).setNormal(pose, normal);
	}

	@Override public Fotograma extractArgument(ItemStack stack) { return new Fotograma(reposo, 0); }
	@Override public void getExtents(Consumer<Vector3fc> consumer) { for (var limite : limites) consumer.accept(limite); }
}
