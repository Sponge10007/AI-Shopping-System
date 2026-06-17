#!/usr/bin/env bash
set -euo pipefail

echo "Start each service in a separate terminal:"
echo "1. docker compose up -d postgres redis chroma"
echo "2. cd backend && mvn spring-boot:run"
echo "3. cd ai-service/app && ../.venv/bin/python -m api.py_api_server"
echo "4. cd frontend && npm run dev"
