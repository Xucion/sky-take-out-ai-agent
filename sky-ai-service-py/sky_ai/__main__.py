import uvicorn

from sky_ai.config import Settings

if __name__ == "__main__":
    uvicorn.run("sky_ai.main:create_app", factory=True, host="127.0.0.1", port=Settings().port)
