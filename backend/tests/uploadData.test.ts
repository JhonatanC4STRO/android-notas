import { describe, it, expect, beforeEach, afterAll } from "vitest";
import request from "supertest";
import bcrypt from "bcrypt";
import { app } from "../src/app";
import { prisma } from "../src/services/prisma";
import { generatePowersyncToken } from "../src/services/jwtService";

const USER_A_EMAIL = "upload-test-a@test.com";
const USER_B_EMAIL = "upload-test-b@test.com";

async function createUser(email: string) {
  const passwordHash = await bcrypt.hash("123456", 10);
  return prisma.user.create({ data: { email, passwordHash } });
}

async function cleanup() {
  const users = await prisma.user.findMany({ where: { email: { in: [USER_A_EMAIL, USER_B_EMAIL] } } });
  const userIds = users.map((u) => u.id);
  if (userIds.length) {
    await prisma.nota.deleteMany({ where: { userId: { in: userIds } } });
    await prisma.user.deleteMany({ where: { id: { in: userIds } } });
  }
}

beforeEach(cleanup);

afterAll(async () => {
  await cleanup();
  await prisma.$disconnect();
});

function authHeader(userId: string) {
  return { Authorization: `Bearer ${generatePowersyncToken(userId)}` };
}

describe("POST /api/upload-data", () => {
  it("inserta una nota nueva via PUT (upsert)", async () => {
    const user = await createUser(USER_A_EMAIL);
    const notaId = "11111111-1111-1111-1111-111111111111";

    const res = await request(app)
      .post("/api/upload-data")
      .set(authHeader(user.id))
      .send({
        batch: [
          {
            op: "PUT",
            table: "notas",
            id: notaId,
            data: { titulo: "Primera nota", contenido: "Contenido", updated_at: 1000, deleted: false },
          },
        ],
      });

    expect(res.status).toBe(200);

    const nota = await prisma.nota.findUnique({ where: { id: notaId } });
    expect(nota?.titulo).toBe("Primera nota");
    expect(nota?.userId).toBe(user.id);
    expect(nota?.updatedAt).toBe(1000n);
  });

  it("aplica el PATCH cuando el updated_at entrante es mayor (gana el mas reciente)", async () => {
    const user = await createUser(USER_A_EMAIL);
    const notaId = "22222222-2222-2222-2222-222222222222";
    await prisma.nota.create({
      data: { id: notaId, userId: user.id, titulo: "Original", contenido: "v1", updatedAt: 1000n, deleted: false },
    });

    const res = await request(app)
      .post("/api/upload-data")
      .set(authHeader(user.id))
      .send({
        batch: [{ op: "PATCH", table: "notas", id: notaId, data: { titulo: "Editada", updated_at: 2000 } }],
      });

    expect(res.status).toBe(200);
    const nota = await prisma.nota.findUnique({ where: { id: notaId } });
    expect(nota?.titulo).toBe("Editada");
    expect(nota?.updatedAt).toBe(2000n);
  });

  it("descarta silenciosamente un PATCH con updated_at menor o igual (gana el dato del servidor)", async () => {
    const user = await createUser(USER_A_EMAIL);
    const notaId = "33333333-3333-3333-3333-333333333333";
    await prisma.nota.create({
      data: {
        id: notaId,
        userId: user.id,
        titulo: "Servidor",
        contenido: "v-servidor",
        updatedAt: 5000n,
        deleted: false,
      },
    });

    const res = await request(app)
      .post("/api/upload-data")
      .set(authHeader(user.id))
      .send({
        batch: [{ op: "PATCH", table: "notas", id: notaId, data: { titulo: "Viejo", updated_at: 3000 } }],
      });

    expect(res.status).toBe(200);
    const nota = await prisma.nota.findUnique({ where: { id: notaId } });
    expect(nota?.titulo).toBe("Servidor");
    expect(nota?.updatedAt).toBe(5000n);
  });

  it("aplica DELETE como soft delete (tombstone) respetando LWW", async () => {
    const user = await createUser(USER_A_EMAIL);
    const notaId = "44444444-4444-4444-4444-444444444444";
    await prisma.nota.create({
      data: { id: notaId, userId: user.id, titulo: "A borrar", contenido: "x", updatedAt: 1000n, deleted: false },
    });

    const res = await request(app)
      .post("/api/upload-data")
      .set(authHeader(user.id))
      .send({
        batch: [{ op: "DELETE", table: "notas", id: notaId, data: { updated_at: 2000 } }],
      });

    expect(res.status).toBe(200);
    const nota = await prisma.nota.findUnique({ where: { id: notaId } });
    expect(nota?.deleted).toBe(true);
    expect(nota?.updatedAt).toBe(2000n);
  });

  it("es idempotente: reenviar el mismo lote no duplica ni corrompe datos", async () => {
    const user = await createUser(USER_A_EMAIL);
    const notaId = "55555555-5555-5555-5555-555555555555";

    const batch = [
      {
        op: "PUT",
        table: "notas",
        id: notaId,
        data: { titulo: "Nota", contenido: "v1", updated_at: 1000, deleted: false },
      },
      { op: "PATCH", table: "notas", id: notaId, data: { contenido: "v2", updated_at: 2000 } },
    ];

    const first = await request(app).post("/api/upload-data").set(authHeader(user.id)).send({ batch });
    const second = await request(app).post("/api/upload-data").set(authHeader(user.id)).send({ batch });

    expect(first.status).toBe(200);
    expect(second.status).toBe(200);

    const notas = await prisma.nota.findMany({ where: { id: notaId } });
    expect(notas).toHaveLength(1);
    expect(notas[0].contenido).toBe("v2");
    expect(notas[0].updatedAt).toBe(2000n);
  });

  it("ignora silenciosamente el intento de escribir una nota de otro usuario", async () => {
    const userA = await createUser(USER_A_EMAIL);
    const userB = await createUser(USER_B_EMAIL);
    const notaId = "66666666-6666-6666-6666-666666666666";

    await prisma.nota.create({
      data: { id: notaId, userId: userA.id, titulo: "De A", contenido: "privado", updatedAt: 1000n, deleted: false },
    });

    const res = await request(app)
      .post("/api/upload-data")
      .set(authHeader(userB.id))
      .send({
        batch: [{ op: "PATCH", table: "notas", id: notaId, data: { titulo: "Hackeada", updated_at: 9999 } }],
      });

    expect(res.status).toBe(200);
    const nota = await prisma.nota.findUnique({ where: { id: notaId } });
    expect(nota?.titulo).toBe("De A");
    expect(nota?.userId).toBe(userA.id);
  });

  it("rechaza peticiones sin un JWT valido", async () => {
    const res = await request(app).post("/api/upload-data").send({ batch: [] });
    expect(res.status).toBe(401);
  });
});
