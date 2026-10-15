from __future__ import annotations

# 系统状态蓝图只负责健康检查，不在这个模块中执行业务逻辑。

from flask import Blueprint, jsonify

from backend.app.api.common import current_user_id, services
from backend.app.config import Config


system_bp = Blueprint("system", __name__)


@system_bp.get("/health")
def health():
    # 返回应用名称、当前是否登录，以及各可选依赖是否可用的能力表。
    # 前端和后端排查环境问题时可以先请求这个接口。
    container = services()
    return jsonify(
        {
            "ok": True,
            "name": Config.APP_NAME,
            "authenticated": bool(current_user_id()),
            "capabilities": container.capabilities(),
        }
    )
