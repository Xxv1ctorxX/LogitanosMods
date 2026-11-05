package com.theslave.ai;

import java.util.List;
import java.util.function.*;

/** Nunca entrega botin si la autorizacion o la eliminacion real del bloque fallan. */
public final class ExtraccionSegura {
    private ExtraccionSegura() {}
    public static <T> boolean ejecutar(BooleanSupplier autorizar,Supplier<List<T>> calcular,
                                      BooleanSupplier destruir,Consumer<T> entregar) {
        if(!autorizar.getAsBoolean())return false;
        List<T> botin=calcular.get();
        if(!destruir.getAsBoolean())return false;
        botin.forEach(entregar);return true;
    }
}
