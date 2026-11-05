package com.theslave.block;

import com.theslave.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Datos de la lápida: de quién es, cómo se llamaba, cuándo cayó y qué ofrendas tiene en custodia. */
public class TumbaBlockEntity extends BlockEntity {
	public UUID dueno;
	/** Vínculo del difunto en el registro (null en tumbas anteriores: se resuelve con {@link #vinculoId()}). */
	public UUID vinculo;
    private UUID operacionRitual;
    public UUID operacionRitual() { return operacionRitual; }
    public void operacionRitual(UUID operacion) { operacionRitual=operacion;setChanged(); }
	public String nombre = "";
	public int variante;
	public int dia;
	public String causa = "";
	public int muertes = 1;
	/** Ofrendas en custodia: bit 0 contrato, bit 1 manzana dorada. Se devuelven si el ritual no llega al final. */
	public int custodia;
	/** Para el entierro: tick de juego del primer clic, 0 si no hay. */
	public long confirmar;

	public TumbaBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.TUMBA_BE, pos, state);
	}

	public void inicializar(UUID dueno, UUID vinculo, String nombre, int variante, int dia, String causa, int muertes) {
		this.dueno = dueno;
		this.vinculo = vinculo;
		this.nombre = nombre;
		this.variante = variante;
		this.dia = dia;
		this.causa = causa;
		this.muertes = muertes;
		setChanged();
	}

	/** Id de vínculo de esta tumba; las anteriores a los vínculos usan el primer difunto del dueño. */
	public UUID vinculoId() {
		if (vinculo == null && dueno != null) {
			for (com.theslave.Registro.Vinculo v : com.theslave.Registro.vinculos(dueno)) {
				if (v.estado == com.theslave.Registro.Estado.DIFUNTO) {
					vinculo = v.id;
					setChanged();
					break;
				}
			}
		}
		if (dueno != null && vinculo != null) {
            int normalizadas=normalizarMuertesLegacy(muertes,com.theslave.Registro.vinculo(dueno,vinculo));
            if(normalizadas!=muertes) { muertes=normalizadas;setChanged(); }
        }
		return vinculo;
	}

    /** Solo repara el cero legado cuando la identidad resuelta sigue en su primera muerte. */
    public static int normalizarMuertesLegacy(int guardadas,com.theslave.Registro.Vinculo resuelto) {
        return guardadas==0 && resuelto!=null && resuelto.estado==com.theslave.Registro.Estado.DIFUNTO
            && resuelto.muertes==1?1:guardadas;
    }

	public static void tick(Level level, BlockPos pos, BlockState state, TumbaBlockEntity be) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		long t = sl.getGameTime();
		if (t % 40 != 0) {
			return;
		}
		Player cerca = sl.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 32, false);
		if (cerca == null) {
			return;
		}
		sl.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 1, 0.12, 0.05, 0.12, 0.01);
		if (t % 200 == 0 && cerca.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 36) {
			sl.playSound(null, pos, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundSource.AMBIENT, 0.6f, 0.8f);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
        if(operacionRitual!=null)out.putString("TheSlaveRitualOperacion",operacionRitual.toString());
		if (dueno != null) {
			out.putString("Dueno", dueno.toString());
		}
		if (vinculo != null) {
			out.putString("Vinculo", vinculo.toString());
		}
		out.putString("Nombre", nombre);
		out.putInt("Variante", variante);
		out.putInt("Dia", dia);
		out.putString("Causa", causa);
		out.putInt("Muertes", muertes);
		out.putInt("Custodia", custodia);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
        String op=in.getStringOr("TheSlaveRitualOperacion","");
        try { operacionRitual=op.isEmpty()?null:UUID.fromString(op); }
        catch(IllegalArgumentException ex) { throw new IllegalStateException("Operacion de tumba corrupta",ex); }
		String d = in.getStringOr("Dueno", "");
		try {
			dueno = d.isEmpty() ? null : UUID.fromString(d);
		} catch (IllegalArgumentException e) {
			dueno = null;
		}
		String vi = in.getStringOr("Vinculo", "");
		try {
			vinculo = vi.isEmpty() ? null : UUID.fromString(vi);
		} catch (IllegalArgumentException e) {
			vinculo = null;
		}
		nombre = in.getStringOr("Nombre", "");
		variante = in.getIntOr("Variante", 0);
		dia = in.getIntOr("Dia", 0);
		causa = in.getStringOr("Causa", "");
		muertes = in.getIntOr("Muertes", 1);
		custodia = in.getIntOr("Custodia", 0);
	}
}
