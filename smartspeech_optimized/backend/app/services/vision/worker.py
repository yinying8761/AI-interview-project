from __future__ import annotations

# 一个面试会话对应一个 LatestFrameWorker。
# HTTP 请求线程只负责把图片放进容量为 1 的队列，后台线程再慢慢分析；
# 若新帧到来时旧帧还没分析，队列会自动丢掉旧帧，只保留最新帧。

import queue
import threading
import time
from collections import deque
from typing import Any, Dict

from backend.app.services.vision.manager import VisionManager
from backend.app.services.vision.session_analyzer import InterviewVideoAnalyzer


_STOP = object()
# 用一个模块级私有对象作为停止信号，避免和正常 bytes 帧混淆。


class LatestFrameWorker:
    # “latest frame”策略优先保证实时性，而不是保证每一帧都处理。
    # 面试画面持续产生时，分析慢一点也胜过积压很多过时图片。
    """Analyze only the newest pending frame for one interview session."""

    def __init__(
        self,
        analyzer: InterviewVideoAnalyzer,
        vision: VisionManager,
        *,
        thread_name: str = "vision-worker",
    ) -> None:
        # analyzer 保存一个面试会话的累计状态，vision 负责解码/缩放图片。
        self.analyzer = analyzer
        self.vision = vision
        # maxsize=1 是关键：队列中最多等待一帧，迫使 submit 替换旧帧。
        self._queue: queue.Queue[bytes | object] = queue.Queue(maxsize=1)
        # 多线程会同时读写统计字段，所有字段更新都由同一把锁保护。
        self._lock = threading.RLock()
        # Event 比普通布尔量更适合跨线程发出“请停止”的信号。
        self._stop_event = threading.Event()
        self._thread: threading.Thread | None = None
        self._thread_name = thread_name
        self._state = "starting"
        # _accepting 控制是否继续接收 HTTP 提交；_processing 表示后台是否正在分析。
        self._accepting = True
        self._processing = False
        self._received_frames = 0
        self._processed_frames = 0
        self._dropped_frames = 0
        self._last_latency_ms: float | None = None
        self._last_submitted_at: float | None = None
        self._last_completed_at: float | None = None
        self._last_error = ""
        self._recent_completions: deque[float] = deque(maxlen=12)
        # 最近一次成功分析返回的完整指标；异常时保留旧指标但状态会变为 degraded。
        self._last_metrics: Dict[str, Any] = {}

    def start(self) -> None:
        # 重复调用 start 不会创建第二个线程。
        with self._lock:
            if self._thread is not None:
                return
            self._thread = threading.Thread(
                target=self._run,
                name=f"{self._thread_name}-{self.analyzer.session_id or 'new'}",
                daemon=True,
            )
            # daemon 线程不会阻止 Python 进程退出；每个会话仍应主动调用 stop。
            self._thread.start()

    def submit(self, image_bytes: bytes) -> tuple[bool, bool]:
        # 返回值的含义是 (是否接收, 是否替换/丢弃了旧帧)。
        """Replace any pending frame with the newest one and return immediately."""
        if not image_bytes:
            raise ValueError("视频帧内容为空。")

        with self._lock:
            if not self._accepting or self._stop_event.is_set():
                # 会话已停止时直接拒绝，调用方无需等待后台线程。
                return False, False

            self._received_frames += 1
            self._last_submitted_at = time.monotonic()
            replaced = False

            while True:
                try:
                    # put_nowait 永不阻塞 HTTP 请求线程。
                    self._queue.put_nowait(image_bytes)
                    break
                except queue.Full:
                    try:
                        # 队列满说明有一帧尚未处理；先移除最旧的等待帧。
                        self._queue.get_nowait()
                        self._queue.task_done()
                    except queue.Empty:
                        continue
                    self._dropped_frames += 1
                    replaced = True

            return True, replaced

    def status(self) -> Dict[str, Any]:
        # status 为接口层提供健康检查、积压情况和实时处理速度。
        with self._lock:
            completions = list(self._recent_completions)
            effective_fps = 0.0
            if len(completions) >= 2:
                # 用首尾完成时间计算最近若干帧的实际 FPS，比固定目标值更贴近真实性能。
                elapsed = completions[-1] - completions[0]
                if elapsed > 0:
                    effective_fps = round(
                        (len(completions) - 1) / elapsed,
                        1,
                    )

            now = time.monotonic()
            return {
                "state": self._state,
                "healthy": self._state not in {"degraded", "stopped"},
                "accepting": self._accepting,
                "processing": self._processing,
                "pending": self._queue.qsize() > 0,
                "received_frames": self._received_frames,
                "processed_frames": self._processed_frames,
                "dropped_frames": self._dropped_frames,
                "effective_fps": effective_fps,
                "last_latency_ms": self._last_latency_ms,
                "last_submitted_at": self._last_submitted_at,
                "last_completed_at": self._last_completed_at,
                "queue_age_seconds": (
                    # 最近提交时间与当前时间的差值，表示最新帧最多等待了多久。
                    round(max(0.0, now - self._last_submitted_at), 2)
                    if self._last_submitted_at is not None
                    else None
                ),
                "last_error": self._last_error,
            }

    def stop(self, timeout: float = 5.0) -> None:
        # 先改状态并放停止信号，再在锁外 join，避免阻塞其他状态读取。
        thread = None
        with self._lock:
            if not self._accepting and self._stop_event.is_set():
                thread = self._thread
            else:
                self._accepting = False
                self._stop_event.set()
                self._clear_pending()
                try:
                    # 停止信号也放进同一个队列，确保后台线程会从 get 中醒来。
                    self._queue.put_nowait(_STOP)
                except queue.Full:
                    pass
                self._state = "stopping"
                thread = self._thread

        if thread is not None and thread.is_alive():
            # 最多等待 timeout 秒；模型线程卡住时不应无限拖住 Web 请求。
            thread.join(timeout=max(0.0, timeout))

        with self._lock:
            if thread is None or not thread.is_alive():
                # 线程确实结束时才标记 stopped；否则保留 stopping 状态供排查。
                self._processing = False
                self._state = "stopped"
            self._last_metrics = {}

    def _run(self) -> None:
        # 该方法运行在独立线程中：先预热模型，再循环等待最新帧。
        self._set_state("warming_up")
        try:
            self.analyzer.warm_up()
        except Exception as exc:
            # 模型加载失败时记录 degraded，但线程仍继续等待提交，便于后续排查或恢复。
            self._set_error(exc, state="degraded")

        if not self._stop_event.is_set() and self._state != "degraded":
            self._set_state("ready")

        while not self._stop_event.is_set():
            try:
                # 超时等待让线程有机会定期检查停止标志。
                image_bytes = self._queue.get(timeout=0.25)
            except queue.Empty:
                continue

            if image_bytes is _STOP:
                # 每个 get 都必须有对应 task_done，队列的未完成任务计数才不会悬空。
                self._queue.task_done()
                break

            started_at = time.perf_counter()
            with self._lock:
                # 分析开始和结束都会更新 processing，status() 能看到当前是否占用模型。
                self._processing = True
                self._state = "processing"

            try:
                if not isinstance(image_bytes, bytes):
                    raise TypeError("视频帧类型无效。")
                metrics = self.vision.process_frame(
                    self.analyzer,
                    image_bytes,
                )
                completed_at = time.monotonic()
                with self._lock:
                    # 只有完整成功后才推进 processed_frames 并保存指标。
                    self._processed_frames += 1
                    self._last_metrics = dict(metrics)
                    self._last_error = ""
                    self._last_completed_at = completed_at
                    self._recent_completions.append(completed_at)
                    self._state = "ready"
            except Exception as exc:
                # 单帧失败不会结束线程；记录错误并标记 degraded，下一帧仍会尝试。
                self._set_error(exc, state="degraded")
            finally:
                latency_ms = (time.perf_counter() - started_at) * 1000.0
                with self._lock:
                    self._processing = False
                    self._last_latency_ms = round(latency_ms, 2)
                    if self._state == "degraded":
                        self._last_error = self._last_error or "视觉分析失败。"
                    else:
                        self._state = "ready"
                # 无论成功或异常，都通知队列这一帧已经处理完。
                self._queue.task_done()

        with self._lock:
            self._processing = False
            self._accepting = False
            self._state = "stopped"
            self._last_metrics = {}

    def _set_state(self, state: str) -> None:
        # 统一在锁内改状态，避免状态读取到一半时被其他线程改动。
        with self._lock:
            self._state = state

    def _set_error(self, error: Exception, *, state: str) -> None:
        # 异常文本可能为空；这种情况用异常类名，保证状态里总有可读信息。
        message = str(error).strip() or error.__class__.__name__
        with self._lock:
            self._last_error = message
            self._state = state

    def _clear_pending(self) -> None:
        # 停止时清空尚未处理的图片；这些帧已经过时，不再分析。
        while True:
            try:
                item = self._queue.get_nowait()
                self._queue.task_done()
                if item is not _STOP:
                    self._dropped_frames += 1
            except queue.Empty:
                return
