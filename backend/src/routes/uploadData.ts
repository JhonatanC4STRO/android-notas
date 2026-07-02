import { Router } from "express";
import { prisma } from "../services/prisma";
import { requirePowersyncAuth, type AuthenticatedRequest } from "../middleware/auth";
import { applyNotaOperation, type CrudOperation } from "../services/uploadDataService";

export const uploadDataRouter = Router();

uploadDataRouter.post("/upload-data", requirePowersyncAuth, async (req: AuthenticatedRequest, res) => {
  const userId = req.userId as string;
  const batch: CrudOperation[] = Array.isArray(req.body?.batch) ? req.body.batch : [];

  // Todo el lote se aplica en UNA transaccion: si hay un error real de BD,
  // se revierte completo y respondemos 500 para que PowerSync reintente
  // el lote. Los rechazos de negocio (ownership/LWW) no son errores: se
  // ignoran operacion por operacion dentro de la misma transaccion.
  await prisma.$transaction(async (tx) => {
    for (const op of batch) {
      await applyNotaOperation(tx, userId, op);
    }
  });

  res.status(200).json({ status: "ok" });
});
