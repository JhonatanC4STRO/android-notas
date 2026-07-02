-- PowerSync lee los cambios de Postgres via replicacion logica: necesita
-- una PUBLICATION que declare que tablas replicar. Solo "notas" sincroniza
-- (users/devices son de uso exclusivo del backend, CLAUDE.md seccion 2.3).
CREATE PUBLICATION powersync FOR TABLE notas;
