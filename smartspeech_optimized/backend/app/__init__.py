from __future__ import annotations

# 本文件是后端应用的组装入口。它不直接处理某条业务请求，而是负责把
# Flask 配置、公共 Service、API 蓝图和前端静态页面连接成一个完整应用。

import logging
from pathlib import Path

from flask import Flask, jsonify, request, send_from_directory
from flask_cors import CORS

from backend.app.api import api
from backend.app.config import Config
from backend.app.services.container import ServiceContainer


def create_app(config_object: type[Config] = Config) -> Flask:
    # 使用工厂函数创建 Flask 应用。测试时可以传入其它配置类，
    # 正常启动时则由 run.py 使用默认的 Config。
    app = Flask(
        __name__,
        # 前端文件由下面的 SPA 回退逻辑手动返回，不使用 Flask 默认静态目录。
        static_folder=None,
    )
    # 把 Config 类中的大写属性加载为 app.config 配置项。
    app.config.from_object(config_object)
    # 保证 JSON 响应直接输出中文，而不是输出 \uXXXX 转义字符。
    app.json.ensure_ascii = False

    # 统一日志格式，便于从终端观察启动、请求和异常信息。
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
    )

    # 只允许配置中的前端开发地址跨域访问 /api/*，并允许携带登录 Cookie。
    CORS(
        app,
        resources={r"/api/*": {"origins": app.config["CORS_ORIGINS"]}},
        supports_credentials=True,
    )

    # ServiceContainer 集中创建认证、语音、视觉、会话和存储等服务。
    # 路由层通过 common.services() 从 Flask 的应用扩展中取得这个容器。
    app.extensions["services"] = ServiceContainer(app)
    # 所有 API 蓝图统一挂到 /api 前缀下。
    app.register_blueprint(api, url_prefix="/api")

    # 文件或请求体超过 MAX_CONTENT_LENGTH 时返回统一的 413 提示。
    @app.errorhandler(413)
    def handle_too_large(_error):
        return jsonify({"error": "上传内容过大，请压缩后重试。"}), 413

    # 捕获未处理的异常。API 请求返回 JSON，普通页面请求继续抛给 Flask，
    # 以保留非 API 页面原有的调试或错误处理行为。
    @app.errorhandler(Exception)
    def handle_unexpected_error(error):
        app.logger.exception("Unhandled request error", exc_info=error)
        if request.path.startswith("/api/"):
            return jsonify({"error": "服务器处理失败，请稍后重试。"}), 500
        raise error

    # 前端首页：优先返回已经构建好的 Vue 页面。
    @app.get("/")
    def serve_index():
        return _serve_frontend("index.html")

    # Vue 使用前端路由时，刷新 /reports 等地址仍会请求后端。
    # 这些非 API 路径统一交给前端 index.html 处理。
    @app.get("/<path:path>")
    def serve_spa(path: str):
        if path.startswith("api/"):
            return jsonify({"error": "接口不存在。"}), 404
        return _serve_frontend(path)

    return app


def _serve_frontend(path: str):
    # 先尝试返回同名静态文件；找不到时回退到 index.html，让 Vue Router
    # 根据浏览器地址显示对应页面。前端尚未构建时返回可操作的 503 提示。
    dist_dir = Path(Config.FRONTEND_DIST_DIR)
    requested = dist_dir / path
    if requested.is_file():
        return send_from_directory(dist_dir, path)
    if (dist_dir / "index.html").exists():
        return send_from_directory(dist_dir, "index.html")
    return (
        jsonify(
            {
                "error": "前端尚未构建。请先运行 npm run build 或使用 run.bat。",
                "hint": "开发模式可运行 frontend/npm run dev。",
            }
        ),
        503,
    )
