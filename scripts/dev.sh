#!/usr/bin/env bash
set -euo pipefail

echo "Start each service in a separate terminal:"
echo "1. docker compose up -d postgres redis chroma"
echo "2. cd backend && mvn spring-boot:run"
echo "3. cd ai-service && uvicorn app.main:app --reload --port 8001"
echo "4. cd frontend && npm run dev"

