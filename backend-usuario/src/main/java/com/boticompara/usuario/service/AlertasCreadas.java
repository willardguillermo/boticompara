package com.boticompara.usuario.service;

import com.boticompara.usuario.service.AlertaService.AlertaCreada;

import java.util.List;

/** H61: se publica con las alertas que se acaban de crear; el correo sale cuando la transacción se confirma. */
public record AlertasCreadas(List<AlertaCreada> alertas) {
}