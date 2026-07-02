# Plan Paso a Paso — App de Notas Offline-First
## Kotlin + Compose + Room/SQLite (PowerSync) + PostgreSQL

**Stack final:**
- **App Android:** Kotlin, Jetpack Compose, Hilt, WorkManager, PowerSync Kotlin SDK
- **Backend:** Node.js + TypeScript + Express + Prisma (mínimo: auth + escrituras)
- **Sync:** PowerSync Service (Cloud o self-hosted)
- **BD:** PostgreSQL (fuente de verdad) + SQLite local (gestionado por PowerSync)

**Cómo usar este documento:** cada paso tiene un objetivo, un entregable y un prompt listo para copiar y pegar en Claude Code. Ejecuta los pasos en orden. El Paso 1 crea el archivo `CLAUDE.md` del proyecto para que los prompts siguientes tengan contexto sin repetir todo.

---

## Paso 0 — Prerequisitos (manual, sin prompt)

1. Instalar **Android Studio** (última versión estable) con SDK de Android 14+.
2. Instalar **Node.js 20+** y **Docker Desktop** (para PostgreSQL local).
3. Crear cuenta gratuita en **PowerSync Cloud** (powersync.com) — el plan free alcanza para desarrollo.
4. Tener **Claude Code** instalado y funcionando.
5. Crear la carpeta raíz del proyecto con dos subcarpetas: `backend/` y `android/`.

---

## Paso 1 — Especificación del proyecto (CLAUDE.md)

**Objetivo:** dejar por escrito la arquitectura para que Claude Code la respete en todos los pasos.
**Entregable:** archivo `CLAUDE.md` en la raíz del proyecto.

**Prompt para Claude Code:**

```
Crea un archivo CLAUDE.md en la raíz del proyecto que documente la arquitectura de una app de notas offline-first. Este archivo será el contexto de todo el desarrollo. Debe incluir:

ARQUITECTURA:
- App Android nativa: Kotlin + Jetpack Compose + Hilt + WorkManager + PowerSync Kotlin SDK.
- La UI NUNCA habla con el servidor: siempre lee y escribe en SQLite local (gestionado por PowerSync). Las lecturas usan watch queries (Flow) para UI reactiva.
- PowerSync sincroniza: bajada por streaming desde PowerSync Service (que lee PostgreSQL por replicación lógica), subida mediante el callback uploadData que llama a nuestro backend.
- Backend: Node.js + TypeScript + Express + Prisma + PostgreSQL. Solo 3 responsabilidades: (1) auth con registro de dispositivos, (2) renovación de JWT de corta duración para PowerSync, (3) endpoint de escritura con resolución de conflictos Last-Write-Wins.

REGLAS DE NEGOCIO:
- IDs: UUID v4 generados SIEMPRE en el cliente.
- Conflictos: Last-Write-Wins comparando updated_at (epoch ms). PostgreSQL es la fuente de verdad.
- Borrados: soft delete con columna deleted (tombstone), nunca DELETE físico desde el cliente.
- Límite de dispositivos por usuario: 2. Al registrar un dispositivo nuevo que exceda el límite, se marca como 'revocado' el dispositivo con last_seen más antiguo.
- JWT para PowerSync: duración 10 minutos. En cada renovación el backend verifica si el dispositivo está revocado; si lo está responde 401 con código DEVICE_REVOKED y la app ejecuta disconnectAndClear() y cierra sesión.

MODELO DE DATOS (tabla notas):
- id TEXT (UUID, PK), user_id TEXT, titulo TEXT, contenido TEXT, updated_at BIGINT (epoch ms), deleted BOOLEAN default false.
- Tabla users: id, email, password_hash, created_at.
- Tabla devices: id, user_id, device_id (único por hardware), estado ('activo'|'revocado'), last_seen, created_at.

ESTRUCTURA DE CARPETAS:
- backend/ → Express + Prisma
- android/ → proyecto Android Studio

CONVENCIONES:
- Código y comentarios en español donde sea natural, nombres de variables en inglés.
- Commits pequeños y descriptivos por cada funcionalidad.

No escribas código todavía, solo el CLAUDE.md completo y bien organizado.
```

---

## Paso 2 — Backend: base de datos y Prisma

**Objetivo:** PostgreSQL corriendo en Docker con el esquema completo.
**Entregable:** `docker-compose.yml`, schema de Prisma, migración aplicada.

**Prompt para Claude Code:**

```
Lee CLAUDE.md. En la carpeta backend/ crea el proyecto base:

1. Inicializa un proyecto Node.js + TypeScript + Express con estructura limpia (src/routes, src/services, src/middleware).
2. Crea un docker-compose.yml con PostgreSQL 16, configurado con wal_level=logical (requisito de PowerSync para replicación lógica). Expón el puerto 5432 y define usuario/contraseña/base de datos en un .env.
3. Configura Prisma con los modelos User, Device y Nota exactamente como los define CLAUDE.md. La tabla notas debe tener updated_at como BigInt (epoch ms) y deleted como Boolean.
4. Crea la migración inicial y un script de seed con un usuario de prueba (email: test@test.com, password: 123456 hasheada con bcrypt).
5. Agrega scripts en package.json: dev (con tsx watch), db:migrate, db:seed.

Verifica que docker compose up levante la BD y que la migración corra sin errores. Muéstrame los comandos para probarlo.
```

---

## Paso 3 — Backend: auth con registro y expulsión de dispositivos

**Objetivo:** login que registra dispositivos, expulsa al más antiguo y emite JWT cortos.
**Entregable:** endpoints `/auth/login` y `/auth/token` funcionando.

**Prompt para Claude Code:**

```
Lee CLAUDE.md. Implementa la autenticación en el backend:

1. POST /auth/login → recibe { email, password, deviceId }. Valida credenciales con bcrypt. Lógica de dispositivos:
   - Si el deviceId ya existe para ese usuario: actualiza last_seen y estado='activo'.
   - Si es nuevo y el usuario tiene menos de 2 dispositivos activos: lo registra.
   - Si es nuevo y ya hay 2 activos: marca como 'revocado' el dispositivo activo con last_seen más antiguo, y registra el nuevo.
   - Devuelve: { refreshToken (duración 30 días, guarda hash en BD asociado al device), powersyncToken (JWT 10 minutos) }.

2. POST /auth/token → recibe { refreshToken, deviceId }. Verifica que el dispositivo siga en estado 'activo':
   - Si está revocado: responde 401 con { code: "DEVICE_REVOKED" }.
   - Si está activo: actualiza last_seen y devuelve un nuevo powersyncToken de 10 minutos.

3. El powersyncToken es un JWT firmado con RS256 (genera el par de llaves y guárdalas en .env como base64). Debe incluir: sub (userId), aud según lo que exige PowerSync, exp de 10 minutos. Expón también GET /auth/keys con el JWKS público para que PowerSync Service valide los tokens.

4. Tests básicos con vitest: login exitoso, expulsión del dispositivo más antiguo al entrar un tercero, y renovación rechazada con DEVICE_REVOKED.

Ejecuta los tests y muéstrame los resultados.
```

---

## Paso 4 — Backend: endpoint de escritura con Last-Write-Wins

**Objetivo:** la única puerta de escritura hacia PostgreSQL, idempotente y con LWW.
**Entregable:** endpoint `/api/upload-data` protegido por JWT.

**Prompt para Claude Code:**

```
Lee CLAUDE.md. Implementa el endpoint de escritura que PowerSync invocará desde la app:

1. Middleware de auth: valida el JWT (RS256) de PowerSync en el header Authorization y extrae el userId.

2. POST /api/upload-data → recibe un lote de operaciones con el formato del CrudBatch de PowerSync: [{ op: 'PUT'|'PATCH'|'DELETE', table: 'notas', id, data }].

3. Para cada operación sobre la tabla notas, dentro de UNA transacción de Prisma:
   - Verifica que la nota pertenezca al userId del token (o que sea nueva). Si no, ignora la operación (no falles el lote completo).
   - PUT (insert/upsert): upsert por id.
   - PATCH (update): aplica Last-Write-Wins → solo actualiza si data.updated_at > updated_at actual en la BD. Si es menor o igual, descarta silenciosamente (el dato del servidor gana).
   - DELETE: aplícalo como soft delete (deleted=true) con LWW igual que PATCH.
   - Toda operación debe ser idempotente: reenviar el mismo lote no debe duplicar ni corromper nada.

4. Responde 200 solo si la transacción se confirmó. Ante cualquier error de BD responde 500 para que PowerSync reintente el lote completo.

5. Tests con vitest: upsert nuevo, conflicto donde gana el más reciente, conflicto donde se descarta el más viejo, delete como tombstone, idempotencia (mismo lote dos veces), e intento de escribir una nota de otro usuario.

Ejecuta los tests y muéstrame los resultados.
```

---

## Paso 5 — PowerSync Service: conexión y sync rules

**Objetivo:** PowerSync Cloud conectado a tu PostgreSQL y filtrando datos por usuario.
**Entregable:** instancia de PowerSync configurada con sync rules.

> ⚠️ Parte de este paso es en el dashboard de PowerSync (manual). Usa el prompt para que Claude Code te genere los archivos y te guíe.

**Prompt para Claude Code:**

```
Lee CLAUDE.md. Ayúdame a configurar PowerSync:

1. Mi PostgreSQL local no es accesible desde PowerSync Cloud. Dame las opciones (ngrok/cloudflared para desarrollo, o migrar la BD a un Postgres gestionado como Neon/Supabase) con pros y contras, y recomiéndame la más simple para desarrollo. Genera los comandos o cambios necesarios en docker-compose/.env según la opción elegida.

2. Genera el archivo sync-rules.yaml para PowerSync con un bucket por usuario: cada usuario solo sincroniza SUS notas (where user_id = request.user_id()), incluyendo las columnas id, user_id, titulo, contenido, updated_at, deleted. Explícame dónde pegarlo en el dashboard.

3. Dame la checklist exacta de configuración en el dashboard de PowerSync: conexión a la BD, publicación de replicación lógica en Postgres (genera el SQL: CREATE PUBLICATION powersync FOR TABLE notas), y configuración del JWKS apuntando a mi endpoint /auth/keys (necesitaré exponerlo también con ngrok en desarrollo).

4. Al final dame una forma de verificar que la replicación funciona (insertar una fila en Postgres y confirmar en el dashboard que PowerSync la procesó).
```

---

## Paso 6 — Android: proyecto base con Compose y Hilt

**Objetivo:** app Android compilando con la estructura y dependencias listas.
**Entregable:** proyecto en `android/` que compila e instala.

**Prompt para Claude Code:**

```
Lee CLAUDE.md. En la carpeta android/ crea el proyecto Android nativo:

1. Proyecto Kotlin con Gradle (Kotlin DSL), minSdk 26, targetSdk el último estable. Paquete: com.jhonatan.notas (ajústalo si prefieres otro).
2. Dependencias: Jetpack Compose (BOM), Material 3, Navigation Compose, Hilt, WorkManager, kotlinx-coroutines, kotlinx-serialization, Ktor Client (para llamar al backend), DataStore Preferences, y el PowerSync Kotlin SDK (busca la última versión estable del artefacto com.powersync).
3. Estructura de paquetes: ui/ (pantallas Compose), data/ (PowerSync, repositorios, API client), domain/ (modelos), di/ (módulos Hilt), sync/ (connector y workers).
4. Configura Hilt en la Application class y un NavHost con dos rutas vacías por ahora: "login" y "notas".
5. Pantallas placeholder: LoginScreen y NotasScreen con un texto simple.

Compila con ./gradlew assembleDebug y corrige cualquier error hasta que el build pase. Muéstrame el resultado del build.
```

---

## Paso 7 — Android: integración de PowerSync (schema, connector, auth)

**Objetivo:** la app conecta con PowerSync, renueva tokens y sube cambios al backend.
**Entregable:** login funcional + PowerSync conectado.

**Prompt para Claude Code:**

```
Lee CLAUDE.md. Integra PowerSync en la app Android:

1. Define el Schema de PowerSync con la tabla "notas": columnas titulo (text), contenido (text), user_id (text), updated_at (integer), deleted (integer). El id lo maneja PowerSync.

2. Crea un DeviceIdProvider: genera un UUID la primera vez, persístelo en DataStore y reutilízalo siempre (ese es nuestro deviceId de hardware).

3. Crea el ApiClient (Ktor) con: login(email, password, deviceId) y renewToken(refreshToken, deviceId), apuntando a la URL del backend definida en BuildConfig (usa 10.0.2.2 para el emulador).

4. Implementa el PowerSyncBackendConnector:
   - fetchCredentials(): llama a renewToken y devuelve el powersyncToken + endpoint de PowerSync. Si el backend responde 401 con code DEVICE_REVOKED, lanza una excepción específica DeviceRevokedException.
   - uploadData(database): toma el CrudBatch pendiente, serialízalo y envíalo a POST /api/upload-data con el token. Solo marca el batch como completado (complete()) si la respuesta fue 200. Ante error de red o 5xx, lanza excepción para que PowerSync reintente y NO pierda la cola.

5. LoginScreen funcional: formulario email/password, llama a login(), guarda el refreshToken en DataStore (encriptado si es posible), abre la base PowerSync y llama a connect() con el connector. Navega a "notas".

6. Al iniciar la app: si hay refreshToken guardado, conecta PowerSync automáticamente y va directo a "notas".

Compila y muéstrame cómo probar el login contra el backend local desde el emulador.
```

---

## Paso 8 — Android: CRUD de notas con UI reactiva

**Objetivo:** crear, listar, editar y borrar notas 100% offline, con UI que reacciona sola.
**Entregable:** NotasScreen completa.

**Prompt para Claude Code:**

```
Lee CLAUDE.md. Implementa el CRUD de notas:

1. NotasRepository sobre la base de PowerSync:
   - observarNotas(): watch query con SELECT * FROM notas WHERE deleted = 0 ORDER BY updated_at DESC, expuesta como Flow.
   - crearNota(titulo, contenido): INSERT con id = UUID v4 generado en el cliente, user_id del usuario logueado, updated_at = System.currentTimeMillis(), deleted = 0.
   - editarNota(id, titulo, contenido): UPDATE con nuevo updated_at.
   - borrarNota(id): UPDATE deleted = 1 con nuevo updated_at (tombstone, NUNCA delete físico).

2. NotasViewModel con Hilt: expone el Flow como StateFlow y las acciones del CRUD.

3. NotasScreen en Compose:
   - Lista con LazyColumn, cada nota en una Card con título, preview del contenido y fecha.
   - FAB para crear nota (abre un diálogo o pantalla de edición con título y contenido).
   - Tap en una nota → editarla. Swipe o icono de basura → borrarla con confirmación.
   - Indicador de estado de sincronización en la TopBar usando el estado de conexión de PowerSync (SyncStatus): ícono verde conectado / gris offline / animación sincronizando.

4. Todo debe funcionar en modo avión: las operaciones escriben en SQLite local y la UI se actualiza al instante vía el Flow. La sincronización es asunto de PowerSync, el CRUD no sabe nada de red.

Compila, y dame un guion de prueba manual: crear nota en modo avión, verificar que aparece, reconectar y confirmar en PostgreSQL que llegó.
```

---

## Paso 9 — Android: revocación de dispositivo y sync en segundo plano

**Objetivo:** el teléfono expulsado se limpia solo; el sync sobrevive con la app cerrada.
**Entregable:** manejo de DEVICE_REVOKED + WorkManager configurado.

**Prompt para Claude Code:**

```
Lee CLAUDE.md. Implementa la expulsión de dispositivos y el sync en background:

1. Manejo de DeviceRevokedException: cuando fetchCredentials la lance, la app debe:
   - Llamar a disconnectAndClear() de PowerSync (borra TODA la base local de forma segura).
   - Borrar refreshToken y datos de sesión de DataStore.
   - Navegar a LoginScreen mostrando un mensaje: "Tu sesión se cerró porque iniciaste sesión en un dispositivo nuevo".
   Centraliza esta lógica en un SessionManager inyectable para no duplicarla.

2. SyncWorker con WorkManager (CoroutineWorker + HiltWorker):
   - Trabajo periódico cada 15 minutos con Constraints de NetworkType.CONNECTED.
   - El worker abre/reutiliza la conexión de PowerSync para drenar la cola de subida pendiente y luego termina.
   - Encólalo como trabajo único (ExistingPeriodicWorkPolicy.KEEP) al hacer login, y cancélalo al cerrar sesión.

3. Reintentos: confirma que uploadData ante fallo deja que PowerSync/WorkManager manejen el backoff (Result.retry() en el worker si aplica).

4. Guion de prueba manual: (a) loguear el mismo usuario en 3 emuladores/dispositivos y verificar que el primero recibe el cierre de sesión con limpieza de datos al renovar token; (b) crear notas en modo avión, cerrar la app por completo, reactivar red y verificar que WorkManager subió los cambios sin abrir la app.

Compila y muéstrame el código del SessionManager y el SyncWorker.
```

---

## Paso 10 — Pruebas de integridad y APK final

**Objetivo:** validar los escenarios críticos de la arquitectura y generar el instalable.
**Entregable:** checklist de pruebas pasada + APK firmado.

**Prompt para Claude Code:**

```
Lee CLAUDE.md. Cerremos el proyecto:

1. Escribe en TESTING.md un plan de pruebas manuales con pasos exactos y resultado esperado para estos escenarios:
   a) CRUD completo en modo avión + sincronización al reconectar.
   b) Conflicto LWW: editar la MISMA nota en dos dispositivos offline con distinto contenido, reconectar ambos, verificar que gana el updated_at más reciente en ambos teléfonos y en PostgreSQL.
   c) Tombstone: borrar una nota offline en el dispositivo A, reconectar, verificar que desaparece del dispositivo B.
   d) Expulsión: tercer dispositivo entra, el más antiguo se limpia y cierra sesión.
   e) Idempotencia: simular fallo de red a mitad de un upload (matar el backend), verificar que al reintentar no hay notas duplicadas ni datos corruptos.
   f) Backend caído: la app debe seguir 100% funcional en local.

2. Revisa el proyecto completo contra CLAUDE.md y lista cualquier desviación de la arquitectura.

3. Genera la configuración de firma (keystore de release), el build de release con minify habilitado, y dame el comando final para producir el APK instalable. Explícame cómo instalarlo en un teléfono físico.
```

---

## Consejos finales

- **Un paso por sesión de Claude Code.** Si el contexto se llena, inicia sesión nueva: el `CLAUDE.md` del Paso 1 es la memoria del proyecto.
- **Commit al final de cada paso** que compile y pase pruebas. Si un paso sale mal, `git reset` y reintenta con el prompt ajustado.
- **Verifica tú mismo cada entregable** antes de avanzar (esto es lo que practicaste con SDD: la especificación manda, no el código generado).
- El orden importa: el backend (pasos 2-4) debe funcionar antes de tocar PowerSync (paso 5), y PowerSync antes que la integración Android (paso 7).
