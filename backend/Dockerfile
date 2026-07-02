FROM node:22-slim

# Prisma necesita openssl para su motor de consultas en imagenes slim/Debian.
RUN apt-get update -y && apt-get install -y openssl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY prisma ./prisma
RUN npx prisma generate

COPY . .

EXPOSE 3000

# Aplica migraciones pendientes (idempotente) y arranca el servidor.
CMD ["sh", "-c", "npx prisma migrate deploy && npx tsx src/index.ts"]
