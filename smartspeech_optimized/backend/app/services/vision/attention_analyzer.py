from __future__ import annotations

# AttentionAnalyzer 接收 MediaPipe Face Mesh 的关键点，估算三件事：
# 1. 头部上下/左右/倾斜角度；2. 视线是否偏中心；3. 一个面试会话的注意力统计。
# 它不直接处理图片，也不负责线程调度，只维护当前会话的累计数据。

import time
from collections import deque
from typing import Any, Deque, Dict, List, Optional, Sequence, Tuple

import cv2
import numpy as np


class AttentionAnalyzer:
    # 本类假设调用方只检测一张人脸，且关键点属于同一个 MediaPipe Face Mesh。
    '''Estimate head pose, gaze direction, and interview attention.'''

    # solvePnP 使用的六个 3D 人脸模型点，顺序与下面的关键点一一对应。
    FACE_INDICES = (1, 152, 33, 263, 61, 291)
    # MediaPipe 左右眼虹膜各四个点的索引。
    LEFT_IRIS = (474, 475, 476, 477)
    RIGHT_IRIS = (469, 470, 471, 472)

    def __init__(self, history_size: int = 30) -> None:
        # 通用三维人脸模板坐标，用于根据二维图像关键点反推头部姿态。
        self.model_points = np.array(
            [
                (0.0, 0.0, 0.0),
                (0.0, -330.0, -65.0),
                (-225.0, 170.0, -135.0),
                (225.0, 170.0, -135.0),
                (-150.0, -150.0, -125.0),
                (150.0, -150.0, -125.0),
            ],
            dtype=np.float64,
        )
        # 超过这些角度或视线阈值就扣分；单位中角度为度，gaze 为归一化偏移。
        self.yaw_threshold = 20.0
        self.pitch_threshold = 15.0
        self.roll_threshold = 15.0
        self.gaze_threshold = 0.15
        # 指数平滑系数：新分数占 24%，历史平滑值占 76%，让实时数值更稳定。
        self.score_smoothing_alpha = 0.24

        # 只保留最近若干帧，长期均值仍由累计计数计算，避免历史列表无限增长。
        self.attention_history: Deque[float] = deque(maxlen=history_size)
        self.gaze_history: Deque[float] = deque(maxlen=history_size)
        self.total_frames = 0
        self.attentive_frames = 0
        self.distracted_frames = 0
        self.events: List[Dict[str, Any]] = []

        # monotonic 时钟只用于计算时长，不受系统时间校准影响。
        self.session_started_at = time.monotonic()
        self.current_state = 'unknown'
        self.state_started_at = self.session_started_at
        self.current_score = 0.0
        self.smoothed_score: Optional[float] = None
        self.distraction_count = 0
        # 最近一次可用的实时指标；单帧姿态失败时可重新返回上一次结果。
        self.last_metrics = self._empty_metrics()

    def process_landmarks(
        self,
        face_landmarks: Any,
        image_shape: Sequence[int],
        timestamp: Optional[float] = None,
    ) -> Dict[str, Any]:
        # 主入口：每个检测到人脸的视觉帧都会调用一次。
        '''Analyze attention from landmarks produced by a shared Face Mesh.'''
        now = time.monotonic() if timestamp is None else timestamp
        self.total_frames += 1

        success, _, _, pitch, yaw, roll = self.get_head_pose(
            face_landmarks,
            image_shape,
        )
        if not success:
            # A single pose-estimation miss should not flash the live score to
            # zero. Reuse the last valid result until a new pose is available.
            if self.smoothed_score is not None:
                # 保留上次指标，只把 detected 标记为 True；防止偶发失败让前端分数瞬间归零。
                self.last_metrics = dict(self.last_metrics)
                self.last_metrics['detected'] = True
            else:
                self.last_metrics = self._empty_metrics(detected=False)
            return dict(self.last_metrics)

        gaze_x, gaze_y, looking_center = self.estimate_gaze_direction(
            face_landmarks,
            image_shape,
        )
        is_attentive, score, issues = self.evaluate_attention(
            pitch,
            yaw,
            roll,
            looking_center,
        )

        # 原始分数反映当前帧，平滑分数供界面实时显示；两者分别保留在结果里。
        instant_score = float(score)
        if self.smoothed_score is None:
            self.smoothed_score = instant_score
        else:
            self.smoothed_score = (
                self.score_smoothing_alpha * instant_score
                + (1.0 - self.score_smoothing_alpha) * self.smoothed_score
            )
        self.current_score = instant_score
        # 历史、帧计数和状态切换都基于原始判定，平滑只影响展示分数。
        self.attention_history.append(float(score))
        if is_attentive:
            self.attentive_frames += 1
        else:
            self.distracted_frames += 1
        self._update_state(is_attentive, score, now)

        self.last_metrics = {
            'detected': True,
            'pitch': round(float(pitch), 1),
            'yaw': round(float(yaw), 1),
            'roll': round(float(roll), 1),
            'gaze_x': round(float(gaze_x), 3),
            'gaze_y': round(float(gaze_y), 3),
            'looking_center': bool(looking_center),
            'is_attentive': bool(is_attentive),
            'attention_score': round(float(self.smoothed_score), 1),
            'attention_score_instant': float(score),
            'issues': list(issues),
            'state': 'attentive' if is_attentive else 'distracted',
        }
        # 返回副本，调用方修改字典不会污染分析器内部状态。
        return dict(self.last_metrics)

    def get_head_pose(
        self,
        face_landmarks: Any,
        image_shape: Sequence[int],
    ) -> Tuple[bool, Optional[np.ndarray], Optional[np.ndarray], float, float, float]:
        # 把 MediaPipe 的归一化关键点乘以宽高，换成图像像素坐标。
        '''Return pitch, yaw, and roll using the original solvePnP model.'''
        height, width = image_shape[:2]
        image_points = np.asarray(
            [
                (
                    face_landmarks.landmark[index].x * width,
                    face_landmarks.landmark[index].y * height,
                )
                for index in self.FACE_INDICES
            ],
            dtype=np.float64,
        )
        camera_matrix = np.asarray(
            # 简化相机内参：焦距约等于图像宽度，主点位于图像中心。
            [
                [width, 0.0, width / 2.0],
                [0.0, width, height / 2.0],
                [0.0, 0.0, 1.0],
            ],
            dtype=np.float64,
        )
        success, rotation_vector, translation_vector = cv2.solvePnP(
            self.model_points,
            image_points,
            camera_matrix,
            np.zeros((4, 1), dtype=np.float64),
            flags=cv2.SOLVEPNP_ITERATIVE,
        )
        if not success:
            # 姿态求解失败时返回失败标志，调用方会复用上一帧结果。
            return False, None, None, 0.0, 0.0, 0.0

        rotation_matrix, _ = cv2.Rodrigues(rotation_vector)
        # 坐标轴修正后分解旋转矩阵，提取欧拉角；顺序对应 pitch、yaw、roll。
        rotation_matrix = rotation_matrix @ np.diag([1.0, -1.0, -1.0])
        projection = np.hstack((rotation_matrix, np.zeros((3, 1))))
        euler = cv2.decomposeProjectionMatrix(projection)[6].flatten()
        pitch, yaw, roll = (float(euler[0]), float(euler[1]), float(euler[2]))
        return True, rotation_vector, translation_vector, pitch, yaw, roll

    def estimate_gaze_direction(
        self,
        face_landmarks: Any,
        image_shape: Sequence[int],
    ) -> Tuple[float, float, bool]:
        # 通过虹膜中心相对眼眶中心的位置估算左右视线偏移。
        '''Estimate horizontal gaze offset with temporal smoothing.'''
        height, width = image_shape[:2]

        def single_eye_gaze(
            outer_index: int,
            inner_index: int,
            iris_indices: Sequence[int],
        ) -> float:
            # 返回单眼归一化偏移：0 表示虹膜在眼眶水平中心。
            iris_points = np.asarray(
                [
                    (
                        face_landmarks.landmark[index].x * width,
                        face_landmarks.landmark[index].y * height,
                    )
                    for index in iris_indices
                ],
                dtype=np.float64,
            )
            iris_center_x = float(np.mean(iris_points[:, 0]))
            outer = face_landmarks.landmark[outer_index]
            inner = face_landmarks.landmark[inner_index]
            eye_center_x = (outer.x + inner.x) / 2.0 * width
            eye_width = abs(inner.x - outer.x) * width
            if eye_width <= 0:
                return 0.0
            # 除以半个眼宽，降低不同人脸大小和相机距离带来的比例差异。
            return (iris_center_x - eye_center_x) / (eye_width / 2.0)

        # 双眼结果取平均，减少单眼关键点抖动带来的误判。
        left_gaze = single_eye_gaze(33, 133, self.LEFT_IRIS)
        right_gaze = single_eye_gaze(263, 362, self.RIGHT_IRIS)
        gaze_x = float(np.mean([left_gaze, right_gaze]))
        self.gaze_history.append(gaze_x)
        # looking_center 使用短期平均偏移判断，而返回的 gaze_x 仍是本帧原始值。
        smoothed = float(np.mean(self.gaze_history))
        return gaze_x, 0.0, abs(smoothed) < self.gaze_threshold

    def evaluate_attention(
        self,
        pitch: float,
        yaw: float,
        roll: float,
        looking_center: bool,
    ) -> Tuple[bool, int, List[str]]:
        # 每帧从 100 分开始，按偏转程度扣分；70 分及以上判为注意力集中。
        '''Apply the source project's thresholds and deduction rules.'''
        issues: List[str] = []
        score = 100.0

        if abs(yaw) > self.yaw_threshold:
            # 扣分最多 30 分，避免一次极端角度把总分直接扣成负数。
            issues.append(f'\u5934\u90e8\u504f\u8f6c({yaw:.1f}\N{DEGREE SIGN})')
            score -= min(30.0, abs(yaw) - self.yaw_threshold)
        if abs(pitch) > self.pitch_threshold:
            issues.append(f'\u5934\u90e8\u4fef\u4ef0({pitch:.1f}\N{DEGREE SIGN})')
            score -= min(25.0, abs(pitch) - self.pitch_threshold)
        if abs(roll) > self.roll_threshold:
            issues.append(f'\u5934\u90e8\u503e\u659c({roll:.1f}\N{DEGREE SIGN})')
            score -= min(20.0, abs(roll) - self.roll_threshold)
        if not looking_center:
            issues.append('\u89c6\u7ebf\u504f\u79fb')
            score -= 25.0

        score = int(max(0.0, min(100.0, score)))
        return score >= 70, score, issues

    def get_live_metrics(self) -> Dict[str, Any]:
        # 在逐帧指标上追加累计统计，供前端实时接口读取。
        metrics = dict(self.last_metrics)
        metrics.update(
            {
                'attention_average': self._average_score(),
                'attention_rate': self._attention_rate(),
                'distraction_count': self.distraction_count,
                'state_changes': len(self.events),
            }
        )
        return metrics

    def build_summary(self, now: Optional[float] = None) -> Dict[str, Any]:
        # 结束时生成会议总结；当前状态尚未记录结束，因此要临时补一个 open_event。
        timestamp = time.monotonic() if now is None else now
        # 复制事件列表，避免为了生成报告修改分析器内部的 events。
        events = list(self.events)
        active_duration = 0.0
        if self.current_state != 'unknown':
            active_duration = max(0.0, timestamp - self.state_started_at)

        distraction_duration = sum(
            # 已关闭的分心事件有完整 duration_seconds。
            event['duration_seconds']
            for event in events
            if event['state'] == 'distracted'
        )
        if self.current_state == 'distracted':
            # 如果会话结束时仍处于分心状态，把尚未关闭的这一段也计入。
            distraction_duration += active_duration

        if self.current_state != 'unknown':
            # 报告中的最后一条事件代表“当前状态从何时开始、持续了多久”。
            events.append(
                {
                    'elapsed_seconds': round(
                        self.state_started_at - self.session_started_at,
                        2,
                    ),
                    'state': self.current_state,
                    'duration_seconds': round(active_duration, 2),
                    'score': self.current_score,
                    'open_event': True,
                }
            )

        return {
            # score 是最近窗口平均值，不是简单算术累计；attention_rate 是集中帧占比。
            'score': self._average_score(),
            'current_state': self.current_state,
            'is_attentive': self.current_state == 'attentive',
            'attention_rate': self._attention_rate(),
            'total_analyzed_frames': self.total_frames,
            'attentive_frames': self.attentive_frames,
            'distracted_frames': self.distracted_frames,
            'distraction_count': self.distraction_count,
            'distraction_duration_seconds': round(distraction_duration, 2),
            'state_change_count': len(self.events),
            'events': events,
            'thresholds': {
                'pitch': self.pitch_threshold,
                'yaw': self.yaw_threshold,
                'roll': self.roll_threshold,
                'gaze': self.gaze_threshold,
            },
            'last_metrics': dict(self.last_metrics),
        }

    def _update_state(self, is_attentive: bool, score: int, now: float) -> None:
        # 状态没有改变时不创建事件，只继续累计当前状态持续时间。
        new_state = 'attentive' if is_attentive else 'distracted'
        if new_state == self.current_state:
            return

        if self.current_state != 'unknown':
            # 旧状态在切换这一刻结束，记录开始偏移和持续时长。
            self.events.append(
                {
                    'elapsed_seconds': round(
                        self.state_started_at - self.session_started_at,
                        2,
                    ),
                    'state': self.current_state,
                    'duration_seconds': round(
                        max(0.0, now - self.state_started_at),
                        2,
                    ),
                    'score': self.current_score,
                }
            )
        if new_state == 'distracted':
            # distraction_count 统计“进入分心”的次数，而不是分心帧数。
            self.distraction_count += 1
        self.current_state = new_state
        self.state_started_at = now
        self.current_score = float(score)

    def _average_score(self) -> float:
        # 没有有效数据时返回 0；有数据时使用滑动窗口平均。
        if not self.attention_history:
            return 0.0
        return round(float(np.mean(self.attention_history)), 1)

    def _attention_rate(self) -> float:
        # 注意力占比 = 集中帧数 / 已分析人脸帧数，结果是百分比。
        if self.total_frames == 0:
            return 0.0
        return round(self.attentive_frames / self.total_frames * 100.0, 1)

    @staticmethod
    def _empty_metrics(detected: bool = False) -> Dict[str, Any]:
        # 统一空指标结构，避免前端在未识别人脸时遇到缺字段。
        return {
            'detected': detected,
            'pitch': 0.0,
            'yaw': 0.0,
            'roll': 0.0,
            'gaze_x': 0.0,
            'gaze_y': 0.0,
            'looking_center': False,
            'is_attentive': False,
            'attention_score': 0.0,
            'attention_score_instant': 0.0,
            'issues': [],
            'state': 'unknown',
        }
