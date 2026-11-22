package com.boticompara.usuario.dto;

/** H103: número sobre la campana. Cuenta las no leídas de los últimos 7 días, sin el límite de 50 de la lista. */
public record TotalNoLeidasResponse(long total) {
}
