# Evidencia: límite de intentos en el login del admin y versiones exactas (SEC-14, SEC-20)

- **Fecha:** 20/11/2026
- **Dónde:** servidor local de Django (`backend-admin/`, `http://127.0.0.1:8000/admin/login/`) con `runserver` contra la base de Supabase.
- **Rama:** `feature/admin-seguridad`
- **Responsable:** Guillermo
- **Datos de prueba:** los usuarios del admin que ya hay en la base (`admin` y `pedro`), sin script nuevo

## Prueba manual

| # | Historia | Paso | Resultado |
|---|---|---|---|
| 1 | SEC-14 | Entrar como `admin` con una contraseña incorrecta 4 veces | Las 4 veces se ve el error normal de Django en el formulario de login; no hay bloqueo |
| 2 | SEC-14 | 5.º intento fallido como `admin` | Se muestra la página con el estilo del admin: "Demasiados intentos fallidos de inicio de sesión." y "Espera 15 minutos y vuelve a intentarlo." |
| 3 | SEC-14 | Con `admin` bloqueado, entrar con la contraseña correcta | Sigue bloqueado: se vuelve a ver la misma página |
| 4 | SEC-14 | Desde el mismo navegador, entrar como `pedro` con una contraseña incorrecta | Error normal de Django; `pedro` no queda bloqueado |
| 5 | SEC-14 | Reiniciar `runserver` y entrar como `admin` con la contraseña correcta | Entra al admin (el bloqueo estaba en la caché en memoria y se borró) |
| 6 | SEC-14 | 4 fallos como `admin`, login correcto, cerrar sesión y 4 fallos más | No se bloquea: el login correcto reinició el contador |

## Cubierto por pruebas automáticas

Casos que cubren las pruebas de `backend-admin/reportes/tests/test_bloqueo_login.py` (el reloj de la caché se
simula, así que la espera de 15 minutos se prueba sin esperar):

| Caso | Prueba |
|---|---|
| 4 fallos aún muestran el login normal | `BloqueoDelLoginTests.test_cuatro_fallos_aun_muestran_el_login` |
| El 5.º fallo bloquea (respuesta 429, `Retry-After: 900`, "Demasiados intentos fallidos" y "Espera 15 minutos"), incluso con la clave correcta | `BloqueoDelLoginTests.test_el_quinto_fallo_bloquea_incluso_con_la_clave_correcta` |
| La página de bloqueo es la misma exista o no el usuario (no revela cuáles existen) | `BloqueoDelLoginTests.test_la_pagina_es_la_misma_si_el_usuario_no_existe` |
| Se bloquea usuario + IP: otro usuario desde la misma IP y el mismo usuario desde otra IP siguen entrando | `BloqueoDelLoginTests.test_otro_usuario_desde_la_misma_ip_no_queda_bloqueado`, `BloqueoDelLoginTests.test_el_mismo_usuario_desde_otra_ip_no_queda_bloqueado` |
| El login correcto reinicia el contador | `BloqueoDelLoginTests.test_el_login_correcto_reinicia_el_contador` |
| El bloqueo dura 15 minutos | `BloqueoDelLoginTests.test_antes_de_15_minutos_sigue_bloqueado`, `BloqueoDelLoginTests.test_tras_15_minutos_vuelve_a_dejar_entrar` |
| Un intento durante el bloqueo vuelve a empezar la espera | `BloqueoDelLoginTests.test_un_intento_durante_el_bloqueo_reinicia_la_espera` |
| axes no tiene migraciones: `migrate` no crea tablas en Supabase | `SinTablasDeAxesTests.test_axes_no_tiene_migraciones` |

## SEC-20: versiones exactas

`backend-admin/requirements.txt` fija con `==` las dependencias directas (las instaladas en el venv):

| Paquete | Versión |
|---|---|
| Django | 5.2.18 |
| psycopg[binary] | 3.3.6 |
| python-dotenv | 1.2.4 |
| openpyxl | 3.1.5 |
| defusedxml | 0.7.1 |
| django-axes | 9.0.0 |

`pip freeze` en el venv coincide con esas versiones.

```
cd backend-admin
pip-audit -r requirements.txt
```

Resultado (20/11/2026): **No known vulnerabilities found**.

## Limitaciones

- Los intentos se guardan en la **caché en memoria** del proceso (`LocMemCache`), no en la base. Por eso
  **reiniciar el servidor borra los bloqueos** (paso 5) y los comandos `axes_reset_*` no los ven (corren en
  otro proceso).
- Esa caché no se comparte entre procesos: con varios procesos (por ejemplo gunicorn en producción) cada uno
  contaría sus propios fallos. Ahí hace falta una caché compartida (**Redis o Memcached**) y quitar `axes.W001`
  de `SILENCED_SYSTEM_CHECKS`.
- SEC-20 fija solo las dependencias directas; las indirectas (por ejemplo `asgiref`, `sqlparse`) no están fijadas.

## Pruebas automáticas

```
cd backend-admin
python manage.py test
python manage.py check
```

Resultado (20/11/2026, después de integrar `main` con H89 y H95): **185 pruebas, OK**. `check`: sin problemas
(1 aviso silenciado, `axes.W001`).
