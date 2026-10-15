from __future__ import annotations

# VisionManager 是视觉链路的“格式转换层”：
# 浏览器上传的是压缩图片字节，OpenCV 分析需要 BGR numpy 图像。
# 帧降低宽度后再交给 InterviewVideoAnalyzer，能减少模型推理耗时。

from typing import Any, Dict

import cv2
import numpy as np

from backend.app.services.vision.session_analyzer import InterviewVideoAnalyzer


class VisionAnalysisError(RuntimeError):
    # 表示图片为空、无法解码或传入视觉管理器时发生错误。
    pass


class VisionManager:
    # 每个分析会话共享一个管理器；管理器本身不保存会话状态。
    """Decode browser snapshots before handing them to a session analyzer."""

    def __init__(self, max_width: int = 640) -> None:
        # 即使配置错误也不会把图像限制到 320 像素以下。
        self.max_width = max(320, int(max_width))

    def process_frame(
        self,
        analyzer: InterviewVideoAnalyzer,
        image_bytes: bytes,
    ) -> Dict[str, Any]:
        # image_bytes 是浏览器压缩后的 JPG/PNG 等数据，不是已经解码的 numpy 图像。
        if not image_bytes:
            raise VisionAnalysisError("视频帧内容为空。")

        frame = cv2.imdecode(
            # 先把 bytes 解释为 uint8 数组，再让 OpenCV 根据文件头识别编码格式。
            np.frombuffer(image_bytes, dtype=np.uint8),
            cv2.IMREAD_COLOR,
        )
        if frame is None or frame.size == 0:
            # 数据非空不代表格式合法，必须检查 imdecode 是否真的产生图像。
            raise VisionAnalysisError("视频帧格式无法解析。")

        height, width = frame.shape[:2]
        if width > self.max_width:
            # 按原图比例缩小，保持人脸形状不变；INTER_AREA 适合缩小图片。
            scale = self.max_width / float(width)
            frame = cv2.resize(
                frame,
                (self.max_width, max(1, int(height * scale))),
                interpolation=cv2.INTER_AREA,
            )

        # 解码和缩放属于通用预处理，具体业务指标由传入的会话分析器更新。
        return analyzer.process_frame(frame)
