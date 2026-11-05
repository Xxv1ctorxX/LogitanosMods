package com.theslave.ritual;

import java.util.UUID;

/** Implementada en ItemEntity por mixin: el botin no circula antes de su ACK durable. */
public interface ItemCustody {
    UUID theslave$operacion();
    boolean theslave$custodiado();
    void theslave$custodiar(UUID operacion);
    void theslave$liberar();
}
