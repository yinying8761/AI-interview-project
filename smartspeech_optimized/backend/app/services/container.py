from __future__ import annotations

# 本文件相当于整个后端的“服务总仓库”。create_app() 启动时创建一次
# ServiceContainer，之后每个 API 路由通过 services() 取用这里保存的服务对象。
# 这样路由不用自己反复 new 数据库、模型或工作线程，依赖关系也更清楚。

import importlib.util
import threading
from typing import Any

from flask import Flask

from backend.app.config import Config
from backend.app.services.asr_service import ASRService
from backend.app.services.auth_service import AuthService
from backend.app.services.deepseek_service import DeepSeekService
from backend.app.services.face_recognition import FaceRecognizer
from backend.app.services.interview_service import InterviewService
from backend.app.services.question_bank import QuestionBankService
from backend.app.services.resume_service import ResumeService
from backend.app.services.tts_service import TTSService
from backend.app.services.vision.manager import VisionManager
from backend.app.storage.report_store import ReportStore
from backend.app.storage.session_store import SessionStore


class ServiceContainer:
    def __init__(self, app: Flask) -> None:
        # app 保存 Flask 应用；config 固定引用全局 Config。
        self.app = app
        self.config = Config
        # RLock 可重入锁，用于安全地延迟创建 DeepSeek 和 InterviewService。
        self._lock = threading.RLock()
        # 两个下划线变量先设为 None，真正需要时才创建，这种写法叫“懒加载”。
        self._deepseek: DeepSeekService | None = None
        self._interview: InterviewService | None = None

        # 以下服务对象随应用一起创建，所有请求共享同一批实例。
        # 共享实例很重要：会话、登录人脸、视觉线程等状态必须让所有请求都能看到。
        self.auth = AuthService()
        self.face = FaceRecognizer(
            threshold=Config.FACE_MATCH_THRESHOLD,
            device="cpu",
        )
        self.asr = ASRService()
        self.tts = TTSService(
            edge_timeout_seconds=Config.TTS_TIMEOUT_SECONDS,
        )
        self.vision = VisionManager()
        self.sessions = SessionStore(vision=self.vision)
        self.reports = ReportStore()
        self.resumes = ResumeService()
        self.question_bank = QuestionBankService()

    @property
    def deepseek(self) -> DeepSeekService:
        # 第一次访问 services().deepseek 时才创建大模型客户端。
        if self._deepseek is None:
            with self._lock:
                # 第一个线程创建完成后，第二个等待锁的线程会再次判断并直接复用，
                # 避免并发请求创建出两个客户端。这叫双重检查。
                if self._deepseek is None:
                    self._deepseek = DeepSeekService()
        return self._deepseek

    @property
    def interview(self) -> InterviewService:
        # InterviewService 依赖 DeepSeekService，所以它也在第一次使用时创建。
        if self._interview is None:
            with self._lock:
                if self._interview is None:
                    self._interview = InterviewService(self.deepseek)
        return self._interview

    def capabilities(self) -> dict[str, bool]:
        # 返回“当前机器具备哪些能力”，供健康检查或前端判断功能是否可用。
        return {
            # API Key 存在只表示已配置，不保证每次网络请求一定成功。
            "deepseek": bool(Config.DEEPSEEK_API_KEY),
            # find_spec 只检查 Python 环境里有没有安装对应依赖，不会加载模型。
            "whisper": importlib.util.find_spec("whisper") is not None,
            "edge_tts": importlib.util.find_spec("edge_tts") is not None,
            "mediapipe": importlib.util.find_spec("mediapipe") is not None,
            "face_recognition": FaceRecognizer.dependency_available(),
            "resume_parsing": True,
            "question_bank": bool(self.question_bank.summary()["question_count"]),
            "frontend_built": (
                # Vue 构建完成后会生成 index.html，后端据此判断前端是否可服务。
                Config.FRONTEND_DIST_DIR / "index.html"
            ).exists(),
        }
