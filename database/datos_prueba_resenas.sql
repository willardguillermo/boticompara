-- =====================================================================
-- BotiCompara - Datos de prueba de reseñas (H89, moderación en Django)
-- Ejecutar en el SQL Editor de Supabase (como postgres) después de
-- v5_sprint4.sql y seed.sql. La tabla resena empieza vacía.
-- Agrega 2 reseñas de carlos@boticompara.nissidev.dev (una por botica,
-- por uq_resena_usuario_botica):
--   - Botica Salud Total: normal, 5 estrellas;
--   - FarmaVida: con "estafadores", para probar el filtro "Con palabras
--     a revisar" y ocultarla desde el admin.
-- Las dos quedan visibles (oculta = false): ocultarla es la prueba.
-- Se puede repetir sin duplicar: si carlos ya reseñó esa botica, se
-- conserva la que tiene (on conflict do nothing).
--
-- PRUEBA EN SECO: reemplazar el "commit;" final por
--   do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;
-- Si el único error es PRUEBA_EN_SECO, todo lo anterior funcionó y se
-- deshizo. Entonces volver a poner "commit;" y ejecutarlo de verdad.
-- =====================================================================

begin;

insert into public.resena (botica_id, usuario_id, estrellas, comentario)
select b.id, u.id, r.estrellas, r.comentario
from (values
  ('Botica Salud Total', 5::smallint,
   'Me atendieron rápido y tenían todo lo de la receta. La señorita me explicó cómo tomar el antibiótico.'),
  ('FarmaVida',          1::smallint,
   'Son unos estafadores, me cobraron más de lo que decía la app y no me quisieron devolver el vuelto.')
) as r(botica, estrellas, comentario)
join public.botica b on b.nombre_comercial = r.botica
join public.usuario u on u.correo = 'carlos@boticompara.nissidev.dev'
on conflict (usuario_id, botica_id) do nothing;

-- Si falta carlos o alguna de las boticas, el insert no hace nada sin avisar: aquí se detiene
do $$
begin
  if (select count(*)
      from public.resena r
      join public.botica b on b.id = r.botica_id
      join public.usuario u on u.id = r.usuario_id
      where u.correo = 'carlos@boticompara.nissidev.dev'
        and b.nombre_comercial in ('Botica Salud Total', 'FarmaVida')) <> 2 then
    raise exception 'Faltan reseñas: revisa que existan carlos@ y las boticas Botica Salud Total y FarmaVida (seed.sql)';
  end if;
end $$;

commit;

-- Verificación (opcional):
-- select b.nombre_comercial, u.correo, r.estrellas, r.oculta, r.motivo_ocultamiento, r.comentario
-- from public.resena r
-- join public.botica b on b.id = r.botica_id
-- join public.usuario u on u.id = r.usuario_id
-- order by r.id;
