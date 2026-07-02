import { Router } from "express";
import bcrypt from "bcrypt";
import { prisma } from "../services/prisma";
import { upsertDeviceForLogin } from "../services/deviceService";
import {
  generateRefreshToken,
  hashRefreshToken,
  verifyRefreshToken,
  REFRESH_TOKEN_TTL_MS,
} from "../services/refreshTokenService";
import { generatePowersyncToken, getJwks } from "../services/jwtService";

export const authRouter = Router();

authRouter.post("/login", async (req, res) => {
  const { email, password, deviceId } = req.body ?? {};

  if (!email || !password || !deviceId) {
    return res.status(400).json({ code: "INVALID_INPUT", message: "email, password y deviceId son requeridos" });
  }

  const user = await prisma.user.findUnique({ where: { email } });
  if (!user) {
    return res.status(401).json({ code: "INVALID_CREDENTIALS" });
  }

  const passwordMatches = await bcrypt.compare(password, user.passwordHash);
  if (!passwordMatches) {
    return res.status(401).json({ code: "INVALID_CREDENTIALS" });
  }

  const refreshToken = generateRefreshToken();
  const refreshTokenHash = await hashRefreshToken(refreshToken);
  const refreshTokenExpiresAt = new Date(Date.now() + REFRESH_TOKEN_TTL_MS);

  await prisma.$transaction((tx) =>
    upsertDeviceForLogin(tx, {
      userId: user.id,
      deviceId,
      refreshTokenHash,
      refreshTokenExpiresAt,
    }),
  );

  const powersyncToken = generatePowersyncToken(user.id);

  res.json({ refreshToken, powersyncToken });
});

authRouter.post("/token", async (req, res) => {
  const { refreshToken, deviceId } = req.body ?? {};

  if (!refreshToken || !deviceId) {
    return res.status(400).json({ code: "INVALID_INPUT", message: "refreshToken y deviceId son requeridos" });
  }

  const device = await prisma.device.findUnique({ where: { deviceId } });

  if (!device) {
    return res.status(401).json({ code: "INVALID_REFRESH_TOKEN" });
  }

  if (device.estado === "revocado") {
    return res.status(401).json({ code: "DEVICE_REVOKED" });
  }

  const isExpired = !device.refreshTokenExpiresAt || device.refreshTokenExpiresAt < new Date();
  const matchesHash = device.refreshTokenHash
    ? await verifyRefreshToken(refreshToken, device.refreshTokenHash)
    : false;

  if (isExpired || !matchesHash) {
    return res.status(401).json({ code: "INVALID_REFRESH_TOKEN" });
  }

  await prisma.device.update({
    where: { id: device.id },
    data: { lastSeen: new Date() },
  });

  const powersyncToken = generatePowersyncToken(device.userId);

  res.json({ powersyncToken });
});

authRouter.get("/keys", (_req, res) => {
  res.json(getJwks());
});
