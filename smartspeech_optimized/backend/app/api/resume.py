from __future__ import annotations

# 本文件是“简历文件”相关的 API 入口。它负责接收上传文件、查询最新简历和
# 删除最新简历；文件格式检查、解析与磁盘保存由 ResumeService 完成。

from flask import Blueprint, jsonify, request, session

from backend.app.api.common import json_error, login_required, services
from backend.app.services.resume_service import ResumeError


# 这组蓝图由 api/__init__.py 统一注册，最终访问地址会带 /api 前缀。
resume_bp = Blueprint("resume", __name__)


@resume_bp.post("/resumes")
@login_required
def upload_resume():
    # 简历必须归属于登录用户，因此 login_required 会先验证 session。
    # 前端上传时，文件字段名必须是 resume。
    uploaded = request.files.get("resume")
    if uploaded is None:
        return json_error("请选择 PDF、DOC 或 DOCX 格式的简历。", 400)

    try:
        # uploaded.read() 读取文件二进制内容；Service 会判断 PDF/DOC/DOCX、
        # 保存原文件，并提取求职者文本等简历信息。
        item = services().resumes.save(
            str(session["user_id"]),
            uploaded.filename or "resume",
            uploaded.read(),
        )
    except ResumeError as exc:
        return json_error(str(exc), 400)
    except OSError as exc:
        # OSError 代表服务器磁盘或目录问题，属于服务端错误 500。
        return json_error(f"简历保存失败：{exc}", 500)

    # public_state() 只输出前端需要看的字段，不直接把内部对象暴露给浏览器。
    return jsonify({"resume": item.public_state()}), 201


@resume_bp.get("/resumes/latest")
@login_required
def get_latest_resume():
    # 每个用户只读取自己最新的一份简历；没有上传过时返回 null。
    item = services().resumes.latest(str(session["user_id"]))
    return jsonify({"resume": item.public_state() if item else None})


@resume_bp.delete("/resumes/latest")
@login_required
def delete_latest_resume():
    # 删除操作同样按当前 session 中的 user_id 隔离，不能删除别人的简历。
    try:
        deleted = services().resumes.delete(str(session["user_id"]))
    except ResumeError as exc:
        return json_error(str(exc), 500)
    if not deleted:
        # 返回 False 表示磁盘上没有该用户的简历可删。
        return json_error("暂无可删除的简历。", 404)
    return jsonify({"ok": True})
