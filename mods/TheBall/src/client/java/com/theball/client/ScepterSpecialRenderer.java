package com.theball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Cetro en la mano dibujado por codigo con la MISMA esfera de la bola de aluminio (no con cubos), la corona de oro
 * y un palo con anillas. Las unidades de abajo son "pixeles" del modelo (1/16 de bloque), medidas desde el centro.
 */
public class ScepterSpecialRenderer implements SpecialModelRenderer<Void> {
	private static final float PX = 1.0f / 16.0f;
	private static final int SILVER = 0xFFE6ECF2;
	private static final int DARK_GOLD = 0xFFC48A1E;

	@Override
	public void submit(Void argument, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
			boolean foil, int outline) {
		poseStack.pushPose();
		// El origen de un modelo especial es la ESQUINA de la celda de 1x1x1, no su centro: lo centramos para que
		// el agarre del palo quede donde dice la pose de mano (antes el cetro salia 8 px desplazado, a la altura de la cadera).
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.scale(PX, PX, PX);

		// Palo, contera, anillas y collar
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(CrownMesh.TEXTURE), (pose, c) -> {
			CrownMesh.cuboid(pose, c, light, SILVER, 0f, 0.5f, 0f, 2f, 0f, 0.8f, 7f, 0.8f);
			CrownMesh.cuboid(pose, c, light, CrownMesh.GOLD, 0f, 0.5f, 0f, -5.6f, 0f, 1.3f, 0.9f, 1.3f);
			for (float y : new float[] {-2.5f, 1.5f, 5.5f}) {
				CrownMesh.cuboid(pose, c, light, CrownMesh.GOLD, 0f, 0.5f, 0f, y, 0f, 1.15f, 0.45f, 1.15f);
			}
			CrownMesh.cuboid(pose, c, light, CrownMesh.GOLD, 0f, 0.5f, 0f, 9.6f, 0f, 2.1f, 0.6f, 2.1f);
			CrownMesh.cuboid(pose, c, light, DARK_GOLD, 0f, 0.5f, 0f, 10.5f, 0f, 1.5f, 0.4f, 1.5f);
		});

		// La bola: la esfera real del mod
		poseStack.pushPose();
		poseStack.translate(0.0f, 14.0f, 0.0f);
		float d = 7.0f;
		poseStack.scale(d, d, d);
		Quaternionf rot = new Quaternionf();
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(AluminumBallRenderer.TEXTURE),
				(pose, c) -> SphereMesh.add(pose, c, rot, light, -1));
		poseStack.popPose();

		// Corona de oro sobre la bola (misma malla que la de la Reina, tamano reducido)
		poseStack.pushPose();
		poseStack.translate(0.0f, 16.2f, 0.0f);
		float cs = 5.6f;
		poseStack.scale(cs, cs, cs);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(CrownMesh.TEXTURE),
				(pose, c) -> CrownMesh.add(pose, c, light, 1));
		poseStack.popPose();

		poseStack.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(-0.25f, -0.45f, -0.25f));
		output.accept(new Vector3f(0.25f, 1.25f, 0.25f));
	}

	@Override
	public Void extractArgument(ItemStack stack) {
		return null;
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<Void> {
		public static final Unbaked INSTANCE = new Unbaked();
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
			return new ScepterSpecialRenderer();
		}
	}
}
