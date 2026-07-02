import { Prisma } from "@prisma/client";

export interface CrudOperation {
  op: "PUT" | "PATCH" | "DELETE";
  table: string;
  id: string;
  data?: Record<string, unknown>;
}

function toUpdatedAt(value: unknown): bigint | undefined {
  if (typeof value !== "number" && typeof value !== "string" && typeof value !== "bigint") {
    return undefined;
  }
  try {
    return BigInt(value);
  } catch {
    return undefined;
  }
}

// Aplica una operacion del CrudBatch de PowerSync sobre la tabla notas,
// respetando pertenencia del recurso y Last-Write-Wins (CLAUDE.md, seccion 3).
// Nunca lanza por una operacion invalida/rechazada: la ignora en silencio
// para no tumbar el resto del lote (solo lanza ante un error real de BD).
export async function applyNotaOperation(tx: Prisma.TransactionClient, userId: string, op: CrudOperation) {
  if (!op || op.table !== "notas" || !op.id || !op.op) {
    return;
  }

  const existing = await tx.nota.findUnique({ where: { id: op.id } });

  if (existing && existing.userId !== userId) {
    // La nota pertenece a otro usuario: se ignora silenciosamente.
    return;
  }

  if (op.op === "PUT") {
    const updatedAt = toUpdatedAt(op.data?.updated_at);
    const { titulo, contenido } = op.data ?? {};

    if (updatedAt === undefined || typeof titulo !== "string" || typeof contenido !== "string") {
      return;
    }

    await tx.nota.upsert({
      where: { id: op.id },
      create: {
        id: op.id,
        userId,
        titulo,
        contenido,
        updatedAt,
        deleted: Boolean(op.data?.deleted),
      },
      update: {
        titulo,
        contenido,
        updatedAt,
        deleted: Boolean(op.data?.deleted),
      },
    });
    return;
  }

  // PATCH y DELETE solo tienen sentido sobre una nota que ya existe.
  if (!existing) {
    return;
  }

  const updatedAt = toUpdatedAt(op.data?.updated_at);

  if (updatedAt === undefined || updatedAt <= existing.updatedAt) {
    // Last-Write-Wins: el dato ya persistido es igual o mas reciente, se descarta.
    return;
  }

  if (op.op === "PATCH") {
    const data: Prisma.NotaUpdateInput = { updatedAt };
    if (typeof op.data?.titulo === "string") data.titulo = op.data.titulo;
    if (typeof op.data?.contenido === "string") data.contenido = op.data.contenido;

    await tx.nota.update({ where: { id: op.id }, data });
    return;
  }

  if (op.op === "DELETE") {
    await tx.nota.update({ where: { id: op.id }, data: { deleted: true, updatedAt } });
  }
}
