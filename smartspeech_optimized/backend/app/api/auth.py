from __future__ import annotations

# 本文件负责“账号认证”这一组 API：注册、密码登录、人脸登录、
# 给已有账号补录人脸、退出登录，以及查询当前登录用户。
# 路由只负责接收和校验 HTTP 数据，真正的账号保存与登录判断交给 auth/face Service。

from typing import Any

import cv2
import numpy as np
from flask import Blueprint, jsonify, request, session

from backend.app.api.common import (
    ApiValidationError,
    begin_user_session,
    current_user,
    image_bytes_from_request,
    json_body,
    json_error,
    login_required,
    services,
)
from backend.app.services.auth_service import (
    AuthError,
    AuthValidationError,
    UsernameAlreadyExists,
)
from backend.app.services.face_recognition import (
    FaceRecognitionError,
    FaceModelUnavailable,
)


# 蓝图可以理解成“一组相关路由的小册子”。它先在 api/__init__.py 中被登记，
# 最后由 app/__init__.py 统一挂到 /api 前缀下。因此这里的 /auth/login
# 对浏览器而言实际地址是 /api/auth/login。
auth_bp = Blueprint("auth", __name__)


def _decode_image(image_bytes: bytes) -> np.ndarray:
    # 把 HTTP 上传得到的二进制图片交给 OpenCV 解码成像素矩阵 frame。
    frame = cv2.imdecode(
        np.frombuffer(image_bytes, dtype=np.uint8),
        cv2.IMREAD_COLOR,
    )
    if frame is None or frame.size == 0:
        # 解码失败通常意味着文件损坏、格式不对，或者上传的不是图片。
        raise ApiValidationError("图像无法解析，请重新拍摄。")
    return frame


def _face_vector_from_request() -> np.ndarray:
    # 先从 multipart 或 Base64 JSON 中取出图片，再调用人脸服务提取特征向量。
    # 特征向量可理解成“能代表人脸的一组数字”，后续用它与注册库比较。
    frame = _decode_image(image_bytes_from_request())
    try:
        return services().face.extract_face_vector(frame)
    except FaceRecognitionError as exc:
        # Service 层异常属于客户端上传问题（如没检测到人脸），转成统一 400 错误。
        raise ApiValidationError(str(exc)) from exc


def _register_payload() -> dict[str, Any]:
    """Accept both JSON and multipart face-enrollment requests."""
    # 注册接口兼容两种提交方式：
    # 1. multipart/form-data：文字字段和图片文件一起上传；
    # 2. application/json：账号文字和 Base64 图片放在 JSON 中。
    if request.files or request.form:
        return {
            "username": request.form.get("username", ""),
            "password": request.form.get("password", ""),
            "display_name": request.form.get("display_name", ""),
        }
    return json_body()


@auth_bp.post("/auth/register")
def register():
    # 第一步：读取 username、password、display_name。
    payload = _register_payload()
    username = str(payload.get("username") or "")
    password = str(payload.get("password") or "")
    display_name = str(payload.get("display_name") or "")

    face_vector = None
    if payload.get("image_data") or "image" in request.files:
        # 人脸注册是可选步骤；提供了图片才提取人脸向量。
        face_vector = _face_vector_from_request()

    try:
        # 账号校验、密码加密和持久化都由 AuthService 完成。
        user = services().auth.register_user(
            username=username,
            password=password,
            display_name=display_name,
            face_vector=face_vector,
        )
    except UsernameAlreadyExists as exc:
        # 用户名冲突属于资源已存在，使用 409，而不是普通参数错误 400。
        return json_error(str(exc), 409)
    except (AuthValidationError, FaceRecognitionError) as exc:
        return json_error(str(exc), 400)
    except AuthError as exc:
        return json_error(str(exc), 500)

    begin_user_session(user)
    # 注册成功后直接建立登录会话，并向浏览器返回 201 Created。
    return jsonify({"user": user}), 201


@auth_bp.post("/auth/login")
def password_login():
    # 密码登录只接收 JSON；authenticate_password 返回 None 表示账号或密码错误。
    payload = json_body()
    try:
        user = services().auth.authenticate_password(
            str(payload.get("username") or ""),
            str(payload.get("password") or ""),
        )
    except AuthError as exc:
        return json_error(str(exc), 500)

    if user is None:
        return json_error("用户名或密码不正确。", 401)

    begin_user_session(user)
    # 登录成功后，后续请求依靠 Flask session Cookie 识别当前用户。
    return jsonify({"user": user})


@auth_bp.post("/auth/face-login")
def face_login():
    # 人脸登录流程：提取新照片向量 -> 读取全部已登记人脸 -> 计算最相似的人。
    try:
        vector = _face_vector_from_request()
        gallery = services().auth.list_face_gallery()
        match = services().face.match(vector, gallery)
    except (ApiValidationError, FaceRecognitionError) as exc:
        return json_error(str(exc), 400)
    except AuthError as exc:
        return json_error(str(exc), 500)

    if not match.matched or not match.user_id:
        # 相似度没有达到阈值，或者没有找到候选用户，都视为认证失败。
        return json_error("未匹配到已注册人脸，请改用密码登录。", 401)

    user = services().auth.get_user(match.user_id)
    if user is None:
        return json_error("匹配账号不存在，请重新注册。", 401)

    begin_user_session(user)
    return jsonify(
        {
            "user": user,
            "match": {
                "similarity": round(float(match.similarity), 4),
                "distance": round(float(match.distance), 4),
            },
        }
    )


@auth_bp.post("/auth/face-register")
@login_required
def register_face():
    # login_required 会先检查 session；通过后这里才能安全地读取 user_id。
    # 这个接口用于给已经通过密码注册的账号补充或更新人脸。
    user_id = str(session["user_id"])
    try:
        vector = _face_vector_from_request()
        user = services().auth.register_face(user_id, vector)
    except (ApiValidationError, FaceRecognitionError) as exc:
        return json_error(str(exc), 400)
    except AuthError as exc:
        return json_error(str(exc), 500)
    return jsonify({"user": user})


@auth_bp.post("/auth/logout")
def logout():
    # 清空 Flask session 中的 user_id，相当于让当前浏览器退出登录。
    session.clear()
    return jsonify({"ok": True})


@auth_bp.get("/auth/me")
def me():
    # 前端刷新页面时调用此接口恢复“当前是谁”的登录状态。
    user = current_user()
    if user is None:
        # session 中没有用户，或 user_id 已找不到对应账号，都清理并返回 401。
        session.clear()
        return json_error("请先登录。", 401)
    return jsonify({"user": user})
