from __future__ import annotations

# 本文件专门负责最终成绩计算。它把大模型对回答内容的评分，
# 与视觉分析得到的表情、注意力评分按权重合并。纯计算函数，不调用网络或磁盘。

from decimal import Decimal, ROUND_HALF_UP
from typing import Any, Dict, Optional


def fuse_interview_scores(
    question_score: Any,
    video_summary: Optional[Dict[str, Any]],
    question_weight: float = 0.8,
    video_weight: float = 0.2,
) -> Dict[str, Any]:
    '''Combine DeepSeek text scoring with local expression/attention scoring.'''
    # 权重必须加起来等于 1。允许极小的浮点误差，避免 0.8 + 0.2 的精度问题误报。
    if abs((question_weight + video_weight) - 1.0) > 1e-9:
        raise ValueError('评分权重之和必须等于 1.0')

    normalized_question_score = _clamp_score(question_score)
    # 视频汇总可能不存在，或样本不足。这里分别安全读取两个可选分数。
    expression_score = _optional_score(
        video_summary.get('expression_score') if video_summary else None
    )
    attention_score = _optional_score(
        video_summary.get('attention_score') if video_summary else None
    )
    video_available = bool(
        # 只有视频分析明确可用且两个分数都存在，才允许计算视频部分。
        video_summary
        and video_summary.get('video_available')
        and expression_score is not None
        and attention_score is not None
    )

    expression_attention_score = None
    final_score = None
    if video_available:
        # 视频表现分 = 表情分 * 50% + 注意力分 * 50%。
        expression_attention_score = _round_decimal(
            Decimal(str(expression_score)) * Decimal('0.5')
            + Decimal(str(attention_score)) * Decimal('0.5')
        )
        # 最终分 = 问答分 * 80% + 视频表现分 * 20%。
        final_score = _round_decimal(
            Decimal(str(normalized_question_score))
            * Decimal(str(question_weight))
            + Decimal(str(expression_attention_score))
            * Decimal(str(video_weight))
        )

    return {
        # available 表示是否真的算出了最终分；前端可据此说明缺少摄像头样本。
        'available': video_available,
        'question_score': normalized_question_score,
        'expression_score': expression_score,
        'attention_score': attention_score,
        'expression_attention_score': expression_attention_score,
        'final_score': final_score,
        'weights': {
            # 返回权重和公式，便于报告解释得分来源，而不是只显示一个数字。
            'question': question_weight,
            'expression_attention': video_weight,
            'expression_within_video': 0.5,
            'attention_within_video': 0.5,
        },
        'formula': (
            'final_score = question_score * 0.8 + '
            '(expression_score * 0.5 + attention_score * 0.5) * 0.2'
        ),
        'video_sample_count': (
            int(video_summary.get('face_frames', 0))
            if video_summary
            else 0
        ),
        'note': (
            '问答、表情和注意力样本均可用，已计算最终得分。'
            if video_available
            else '有效视频样本不足，未生成最终得分；权重不会重新分配。'
        ),
    }


def _clamp_score(value: Any) -> float:
    # 无法转换的值按 0 分处理，然后强制限制在 0 到 100 之间。
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        numeric = 0.0
    return _round_score(max(0.0, min(100.0, numeric)))


def _optional_score(value: Any) -> Optional[float]:
    # 与 _clamp_score 的区别：None 仍保持 None，表示这项没有有效样本。
    if value is None:
        return None
    return _clamp_score(value)


def _round_score(value: float) -> float:
    return _round_decimal(Decimal(str(value)))


def _round_decimal(value: Decimal) -> float:
    # 使用 Decimal 和“四舍五入”规则保留一位小数，减少普通浮点数误差。
    rounded = value.quantize(
        Decimal('0.1'),
        rounding=ROUND_HALF_UP,
    )
    return float(rounded)
