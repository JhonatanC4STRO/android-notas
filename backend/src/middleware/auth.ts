import type { NextFunction, Request, Response } from "express";
import { verifyPowersyncToken } from "../services/jwtService";

export interface AuthenticatedRequest extends Request {
  userId?: string;
}

// Valida el powersyncToken (RS256) que PowerSync Service adjunta en el
// header Authorization al llamar a nuestros endpoints, y expone el userId
// del token (claim `sub`) en req.userId.
export function requirePowersyncAuth(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  const token = header?.startsWith("Bearer ") ? header.slice("Bearer ".length) : undefined;

  if (!token) {
    return res.status(401).json({ code: "UNAUTHORIZED" });
  }

  try {
    req.userId = verifyPowersyncToken(token);
    next();
  } catch {
    res.status(401).json({ code: "UNAUTHORIZED" });
  }
}
