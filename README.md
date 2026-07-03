# Notas — App offline-first

App de notas offline-first (Android + backend PowerSync). La arquitectura completa
(reglas de negocio, modelo de datos, decisiones de diseño) está documentada en
[CLAUDE.md](CLAUDE.md) — léelo primero si vas a tocar código.

## Probar la app rápido (sin instalar nada del backend)

Hay un backend compartido corriendo 24/7 en un VPS, pensado exactamente para esto:
que cualquiera pueda clonar el repo y probar la app sin levantar Postgres, Docker
ni el servidor Node en su propia máquina.

1. Clona el repo y abre la carpeta `android/` en **Android Studio**.
2. Espera a que sincronice Gradle (puede tardar la primera vez).
3. Crea/inicia un emulador (API 26+) o conecta un dispositivo físico.
4. Dale **Run ▶**.
5. En el login, usa el usuario de prueba:
   - Email: `test@test.com`
   - Contraseña: `123456`

Por defecto, `BuildConfig.BACKEND_URL` y `BuildConfig.POWERSYNC_URL` (definidos
en `android/app/build.gradle.kts`) ya apuntan al backend y a la instancia de
PowerSync Cloud compartidos. No necesitas cambiar nada para esto — la
sincronización real (no solo el login) ya funciona con esta configuración por
defecto: crea una nota, debería verse el ícono ☁️ verde en la barra superior
una vez sincronice.

Plan de pruebas manuales completo (offline, conflictos, expulsión de
dispositivos, etc.) en [TESTING.md](TESTING.md).

> ⚠️ `android/gradlew.bat` puede faltar en tu copia local si tu antivirus lo puso
> en cuarentena al clonar (le pasó a Windows Defender en la máquina original).
> Si te pasa, no afecta a Android Studio — solo hace falta si quieres correr
> `./gradlew` desde una terminal en Windows. Puedes regenerarlo con
> `gradle wrapper` (si tienes Gradle instalado) o descargarlo de
> [github.com/gradle/gradle](https://github.com/gradle/gradle) para la versión
> indicada en `android/gradle/wrapper/gradle-wrapper.properties`.

## Desarrollo local (correr tu propio backend)

Si vas a modificar el backend, corre tu propia copia local en vez de usar la
compartida:

```bash
cd backend
cp .env.example .env   # y completa los valores (ver seccion siguiente)
docker compose up -d   # levanta Postgres con wal_level=logical
npm install
npm run db:migrate
npm run db:seed
npm run dev             # backend en http://localhost:3000
```

Luego, en `android/app/build.gradle.kts`, cambia temporalmente:

```kotlin
buildConfigField("String", "BACKEND_URL", "\"http://10.0.2.2:3000\"")
```

(`10.0.2.2` es como el emulador de Android alcanza el `localhost` de tu PC.)

### Variables de entorno necesarias (`backend/.env`)

- `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `POSTGRES_PORT`, `DATABASE_URL`
- `JWT_KID`, `JWT_PRIVATE_KEY_BASE64`, `JWT_PUBLIC_KEY_BASE64` — genera tu propio
  par de llaves con `npm run generate:jwt-keys` (nunca reutilices las de otro
  entorno).
- `POWERSYNC_JWT_ISSUER`, `POWERSYNC_JWT_AUDIENCE`

Ver `backend/.env.example` para la plantilla completa.

## Arquitectura

- **`android/`** — app Kotlin + Jetpack Compose + Hilt + PowerSync Kotlin SDK.
  La UI nunca habla con el servidor directamente; todo pasa por PowerSync
  (SQLite local + sincronización).
- **`backend/`** — Node + TypeScript + Express + Prisma. Responsabilidad mínima:
  autenticación con registro de dispositivos, renovación de JWT de PowerSync, y
  el endpoint de escritura (`/api/upload-data`) con resolución de conflictos
  Last-Write-Wins.
- **`powersync/`** — sync rules (formato "Sync Streams", ya desplegado en el
  dashboard) y SQL de configuración para PowerSync Cloud.

Detalle completo de reglas de negocio (límite de dispositivos, tombstones,
LWW, etc.) en [CLAUDE.md](CLAUDE.md).

## Infraestructura compartida (VPS + PowerSync Cloud)

- **Backend + Postgres**: corren en un VPS compartido (vía Dokploy) en
  `https://notasapi.shona.lat`. Postgres tiene SSL habilitado (certificado
  autofirmado) porque PowerSync Cloud exige `sslmode=verify-ca` o superior
  para conexiones públicas — no acepta `disable`/`prefer`.
- **PowerSync Cloud**: instancia ya conectada a ese Postgres, con las Sync
  Streams de `powersync/sync-rules.yaml` desplegadas y el Client Auth (JWKS)
  apuntando a `https://notasapi.shona.lat/auth/keys`.

**Las credenciales (SSH del VPS, contraseña de Postgres, llaves JWT, acceso al
dashboard de PowerSync) no están en este repositorio a propósito** — el VPS es
compartido con otros proyectos y este repo es público. Si necesitas acceso
para redesplegar, ver logs, o cambiar la configuración, pide las credenciales
directamente al dueño del proyecto.

Para redesplegar cambios del backend: cualquier push a `main` en este repo
puede configurarse para redeploy automático en Dokploy (Provider → GitHub →
`./backend/docker-compose.dokploy.yml`), o se puede disparar manualmente desde
su dashboard.

## Generar el APK de release

```bash
cd android
java -jar gradle/wrapper/gradle-wrapper.jar assembleRelease
```

Requiere `android/keystore.properties` y el keystore que referencia (ambos
gitignored — pídelos al dueño del proyecto, o genera los tuyos propios si es
para tu propia firma). Sin ese archivo, `assembleRelease` genera un APK sin
firmar. El resultado queda en
`android/app/build/outputs/apk/release/app-release.apk`.
