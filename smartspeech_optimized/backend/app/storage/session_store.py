from __future__ import annotations

# 本文件管理“正在进行中的面试会话”。会话保存在服务器内存中，浏览器只拿 session_id；
# 每个会话包含聊天记录、题目进度、视觉分析器和最新帧工作线程等运行状态。

import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, List

from backend.app.config import Config
from backend.app.services.vision.manager import VisionManager
from backend.app.services.vision.session_analyzer import InterviewVideoAnalyzer
from backend.app.services.vision.worker import LatestFrameWorker


def utc_now() -> str:
    # 统一使用带时区的 UTC ISO 时间，避免不同机器本地时间造成排序错误。
    return datetime.now(timezone.utc).isoformat()


@dataclass
class InterviewSession:
    # 下面是一轮面试的全部运行状态。无默认值的字段由 create() 必传；
    # 有默认值的字段会随着面试进行逐步更新。
    id: str
    user_id: str
    job_role: str
    difficulty: str
    voice: str
    auto_tts: bool
    # 摄像头每秒分析帧数和每隔多少秒汇总一次快照。
    analysis_fps: float
    snapshot_interval: float
    resume_id: str | None = None
    resume_filename: str | None = None
    resume_content: str = ""
    # follow_up_mode 决定每个主问题后追问几次；question_limit 是整场主问题上限。
    follow_up_mode: str = "random"
    question_limit: int = 12
    status: str = "active"
    created_at: str = field(default_factory=utc_now)
    finished_at: str | None = None
    transcript: List[Dict[str, Any]] = field(default_factory=list)
    # 题库题号与当前场景进度，用于避免重复抽题并决定下一道题。
    question_count: int = 0
    current_scene_id: str = ""
    current_scene_label: str = ""
    scene_index: int = -1
    current_question_mode: str = "opening"
    current_follow_up_count: int = 0
    follow_up_target: int = 0
    current_bank_question_id: str | None = None
    used_bank_question_ids: List[str] = field(default_factory=list)
    # checkpoint 表示“题目已问满，等待用户选择继续或结束”。
    checkpoint_reason: str | None = None
    # analyzer 负责统计，vision_worker 负责后台消费最新摄像头帧。
    analyzer: InterviewVideoAnalyzer | None = None
    vision_worker: LatestFrameWorker | None = None
    video_summary: Dict[str, Any] | None = None
    report: Dict[str, Any] | None = None
    # 每个会话单独一把可重入锁，多个请求同时操作同一场面试时保护字段一致性。
    lock: threading.RLock = field(default_factory=threading.RLock)

    def public_state(self, include_metrics: bool = True) -> Dict[str, Any]:
        # 只整理可公开给前端的字段。user_id、锁、工作线程等内部对象不会返回。
        payload: Dict[str, Any] = {
            "id": self.id,
            "job_role": self.job_role,
            "difficulty": self.difficulty,
            "voice": self.voice,
            "auto_tts": self.auto_tts,
            "analysis_fps": self.analysis_fps,
            "snapshot_interval": self.snapshot_interval,
            "resume": (
                {
                    "id": self.resume_id,
                    "filename": self.resume_filename,
                }
                if self.resume_id
                else None
            ),
            "follow_up_mode": self.follow_up_mode,
            "question_limit": self.question_limit,
            "question_count": self.question_count,
            "current_scene_id": self.current_scene_id,
            "current_scene_label": self.current_scene_label,
            "current_question_mode": self.current_question_mode,
            "current_follow_up_count": self.current_follow_up_count,
            "follow_up_target": self.follow_up_target,
            "checkpoint_reason": self.checkpoint_reason,
            "status": self.status,
            "created_at": self.created_at,
            "finished_at": self.finished_at,
            # 复制聊天列表，避免调用方在 JSON 序列化时直接操作内部列表。
            "transcript": list(self.transcript),
        }
        if include_metrics and self.analyzer is not None:
            # 如果启用了视觉分析，把当前实时指标一起放进会话状态。
            payload["live_metrics"] = self.analyzer.get_live_metrics()
        return payload


class SessionStore:
    def __init__(
        self,
        vision: VisionManager | None = None,
        max_age_seconds: int = 60 * 60 * 8,
    ) -> None:
        # 字典键是 session_id，值是 InterviewSession 对象。
        self._sessions: Dict[str, InterviewSession] = {}
        # 保护整个内存字典的读写；至少保证 60 秒，默认 8 小时自动过期。
        self._lock = threading.RLock()
        self._max_age_seconds = max(60, int(max_age_seconds))
        self._vision = vision or VisionManager()

    def create(
        self,
        *,
        user_id: str,
        job_role: str,
        difficulty: str,
        voice: str,
        auto_tts: bool,
        analysis_fps: float,
        snapshot_interval: float,
        resume_id: str | None = None,
        resume_filename: str | None = None,
        resume_content: str = "",
        follow_up_mode: str = "random",
        question_limit: int = Config.INTERVIEW_QUESTION_LIMIT,
    ) -> InterviewSession:
        # 每次创建新会话前顺手清理过旧会话，无需单独的定时任务。
        self.cleanup()
        session_id = uuid.uuid4().hex
        # 创建本场视觉统计器，并告诉它当前会话编号。
        analyzer = InterviewVideoAnalyzer(
            target_fps=analysis_fps,
            snapshot_interval_seconds=snapshot_interval,
            min_valid_face_frames=10,
        )
        analyzer.start_session(session_id)
        # 工作线程从队列中拿最新摄像头帧，避免请求线程被模型推理拖慢。
        vision_worker = LatestFrameWorker(
            analyzer,
            self._vision,
            thread_name=f"vision-{session_id[:10]}",
        )
        vision_worker.start()
        # 把请求参数、视觉对象和其它默认状态组装成一个会话。
        item = InterviewSession(
            id=session_id,
            user_id=str(user_id),
            job_role=job_role,
            difficulty=difficulty,
            voice=voice,
            auto_tts=bool(auto_tts),
            analysis_fps=analysis_fps,
            snapshot_interval=snapshot_interval,
            resume_id=resume_id,
            resume_filename=resume_filename,
            resume_content=resume_content,
            follow_up_mode=follow_up_mode,
            question_limit=max(1, int(question_limit)),
            analyzer=analyzer,
            vision_worker=vision_worker,
        )
        with self._lock:
            # 只有在对象完全准备好后才放入字典，其它请求不会拿到半成品。
            self._sessions[session_id] = item
        return item

    def get(
        self,
        session_id: str,
        user_id: str | None = None,
    ) -> InterviewSession | None:
        # 先按编号查找。user_id 不为空时再核对归属，防止跨用户访问。
        with self._lock:
            item = self._sessions.get(str(session_id))
        if item is None:
            return None
        if user_id is not None and item.user_id != str(user_id):
            return None
        return item

    def delete(self, session_id: str, user_id: str) -> bool:
        # 先查归属，再从字典移除。删除失败返回 False，让 API 层给出 404。
        with self._lock:
            item = self._sessions.get(str(session_id))
            if item is None or item.user_id != str(user_id):
                return False
            self._sessions.pop(str(session_id), None)
        self._close_session(item)
        return True

    def cleanup(self, now: float | None = None) -> int:
        # now 参数便于测试传入固定时间，正常运行时使用当前 Unix 时间。
        current = time.time() if now is None else float(now)
        expired: List[InterviewSession] = []
        with self._lock:
            for session_id, item in list(self._sessions.items()):
                try:
                    # 把记录中的 ISO 时间转换为 Unix 秒数，方便和当前时间相减。
                    created = datetime.fromisoformat(item.created_at).timestamp()
                except ValueError:
                    # 旧数据时间格式异常时当作刚创建，下一轮再尝试清理。
                    created = current
                if current - created > self._max_age_seconds:
                    expired.append(self._sessions.pop(session_id))
        for item in expired:
            # 在线程锁外停止工作线程，避免关线程耗时过长阻塞整个会话字典。
            self._close_session(item)
        return len(expired)

    @staticmethod
    def _close_session(item: InterviewSession) -> None:
        # 按顺序停止帧工作线程并关闭视觉分析器，释放本地模型和线程资源。
        if item.vision_worker is not None:
            item.vision_worker.stop()
        if item.analyzer is not None:
            item.analyzer.close()
