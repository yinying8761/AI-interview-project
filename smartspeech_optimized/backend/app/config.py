from __future__ import annotations

# 本文件集中定义项目路径、运行目录、模型参数和 Flask 配置。
# 其它模块只读取这里的大写常量或 Config 属性，避免自行拼接路径。

import os
import secrets
import shutil
from pathlib import Path

from dotenv import load_dotenv


PROJECT_ROOT = Path(__file__).resolve().parents[2]
# 在创建 Config 前加载项目根目录 .env，使环境变量可覆盖默认配置。
load_dotenv(PROJECT_ROOT / ".env")

# 让 Whisper 能定位 ffmpeg：优先读 FFMPEG_DIR，否则在常见 Windows 位置查找并加入 PATH。
_FFMPEG_SEARCH_DIRS = [
    os.getenv("FFMPEG_DIR", ""),
    r"C:\ffmpeg\bin",
    r"C:\Program Files\ffmpeg\bin",
]


def _ensure_ffmpeg_in_path() -> None:
    # 把"含 ffmpeg.exe 的目录"前置到 PATH，让 openai-whisper 的 load_audio
    # 在调用 run(["ffmpeg", ...]) 时能找到完整版 ffmpeg（支持 s16le / opus）。
    # 优先项目根目录（常有完整版 ffmpeg.exe），再依次尝试其它常见位置。
    candidates = [
        str(PROJECT_ROOT),
        os.getenv("FFMPEG_DIR", ""),
        r"C:\ffmpeg\bin",
        r"C:\Program Files\ffmpeg\bin",
    ]
    for directory in candidates:
        if directory and os.path.isfile(os.path.join(directory, "ffmpeg.exe")):
            os.environ["PATH"] = directory + os.pathsep + os.environ.get("PATH", "")
            print(f"[config] 已把 ffmpeg 目录加入 PATH: {directory}")
            return

    # 兜底：imageio-ffmpeg 内置完整版 ffmpeg，复制为 ffmpeg.exe 后加入 PATH。
    try:
        import imageio_ffmpeg

        src = imageio_ffmpeg.get_ffmpeg_exe()
        dest_dir = PROJECT_ROOT / "runtime" / "ffmpeg"
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / "ffmpeg.exe"
        if os.path.isfile(src) and not dest.exists():
            shutil.copy(src, dest)
        if dest.exists():
            os.environ["PATH"] = str(dest_dir) + os.pathsep + os.environ.get("PATH", "")
            print(f"[config] 已把 imageio-ffmpeg 的 ffmpeg 目录加入 PATH: {dest_dir}")
            return
    except Exception:
        pass


_ensure_ffmpeg_in_path()

# 持久数据放在 data，临时文件和运行产物放在 runtime。
DATA_DIR = PROJECT_ROOT / "data"
RUNTIME_DIR = PROJECT_ROOT / "runtime"
FRONTEND_DIST_DIR = PROJECT_ROOT / "frontend" / "dist"

# 认证数据：用户账号文件和每个人对应的 512 维人脸特征文件。
AUTH_DATA_DIR = DATA_DIR / "auth"
AUTH_USERS_FILE = AUTH_DATA_DIR / "users.json"
AUTH_FACES_DIR = AUTH_DATA_DIR / "faces"

# 临时或可再生成的数据目录。程序启动时若目录不存在会自动创建。
ASR_TEMP_DIR = RUNTIME_DIR / "asr"
TTS_CACHE_DIR = RUNTIME_DIR / "tts"
REPORT_DIR = RUNTIME_DIR / "reports"
RESUME_DATA_DIR = DATA_DIR / "resumes"
RESUME_TEMP_DIR = RUNTIME_DIR / "resumes"
QUESTION_BANK_FILE = DATA_DIR / "interview_question_bank.json"

for directory in (
    DATA_DIR,
    RUNTIME_DIR,
    AUTH_DATA_DIR,
    AUTH_FACES_DIR,
    ASR_TEMP_DIR,
    TTS_CACHE_DIR,
    REPORT_DIR,
    RESUME_DATA_DIR,
    RESUME_TEMP_DIR,
):
    # parents=True 允许一次创建多级目录，exist_ok=True 表示重复启动不报错。
    directory.mkdir(parents=True, exist_ok=True)


def _persistent_secret() -> str:
    # Flask 用 SECRET_KEY 签名登录 Cookie。优先使用 .env 中的值；
    # 未配置时生成一次并保存到 data/.flask_secret，重启后仍可解签旧 Cookie。
    configured = os.getenv("FLASK_SECRET_KEY", "").strip()
    if configured:
        return configured

    secret_file = DATA_DIR / ".flask_secret"
    if secret_file.exists():
        value = secret_file.read_text(encoding="utf-8").strip()
        if value:
            return value

    value = secrets.token_urlsafe(48)
    secret_file.write_text(value, encoding="utf-8")
    return value


class Config:
    # Flask 和业务服务共同使用的应用级配置。
    APP_NAME = "智能体模拟面试系统"
    SECRET_KEY = _persistent_secret()
    # 登录 Cookie 采用 HttpOnly 和 SameSite=Lax，降低脚本窃取及跨站请求风险。
    SESSION_COOKIE_NAME = "smart_interview_session"
    SESSION_COOKIE_HTTPONLY = True
    SESSION_COOKIE_SAMESITE = "Lax"
    SESSION_COOKIE_SECURE = os.getenv("SESSION_COOKIE_SECURE", "0") == "1"
    PERMANENT_SESSION_LIFETIME = 60 * 60 * 12

    # 单次 HTTP 请求体最大 12 MiB，防止超大上传占用过多内存。
    MAX_CONTENT_LENGTH = 12 * 1024 * 1024
    JSON_AS_ASCII = False

    # 把上面定义的绝对路径挂到 Config，便于 Flask 和 Service 统一读取。
    DATA_DIR = DATA_DIR
    RUNTIME_DIR = RUNTIME_DIR
    AUTH_DATA_DIR = AUTH_DATA_DIR
    AUTH_USERS_FILE = AUTH_USERS_FILE
    AUTH_FACES_DIR = AUTH_FACES_DIR
    ASR_TEMP_DIR = ASR_TEMP_DIR
    TTS_CACHE_DIR = TTS_CACHE_DIR
    REPORT_DIR = REPORT_DIR
    RESUME_DATA_DIR = RESUME_DATA_DIR
    RESUME_TEMP_DIR = RESUME_TEMP_DIR
    QUESTION_BANK_FILE = QUESTION_BANK_FILE
    FRONTEND_DIST_DIR = FRONTEND_DIST_DIR
    # 逗号分隔的环境变量会转换成列表，并剔除多余空格和空项。
    CORS_ORIGINS = [
        item.strip()
        for item in os.getenv(
            "CORS_ORIGINS",
            "http://127.0.0.1:5173,http://localhost:5173",
        ).split(",")
        if item.strip()
    ]

    # DeepSeek 用于生成面试问题和最终评价；没配置 API Key 时应用仍可启动。
    DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "").strip()
    DEEPSEEK_API_URL = (
        os.getenv("DEEPSEEK_API_URL", "").strip()
        or "https://api.deepseek.com/chat/completions"
    )
    DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat").strip()
    DEEPSEEK_TIMEOUT = int(os.getenv("DEEPSEEK_TIMEOUT", "90"))
    CHAT_CONTEXT_MESSAGES = int(os.getenv("CHAT_CONTEXT_MESSAGES", "20"))


    # 简历限制同时包含上传体积、提取结果长度和发送给模型的长度。
    MAX_RESUME_SIZE = int(os.getenv("MAX_RESUME_SIZE", str(8 * 1024 * 1024)))
    RESUME_MAX_EXTRACTED_CHARS = int(
        os.getenv("RESUME_MAX_EXTRACTED_CHARS", "50000")
    )
    RESUME_PROMPT_MAX_CHARS = int(
        os.getenv("RESUME_PROMPT_MAX_CHARS", "24000")
    )
    # 每场面试达到该题数后进入 checkpoint，等待用户选择评估或下一轮。
    INTERVIEW_QUESTION_LIMIT = max(
        1,
        int(os.getenv("INTERVIEW_QUESTION_LIMIT", "12")),
    )

    # Whisper 负责把录音转换为文字，音色与超时参数用于 TTS。
    WHISPER_MODEL_SIZE = os.getenv("WHISPER_MODEL_SIZE", "small").strip()
    WHISPER_DEVICE = os.getenv("WHISPER_DEVICE", "cpu").strip()
    # 语言固定为普通话可显著改善中文短句识别；置空则恢复自动检测。
    WHISPER_LANGUAGE = os.getenv("WHISPER_LANGUAGE", "zh").strip()
    WHISPER_INITIAL_PROMPT = os.getenv(
        "WHISPER_INITIAL_PROMPT", "以下是普通话的句子。"
    ).strip()

    DEFAULT_TTS_VOICE = os.getenv(
        "DEFAULT_TTS_VOICE",
        "zh-CN-YunxiNeural",
    ).strip()
    TTS_TIMEOUT_SECONDS = float(os.getenv("TTS_TIMEOUT_SECONDS", "35"))

    # 视觉帧率和抓拍间隔都有上下限，防止前端参数造成过高的计算负担。
    DEFAULT_VISION_FPS = float(os.getenv("DEFAULT_VISION_FPS", "3"))
    MAX_VISION_FPS = float(os.getenv("MAX_VISION_FPS", "8"))
    SNAPSHOT_INTERVAL_SECONDS = float(
        os.getenv("SNAPSHOT_INTERVAL_SECONDS", "2")
    )
    FACE_MATCH_THRESHOLD = float(
        os.getenv("FACE_MATCH_THRESHOLD", "1.0")
    )

    # 服务监听地址、端口以及 Flask 调试开关。
    HOST = os.getenv("APP_HOST", "127.0.0.1")
    PORT = int(os.getenv("APP_PORT", "8765"))
    DEBUG = os.getenv("FLASK_DEBUG", "0") == "1"
