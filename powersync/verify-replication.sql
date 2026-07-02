-- Fila de prueba para verificar que PowerSync Service esta replicando.
-- user_id corresponde al usuario de seed (test@test.com).
INSERT INTO notas (id, user_id, titulo, contenido, updated_at, deleted)
VALUES (
  gen_random_uuid()::text,
  '4023491e-a4e5-4662-8c31-aa27a03dbb99',
  'Nota de verificacion',
  'Insertada manualmente para probar la replicacion logica',
  (extract(epoch from now()) * 1000)::bigint,
  false
);
