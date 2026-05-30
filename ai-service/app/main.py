from fastapi import FastAPI

from app.api.routes import router


app = FastAPI(
    title="AI Shopping Internal Service",
    version="0.1.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.include_router(router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}

