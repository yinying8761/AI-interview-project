import asyncio
import base64
import os
import subprocess
import time
import uuid
from pathlib import Path
from typing import Union

import edge_tts

from backend.app.config import Config

# TTS 是 Text To Speech（文字转语音）的缩写。
# 优先使用在线 Edge-TTS；Windows 上失败时退回系统自带 SAPI 语音。
# 生成文件放在缓存目录，并定期清理旧文件，避免磁盘无限增长。


# 低于该体积的音频几乎一定是空文件或纯头部，直接判定为合成失败
MIN_AUDIO_BYTES = 1024
# Edge-TTS 偶发返回空音频，同一请求最多尝试次数
EDGE_ATTEMPTS = 2
# 合成缓存保留时长，避免 runtime/tts 无限增长
CACHE_MAX_AGE_SECONDS = 24 * 60 * 60

AUDIO_MIME_TYPES = {
    ".mp3": "audio/mpeg",
    ".wav": "audio/wav",
    ".m4a": "audio/mp4",
    ".ogg": "audio/ogg",
    ".webm": "audio/webm",
}


def get_audio_mime_type(audio_path: Union[str, os.PathLike]) -> str:
    """Return a browser-friendly MIME type for a generated audio file."""
    # 根据扩展名告诉浏览器音频格式，便于直接播放。
    suffix = Path(audio_path).suffix.lower()
    return AUDIO_MIME_TYPES.get(suffix, "application/octet-stream")


def _has_audio_content(audio_path: Path) -> bool:
    """Check that the file exists and holds more than an empty container header."""
    # 文件存在且达到最小体积，才认为里面可能包含有效音频。
    try:
        return (
            audio_path.is_file()
            and audio_path.stat().st_size >= MIN_AUDIO_BYTES
        )
    except OSError:
        return False


def _remove_file(audio_path: Path) -> None:
    # 清理过程中文件不存在或暂时无法删除都不抛出异常，
    # 避免清理问题覆盖真正的语音合成错误。
    try:
        audio_path.unlink()
    except FileNotFoundError:
        pass
    except OSError:
        pass


def _prune_cache() -> None:
    """Delete stale synthesis output so the cache directory stays small."""
    # 列出缓存目录失败时直接返回；缓存可能由另一次请求同时修改。
    try:
        entries = list(Config.TTS_CACHE_DIR.iterdir())
    except OSError:
        return

    deadline = time.time() - CACHE_MAX_AGE_SECONDS
    for entry in entries:
        # 只删除普通文件和超过 24 小时未修改的文件。
        try:
            if entry.is_file() and entry.stat().st_mtime < deadline:
                entry.unlink()
        except OSError:
            continue


class TTSService:
    def __init__(self, edge_timeout_seconds: float = 30.0):
        # 超时时间至少 5 秒，避免配置为 0 导致每次在线合成立即失败。
        self.edge_timeout_seconds = max(5.0, float(edge_timeout_seconds))

    async def synthesize(
        self,
        text: str,
        voice: str = Config.DEFAULT_TTS_VOICE,
        output_filename: str = None,
    ) -> str:
        """
        Synthesize text with Edge-TTS and fall back to Windows SAPI.

        Edge-TTS can occasionally return an empty file when its online service
        is unavailable. In that case the local Windows voice keeps the voice
        interaction usable without changing the rest of the application.
        """
        # 空文本没有可合成内容，直接拒绝。
        if not text.strip():
            raise ValueError("Text for speech synthesis cannot be empty.")

        # 每次随机文件名可避免不同请求互相覆盖。
        if output_filename is None:
            output_filename = f"{uuid.uuid4()}.mp3"

        Config.TTS_CACHE_DIR.mkdir(parents=True, exist_ok=True)
        # 每次合成前顺便清理旧缓存。
        _prune_cache()
        requested_path = Config.TTS_CACHE_DIR / output_filename
        requested_path.parent.mkdir(parents=True, exist_ok=True)

        # Edge-TTS writes MP3 regardless of the requested suffix.
        edge_path = requested_path
        if edge_path.suffix.lower() != ".mp3":
            edge_path = edge_path.with_suffix(".mp3")

        edge_error: Exception | None = None
        for attempt in range(EDGE_ATTEMPTS):
            try:
                # Communicate 负责连接在线语音服务并保存音频；
                # wait_for 限制单次等待时间，超时后进入重试或本地回退。
                communicate = edge_tts.Communicate(text, voice)
                await asyncio.wait_for(
                    communicate.save(str(edge_path)),
                    timeout=self.edge_timeout_seconds,
                )
                if not _has_audio_content(edge_path):
                    raise RuntimeError("Edge-TTS 返回了空音频文件。")
                return str(edge_path)
            except Exception as exc:
                # 保存错误、删除不完整文件；若还有机会则短暂等待后重试一次。
                edge_error = exc
                _remove_file(edge_path)
                if attempt + 1 < EDGE_ATTEMPTS:
                    await asyncio.sleep(0.6)

        if os.name != "nt":
            # 非 Windows 系统没有 SAPI 回退，只能把 Edge-TTS 原始错误向上抛。
            raise RuntimeError(
                f"Edge-TTS 语音合成失败：{edge_error}"
            ) from edge_error

        # 在线服务不可用时退回本机 Windows 语音，保证面试流程不中断
        fallback_path = requested_path.with_suffix(".wav")
        _remove_file(fallback_path)
        try:
            # 调用 Windows 自带语音引擎生成本地 WAV。
            self._synthesize_windows_sapi(text, fallback_path)
        except Exception as fallback_error:
            raise RuntimeError(
                "语音合成失败。"
                f"Edge-TTS：{edge_error}；"
                f"Windows 本地语音：{fallback_error}"
            ) from fallback_error

        if not _has_audio_content(fallback_path):
            raise RuntimeError(
                "语音合成失败：在线与本地语音引擎都未生成有效音频，"
                "请检查网络后重试。"
            )
        return str(fallback_path)

    @staticmethod
    def _synthesize_windows_sapi(text: str, output_path: Path) -> None:
        """Generate a WAV file through the built-in Windows speech engine."""
        if os.name != "nt":
            raise RuntimeError("Windows SAPI is only available on Windows.")

        output_path.parent.mkdir(parents=True, exist_ok=True)
        # 文本先编码成 Base64，再通过环境变量传给 PowerShell。
        # 这样中文和引号都不会破坏命令行参数。
        encoded_text = base64.b64encode(text.encode("utf-8")).decode("ascii")
        environment = os.environ.copy()
        environment["SMARTSPEECH_TTS_TEXT_B64"] = encoded_text
        environment["SMARTSPEECH_TTS_OUTPUT"] = str(output_path)

        script = r"""
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$text = [Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String($env:SMARTSPEECH_TTS_TEXT_B64)
)
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $voices = @(
        $synth.GetInstalledVoices() |
        Where-Object {
            $_.Enabled -and $_.VoiceInfo.Culture.Name -like 'zh-CN*'
        }
    )
    if ($voices.Count -gt 0) {
        $synth.SelectVoice($voices[0].VoiceInfo.Name)
    }
    $synth.SetOutputToWaveFile($env:SMARTSPEECH_TTS_OUTPUT)
    $synth.Speak($text)
}
finally {
    $synth.Dispose()
}
"""
        # PowerShell 脚本查找一个已启用的 zh-CN 语音，并把文本写进 WAV 文件。
        # CREATE_NO_WINDOW 防止后端运行时弹出控制台窗口。
        result = subprocess.run(
            [
                "powershell.exe",
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script,
            ],
            env=environment,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=90,
            check=False,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        if result.returncode != 0 or not _has_audio_content(output_path):
            # 截取错误末尾 800 个字符，既保留关键信息又避免异常消息过长。
            detail = (result.stderr or result.stdout or "unknown error").strip()
            raise RuntimeError(detail[-800:])

    @staticmethod
    async def get_voices():
        """
        Get list of available voices.
        """
        # 向 Edge-TTS 查询当前在线可用音色。
        voices = await edge_tts.list_voices()
        return voices


def run_async(coro):
    """Run async method synchronously in a safe way (Thread isolation)"""
    # Flask 视图是同步函数，而 Edge-TTS 是异步函数。这里新建一个线程和事件循环，
    # 在该线程执行 coroutine，主线程 join 等待结果，从而避免事件循环冲突。
    import threading
    result = []
    exc = []

    def target():
        # Create a new event loop for this thread
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            res = loop.run_until_complete(coro)
            result.append(res)
        except Exception as e:
            # 异步函数内部的异常不能直接从线程抛出，先存起来由主线程重新抛出。
            exc.append(e)
        finally:
            loop.close()

    t = threading.Thread(target=target)
    t.start()
    t.join()

    if exc:
        # 保持原始异常类型和消息，例如 ValueError、RuntimeError。
        raise exc[0]
    return result[0]
