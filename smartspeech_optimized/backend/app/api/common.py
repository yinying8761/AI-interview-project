from __future__ import annotations

# 本文件提供所有 API 蓝图共享的工具：读取 Service、判断登录状态、
# 校验 JSON、解析上传图片以及生成统一错误响应。

import base64
import binascii
from functools import wraps
from typing import Any, Callable

from flask import current_app, jsonify, request, session


class ApiValidationError(ValueError):
    # 表示客户端提交的数据格式不对，路由通常把它转换为 HTTP 400。
    pass


def services():
    # 从当前 Flask 应用取得 create_app() 中保存的 ServiceContainer。
    # 这里不重新创建服务，因此所有请求共享同一批服务对象。
    return current_app.extensions["services"]


def current_user_id() -> str | None:
    # Flask session 中只保存 user_id；读取后统一转成字符串。
    value = session.get("user_id")
    return str(value) if value else None


def current_user() -> dict[str, Any] | None:
    # 根据登录会话中的 user_id 查询完整公开用户信息。
    user_id = current_user_id()
    return services().auth.get_user(user_id) if user_id else None


def begin_user_session(user: dict[str, Any]) -> None:
    # 建立登录会话前先清理旧内容，避免会话固定攻击。
    # permanent=True 会使用 Config.PERMANENT_SESSION_LIFETIME 设置有效期。
    session.clear()
    session["user_id"] = str(user["id"])
    session.permanent = True


def login_required(view: Callable):
    # 这是视图函数装饰器。请求先经过 wrapped() 检查登录状态，
    # 只有账号仍存在时才调用原始视图函数。
    @wraps(view)
    def wrapped(*args, **kwargs):
        user_id = current_user_id()
        if not user_id:
            return jsonify({"error": "请先登录。"}), 401
        user = services().auth.get_user(user_id)
        if user is None:
            # Cookie 里的用户可能已被删除，此时清理失效会话。
            session.clear()
            return jsonify({"error": "登录状态已失效，请重新登录。"}), 401
        return view(*args, **kwargs)

    return wrapped



def json_body() -> dict[str, Any]:
    # silent=True 让 Flask 在内容不是 JSON 时返回 None，由这里统一给出中文错误。
    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        raise ApiValidationError("请求内容必须是 JSON 对象。")
    return payload


def image_bytes_from_request(
    field_name: str = "image",
    json_field: str = "image_data",
) -> bytes:
    """Read a camera image from multipart form data or a base64 JSON field."""
    # 优先支持 multipart/form-data 文件字段，例如浏览器 FormData 上传的 image。
    uploaded = request.files.get(field_name)
    if uploaded is not None:
        data = uploaded.read()
        if not data:
            raise ApiValidationError("未收到图像内容。")
        return data

    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        raise ApiValidationError("请上传摄像头图像。")

    raw_value = payload.get(json_field)
    if not isinstance(raw_value, str) or not raw_value.strip():
        raise ApiValidationError("请上传摄像头图像。")

    encoded = raw_value.strip()
    if encoded.startswith("data:"):
        # 浏览器 canvas.toDataURL() 会产生 data:image/...;base64,xxx 格式，
        # 解码前需要去掉逗号左侧的 MIME 声明。
        try:
            encoded = encoded.split(",", 1)[1]
        except IndexError as exc:
            raise ApiValidationError("图像编码格式无效。") from exc

    try:
        # validate=True 会拒绝夹杂非法字符的 Base64，避免静默解码出错误图片。
        data = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ApiValidationError("图像编码格式无效。") from exc

    if not data:
        raise ApiValidationError("未收到图像内容。")
    return data


def json_error(message: str, status: int = 400):
    # API 错误统一使用 {"error": "..."} 结构，并保留指定 HTTP 状态码。
    return jsonify({"error": message}), status
