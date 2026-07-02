import { randomBytes } from "crypto";
import bcrypt from "bcrypt";

export const REFRESH_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 dias

export function generateRefreshToken(): string {
  return randomBytes(32).toString("hex");
}

export function hashRefreshToken(token: string): Promise<string> {
  return bcrypt.hash(token, 10);
}

export function verifyRefreshToken(token: string, hash: string): Promise<boolean> {
  return bcrypt.compare(token, hash);
}
