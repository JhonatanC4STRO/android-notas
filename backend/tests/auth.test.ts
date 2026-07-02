import { describe, it, expect, beforeEach, afterAll } from "vitest";
import request from "supertest";
import bcrypt from "bcrypt";
import { app } from "../src/app";
import { prisma } from "../src/services/prisma";

const TEST_EMAIL = "auth-test@test.com";
const TEST_PASSWORD = "123456";

async function createTestUser() {
  const passwordHash = await bcrypt.hash(TEST_PASSWORD, 10);
  return prisma.user.create({ data: { email: TEST_EMAIL, passwordHash } });
}

async function cleanup() {
  const user = await prisma.user.findUnique({ where: { email: TEST_EMAIL } });
  if (user) {
    await prisma.device.deleteMany({ where: { userId: user.id } });
    await prisma.user.delete({ where: { id: user.id } });
  }
}

beforeEach(cleanup);

afterAll(async () => {
  await cleanup();
  await prisma.$disconnect();
});

describe("POST /auth/login", () => {
  it("permite el login con credenciales validas y devuelve refreshToken + powersyncToken", async () => {
    await createTestUser();

    const res = await request(app)
      .post("/auth/login")
      .send({ email: TEST_EMAIL, password: TEST_PASSWORD, deviceId: "device-1" });

    expect(res.status).toBe(200);
    expect(res.body.refreshToken).toBeTypeOf("string");
    expect(res.body.powersyncToken).toBeTypeOf("string");

    const device = await prisma.device.findUnique({ where: { deviceId: "device-1" } });
    expect(device?.estado).toBe("activo");
    expect(device?.refreshTokenHash).not.toBeNull();
  });

  it("rechaza credenciales invalidas", async () => {
    await createTestUser();

    const res = await request(app)
      .post("/auth/login")
      .send({ email: TEST_EMAIL, password: "clave-incorrecta", deviceId: "device-1" });

    expect(res.status).toBe(401);
    expect(res.body.code).toBe("INVALID_CREDENTIALS");
  });

  it("expulsa el dispositivo activo con last_seen mas antiguo al superar el limite de 2", async () => {
    const user = await createTestUser();

    await prisma.device.create({
      data: {
        userId: user.id,
        deviceId: "device-old",
        estado: "activo",
        lastSeen: new Date(Date.now() - 60_000),
      },
    });
    await prisma.device.create({
      data: {
        userId: user.id,
        deviceId: "device-recent",
        estado: "activo",
        lastSeen: new Date(),
      },
    });

    const res = await request(app)
      .post("/auth/login")
      .send({ email: TEST_EMAIL, password: TEST_PASSWORD, deviceId: "device-new" });

    expect(res.status).toBe(200);

    const oldDevice = await prisma.device.findUnique({ where: { deviceId: "device-old" } });
    const recentDevice = await prisma.device.findUnique({ where: { deviceId: "device-recent" } });
    const newDevice = await prisma.device.findUnique({ where: { deviceId: "device-new" } });

    expect(oldDevice?.estado).toBe("revocado");
    expect(recentDevice?.estado).toBe("activo");
    expect(newDevice?.estado).toBe("activo");
  });
});

describe("POST /auth/token", () => {
  it("rechaza la renovacion de un dispositivo revocado con DEVICE_REVOKED", async () => {
    const user = await createTestUser();
    const refreshTokenHash = await bcrypt.hash("token-cualquiera", 10);

    await prisma.device.create({
      data: {
        userId: user.id,
        deviceId: "device-revoked",
        estado: "revocado",
        lastSeen: new Date(),
        refreshTokenHash,
        refreshTokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000),
      },
    });

    const res = await request(app)
      .post("/auth/token")
      .send({ refreshToken: "token-cualquiera", deviceId: "device-revoked" });

    expect(res.status).toBe(401);
    expect(res.body.code).toBe("DEVICE_REVOKED");
  });

  it("renueva el powersyncToken para un dispositivo activo con refreshToken valido", async () => {
    await createTestUser();

    const loginRes = await request(app)
      .post("/auth/login")
      .send({ email: TEST_EMAIL, password: TEST_PASSWORD, deviceId: "device-active" });

    const res = await request(app)
      .post("/auth/token")
      .send({ refreshToken: loginRes.body.refreshToken, deviceId: "device-active" });

    expect(res.status).toBe(200);
    expect(res.body.powersyncToken).toBeTypeOf("string");
  });

  it("rechaza un refreshToken invalido con INVALID_REFRESH_TOKEN", async () => {
    await createTestUser();

    await request(app)
      .post("/auth/login")
      .send({ email: TEST_EMAIL, password: TEST_PASSWORD, deviceId: "device-active" });

    const res = await request(app)
      .post("/auth/token")
      .send({ refreshToken: "token-invalido", deviceId: "device-active" });

    expect(res.status).toBe(401);
    expect(res.body.code).toBe("INVALID_REFRESH_TOKEN");
  });
});

describe("GET /auth/keys", () => {
  it("expone el JWKS publico con el kid configurado", async () => {
    const res = await request(app).get("/auth/keys");

    expect(res.status).toBe(200);
    expect(res.body.keys).toHaveLength(1);
    expect(res.body.keys[0]).toMatchObject({ kty: "RSA", use: "sig", alg: "RS256" });
    expect(res.body.keys[0].kid).toBeTypeOf("string");
  });
});
