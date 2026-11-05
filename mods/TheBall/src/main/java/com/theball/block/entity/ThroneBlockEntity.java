package com.theball.block.entity;

import com.mojang.serialization.Codec;
import com.theball.entity.AluminumBallEntity;
import com.theball.registry.ModBlockEntities;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Datos del trono: qué bola tiene sentada y los premios pendientes de jugadores que no estaban conectados cuando
 * se ganaron (se entregan la próxima vez que usen el trono con la mano vacía).
 */
public class ThroneBlockEntity extends BlockEntity {
	private static final Codec<Map<UUID, Integer>> PENDING_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT);

	private UUID ballId;
	private final Map<UUID, Integer> pendingRewards = new HashMap<>();

	public ThroneBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.THRONE, pos, state);
	}

	// ------------------------------------------------------------------ bola sentada

	public UUID getBallId() {
		return ballId;
	}

	public void setBallId(UUID id) {
		this.ballId = id;
		setChanged();
	}

	/** La bola sentada si existe, está cargada y sigue anclada a este trono. */
	public AluminumBallEntity findBall(ServerLevel level) {
		if (ballId == null) {
			return null;
		}
		if (level.getEntity(ballId) instanceof AluminumBallEntity ball && !ball.isRemoved()
				&& getBlockPos().equals(ball.getAnchor())) {
			return ball;
		}
		return null;
	}

	// ------------------------------------------------------------------ premios pendientes

	public void addPending(UUID player, int bits) {
		if (bits != 0) {
			pendingRewards.merge(player, bits, (a, b) -> a | b);
			setChanged();
		}
	}

	/** Saca (y borra) los premios pendientes de un jugador. */
	public int takePending(UUID player) {
		Integer bits = pendingRewards.remove(player);
		if (bits != null) {
			setChanged();
			return bits;
		}
		return 0;
	}

	public Map<UUID, Integer> pending() {
		return pendingRewards;
	}

	// ------------------------------------------------------------------ al romperse

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (this.level instanceof ServerLevel serverLevel) {
			AluminumBallEntity ball = findBall(serverLevel);
			if (ball != null) {
				ball.release(); // la bola vuelve a la física normal (puede medir 8 bloques: no se convierte en item)
			}
		}
	}

	// ------------------------------------------------------------------ guardado

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.ballId = input.read("Ball", UUIDUtil.CODEC).orElse(null);
		this.pendingRewards.clear();
		input.read("PendingRewards", PENDING_CODEC).ifPresent(this.pendingRewards::putAll);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("Ball", UUIDUtil.CODEC, this.ballId);
		if (!this.pendingRewards.isEmpty()) {
			output.store("PendingRewards", PENDING_CODEC, this.pendingRewards);
		}
	}
}
