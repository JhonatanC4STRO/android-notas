# Plan de pruebas manuales

Estas pruebas validan las garantías críticas de la arquitectura offline-first
descrita en [CLAUDE.md](CLAUDE.md): LWW, tombstones, límite de dispositivos, e
idempotencia del upload. Requieren 2-3 emuladores/dispositivos y acceso a la
base de datos (local o del VPS, según se indique en cada prueba).

Notación: `psql` = `docker exec notas_postgres psql -U notas_user -d notas_db -c "..."`
(local) o el equivalente contra el Postgres del VPS.

---

## a) CRUD completo en modo avión + sincronización al reconectar

**Dispositivo:** uno solo (A). **Backend:** el que tengas configurado (local o VPS).

1. Login normal con `test@test.com` / `123456`, con red activa.
2. Activa **modo avión**. El ícono de sync en la TopBar debe pasar a ☁️ gris.
3. Crea una nota ("Nota avión 1", "contenido 1"). **Esperado:** aparece en la
   lista al instante, sin ningún delay ni spinner de red.
4. Edita esa nota ("contenido 1 editado"). **Esperado:** se actualiza al instante.
5. Crea una segunda nota ("Nota avión 2") y bórrala inmediatamente (ícono de
   basura + confirmar). **Esperado:** desaparece de la lista al instante.
6. Verifica en la BD que **nada de esto llegó todavía**:
   ```
   psql -c "SELECT titulo FROM notas WHERE titulo LIKE 'Nota avión%';"
   ```
   → 0 filas.
7. Desactiva modo avión. El ícono debe pasar brevemente a 🔄 (sincronizando) y
   luego a ☁️ verde.
8. Verifica en la BD:
   ```
   psql -c "SELECT titulo, contenido, deleted, updated_at FROM notas WHERE titulo LIKE 'Nota avión%' ORDER BY updated_at;"
   ```
   **Esperado:** "Nota avión 1" presente con `contenido 1 editado`, `deleted=false`.
   "Nota avión 2" presente con `deleted=true` (tombstone, no una fila borrada
   físicamente).

---

## b) Conflicto LWW: misma nota editada offline en dos dispositivos

**Dispositivos:** A y B (mismo usuario — cabe justo en el límite de 2 activos).

1. Login con el mismo usuario en A y B, ambos con red activa.
2. En A, crea la nota "Conflicto" con contenido "original". Espera a que
   sincronice (ícono verde) y que **B la reciba** (debe aparecer sola en la
   lista de B sin que hagas nada).
3. Pon **A y B en modo avión**.
4. En A, edita "Conflicto" → contenido "Version A".
5. Espera al menos 2 segundos (para asegurar un `updated_at` distinto).
6. En B, edita la misma nota → contenido "Version B" (este `updated_at` es
   mayor que el de A).
7. Reconecta A. Espera a que sincronice.
8. Reconecta B. Espera a que sincronice.
9. Verifica en la BD:
   ```
   psql -c "SELECT contenido, updated_at FROM notas WHERE titulo = 'Conflicto';"
   ```
   **Esperado:** una sola fila, `contenido = 'Version B'` (el `updated_at`
   mayor gana, sin importar el orden de llegada — CLAUDE.md sección 3.2).
10. Espera un momento y revisa **ambos dispositivos**: A debe terminar
    mostrando "Version B" también (PowerSync le baja la versión que ganó,
    sobreescribiendo su intento local que perdió).

---

## c) Tombstone: borrar offline en A, verificar que desaparece en B

**Dispositivos:** A y B (mismo usuario, ambos con la nota ya sincronizada).

1. Con A y B online, crea una nota en A y espera que B la reciba.
2. Pon **A en modo avión**. Borra la nota en A (ícono de basura + confirmar).
   **Esperado:** desaparece de la lista de A al instante.
3. B sigue online — **no debería pasar nada todavía** en B (A no ha subido el
   cambio).
4. Reconecta A. Espera a que sincronice.
5. Verifica en la BD que quedó como tombstone, no borrada físicamente:
   ```
   psql -c "SELECT titulo, deleted FROM notas WHERE titulo = '<la que borraste>';"
   ```
   **Esperado:** la fila existe, `deleted=true`.
6. Revisa B (puede tardar unos segundos en recibir el cambio vía PowerSync).
   **Esperado:** la nota desaparece también de la lista de B, sin recargar la
   app manualmente (la UI reacciona sola al Flow local).

---

## d) Expulsión: tercer dispositivo entra, el más antiguo se cierra solo

**Dispositivos:** A, B, C (mismo usuario).

1. Login en A. Login en B (ambos quedan `activo`).
2. Verifica en la BD:
   ```
   psql -c "SELECT device_id, estado, last_seen FROM devices ORDER BY last_seen;"
   ```
   **Esperado:** A y B, ambos `activo`, A con el `last_seen` más antiguo.
3. Login en C. **Esperado en la BD:** A queda `revocado`, B y C quedan `activo`.
4. En el dispositivo A (el expulsado), fuerza una renovación de token: toggle
   modo avión (activar y desactivar) para que el connector intente
   `renewToken`. (O espera hasta 10 minutos, que es cuando expira el
   `powersyncToken` de todas formas.)
5. **Esperado en A:** aparece el Snackbar *"Tu sesión se cerró porque
   iniciaste sesión en un dispositivo nuevo"* y la app navega a Login.
6. **Esperado:** si vuelves a entrar a A con el mismo usuario, la base local
   de PowerSync fue limpiada (`disconnectAndClear()`) — no debe quedar ningún
   dato de la sesión anterior visible antes de que sincronice de nuevo.
7. **Esperado:** B y C siguen funcionando con normalidad (crear una nota en
   cualquiera de los dos sincroniza sin problema).

---

## e) Idempotencia: fallo de red a mitad de un upload

**Usa el backend local** (`backend/docker-compose.yml`), no el compartido del
VPS, para no interrumpir a otras personas probando ahí mismo.

1. Con el backend local corriendo y la app conectada, detén el backend:
   ```
   cd backend && npm run dev   # Ctrl+C para detenerlo cuando quieras
   ```
2. Con el backend caído, crea una nota ("Nota idempotencia") y edítala una vez
   más (para generar más de una operación en la cola de subida).
3. Verifica que PowerSync está reintentando (el ícono de sync debe quedarse
   en 🔄 o parpadear entre estados, nunca en verde).
4. Vuelve a levantar el backend (`npm run dev`).
5. Espera a que el ícono pase a verde.
6. Verifica en la BD:
   ```
   psql -c "SELECT id, titulo, contenido FROM notas WHERE titulo = 'Nota idempotencia';"
   ```
   **Esperado:** **una sola fila** (no duplicados), con el contenido de la
   última edición. Esto confirma que aunque PowerSync haya reintentado el
   mismo lote varias veces mientras el backend estaba caído, `/api/upload-data`
   lo procesó de forma idempotente (Paso 4).

---

## f) Backend caído: la app sigue 100% funcional en local

1. Con el backend detenido (local, igual que en el punto anterior) o en modo
   avión, usa la app con normalidad: crea, edita y borra varias notas.
2. **Esperado:** ninguna operación falla, no hay crashes, no hay ANRs
   ("app no responde"), la UI se actualiza al instante en cada acción — el
   CRUD (`NotasRepository`) nunca llama directamente al backend, solo a
   PowerSync/SQLite local (CLAUDE.md sección 2.1).
3. **Esperado:** el ícono de sync se mantiene en ☁️ gris (o 🔄 si PowerSync
   está reintentando conectar) todo el tiempo, sin bloquear ninguna acción del
   CRUD.
