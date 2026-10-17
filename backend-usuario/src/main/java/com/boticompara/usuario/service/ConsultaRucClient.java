package com.boticompara.usuario.service;

/** H2: consulta de un RUC en SUNAT. La real es DecolectaRucClient; los tests usan una simulada. */
public interface ConsultaRucClient {

    ResultadoSunat consultar(String ruc);

    enum Tipo { ENCONTRADO, NO_EXISTE, NO_DISPONIBLE }

    record ResultadoSunat(Tipo tipo, String razonSocial, String estado, String condicion) {

        public static ResultadoSunat encontrado(String razonSocial, String estado, String condicion) {
            return new ResultadoSunat(Tipo.ENCONTRADO, razonSocial, estado, condicion);
        }

        public static ResultadoSunat noExiste() {
            return new ResultadoSunat(Tipo.NO_EXISTE, null, null, null);
        }

        /** Timeout, error de red, token mal configurado, cuota agotada, etc. */
        public static ResultadoSunat noDisponible() {
            return new ResultadoSunat(Tipo.NO_DISPONIBLE, null, null, null);
        }
    }
}