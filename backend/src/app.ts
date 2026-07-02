import "dotenv/config";
import express, { type ErrorRequestHandler } from "express";
import { authRouter } from "./routes/auth";
import { uploadDataRouter } from "./routes/uploadData";

export const app = express();

app.use(express.json());

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.use("/auth", authRouter);
app.use("/api", uploadDataRouter);

const errorHandler: ErrorRequestHandler = (err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ code: "INTERNAL_ERROR" });
};

app.use(errorHandler);
