package com.jhonatan.notas.sync

// Se lanza cuando el backend responde 401 { code: "DEVICE_REVOKED" } al
// renovar el powersyncToken (CLAUDE.md seccion 3.5). El manejo completo
// (disconnectAndClear + logout + navegar a login) se centraliza en
// SessionManager en el Paso 9; por ahora solo se propaga la señal.
class DeviceRevokedException : Exception("El dispositivo fue revocado")
