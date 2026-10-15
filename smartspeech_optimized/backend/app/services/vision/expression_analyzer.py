from __future__ import annotations

# ExpressionAnalyzer 不直接读取图片，而是接收 MediaPipe Face Mesh 的关键点。
# 它先把眼睛、嘴、眉毛的几何变化变成数值，再根据个人基线判断表情。
# 先校准个人的中性表情，可减少脸型差异和镜头距离造成的误判。

import time
from collections import Counter, deque
from typing import Any, Deque, Dict, Optional, Sequence, Tuple

import numpy as np


class ExpressionAnalyzer:
    # 该类维护一个面试会话的表情统计；新会话应创建新实例。
    '''UI-independent adapter for the original expression recognition model.'''

    # 这些下标来自 MediaPipe Face Mesh，分别描述眼、眉和嘴的轮廓。
    LEFT_EYE = (33, 160, 158, 133, 153, 144)
    RIGHT_EYE = (362, 385, 387, 263, 373, 380)
    LEFT_EYEBROW = (70, 63, 105, 66, 107)
    RIGHT_EYEBROW = (336, 296, 334, 293, 300)
    MOUTH_OUTER = (61, 291, 0, 17)
    MOUTH_INNER = (78, 308, 13, 14)

    def __init__(
        self,
        calibration_warmup_frames: int = 5,
        calibration_frames_required: int = 30,
    ) -> None:
        # 前几帧先丢弃，等待画面稳定；之后累计若干帧建立个人中性基线。
        self.calibration_warmup_frames = calibration_warmup_frames
        self.calibration_frames_required = calibration_frames_required
        self.EAR_THRESHOLD = 0.21
        self.SMILE_DELTA_MIN = 0.008
        self.LAUGH_MOUTH_DELTA = 0.055
        self.SURPRISE_MOUTH_DELTA = 0.075
        self.SURPRISE_EYEBROW_DELTA = 0.045
        self.CONFUSION_ASYMMETRY_DELTA = 0.055
        self.SPEECH_ACTIVITY_MIN = 0.006

        # 每个特征保留几帧历史，后续使用平均值降低单帧关键点抖动。
        self.ear_history: Deque[float] = deque(maxlen=5)
        self.smile_history: Deque[float] = deque(maxlen=5)
        self.mouth_open_history: Deque[float] = deque(maxlen=8)
        self.eyebrow_history: Deque[float] = deque(maxlen=5)
        self.eyebrow_asymmetry_history: Deque[float] = deque(maxlen=5)
        self.candidate_history: Deque[Tuple[str, float]] = deque(maxlen=5)
        # 最近 60 帧表情序列可用于调试，但正式统计使用 counts/durations。
        self.expression_history: Deque[str] = deque(maxlen=60)

        self.session_started_at = time.monotonic()
        self.last_frame_at: Optional[float] = None
        self.total_face_frames = 0
        self.analyzed_frames = 0
        self.expression_counts = {
            '中性': 0,
            '微笑': 0,
            '大笑': 0,
            '惊讶': 0,
            '困惑': 0,
            '说话': 0,
        }
        # 每个表情持续时长按相邻帧时间差累计，单位为秒。
        self.expression_durations = {key: 0.0 for key in self.expression_counts}
        self.expression_events = []
        self.feature_ranges: Dict[str, Dict[str, float]] = {}
        self.current_expression = '校准中'
        self.current_expression_confidence = 0.0
        self.expression_started_at = self.session_started_at
        # 眨眼通过“先闭眼、再睁眼”的边沿检测计数，而不是每帧都加一。
        self.blink_count = 0
        self.eye_closed = False
        self.last_blink_at = 0.0

        self.calibration_detected_frames = 0
        self.calibration_samples: Dict[str, list] = {}
        self.baseline: Dict[str, float] = {}
        self.baseline_noise: Dict[str, float] = {}
        self.is_calibrated = False
        # 构造对象时立即进入校准状态。
        self.start_calibration()

    def start_calibration(self) -> None:
        # 清空所有基线相关数据和短期滑动窗口，重新从当前用户采集中性表情。
        keys = ('ear', 'smile_ratio', 'mouth_openness', 'eyebrow_raise', 'eyebrow_asymmetry')
        self.calibration_samples = {key: [] for key in keys}
        self.baseline = {}
        self.baseline_noise = {}
        self.calibration_detected_frames = 0
        self.is_calibrated = False
        self.candidate_history.clear()
        self.ear_history.clear()
        self.smile_history.clear()
        self.mouth_open_history.clear()
        self.eyebrow_history.clear()
        self.eyebrow_asymmetry_history.clear()

    def get_calibration_progress(self) -> float:
        # 校准进度以 ear 样本数量为准；所有特征在每帧同时采样，因此数量一致。
        if self.is_calibrated:
            return 1.0
        sample_count = len(self.calibration_samples.get('ear', []))
        return min(1.0, sample_count / float(self.calibration_frames_required))

    def process_landmarks(
        self,
        face_landmarks: Any,
        image_shape: Sequence[int],
        timestamp: Optional[float] = None,
    ) -> Dict[str, Any]:
        # 主入口：输入一张脸的全部 MediaPipe 关键点，返回本帧表情特征。
        '''Analyze one detected face from a shared Face Mesh result.'''
        now = time.monotonic() if timestamp is None else timestamp
        # dt 用于按真实经过时间累计表情持续时间，而不是假设固定 FPS。
        dt = self._elapsed_delta(now)
        self.total_face_frames += 1

        # _coords 把归一化坐标转换成像素坐标，方便用欧氏距离计算几何比例。
        left_eye = self._coords(face_landmarks, self.LEFT_EYE, image_shape)
        right_eye = self._coords(face_landmarks, self.RIGHT_EYE, image_shape)
        mouth_outer = self._coords(face_landmarks, self.MOUTH_OUTER, image_shape)
        mouth_inner = self._coords(face_landmarks, self.MOUTH_INNER, image_shape)
        left_eyebrow = self._coords(face_landmarks, self.LEFT_EYEBROW, image_shape)
        right_eyebrow = self._coords(face_landmarks, self.RIGHT_EYEBROW, image_shape)

        # EAR 是眼睛纵横比，眼睛越闭合，该值通常越小。
        ear = (self.calculate_ear(left_eye) + self.calculate_ear(right_eye)) / 2.0
        self.ear_history.append(ear)
        # 嘴角上扬程度经过嘴宽归一化，降低远近和脸型尺度的影响。
        smile_ratio, mouth_curve = self.calculate_smile_features(mouth_outer)
        self.smile_history.append(smile_ratio)
        mouth_openness = self.calculate_mouth_openness(mouth_inner)
        self.mouth_open_history.append(mouth_openness)
        # 嘴部开口变化的标准差用于识别“持续说话”而不是单纯张嘴。
        mouth_activity = float(np.std(list(self.mouth_open_history)))

        left_raise = self.calculate_eyebrow_raise(left_eyebrow, left_eye)
        right_raise = self.calculate_eyebrow_raise(right_eyebrow, right_eye)
        eyebrow_raise = (left_raise + right_raise) / 2.0
        eyebrow_asymmetry = abs(left_raise - right_raise)
        self.eyebrow_history.append(eyebrow_raise)
        self.eyebrow_asymmetry_history.append(eyebrow_asymmetry)

        features: Dict[str, Any] = {
            # 同时保留平滑值和 raw 原始值；校准时使用 raw，实时展示使用平滑值。
            'detected': True,
            'ear': float(np.mean(self.ear_history)),
            'raw_ear': float(ear),
            'smile_ratio': float(np.mean(self.smile_history)),
            'raw_smile_ratio': float(smile_ratio),
            'mouth_curve': float(mouth_curve),
            'mouth_openness': float(np.mean(self.mouth_open_history)),
            'raw_mouth_openness': float(mouth_openness),
            'mouth_activity': mouth_activity,
            'eyebrow_raise': float(np.mean(self.eyebrow_history)),
            'raw_eyebrow_raise': float(eyebrow_raise),
            'eyebrow_asymmetry': float(np.mean(self.eyebrow_asymmetry_history)),
            'raw_eyebrow_asymmetry': float(eyebrow_asymmetry),
        }

        self._detect_blink(features['ear'], now)
        if not self.is_calibrated:
            # 校准期只采集中性样本，不参与正式表情分布和评分。
            if self._update_calibration(features):
                expression, confidence = '中性', 0.60
            else:
                expression, confidence = '校准中', self.get_calibration_progress()
        else:
            expression, confidence = self._recognize_expression(features)
            # 只有校准完成后，统计才把这一帧视为正式分析帧。
            self.analyzed_frames += 1
            self._update_expression_state(expression, confidence, now, dt)
            self.expression_history.append(expression)

        self.current_expression = expression
        self.current_expression_confidence = float(confidence)
        features.update(
            {
                # 以下字段把识别结果和当前进度拼接到返回值，供上层统一汇总。
                'expression': expression,
                'confidence': float(confidence),
                'expression_score': self.calculate_expression_score(),
                'calibration_progress': self.get_calibration_progress(),
            }
        )
        return features

    def get_live_metrics(self) -> Dict[str, Any]:
        # 构造实时接口需要的精简结果。
        score = self.calculate_expression_score()
        return {
            'expression': self.current_expression,
            'expression_confidence': round(self.current_expression_confidence, 2),
            'expression_score': score,
            'blink_count': self.blink_count,
            'expression_changes': len(self.expression_events),
            'calibration_progress': round(self.get_calibration_progress(), 2),
        }

    def calculate_ear(self, eye_coords: np.ndarray) -> float:
        # 六点眼型中，两条竖向距离表示上下眼睑开口，横向距离表示眼睛宽度。
        '''Calculate eye aspect ratio from six eye landmarks.'''
        vertical_1 = np.linalg.norm(eye_coords[1] - eye_coords[5])
        vertical_2 = np.linalg.norm(eye_coords[2] - eye_coords[4])
        horizontal = np.linalg.norm(eye_coords[0] - eye_coords[3])
        if horizontal == 0:
            # 关键点退化（眼睛宽度为 0）时返回 0，避免除以零。
            return 0.0
        return float((vertical_1 + vertical_2) / (2.0 * horizontal))

    def calculate_smile_features(
        self,
        mouth_outer_coords: np.ndarray,
    ) -> Tuple[float, float]:
        # 嘴角平均 y 与上下嘴唇中心 y 的差值可以表示嘴角相对嘴唇中线的抬升。
        '''Calculate normalized mouth-corner elevation.'''
        left_corner = mouth_outer_coords[0]
        right_corner = mouth_outer_coords[1]
        top_lip = mouth_outer_coords[2]
        bottom_lip = mouth_outer_coords[3]

        mouth_width = np.linalg.norm(right_corner - left_corner)
        lip_center_y = (top_lip[1] + bottom_lip[1]) / 2.0
        corners_avg_y = (left_corner[1] + right_corner[1]) / 2.0
        # 在图像坐标系中 y 向下为正，因此嘴角高于嘴唇中心时通常得到正数。
        mouth_curve = float(lip_center_y - corners_avg_y)
        smile_ratio = float(mouth_curve / mouth_width) if mouth_width > 0 else 0.0
        return smile_ratio, mouth_curve

    @staticmethod
    def calculate_mouth_openness(mouth_inner_coords: np.ndarray) -> float:
        # 内唇垂直距离除以水平距离，得到不依赖绝对像素尺度的开口比例。
        '''Calculate normalized inner-lip opening.'''
        vertical_distance = np.linalg.norm(
            mouth_inner_coords[2] - mouth_inner_coords[3]
        )
        horizontal_distance = np.linalg.norm(
            mouth_inner_coords[0] - mouth_inner_coords[1]
        )
        if horizontal_distance == 0:
            return 0.0
        return float(vertical_distance / horizontal_distance)

    @staticmethod
    def calculate_eyebrow_raise(
        eyebrow_coords: np.ndarray,
        eye_coords: np.ndarray,
    ) -> float:
        # 眉毛中心到眼睛上沿的距离再除以眼宽，得到归一化抬眉程度。
        '''Calculate eyebrow elevation normalized by eye width.'''
        eyebrow_center_y = float(np.mean(eyebrow_coords[:, 1]))
        eye_top_y = float(np.min(eye_coords[:, 1]))
        eye_width = np.linalg.norm(eye_coords[0] - eye_coords[3])
        if eye_width == 0:
            return 0.0
        return float((eye_top_y - eyebrow_center_y) / eye_width)

    @staticmethod
    def _coords(
        face_landmarks: Any,
        indices: Sequence[int],
        image_shape: Sequence[int],
    ) -> np.ndarray:
        # MediaPipe 的 x/y 是相对图像宽高的 0~1 坐标，这里转为像素坐标。
        height, width = image_shape[:2]
        return np.asarray(
            [
                (
                    face_landmarks.landmark[index].x * width,
                    face_landmarks.landmark[index].y * height,
                )
                for index in indices
            ],
            dtype=np.float64,
        )

    def _elapsed_delta(self, now: float) -> float:
        # 首帧没有上一帧，时间差取 0。
        if self.last_frame_at is None:
            delta = 0.0
        else:
            # 限幅到 2 秒，避免暂停后重新开始把一次很长的间隔全归到某一种表情。
            delta = max(0.0, min(2.0, now - self.last_frame_at))
        self.last_frame_at = now
        return delta

    def _update_calibration(self, features: Dict[str, Any]) -> bool:
        # 预热帧只用来等待模型和用户姿势稳定，不进入基线样本。
        self.calibration_detected_frames += 1
        if self.calibration_detected_frames <= self.calibration_warmup_frames:
            return False

        for key in self.calibration_samples:
            # 优先使用 raw 值，防止校准前的滑动平均把边界样本带进来。
            value = features.get(f'raw_{key}', features.get(key, 0.0))
            self.calibration_samples[key].append(float(value))

        sample_count = len(self.calibration_samples['ear'])
        if sample_count < self.calibration_frames_required:
            # 样本还不够，继续返回 False，让上层保持“校准中”。
            return False

        for key, values in self.calibration_samples.items():
            samples = np.asarray(values, dtype=np.float64)
            # 中位数比平均值更不容易被偶发极端关键点影响。
            median = float(np.median(samples))
            # MAD 表示样本围绕中位数的波动，用作每个用户的自适应噪声底线。
            mad = float(np.median(np.abs(samples - median)))
            self.baseline[key] = median
            self.baseline_noise[key] = max(mad, 1e-4)

        self.is_calibrated = True
            # 校准刚结束时清空短暂历史，避免混入校准前后不同状态的数据。
        self.candidate_history.clear()
        self.mouth_open_history.clear()
        return True

    def _adaptive_threshold(
        self,
        feature_name: str,
        minimum: float,
        maximum: float,
        noise_multiplier: float = 2.5,
    ) -> float:
        # 用户自身抖动越大，触发动作所需的特征变化也相应提高。
        noise = self.baseline_noise.get(feature_name, 0.0)
        # 但仍限制在 minimum 和 maximum 之间，防止阈值过小误报或过大永不触发。
        return float(np.clip(noise * noise_multiplier, minimum, maximum))

    def _update_feature_ranges(self, features: Dict[str, Any]) -> None:
        # 记录会话中各特征出现过的最小/最大值，主要用于调参和排查。
        keys = (
            'smile_delta',
            'mouth_delta',
            'eyebrow_delta',
            'eyebrow_asymmetry_delta',
            'mouth_activity',
            'ear_delta',
        )
        for key in keys:
            value = float(features.get(key, 0.0))
            if key not in self.feature_ranges:
                self.feature_ranges[key] = {'min': value, 'max': value}
                continue
            self.feature_ranges[key]['min'] = min(
                self.feature_ranges[key]['min'],
                value,
            )
            self.feature_ranges[key]['max'] = max(
                self.feature_ranges[key]['max'],
                value,
            )

    def get_effective_thresholds(self) -> Dict[str, float]:
        # 未校准前没有个人基线，因此没有可用阈值。
        if not self.is_calibrated:
            return {}
        return {
            # 微笑、抬眉和眉不对称使用个人噪声动态计算阈值。
            'smile_delta': self._adaptive_threshold(
                'smile_ratio',
                self.SMILE_DELTA_MIN,
                0.025,
            ),
            'laugh_mouth_delta': self.LAUGH_MOUTH_DELTA,
            'surprise_mouth_delta': self.SURPRISE_MOUTH_DELTA,
            'surprise_eyebrow_delta': self._adaptive_threshold(
                'eyebrow_raise',
                self.SURPRISE_EYEBROW_DELTA,
                0.12,
            ),
            'confusion_asymmetry_delta': self._adaptive_threshold(
                'eyebrow_asymmetry',
                self.CONFUSION_ASYMMETRY_DELTA,
                0.12,
            ),
            'speech_activity': float(
                np.clip(
                    self.baseline_noise['mouth_openness'] * 2.5,
                    self.SPEECH_ACTIVITY_MIN,
                    0.020,
                )
            ),
        }

    def _smooth_expression(
        self,
        candidate: str,
        confidence: float,
    ) -> Tuple[str, float]:
        # 把本帧候选表情加入最近 5 帧，减少关键点抖动造成的表情闪烁。
        self.candidate_history.append((candidate, confidence))
        if len(self.candidate_history) < 3:
            return candidate, confidence

        counts = Counter(item[0] for item in self.candidate_history)
        expression, votes = counts.most_common(1)[0]
        if votes < 3:
            # 最近 5 帧中至少 3 帧一致才采用“多数结果”，否则沿用本帧候选。
            return candidate, confidence

        confidences = [
            item[1]
            for item in self.candidate_history
            if item[0] == expression
        ]
        return expression, float(np.mean(confidences))

    def _recognize_expression(
        self,
        features: Dict[str, Any],
    ) -> Tuple[str, float]:
        # 计算相对个人中性基线的变化量，而不是比较绝对几何尺寸。
        smile_delta = features['smile_ratio'] - self.baseline['smile_ratio']
        mouth_delta = (
            features['mouth_openness'] - self.baseline['mouth_openness']
        )
        eyebrow_delta = (
            features['eyebrow_raise'] - self.baseline['eyebrow_raise']
        )
        asymmetry_delta = (
            features['eyebrow_asymmetry']
            - self.baseline['eyebrow_asymmetry']
        )
        ear_delta = features['ear'] - self.baseline['ear']

        features.update(
            {
                'smile_delta': float(smile_delta),
                'mouth_delta': float(mouth_delta),
                'eyebrow_delta': float(eyebrow_delta),
                'eyebrow_asymmetry_delta': float(asymmetry_delta),
                'ear_delta': float(ear_delta),
            }
        )
        # 记录本次特征在会话中的观察范围，方便后续调阈值。
        self._update_feature_ranges(features)

        # 三种关键阈值都根据校准时测得的个人噪声做小幅自适应调整。
        smile_threshold = self._adaptive_threshold(
            'smile_ratio',
            self.SMILE_DELTA_MIN,
            0.025,
        )
        eyebrow_threshold = self._adaptive_threshold(
            'eyebrow_raise',
            self.SURPRISE_EYEBROW_DELTA,
            0.12,
        )
        asymmetry_threshold = self._adaptive_threshold(
            'eyebrow_asymmetry',
            self.CONFUSION_ASYMMETRY_DELTA,
            0.12,
        )
        speech_threshold = float(
            np.clip(
                self.baseline_noise['mouth_openness'] * 2.5,
                self.SPEECH_ACTIVITY_MIN,
                0.020,
            )
        )

        expression = '中性'
        confidence = 0.60
        if (
            # 惊讶同时要求抬眉、张嘴和眼睛未明显闭合。
            eyebrow_delta > eyebrow_threshold
            and mouth_delta > self.SURPRISE_MOUTH_DELTA
            and ear_delta > -0.03
        ):
            expression, confidence = '惊讶', 0.85
        elif (
            # 大笑 = 明显微笑 + 较大张嘴幅度。
            smile_delta > smile_threshold
            and mouth_delta > self.LAUGH_MOUTH_DELTA
        ):
            expression, confidence = '大笑', 0.88
        elif smile_delta > smile_threshold:
            expression, confidence = '微笑', 0.82
        elif (
            # 说话更关注嘴部随时间的变化，而不只是某一帧嘴张得很大。
            features['mouth_activity'] > speech_threshold
            and mouth_delta > 0.005
        ):
            expression, confidence = '说话', 0.78
        elif (
            # 困惑表现为眉毛不对称，同时没有明显大笑或张嘴。
            asymmetry_delta > asymmetry_threshold
            and eyebrow_delta > 0.02
            and mouth_delta < 0.08
        ):
            expression, confidence = '困惑', 0.72

        return self._smooth_expression(expression, confidence)

    def _update_expression_state(
        self,
        expression: str,
        confidence: float,
        now: float,
        delta: float,
    ) -> None:
        # 每帧先累计计数和持续时间。
        self.expression_counts[expression] += 1
        self.expression_durations[expression] += delta

        if expression == self.current_expression:
            # 表情未变化，只刷新置信度并继续当前持续时长。
            self.current_expression_confidence = confidence
            return

        previous_expression = self.current_expression
        duration = max(0.0, now - self.expression_started_at)
        if previous_expression not in {'校准中', '中性'} and duration >= 0.5:
            # 短暂闪烁不单独记事件；至少持续半秒才形成一条变化记录。
            self.expression_events.append(
                {
                    'elapsed_seconds': round(
                        self.expression_started_at - self.session_started_at,
                        2,
                    ),
                    'expression': previous_expression,
                    'duration_seconds': round(duration, 2),
                    'confidence': round(
                        self.current_expression_confidence,
                        2,
                    ),
                }
            )
        self.current_expression = expression
        # 状态切换后重新开始计时。
        self.current_expression_confidence = confidence
        self.expression_started_at = now

    def _detect_blink(self, ear: float, now: float) -> None:
        # EAR 低于阈值视为闭眼，但这时还不增加计数。
        if ear < self.EAR_THRESHOLD:
            self.eye_closed = True
            return
        if self.eye_closed:
            # 从闭眼恢复为睁眼才完成一次眨眼；0.3 秒间隔避免重复计数。
            if now - self.last_blink_at > 0.3:
                self.blink_count += 1
                self.last_blink_at = now
            self.eye_closed = False

    def calculate_expression_score(self) -> float:
        # 尚未完成校准或没有正式分析帧时，用 50 分作为中性占位值。
        if self.analyzed_frames == 0:
            return 50.0
        positive_ratio = (
            # 微笑和大笑都视为积极表情，它们占比越高得分越高。
            self.expression_counts['微笑'] + self.expression_counts['大笑']
        ) / self.analyzed_frames
        neutral_ratio = self.expression_counts['中性'] / self.analyzed_frames
        score = 50.0 + positive_ratio * 50.0
        if neutral_ratio > 0.8:
            # 超过 80% 的帧都完全中性时，适度降低表现分。
            score -= 20.0
        return round(float(np.clip(score, 0.0, 100.0)), 1)

    def build_summary(self, now: Optional[float] = None) -> Dict[str, Any]:
        # 生成整个会话的表情报告。
        timestamp = time.monotonic() if now is None else now
        # 没有正式分析帧时用 1 作除数，保证分布字典不会除零。
        analyzed_frames = max(1, self.analyzed_frames)
        distribution = {
            expression: round(count / analyzed_frames * 100.0, 1)
            for expression, count in self.expression_counts.items()
        }
        elapsed = max(0.0, timestamp - self.session_started_at)
        # 把眨眼总数换算为“每分钟次数”。
        blink_frequency = (
            self.blink_count / elapsed * 60.0 if elapsed > 0 else 0.0
        )
        score = self.calculate_expression_score()
        return {
            # baseline 和 thresholds 一并返回，便于理解本用户为何得到该判定。
            'score': score,
            'rating': self._score_rating(score),
            'state': self.current_expression,
            'confidence': round(self.current_expression_confidence, 2),
            'calibrated': self.is_calibrated,
            'total_face_frames': self.total_face_frames,
            'analyzed_frames': self.analyzed_frames,
            'distribution_percent': distribution,
            'blink_count': self.blink_count,
            'blink_frequency_per_minute': round(blink_frequency, 1),
            'change_count': len(self.expression_events),
            'events': list(self.expression_events),
            'baseline': {
                key: round(value, 6)
                for key, value in self.baseline.items()
            },
            'thresholds': self.get_effective_thresholds(),
        }

    @staticmethod
    def _score_rating(score: float) -> str:
        # 将 0~100 分转换为四档可读标签。
        if score >= 80:
            return '积极'
        if score >= 60:
            return '良好'
        if score >= 40:
            return '一般'
        return '低落'
