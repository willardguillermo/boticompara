package com.boticompara.usuario.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** H37: porcentaje de ahorro de una alternativa frente al producto consultado. */
public final class Ahorro {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private Ahorro() {
    }

    /**
     * (consultado - alternativa) / consultado * 100, con 1 decimal.
     * Devuelve null si la alternativa no es más barata (o el ahorro redondea a 0.0).
     */
    public static BigDecimal porcentaje(BigDecimal consultado, BigDecimal alternativa) {
        if (consultado == null || alternativa == null || consultado.signum() <= 0) {
            return null;
        }
        BigDecimal ahorro = consultado.subtract(alternativa).multiply(CIEN)
                .divide(consultado, 1, RoundingMode.HALF_UP);
        return ahorro.signum() > 0 ? ahorro : null;
    }
}
