import { Prisma } from "@prisma/client";

const MAX_ACTIVE_DEVICES = 2;

interface UpsertDeviceParams {
  userId: string;
  deviceId: string;
  refreshTokenHash: string;
  refreshTokenExpiresAt: Date;
}

// Registra el login de un deviceId para un usuario, aplicando el limite de
// dispositivos activos (CLAUDE.md, seccion 3.4). Debe llamarse dentro de una
// transaccion para evitar condiciones de carrera entre logins simultaneos.
export async function upsertDeviceForLogin(
  tx: Prisma.TransactionClient,
  { userId, deviceId, refreshTokenHash, refreshTokenExpiresAt }: UpsertDeviceParams,
) {
  const now = new Date();
  const existing = await tx.device.findUnique({ where: { deviceId } });

  if (existing) {
    return tx.device.update({
      where: { id: existing.id },
      data: { userId, estado: "activo", lastSeen: now, refreshTokenHash, refreshTokenExpiresAt },
    });
  }

  const activeDevices = await tx.device.findMany({
    where: { userId, estado: "activo" },
    orderBy: { lastSeen: "asc" },
  });

  if (activeDevices.length >= MAX_ACTIVE_DEVICES) {
    const oldest = activeDevices[0];
    await tx.device.update({
      where: { id: oldest.id },
      data: { estado: "revocado" },
    });
  }

  return tx.device.create({
    data: { userId, deviceId, estado: "activo", lastSeen: now, refreshTokenHash, refreshTokenExpiresAt },
  });
}
