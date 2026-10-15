from __future__ import annotations

# 本文件把“人脸图片处理”和“账号业务”分开。
# 它只负责检测人脸、提取 512 维向量、保存/读取向量以及比较相似度。
# AuthService 再决定这些向量属于哪个本地账号。

import os
import tempfile
import threading
from dataclasses import dataclass
from importlib.util import find_spec
from pathlib import Path
from typing import Any, Dict, Mapping, Optional, Sequence

import cv2
import numpy as np


FACE_VECTOR_SIZE = 512
# InceptionResnetV1 为每张人脸输出 512 个浮点数，所有文件都必须保持这一维度。
DEFAULT_FACE_THRESHOLD = 1.0
# 欧氏距离小于该阈值才判为同一个人；距离越小通常表示越相似。


class FaceRecognitionError(RuntimeError):
# 人脸检测、模型加载、文件读写和向量处理的公共异常类型。
    """Base error for face detection and recognition."""


class FaceModelUnavailable(FaceRecognitionError):
# 表示 facenet-pytorch、torch 等可选依赖缺失或模型无法初始化。
    """Raised when the local face model dependencies cannot be loaded."""


class NoFaceDetected(FaceRecognitionError):
# 表示图片可读取，但 MTCNN 没有找到可用的人脸。
    """Raised when an input image contains no detectable face."""


@dataclass(frozen=True)
class FaceMatch:
    # 比对结果对象；frozen=True 让实例创建后不可修改，避免结果被意外改坏。
    user_id: Optional[str]
    distance: float
    similarity: float
    matched: bool


def normalize_face_vector(
    vector: np.ndarray,
    expected_size: Optional[int] = None,
) -> np.ndarray:
    # 将输入压成一维 float32，并执行必要校验。
    # 归一化后向量长度为 1，比较两点距离时更稳定，不易被向量整体大小干扰。
    """Return a finite, L2-normalized float32 face vector."""
    array = np.asarray(vector, dtype=np.float32).reshape(-1)
    if array.size == 0:
        raise FaceRecognitionError("人脸特征向量为空。")
    if expected_size is not None and array.size != int(expected_size):
        raise FaceRecognitionError(
            f"人脸特征维度应为 {expected_size}，实际为 {array.size}。"
        )
    if not np.all(np.isfinite(array)):
        # NaN、正无穷和负无穷都不能参与正常的距离计算。
        raise FaceRecognitionError("人脸特征向量包含无效数值。")

    norm = float(np.linalg.norm(array))
    if norm <= 1e-12:
        raise FaceRecognitionError("人脸特征向量无法归一化。")
    # 每个元素除以向量长度，得到 L2 范数为 1 的向量。
    return (array / norm).astype(np.float32, copy=False)


def average_face_vectors(
    vectors: Sequence[np.ndarray],
    expected_size: Optional[int] = FACE_VECTOR_SIZE,
) -> np.ndarray:
    # 一个账号可以用多张注册照提取多个向量，先分别归一化，再逐维平均。
    # 平均值还要再次归一化，保证返回值与单张图片产生的向量格式完全一致。
    """Average multiple enrollment vectors and normalize the result."""
    if not vectors:
        raise FaceRecognitionError("至少需要一个人脸特征向量。")
    prepared = [
        normalize_face_vector(vector, expected_size=expected_size)
        for vector in vectors
    ]
    return normalize_face_vector(
        np.mean(np.stack(prepared, axis=0), axis=0),
        expected_size=expected_size,
    )


def save_face_vector(path: str | Path, vector: np.ndarray) -> Path:
    # 保存前再次校验，保证磁盘上的所有特征文件都符合 512 维且已归一化。
    """Atomically save a normalized face vector as a NumPy file."""
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    normalized = normalize_face_vector(vector, expected_size=FACE_VECTOR_SIZE)

    file_descriptor, temp_name = tempfile.mkstemp(
        # 临时文件放在目标目录中，保证 os.replace 是同一文件系统内的原子操作。
        prefix=f".{destination.stem}.",
        suffix=".tmp",
        dir=str(destination.parent),
    )
    os.close(file_descriptor)
    temp_path = Path(temp_name)

    try:
        with temp_path.open("wb") as vector_file:
            # allow_pickle=False 禁止把文件内容当成 Python 对象反序列化，降低恶意文件执行风险。
            np.save(vector_file, normalized, allow_pickle=False)
            vector_file.flush()
            os.fsync(vector_file.fileno())
        os.replace(temp_path, destination)
    except Exception as exc:
        try:
            temp_path.unlink(missing_ok=True)
        except OSError:
            pass
        if isinstance(exc, FaceRecognitionError):
            # 保留本模块主动抛出的中文校验错误，避免重复包装。
            raise
        raise FaceRecognitionError(f"保存人脸特征失败：{exc}") from exc

    return destination


def load_face_vector(
    path: str | Path,
    expected_size: Optional[int] = FACE_VECTOR_SIZE,
) -> np.ndarray:
    # 读取后仍会经过维度、有限数值和归一化校验，不能假设磁盘文件永远可信。
    """Load a face vector without allowing pickle deserialization."""
    source = Path(path)
    try:
        with source.open("rb") as vector_file:
            vector = np.load(vector_file, allow_pickle=False)
    except (OSError, ValueError) as exc:
        raise FaceRecognitionError(f"读取人脸特征失败：{exc}") from exc
    return normalize_face_vector(vector, expected_size=expected_size)


def match_face(
    vector: np.ndarray,
    gallery: Mapping[str, np.ndarray],
    threshold: float = DEFAULT_FACE_THRESHOLD,
) -> FaceMatch:
    # 使用欧氏距离找 gallery 中离待识别向量最近的人脸。
    """Find the nearest gallery vector using Euclidean distance."""
    probe = normalize_face_vector(vector, expected_size=FACE_VECTOR_SIZE)
    if not gallery:
        # 空人脸库表示没有任何候选；距离设为无穷大，相似度设为 0。
        return FaceMatch(None, float("inf"), 0.0, False)

    threshold = float(threshold)
    best_user_id: Optional[str] = None
    best_distance = float("inf")

    for user_id, candidate in gallery.items():
        # gallery 中每条候选向量也重新归一化，兼容旧版本或手工写入的数据。
        candidate_vector = normalize_face_vector(
            candidate,
            expected_size=FACE_VECTOR_SIZE,
        )
        distance = float(np.linalg.norm(probe - candidate_vector))
        if distance < best_distance:
            # 只保留到目前为止距离最小的候选。
            best_user_id = str(user_id)
            best_distance = distance

    # 找到“最近的人”不等于同一人，还必须严格小于业务阈值。
    matched = best_user_id is not None and best_distance < threshold
    # 把 0~2 的欧氏距离粗略映射为 0~1 的展示相似度；它只用于界面解释，不参与判定。
    similarity = max(0.0, min(1.0, 1.0 - best_distance / 2.0))
    return FaceMatch(
        # 未达到阈值时故意返回 None，调用方不能误把最近候选当成匹配用户。
        user_id=best_user_id if matched else None,
        distance=best_distance,
        similarity=similarity,
        matched=matched,
    )


class FaceRecognizer:
    # 模型加载很慢且占内存，因此实例首次真正识别时才导入和加载。
    """Lazy MTCNN + InceptionResnetV1 recognizer for Python 3.10."""

    def __init__(
        self,
        threshold: float = DEFAULT_FACE_THRESHOLD,
        device: str = "cpu",
    ) -> None:
        self.threshold = float(threshold)
        self.device = str(device)
        # 模型初始化与推理都加锁；同一实例不会被多个线程同时调用底层模型。
        self._lock = threading.RLock()
        # _mtcnn 负责人脸检测和裁剪，_model 负责把人脸编码为 512 维向量。
        self._mtcnn: Any = None
        self._model: Any = None

    @staticmethod
    def dependency_available() -> bool:
        # find_spec 只检查模块是否能找到，不会在这一步真正加载 PyTorch。
        return (
            find_spec("facenet_pytorch") is not None
            and find_spec("torch") is not None
            and find_spec("torchvision") is not None
        )

    @property
    def is_loaded(self) -> bool:
        # 两个模型都非空才算完成初始化。
        return self._mtcnn is not None and self._model is not None

    def warm_up(self) -> None:
        # 可在开始面试前主动调用，避免用户第一次上传人脸时等待模型加载。
        """Load MTCNN and the embedding model before the first capture."""
        self._ensure_models()

    def _ensure_models(self) -> None:
        # 无锁快路径：模型已加载时直接返回，避免每次识别都争抢锁。
        if self.is_loaded:
            return

        with self._lock:
            # 双重检查：可能在等待锁期间，另一个线程已经完成了加载。
            if self.is_loaded:
                return
            try:
                # 延迟导入让应用启动和普通账号功能不依赖模型是否安装。
                from facenet_pytorch import InceptionResnetV1, MTCNN
            except Exception as exc:
                raise FaceModelUnavailable(
                    "人脸识别依赖未安装或无法加载，请安装 "
                    "facenet-pytorch==2.5.3、torch 与 torchvision。"
                ) from exc

            try:
                # MTCNN 把检测到的人脸对齐并缩放到 160x160。
                self._mtcnn = MTCNN(
                    image_size=160,
                    keep_all=False,
                    device=self.device,
                )
                self._model = (
                    # 使用 vggface2 预训练权重，切换到推理模式后不再更新参数。
                    InceptionResnetV1(pretrained="vggface2")
                    .eval()
                    .to(self.device)
                )
            except Exception as exc:
                # 初始化半途失败时清空引用，下次调用还能重新尝试加载。
                self._mtcnn = None
                self._model = None
                raise FaceModelUnavailable(
                    f"人脸模型加载失败：{exc}"
                ) from exc

    def extract_face_vector(self, frame_bgr: np.ndarray) -> np.ndarray:
        # 输入约定为 OpenCV 常见的 BGR 图像，输出为可直接保存和比较的 512 维向量。
        """Extract a normalized 512-D vector from a BGR image."""
        # 首次调用会加载模型；后续调用会直接使用缓存。
        self._ensure_models()

        # 统一图像通道格式。MTCNN 需要三通道 RGB，而 OpenCV 默认读取为 BGR。
        frame = np.asarray(frame_bgr)
        if frame.ndim == 2:
            frame = cv2.cvtColor(frame, cv2.COLOR_GRAY2BGR)
        elif frame.ndim != 3:
            raise FaceRecognitionError("输入图像格式无效。")
        if frame.ndim == 3 and frame.shape[2] == 4:
            frame = cv2.cvtColor(frame, cv2.COLOR_BGRA2BGR)
        elif frame.ndim == 3 and frame.shape[2] == 1:
            frame = cv2.cvtColor(frame, cv2.COLOR_GRAY2BGR)
        elif frame.shape[2] != 3:
            raise FaceRecognitionError("输入图像必须包含 1、3 或 4 个通道。")

        if frame.dtype != np.uint8:
            # 深度学习库通常接受 uint8 图像；这里把浮点或其他整型统一转换。
            frame = frame.astype(np.float32)
            if np.issubdtype(np.asarray(frame_bgr).dtype, np.floating):
                # 兼容 0-1 范围的浮点图像，避免整张图被压成近黑色。
                peak = float(np.nanmax(frame)) if frame.size else 0.0
                if 0.0 < peak <= 1.0:
                    frame = frame * 255.0
            # 最后裁掉越界值，避免转换时发生数值回绕。
            frame = np.clip(frame, 0, 255).astype(np.uint8)

        try:
            # Pillow 负责把 numpy 图像包装成 MTCNN 可接受的格式。
            from PIL import Image
            import torch
        except Exception as exc:
            raise FaceModelUnavailable(
                "Pillow 或 PyTorch 无法加载。"
            ) from exc

        # OpenCV 通道顺序是 BGR；MTCNN/Pillow 按 RGB 解释像素，所以这里显式换序。
        rgb_image = Image.fromarray(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))

        with self._lock:
            # MTCNN 检测可能较耗时；加锁保证同一个识别器不会并发调用底层模型。
            boxes, _ = self._mtcnn.detect(rgb_image)
            if boxes is None or len(boxes) == 0:
                raise NoFaceDetected(
                    "未检测到人脸，请正对镜头并保持光线充足。"
                )

            face = self._mtcnn.extract(
                rgb_image,
                # 只取置信度/面积排序中的第一张脸，避免多人画面产生歧义。
                boxes[:1],
                save_path=None,
            )
            if face is None:
                raise NoFaceDetected("未能提取人脸区域，请重新拍摄。")

            if isinstance(face, (list, tuple)):
                # 不同版本可能返回单张张量或只含一张张量的容器，这里统一拆开。
                if not face:
                    raise NoFaceDetected("未能提取人脸区域，请重新拍摄。")
                face = face[0]
            if not torch.is_tensor(face):
                face = torch.as_tensor(face)
            if face.ndim == 3:
                # 模型要求批维度：从 (C,H,W) 变成 (1,C,H,W)。
                face = face.unsqueeze(0)
            if face.ndim != 4 or face.shape[1] != 3:
                raise FaceRecognitionError("人脸图像张量格式无效。")

            # 推理阶段不需要梯度，关闭梯度记录可显著减少内存占用。
            with torch.no_grad():
                # 人脸张量移动到模型所在设备（通常是 CPU，也可由构造参数指定）。
                embedding = self._model(face.to(self.device))

        # 把 PyTorch 张量移回 CPU，脱离计算图后转成 numpy，再统一为 512 维。
        vector = embedding.detach().cpu().numpy().reshape(-1)
        return normalize_face_vector(vector, expected_size=FACE_VECTOR_SIZE)

    def match(
        self,
        vector: np.ndarray,
        gallery: Mapping[str, np.ndarray],
    ) -> FaceMatch:
        # 实例方法复用全局 match_face，并自动带入构造对象时的阈值。
        return match_face(vector, gallery, threshold=self.threshold)
