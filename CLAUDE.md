# CLAUDE.md — App de Notas Offline-First

Este documento es la **fuente de verdad de la arquitectura**. Cualquier código, prompt o decisión posterior debe respetar lo aquí definido. Si algo generado se desvía de esta especificación, se considera un bug de implementación, no una mejora.

---

## 1. Visión general

App de notas **offline-first**: el usuario puede crear, editar y borrar notas sin conexión, y los cambios se sincronizan automáticamente contra un backend central cuando hay red. La sincronización es responsabilidad exclusiva de **PowerSync**; la UI nunca sabe que existe un servidor.

---

## 2. Arquitectura

### 2.1 App Android (cliente)

- **Kotlin** + **Jetpack Compose** (UI declarativa).
- **Hilt** para inyección de dependencias.
- **WorkManager** para trabajo en background (sincronización periódica cuando la app está cerrada).
- **PowerSync Kotlin SDK** como capa de persistencia y sincronización.

**Regla fundamental:** la UI **NUNCA habla con el servidor**. Siempre lee y escribe en la base **SQLite local**, gestionada internamente por PowerSync.

- **Lecturas:** siempre mediante *watch queries* de PowerSync, expuestas como `Flow`. La UI observa el `Flow` y se recompone sola cuando cambian los datos locales — no hay refresco manual ni llamadas a red desde la UI ni desde el ViewModel.
- **Escrituras:** el CRUD hace `INSERT` / `UPDATE` directos sobre las tablas locales de PowerSync (vía SQL o el API del SDK). PowerSync detecta esos cambios y los encola para subir.
- La app debe funcionar **100% funcional en modo avión**: crear, ver, editar y borrar notas sin ninguna dependencia de red.

### 2.2 Sincronización (PowerSync)

PowerSync resuelve la sincronización en dos direcciones independientes:

- **Bajada (download):** streaming continuo desde el **PowerSync Service**, que a su vez lee **PostgreSQL** mediante **replicación lógica** (WAL, `wal_level=logical`). Cuando otro dispositivo o el backend modifica una fila en Postgres, PowerSync Service la empuja al cliente casi en tiempo real.
- **Subida (upload):** el SDK acumula las escrituras locales pendientes en una cola (`CrudBatch`) y las envía a nuestro backend a través del callback `uploadData` del `PowerSyncBackendConnector`. Nuestro backend es el único que escribe en PostgreSQL.

PowerSync **no** escribe directamente en Postgres desde el cliente: todo pasa por el backend, que aplica las reglas de negocio (LWW, pertenencia del recurso, etc.) antes de confirmar.

Las *sync rules* de PowerSync particionan los datos por usuario (un "bucket" por `user_id`), de forma que cada dispositivo solo descarga las notas de su propio usuario.

### 2.3 Backend

- **Node.js** + **TypeScript** + **Express** + **Prisma** + **PostgreSQL**.
- Responsabilidad **deliberadamente mínima** — el backend NO es una API CRUD tradicional. Solo tiene 3 funciones:

  1. **Auth con registro de dispositivos** (`/auth/login`): valida credenciales y registra/gestiona el dispositivo desde el que se conecta el usuario (ver límite de dispositivos, sección 3).
  2. **Renovación de JWT de corta duración para PowerSync** (`/auth/token`): PowerSync Service necesita un JWT válido y vigente para autenticar el stream de sincronización de cada dispositivo. El backend emite y renueva ese token, y en cada renovación verifica si el dispositivo sigue activo.
  3. **Endpoint de escritura** (`/api/upload-data`): única puerta de entrada para persistir cambios en PostgreSQL. Aplica resolución de conflictos **Last-Write-Wins** antes de confirmar cada operación.

El backend expone también el JWKS público (`/auth/keys`) para que PowerSync Service pueda validar la firma de los tokens que la app presenta.

---

## 3. Reglas de negocio

### 3.1 Identificadores
- Todos los IDs de entidades sincronizadas (ej. notas) son **UUID v4**.
- El UUID se genera **siempre en el cliente**, nunca en el backend. Esto permite crear registros completos estando offline, sin esperar un ID del servidor.

### 3.2 Resolución de conflictos — Last-Write-Wins (LWW)
- Cada fila sincronizable tiene `updated_at` (epoch **ms**, entero).
- Ante un conflicto de escritura sobre el mismo registro, gana la versión con `updated_at` **mayor**.
- **PostgreSQL es la fuente de verdad final.** El backend, al recibir una operación `PATCH`/`DELETE`, compara el `updated_at` entrante contra el valor ya persistido:
  - Si el entrante es **mayor** → se aplica.
  - Si es **menor o igual** → se descarta silenciosamente (no es un error; el dato del servidor gana y esa versión eventualmente llega al cliente que perdió el conflicto vía sync down).

### 3.3 Borrados — Soft delete (tombstone)
- Nunca se hace `DELETE` físico desde el cliente.
- Borrar una nota = `UPDATE` marcando `deleted = true` (con su propio `updated_at` nuevo), igual que cualquier otra escritura.
- El registro sigue existiendo como tombstone para que la sincronización pueda propagar el borrado a otros dispositivos. La UI filtra `deleted = false` en sus queries.

### 3.4 Límite de dispositivos por usuario
- Máximo **2 dispositivos activos** por usuario.
- Al hacer login desde un `deviceId` nuevo cuando el usuario ya tiene 2 dispositivos en estado `activo`:
  - Se marca como `revocado` el dispositivo activo con `last_seen` **más antiguo**.
  - El nuevo dispositivo se registra como `activo`.
- Si el `deviceId` ya existe y está `activo`, el login simplemente actualiza su `last_seen`.

### 3.5 JWT de PowerSync y revocación
- El JWT que la app usa para autenticar el stream de PowerSync tiene una duración corta: **10 minutos**.
- En **cada renovación** (`/auth/token`), el backend verifica el estado del dispositivo asociado:
  - Si está `revocado` → responde **401** con `{ code: "DEVICE_REVOKED" }`.
  - Si está `activo` → emite un nuevo JWT de 10 minutos y actualiza `last_seen`.
- Cuando la app recibe `DEVICE_REVOKED`, debe:
  1. Ejecutar `disconnectAndClear()` de PowerSync (borra toda la base local de forma segura — no debe quedar rastro de datos de la sesión anterior).
  2. Cerrar sesión (limpiar credenciales/refresh token locales).
  3. Redirigir a la pantalla de login.

---

## 4. Modelo de datos

### Tabla `notas`
| Columna      | Tipo               | Notas                                  |
|--------------|---------------------|-----------------------------------------|
| `id`         | TEXT (UUID)          | PK, generado en el cliente              |
| `user_id`    | TEXT                 | FK lógica a `users.id`                  |
| `titulo`     | TEXT                 |                                          |
| `contenido`  | TEXT                 |                                          |
| `updated_at` | BIGINT               | epoch ms, usado para LWW                |
| `deleted`    | BOOLEAN (default false) | tombstone, soft delete               |

### Tabla `users`
| Columna         | Tipo   | Notas                  |
|-----------------|--------|------------------------|
| `id`            | TEXT   | PK                     |
| `email`         | TEXT   | único                  |
| `password_hash` | TEXT   |                        |
| `created_at`    | —      |                        |

### Tabla `devices`
| Columna     | Tipo                       | Notas                                  |
|-------------|-----------------------------|------------------------------------------|
| `id`        | TEXT                        | PK                                       |
| `user_id`   | TEXT                        | FK lógica a `users.id`                   |
| `device_id` | TEXT                        | único por hardware, generado en el cliente |
| `estado`    | `'activo'` \| `'revocado'`  |                                           |
| `last_seen` | —                           | timestamp de última actividad            |
| `created_at`| —                           |                                           |

---

## 5. Estructura de carpetas

```
/
├── CLAUDE.md          ← este archivo
├── backend/           ← Node.js + TypeScript + Express + Prisma
└── android/           ← proyecto Android Studio (Kotlin + Compose)
```

- `backend/` y `android/` son proyectos independientes, cada uno con su propio gestor de dependencias (npm / Gradle) y su propio ciclo de build.
- No debe haber código Kotlin en `backend/` ni código TypeScript en `android/`.

---

## 6. Convenciones

- **Idioma:** código y comentarios en **español** donde sea natural (nombres de entidades de negocio, mensajes de error, docs); **nombres de variables, funciones y clases en inglés**, siguiendo la convención estándar de cada lenguaje.
- **Commits:** pequeños y descriptivos, uno por funcionalidad concreta (evitar commits gigantes que mezclen backend y app, o varias features a la vez).
- **Sin escritura directa a Postgres desde el cliente:** toda persistencia remota pasa por `uploadData` → `/api/upload-data`. El cliente jamás abre una conexión a PostgreSQL.
- **Sin lógica de red en la capa de UI:** ViewModels y Composables solo conocen PowerSync/SQLite local; el `PowerSyncBackendConnector` es el único punto de contacto con el backend.
