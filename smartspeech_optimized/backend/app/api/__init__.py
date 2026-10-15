from flask import Blueprint

# api 是 API 层的总蓝图，本身不定义具体接口。
# 它把认证、面试、媒体、简历和系统能力等子蓝图组装在一起，
# 最后由 app/__init__.py 统一挂载到 /api 前缀。

from backend.app.api.ai import ai_bp
from backend.app.api.auth import auth_bp
from backend.app.api.interview import interview_bp
from backend.app.api.media import media_bp
from backend.app.api.resume import resume_bp
from backend.app.api.system import system_bp


api = Blueprint("api", __name__)
# 注册顺序不改变各接口的 URL；每个子蓝图内部已经写明完整资源前缀。
api.register_blueprint(system_bp)
api.register_blueprint(ai_bp)
api.register_blueprint(auth_bp)
api.register_blueprint(interview_bp)
api.register_blueprint(media_bp)
api.register_blueprint(resume_bp)
