import os
import threading

try:
    import whisper
except ImportError:  # pragma: no cover
    # Whisper 属于重型可选依赖（连带 torch，约 2.5GB）。
    # 未安装时仍允许服务启动，仅语音转写能力不可用；
    # 否则容器会因导入失败而整体起不来，出题与评分等核心能力被连坐。
    whisper = None

from backend.app.config import Config

# ASR 是 Automatic Speech Recognition（自动语音识别）的缩写。
# 本文件把 Whisper 模型包装成一个可复用 Service：模型只加载一次，
# 多个请求共享它，并通过锁避免同一个模型被同时调用而互相干扰。


class ASRService:
    def __init__(self):
        # 模型先不加载，等到第一次真正转写时再加载，缩短后端启动时间。
        self._model = None
        self._lock = threading.RLock()

    @staticmethod
    def dependency_available() -> bool:
        # 供健康检查与能力探测使用，不触发模型加载。
        return whisper is not None

    def warm_up(self) -> None:
        if whisper is None:
            raise RuntimeError(
                "Whisper 未安装，语音转写能力不可用。"
                "如需启用请安装 openai-whisper（含 torch 依赖）。"
            )
        # 已加载就直接复用。
        if self._model is not None:
            return
        with self._lock:
            if self._model is not None:
                return
            model_size = Config.WHISPER_MODEL_SIZE
            device = Config.WHISPER_DEVICE
            print(f"Loading Whisper model: {model_size} on {device}...")
            self._model = whisper.load_model(model_size, device=device)
            print("Whisper model loaded successfully.")

    def transcribe(self, audio_path: str) -> dict:
        """
        Transcribe audio file to text.
        Returns a dictionary containing the text and language.
        """
        # API 层已经把上传内容保存为临时文件，这里先确认文件确实存在。
        if not os.path.exists(audio_path):
            raise FileNotFoundError(f"Audio file not found: {audio_path}")

        self.warm_up()
        with self._lock:
            # Whisper 模型对象不适合并发执行同一个实例，因此整段识别放在锁内。
            # CUDA 设备可使用 fp16 加速；CPU 上必须关闭，避免不支持半精度。
            # 检测到 av(PyAV) 时，whisper 会用它直接解码 webm/opus，无需外部 ffmpeg。
            # 固定语言与初始提示可避免中文短句被误判语种，并改善标点与断句。
            result = self._model.transcribe(
                audio_path,
                fp16=Config.WHISPER_DEVICE.startswith("cuda"),
                language=Config.WHISPER_LANGUAGE or None,
                initial_prompt=Config.WHISPER_INITIAL_PROMPT or None,
            )

        return {
            "text": result["text"],
            "language": result["language"],
            "segments": result["segments"],
        }
