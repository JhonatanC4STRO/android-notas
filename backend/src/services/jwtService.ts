import { createPublicKey } from "crypto";
import jwt from "jsonwebtoken";

const POWERSYNC_TOKEN_TTL = "10m";

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Falta la variable de entorno ${name}`);
  }
  return value;
}

const privateKey = Buffer.from(requireEnv("JWT_PRIVATE_KEY_BASE64"), "base64").toString("utf-8");
const publicKey = Buffer.from(requireEnv("JWT_PUBLIC_KEY_BASE64"), "base64").toString("utf-8");
const kid = requireEnv("JWT_KID");
const issuer = requireEnv("POWERSYNC_JWT_ISSUER");
const audience = requireEnv("POWERSYNC_JWT_AUDIENCE");

export function generatePowersyncToken(userId: string): string {
  return jwt.sign({}, privateKey, {
    algorithm: "RS256",
    subject: userId,
    issuer,
    audience,
    expiresIn: POWERSYNC_TOKEN_TTL,
    keyid: kid,
  });
}

// Verifica el powersyncToken (RS256) y devuelve el userId (claim `sub`).
// Lanza si la firma, el issuer/audience o la expiracion no son validos.
export function verifyPowersyncToken(token: string): string {
  const payload = jwt.verify(token, publicKey, { algorithms: ["RS256"], issuer, audience });

  if (typeof payload !== "object" || typeof payload.sub !== "string") {
    throw new Error("Token invalido: falta el claim sub");
  }

  return payload.sub;
}

export function getJwks() {
  const jwk = createPublicKey(publicKey).export({ format: "jwk" }) as Record<string, unknown>;

  return {
    keys: [
      {
        ...jwk,
        kid,
        use: "sig",
        alg: "RS256",
      },
    ],
  };
}
