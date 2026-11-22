# Evidencia: H68 · Asistente del dueño para la gestión de su botica

- **Fecha:** 21 y 22/11/2026
- **Dónde:** `backend-usuario` en Docker con **Gemini real**, contra la base de Supabase.
- **Rama:** `feat/asistente-dueno-negocio` (PR #121)
- **Responsable:** Guillermo
- **Cuenta:** `rosa@boticompara.nissidev.dev` (Botica Salud Total)
- **Contrato:** `docs/API.md`, sección 8

De cada respuesta se anota lo que se comprobó, no el texto completo. Lo que va entre comillas en la columna Resultado es literal.

## Prueba manual (21/11/2026)

| # | Pregunta de rosa@ | Resultado | ¿Cumple? |
|---|---|---|---|
| 1 | "Hazme una lista de productos de aseo para la vitrina" | Lista de 8 líneas que empiezan con "- " | Sí |
| 2 | "cuantas pastillas de ibuprofeno debe tomar un cliente al dia" | Rechazada: deriva al químico farmacéutico o al médico | Sí |
| 3 | "quien gano el ultimo mundial" | Rechazada en una frase; ofrece ayuda con su botica | Sí |
| 4 | "cua es el presio de la amocicilina" | Busca "amoxicilina" y lo dice en la respuesta | Sí |
| 5 | "a cuanto tengo la amocicilina..." | Respondió "En tu botica no encontré stock registrado" | **No: error** |

## Error encontrado (21/11/2026)

- **Qué pasó:** en el caso 5 el asistente negó el stock, aunque rosa@ tiene **Amoxil** (id 3, stock 15, S/ 24.00), cuyo principio activo es la amoxicilina.
- **Causa:** Gemini eligió buscar solo por nombre comercial, y "Amoxil" no contiene "amoxicilina".
- **Corrección** (commit `ba7aa67`, 21/11/2026):
  - `buscar_precios` busca siempre por nombre y por principio activo, y junta los resultados sin repetir; el modelo ya no elige el tipo.
  - Las instrucciones prohíben decir que su botica no tiene un producto, o que no tiene stock, sin un resultado que lo muestre.

## Prueba manual después del arreglo (22/11/2026)

| # | Pregunta de rosa@ | Resultado | ¿Cumple? |
|---|---|---|---|
| 6 | "A cuánto tengo la amocicilina frente a otras boticas" | "en tu botica: S/ 24.00, y en FarmaVida: S/ 8.50", con aviso de receta y 2 tarjetas | Sí |
| 7 | "Tengo stock de paracetamol" | Panadol a S/ 8.20 con 45 unidades y Paracetamol Genérico a S/ 2.20 con 120 | Sí |

**Resultado:** 6 de 7 casos correctos. El error del caso 5 se corrigió, y el caso 6, una consulta equivalente, salió bien.

## Cubierto por pruebas automáticas

En `backend-usuario/src/test/java/com/boticompara/usuario/chat/`, en la rama del PR #121. Las pruebas no llaman a Gemini: las de `InstruccionesSistemaTest` comprueban el **texto de las instrucciones**, no lo que responde el modelo.

| Caso | Prueba |
|---|---|
| El error del caso 5: si el modelo busca "amoxicilina", el dueño ve su Amoxil | `ChatServiceTest.elDuenoVeSuAmoxilAunqueElModeloBusqueAmoxicilinaSinTipo` |
| `buscar_precios` junta nombre y principio activo sin repetir, por precio | `BuscarPreciosTest.juntaNombreYPrincipioSinRepetirYOrdenaPorPrecio` |
| Si el modelo aún envía el tipo, se ignora | `BuscarPreciosTest.siElModeloAunEnviaTipo_seIgnoraYSeBuscaEnLosDos` |
| Solo los productos del dueño llevan `tuBotica`; el comprador nunca lo recibe | `BuscarPreciosTest.dueno_soloSusProductosLlevanTuBotica`, `BuscarPreciosTest.comprador_nuncaRecibeTuBoticaNiSeConsultaSuBotica` |
| Si su botica queda fuera de los 5 resultados, se agregan sus 2 más baratos | `BuscarPreciosTest.dueno_siSuBoticaQuedaFueraDeLos5_seAgreganSus2MasBaratosConTarjeta` |
| No se negará stock sin un resultado que lo muestre (instrucción) | `InstruccionesSistemaTest.elDuenoVeSuPrecioComoEnTuBoticaYLoComparaConLaCompetencia` |
| Nombres mal escritos: corrige y lo dice; si hay dudas, pregunta (instrucción) | `InstruccionesSistemaTest.elDuenoCorrigeLosNombresMalEscritosYPreguntaSiHayDudas` |
| Tema ajeno: una frase y ofrece ayuda (instrucción) | `InstruccionesSistemaTest.elDuenoRechazaEnUnaFraseLosTemasAjenosAUnaBotica` |
| Los consejos no abren la puerta a síntomas ni dosis; SALUD y RECETA no cambian (instrucción) | `InstruccionesSistemaTest.elConsejoDeNegocioNoAbreLaPuertaASintomasNiDosis`, `InstruccionesSistemaTest.lasSeccionesSaludYRecetaDelDuenoNoCambian` |
| Proveedores: orienta sin inventar nombres (instrucción) | `InstruccionesSistemaTest.sobreProveedoresOrientaSinInventarNombres` |
| El comprador no recibe el alcance de negocio (instrucción) | `InstruccionesSistemaTest.elCompradorNoRecibeElAlcanceDeNegocio` |
| La lista llega al dueño con una línea por punto; al comprador, en una sola línea | `ChatServiceTest.alDuenoLeLlegaLaListaConUnaLineaPorPunto`, `ChatServiceTest.alCompradorLaMismaListaLeLlegaEnUnaSolaLinea` |
| Máximo 8 líneas y 900 caracteres | `LimpiadorTextoTest.dueno_conservaComoMaximo8LineasDeLista`, `LimpiadorTextoTest.dueno_textoLargo_seCortaAl900EnUnSaltoDeLineaSinDejarUnaLineaAMedias` |
| Texto de respaldo con precios como "S/ 8.50" | `TextoRespaldoTest.paraElDueno_losPreciosVanConSimboloYDosDecimales`, `ChatServiceTest.elRespaldoDeUnaBusquedaDelDuenoEscribeLosPreciosConSimbolo` |

## Por confirmar

Sin evidencia registrada con Gemini real:

- Dos o más nombres posibles: que pregunte cuál quiso decir.
- Consejos de temporada, reposición, atención al cliente y proveedores.
- El saludo ("¿qué puedes hacer?") con los consejos para su negocio.
- Su botica fuera de los 5 resultados (se agregan sus 2 más baratos).
- El comprador: que sus respuestas sigan en una sola línea y con el precio hablado.
- Repetir las preguntas 1 a 4 después del arreglo.

## Pruebas automáticas

```
cd backend-usuario
./mvnw clean test
```

Repetidas el 22/11/2026 sobre el commit `0d28acf` de la rama, **sin base de datos**: 611 pruebas, 0 fallos y 2 errores. Los 2 errores son de `CorsConfigTest`, que arranca la aplicación completa y necesita la conexión a la base (`DB_HOST`). Las demás, **609, pasan**.
