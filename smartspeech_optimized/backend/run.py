from __future__ import annotations

from waitress import serve

from backend.app import create_app
from backend.app.config import Config


app = create_app()


if __name__ == "__main__":
    print(f"{Config.APP_NAME} 正在启动：http://{Config.HOST}:{Config.PORT}")
    serve(
        app,
        host=Config.HOST,
        port=Config.PORT,
        threads=8,
        channel_timeout=180,
    )
