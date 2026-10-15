from __future__ import annotations

# InterviewVideoAnalyzer 是单个面试视频会话的总协调器。
# 它只让 MediaPipe Face Mesh 跑一遍，然后把同一组关键点同时交给：
# - ExpressionAnalyzer：表情、眨眼和校准；
# - AttentionAnalyzer：头部姿态、视线和注意力。
# 因此模型推理不会重复执行两遍。

import json
import threading
import time
from collections import deque
from pathlib import Path
from typing import Any, Deque, Dict, List, Optional

import cv2
import numpy as np

from backend.app.config import Config
from backend.app.services.vision.attention_analyzer import AttentionAnalyzer
from backend.app.services.vision.expression_analyzer import ExpressionAnalyzer


class InterviewVideoAnalyzer:
    # 上层每次提交新输入时，实际调用的就是本类 process_frame。
    '''Aggregate expression and attention metrics for one interview session.'''

    def __init__(
        self,
        target_fps: float = 8.0,
        snapshot_interval_seconds: float = 2.0,
        min_valid_face_frames: int = 15,
    ) -> None:
        # 目标 FPS 限制在 1~10，避免浏览器发送太快导致 CPU 长期满载。
        self.target_fps = max(1.0, min(10.0, float(target_fps)))
        self.sample_interval = 1.0 / self.target_fps
        self.snapshot_interval_seconds = max(
            0.5,
            float(snapshot_interval_seconds),
        )
        self.min_valid_face_frames = max(1, int(min_valid_face_frames))

        # 后台线程和接口线程都会访问本实例，所有累计状态由这把锁保护。
        self._lock = threading.RLock()
        self._face_mesh: Any = None
        self._closed = False
        self.active = False
        self.session_id = ''
        self.session_started_at: Optional[float] = None
        self.last_processed_at: Optional[float] = None
        self.last_snapshot_at: Optional[float] = None
        self.last_face_at: Optional[float] = None
        self.missing_faces_since: Optional[float] = None

        self.processed_frames = 0
        self.face_frames = 0
        self.missing_face_frames = 0
        self.missing_face_episodes = 0
        self.missing_face_duration_seconds = 0.0
        self.snapshots: List[Dict[str, Any]] = []
        self.live_trend: Deque[Dict[str, Any]] = deque(maxlen=60)
        self.events: List[Dict[str, Any]] = []
        self.latest_metrics: Dict[str, Any] = {}
        # 两个子分析器各自维护自己的滑动窗口和统计，由总分析器统一调用。
        self.expression_analyzer = ExpressionAnalyzer()
        self.attention_analyzer = AttentionAnalyzer()

    def start_session(self, session_id: Optional[str] = None) -> None:
        # 开始新会话会重置所有统计，但保留已经加载的 Face Mesh 模型。
        '''Reset all aggregate metrics while retaining the loaded model.'''
        with self._lock:
            now = time.monotonic()
            self.session_id = session_id or time.strftime('%Y%m%d_%H%M%S')
            self.session_started_at = now
            self.last_processed_at = None
            # 置空后，第一帧会立即生成快照。
            # Capture the first analyzed frame immediately as a baseline.
            self.last_snapshot_at = None
            self.last_face_at = None
            self.missing_faces_since = None
            self.processed_frames = 0
            self.face_frames = 0
            self.missing_face_frames = 0
            self.missing_face_episodes = 0
            self.missing_face_duration_seconds = 0.0
            self.snapshots = []
            self.live_trend.clear()
            self.events = []
            # 创建新的子分析器，清除上一场面试的校准基线和事件。
            self.expression_analyzer = ExpressionAnalyzer()
            self.attention_analyzer = AttentionAnalyzer()
            self.active = True
            self.latest_metrics = {
                # 会话刚开始还没有帧，先向接口返回结构完整的占位指标。
                'session_active': True,
                'face_detected': False,
                'processed_frames': 0,
                'expression': '校准中',
                'expression_confidence': 0.0,
                'expression_score': None,
                'expression_available': False,
                'attention_state': 'unknown',
                'attention_score': None,
                'attention_available': False,
                'calibration_progress': 0.0,
                'pitch': 0.0,
                'yaw': 0.0,
                'roll': 0.0,
                'gaze_x': 0.0,
                'looking_center': False,
                'attention_rate': 0.0,
                'distraction_count': 0,
                'blink_count': 0,
                'expression_changes': 0,
                'video_score_available': False,
                'issues': [],
                'trend': [],
            }

    def should_process(self, now: Optional[float] = None) -> bool:
        # 调用方可据此抽样，避免处理超过 target_fps 的帧。
        timestamp = time.monotonic() if now is None else now
        with self._lock:
            if not self.active or self._closed:
                return False
            if self.last_processed_at is None:
                # 第一帧总应处理。
                return True
            return timestamp - self.last_processed_at >= self.sample_interval

    def warm_up(self) -> None:
        # 模型加载通常较慢，放在背景工作线程中执行，不阻塞每个 HTTP 请求。
        '''Load the Face Mesh model without blocking an HTTP request thread.'''
        with self._lock:
            if self._closed:
                raise RuntimeError('当前视觉分析会话已关闭。')
            self._ensure_face_mesh()

    def process_frame(
        self,
        frame_bgr: np.ndarray,
        timestamp: Optional[float] = None,
    ) -> Dict[str, Any]:
        # 一个输入 BGR 帧的分析流程都集中在这里。
        '''Run one shared Face Mesh pass and update expression/attention metrics.'''
        now = time.monotonic() if timestamp is None else timestamp
        with self._lock:
            if not self.active or self._closed:
                # 会话未开始或已关闭时直接返回最后指标，行为可预测且不抛线程异常。
                return dict(self.latest_metrics)

            face_mesh = self._ensure_face_mesh()
            # MediaPipe 期望 RGB；标记 numpy 数组只读可避免底层意外修改调用方图像。
            rgb_frame = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
            rgb_frame.flags.writeable = False
            results = face_mesh.process(rgb_frame)
            self.processed_frames += 1
            self.last_processed_at = now

            face_detected = bool(results.multi_face_landmarks)
            if face_detected:
                # 检测器被限制为最多一张脸，因此只取第一个结果。
                face_landmarks = results.multi_face_landmarks[0]
                self.face_frames += 1
                self.last_face_at = now
                self._close_missing_face_episode(now)

                # 表情和注意力共享同一次 Face Mesh 结果，避免重复推理。
                expression_metrics = self.expression_analyzer.process_landmarks(
                    face_landmarks,
                    frame_bgr.shape,
                    timestamp=now,
                )
                attention_metrics = self.attention_analyzer.process_landmarks(
                    face_landmarks,
                    frame_bgr.shape,
                    timestamp=now,
                )
                self.latest_metrics = self._live_metrics_from(
                    expression_metrics,
                    attention_metrics,
                    now,
                )
            else:
                # 从“有人脸”变为“无人脸”时只记一次事件，连续丢帧不会重复计数。
                self.missing_face_frames += 1
                if self.missing_faces_since is None:
                    self.missing_faces_since = now
                    self.missing_face_episodes += 1
                    self._append_event(
                        now,
                        'face',
                        'lost',
                        '未检测到人脸',
                    )
                self.latest_metrics = self._live_metrics_from(
                    {}, {}, now, face_detected=False
                )

            self._capture_snapshot(now, face_detected)
            # 快照是低频报告数据，trend 是高频前端曲线数据，两者更新频率不同。
            self._capture_live_trend(now, face_detected)
            return dict(self.latest_metrics)

    def stop_session(self, now: Optional[float] = None) -> Dict[str, Any]:
        # 结束前先关闭可能仍在进行的“无人脸”事件，再汇总最终报告。
        '''Stop sampling and return a final compact summary.'''
        timestamp = time.monotonic() if now is None else now
        with self._lock:
            self._close_missing_face_episode(timestamp)
            self.active = False
            self.latest_metrics['session_active'] = False
            return self.build_summary(timestamp)

    def get_live_metrics(self) -> Dict[str, Any]:
        # 在最新逐帧指标中补充会话持续时间、人脸检测率和最近趋势。
        with self._lock:
            metrics = dict(self.latest_metrics)
            if self.session_started_at is not None:
                metrics['duration_seconds'] = round(
                    max(0.0, time.monotonic() - self.session_started_at),
                    1,
                )
            metrics['face_detection_rate'] = self._face_detection_rate()
            metrics['missing_face_episodes'] = self.missing_face_episodes
            metrics['processed_frames'] = self.processed_frames
            metrics['snapshot_count'] = len(self.snapshots)
            metrics['trend'] = list(self.live_trend)
            metrics['trend_count'] = len(self.live_trend)
            return metrics

    def build_summary(self, now: Optional[float] = None) -> Dict[str, Any]:
        # 报告只保存数值和事件，不保存原始视频帧，降低隐私风险。
        '''Return a compact report without storing original video frames.'''
        timestamp = time.monotonic() if now is None else now
        with self._lock:
            expression_summary = self.expression_analyzer.build_summary(timestamp)
            attention_summary = self.attention_analyzer.build_summary(timestamp)
            # 只有校准完成且分析过帧，表情结果才视为可用。
            expression_available = bool(
                expression_summary['calibrated']
                and expression_summary['analyzed_frames'] > 0
            )
            attention_available = bool(
                # 至少有一个人脸帧成功估算过注意力。
                attention_summary['total_analyzed_frames'] > 0
            )
            raw_attention_score = attention_summary['score']
            attention_coverage_factor = self._attention_coverage_factor()
            # 人脸出现得越少，注意力总分打折越多，防止少量有效帧给出虚高分。
            adjusted_attention_score = round(
                raw_attention_score * attention_coverage_factor,
                1,
            )
            video_available = bool(
                # 两种指标都有效，并且有效人脸帧达到业务最低要求，视频分才可用。
                expression_available
                and attention_available
                and self.face_frames >= self.min_valid_face_frames
            )
            duration = 0.0
            if self.session_started_at is not None:
                duration = max(0.0, timestamp - self.session_started_at)

            combined_events = self._combined_events(attention_summary)
            # 已关闭的离帧时长保存在累计字段中；若当前仍离帧，再补上尚未结束的一段。
            missing_face_duration = self.missing_face_duration_seconds
            if self.missing_faces_since is not None:
                missing_face_duration += max(
                    0.0,
                    timestamp - self.missing_faces_since,
                )

            return {
                # version 便于报告消费者识别数据结构版本。
                'version': 1,
                'session_id': self.session_id,
                'session_active': self.active,
                'duration_seconds': round(duration, 1),
                'target_fps': self.target_fps,
                'processed_frames': self.processed_frames,
                'face_frames': self.face_frames,
                'missing_face_frames': self.missing_face_frames,
                'face_detection_rate': self._face_detection_rate(),
                'missing_face_episodes': self.missing_face_episodes,
                'missing_face_duration_seconds': round(
                    missing_face_duration,
                    2,
                ),
                'expression_available': expression_available,
                'attention_available': attention_available,
                'video_available': video_available,
                'expression_score': (
                    expression_summary['score']
                    if expression_available
                    else None
                ),
                'attention_score': (
                    adjusted_attention_score
                    if attention_available
                    else None
                ),
                'attention_score_raw': (
                    raw_attention_score if attention_available else None
                ),
                'attention_coverage_factor': attention_coverage_factor,
                'expression': expression_summary,
                'attention': attention_summary,
                'event_count': len(combined_events),
                'events': combined_events,
                'snapshots': list(self.snapshots),
                'privacy': {
                    # 这是对报告内容的行为说明，也提醒调用方不要在此处写入原图。
                    'stores_video_frames': False,
                    'stores_face_images': False,
                    'sampling_interval_seconds': self.snapshot_interval_seconds,
                },
            }

    def save_report(self, summary: Optional[Dict[str, Any]] = None) -> Path:
        # 调用方可以传已经 build 好的 summary，也可以让本方法现场生成。
        '''Persist only the lightweight summary and sampled state changes.'''
        report = summary or self.build_summary()
        output_dir = Config.REPORT_DIR / 'video'
        output_dir.mkdir(parents=True, exist_ok=True)
        suffix = report.get('session_id') or time.strftime('%Y%m%d_%H%M%S')
        output_path = output_dir / f'interview_metrics_{suffix}.json'
        # 报告是人类可读 JSON，ensure_ascii=False 让中文按原字符写入。
        output_path.write_text(
            json.dumps(report, ensure_ascii=False, indent=2),
            encoding='utf-8',
        )
        return output_path

    def close(self) -> None:
        # 主动释放 MediaPipe 原生资源；关闭后 process_frame 不再工作。
        with self._lock:
            if self._face_mesh is not None:
                self._face_mesh.close()
                self._face_mesh = None
            self._closed = True
            self.active = False

    def _ensure_face_mesh(self) -> Any:
        # 延迟加载 mediapipe，应用启动时不必立刻初始化视觉模型。
        if self._face_mesh is not None:
            return self._face_mesh
        try:
            import mediapipe as mp
        except ImportError as exc:
            raise RuntimeError(
                '未安装 mediapipe，请执行 pip install -r requirements.txt'
            ) from exc

        self._face_mesh = mp.solutions.face_mesh.FaceMesh(
            # 视频模式会跨帧跟踪；只追踪一张脸并启用虹膜细化点。
            static_image_mode=False,
            max_num_faces=1,
            refine_landmarks=True,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5,
        )
        return self._face_mesh

    def _live_metrics_from(
        self,
        expression_metrics: Dict[str, Any],
        attention_metrics: Dict[str, Any],
        now: float,
        face_detected: bool = True,
    ) -> Dict[str, Any]:
        # 从两个子分析器的实时结果拼出前端需要的统一指标字典。
        expression_live = self.expression_analyzer.get_live_metrics()
        attention_live = self.attention_analyzer.get_live_metrics()
        expression_available = bool(
            expression_live['calibration_progress'] >= 1.0
            and self.expression_analyzer.analyzed_frames > 0
        )
        attention_available = bool(attention_metrics.get('detected'))
        instant_attention_score = attention_live.get(
            'attention_score_instant',
            attention_live['attention_score'],
        )
        attention_coverage_factor = self._attention_coverage_factor()
        # 视频综合分可用性同时受表情、注意力和最低人脸帧数约束。
        video_available = bool(
            expression_available
            and attention_available
            and self.face_frames >= self.min_valid_face_frames
        )
        return {
            # last_updated_at 使用 monotonic 时间，适合内部计算但不应当作墙上时钟展示。
            'session_active': self.active,
            'face_detected': face_detected,
            'last_updated_at': now,
            'processed_frames': self.processed_frames,
            'expression': expression_metrics.get(
                'expression', expression_live['expression']
            ),
            'expression_confidence': expression_metrics.get(
                'confidence', expression_live['expression_confidence']
            ),
            'expression_score': (
                expression_live['expression_score']
                if expression_available
                else None
            ),
            'expression_available': expression_available,
            'attention_state': attention_live['state'],
            'attention_score': (
                # 展示分经过人脸覆盖率修正；raw 字段保留原始注意力分。
                round(
                    attention_live['attention_score']
                    * attention_coverage_factor,
                    1,
                )
                if attention_available
                else None
            ),
            'attention_score_raw': (
                instant_attention_score if attention_available else None
            ),
            'attention_coverage_factor': attention_coverage_factor,
            'attention_available': attention_available,
            'calibration_progress': expression_live['calibration_progress'],
            'pitch': attention_metrics.get('pitch', 0.0),
            'yaw': attention_metrics.get('yaw', 0.0),
            'roll': attention_metrics.get('roll', 0.0),
            'gaze_x': attention_metrics.get('gaze_x', 0.0),
            'looking_center': attention_metrics.get(
                'looking_center', False
            ),
            'attention_rate': attention_live['attention_rate'],
            'distraction_count': attention_live['distraction_count'],
            'attention_state_changes': attention_live['state_changes'],
            'blink_count': expression_live['blink_count'],
            'expression_changes': expression_live['expression_changes'],
            'video_score_available': video_available,
            'issues': list(attention_metrics.get('issues', [])),
        }

    def _capture_snapshot(self, now: float, face_detected: bool) -> None:
        # 快照用于最终报告，默认每 2 秒保存一次，而不是每一帧都追加。
        if self.last_snapshot_at is None:
            should_capture = True
        else:
            should_capture = (
                now - self.last_snapshot_at >= self.snapshot_interval_seconds
            )
        if not should_capture:
            return

        self.last_snapshot_at = now
        # 从子分析器复制当前值，之后再构造一个精简快照。
        expression = self.expression_analyzer.get_live_metrics()
        attention = self.attention_analyzer.get_live_metrics()
        expression_available = bool(
            self.expression_analyzer.is_calibrated
            and self.expression_analyzer.analyzed_frames > 0
        )
        attention_available = bool(attention.get('detected'))
        self.snapshots.append(
            {
                'elapsed_seconds': round(
                    now - (self.session_started_at or now),
                    2,
                ),
                'face_detected': face_detected,
                'expression': expression['expression'],
                'expression_score': expression['expression_score'],
                'expression_available': expression_available,
                'attention_state': attention['state'],
                'attention_score': attention['attention_score'],
                'attention_available': attention_available,
                'attention_rate': attention['attention_rate'],
                'pitch': attention['pitch'],
                'yaw': attention['yaw'],
                'roll': attention['roll'],
                'gaze_x': attention['gaze_x'],
                'blink_count': expression['blink_count'],
            }
        )

    def _capture_live_trend(self, now: float, face_detected: bool) -> None:
        # live_trend 只保留最近 60 个有效点，给前端画轻量曲线。
        """Store high-frequency points without changing report snapshots."""
        expression_score = self._optional_score(
            self.latest_metrics.get('expression_score')
        )
        attention_score = self._optional_score(
            self.latest_metrics.get('attention_score')
        )
        if expression_score is None and attention_score is None:
            # 两路都没有有效分时不增加空点。
            return

        self.live_trend.append(
            {
                'elapsed_seconds': round(
                    now - (self.session_started_at or now),
                    2,
                ),
                'face_detected': face_detected,
                'expression_score': expression_score,
                'attention_score': attention_score,
            }
        )

    def _append_event(
        self,
        now: float,
        source: str,
        state: str,
        detail: str,
    ) -> None:
        # 将人脸丢失、注意力变化等事件写成统一结构，后续按时间排序。
        self.events.append(
            {
                'elapsed_seconds': round(
                    now - (self.session_started_at or now),
                    2,
                ),
                'source': source,
                'state': state,
                'detail': detail,
            }
        )

    def _close_missing_face_episode(self, now: float) -> None:
        # missing_faces_since 为 None 表示当前人脸正常，无需关闭事件。
        if self.missing_faces_since is None:
            return
        duration = max(0.0, now - self.missing_faces_since)
        # 把人脸离开累计到总时长，并记录一次 recovered 事件。
        self.missing_face_duration_seconds += duration
        self._append_event(
            now,
            'face',
            'recovered',
            f'人脸恢复，离帧 {duration:.1f} 秒',
        )
        self.missing_faces_since = None

    def _combined_events(
        self,
        attention_summary: Dict[str, Any],
    ) -> List[Dict[str, Any]]:
        # 合并总分析器、注意力子分析器和表情子分析器的事件，并标注来源。
        combined = [dict(event) for event in self.events]
        for event in attention_summary['events']:
            item = dict(event)
            item['source'] = 'attention'
            combined.append(item)
        for event in self.expression_analyzer.expression_events:
            item = dict(event)
            item['source'] = 'expression'
            item.setdefault('state', item.get('expression', 'unknown'))
            combined.append(item)
        return sorted(
            # 所有事件统一按会话开始后的秒数升序排列。
            combined,
            key=lambda item: float(item.get('elapsed_seconds', 0.0)),
        )

    def _face_detection_rate(self) -> float:
        # 人脸检测率 = 检测到人脸的帧数 / 总处理帧数。
        if self.processed_frames == 0:
            return 0.0
        return round(self.face_frames / self.processed_frames * 100.0, 1)

    def _attention_coverage_factor(self) -> float:
        # 覆盖率系数范围为 0.75~1.0：无人脸时也不把有效注意力分直接清零，但会明显降权。
        if self.processed_frames == 0:
            return 0.0
        coverage = min(1.0, self.face_frames / self.processed_frames)
        return round(0.75 + 0.25 * coverage, 4)

    @staticmethod
    def _optional_score(value: Any) -> Optional[float]:
        # 将 None、无法转数字和 NaN 统一视为“无分数”，其余值限制到 0~100。
        if value is None:
            return None
        try:
            number = float(value)
        except (TypeError, ValueError):
            return None
        if number != number:
            return None
        return round(max(0.0, min(100.0, number)), 1)
