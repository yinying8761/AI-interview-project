from __future__ import annotations

# 本文件是“音频媒体”相关的 API 入口，提供三件事：
# 1. 把用户的面试录音转成文字（ASR）；
# 2. 把面试题文字合成语音（TTS）；
# 3. 返回可用的中文音色列表。

import uuid
from pathlib import Path

from flask import Blueprint, jsonify, request, send_file

from backend.app.api.common import (
    ApiValidationError,
    json_body,
    json_error,
    login_required,
    services,
)
from backend.app.config import Config
from backend.app.services.tts_service import (
    get_audio_mime_type,
    run_async,
)


# 与其它 API 一样，这个蓝图最终由 app 统一挂载到 /api 前缀。
media_bp = Blueprint("media", __name__)

# 浏览器上传音频时，文件名后缀和 MIME 类型可能不完整。
# 这里限定可接受的后缀，防止把任意文件当成音频交给识别器处理。
ALLOWED_AUDIO_SUFFIXES = {
    ".wav",
    ".mp3",
    ".m4a",
    ".mp4",
    ".ogg",
    ".webm",
    ".flac",
}


@media_bp.post("/media/transcribe")
@login_required
def transcribe():
    # 第一步：从前端 multipart 表单中读取字段名为 audio 的文件。
    uploaded = request.files.get("audio")
    if uploaded is None:
        return json_error("请先录制或选择音频。", 400)

    original_suffix = Path(uploaded.filename or "").suffix.lower()
    if original_suffix not in ALLOWED_AUDIO_SUFFIXES:
        # 文件名不可靠时，再根据浏览器提供的 Content-Type 猜一个后缀。
        # 仍无法判断就按 .wav 处理，后续由识别器真正校验内容。
        content_type = (uploaded.mimetype or "").lower()
        original_suffix = {
            "audio/wav": ".wav",
            "audio/x-wav": ".wav",
            "audio/mpeg": ".mp3",
            "audio/mp4": ".m4a",
            "audio/ogg": ".ogg",
            "audio/webm": ".webm",
            "video/webm": ".webm",
        }.get(content_type, ".wav")

    # 每个请求使用随机 UUID 生成独立临时文件名，避免并发上传时互相覆盖。
    audio_path = Config.ASR_TEMP_DIR / (
        f"interview_{uuid.uuid4().hex}{original_suffix}"
    )
    # 将内存中的上传内容保存到磁盘，因为语音识别命令行需要真实文件路径。
    uploaded.save(audio_path)

    try:
        if not audio_path.is_file() or audio_path.stat().st_size == 0:
            raise ApiValidationError("录音文件为空，请重新录制。")
        result = services().asr.transcribe(str(audio_path))
    except ApiValidationError as exc:
        return json_error(str(exc), 400)
    except Exception as exc:
        return json_error(f"语音识别失败：{exc}", 500)
    finally:
        # 无论识别成功还是失败，都要删除临时音频，避免磁盘不断堆积。
        # missing_ok=True 表示文件已经不存在时不报错；清理失败也不覆盖原结果。
        try:
            audio_path.unlink(missing_ok=True)
        except OSError:
            pass

    segments = result.get("segments") or []
    # Service 返回值可能包含额外字段；这里只挑选前端需要的三段信息，
    # 并把缺失值转换为安全默认值，保证响应结构稳定。
    compact_segments = [
        {
            "start": float(segment.get("start") or 0.0),
            "end": float(segment.get("end") or 0.0),
            "text": str(segment.get("text") or "").strip(),
        }
        for segment in segments
        if isinstance(segment, dict)
    ]
    return jsonify(
        {
            "text": str(result.get("text") or "").strip(),
            "language": result.get("language"),
            "segments": compact_segments,
        }
    )


@media_bp.post("/media/tts")
@login_required
def synthesize_speech():
    # TTS 接口接收 JSON，例如 {"text": "...", "voice": "zh-CN-..."}。
    payload = json_body()
    text = str(payload.get("text") or "").strip()
    voice = str(payload.get("voice") or Config.DEFAULT_TTS_VOICE).strip()
    if not text:
        return json_error("待合成文本不能为空。", 400)
    if len(text) > 5000:
        # 设置长度上限，避免单次请求占用过长时间或产生超大音频。
        return json_error("单次语音合成文本不能超过 5000 个字符。", 400)

    try:
        # tts.synthesize 是 async 函数，run_async 会等它执行完成后返回音频路径。
        # 对 Flask 同步视图来说，这里表现得像一次普通函数调用。
        output_path = run_async(
            services().tts.synthesize(
                text,
                voice=voice or Config.DEFAULT_TTS_VOICE,
            )
        )
    except Exception as exc:
        return json_error(f"语音合成失败：{exc}", 500)

    audio_path = Path(output_path)
    if not audio_path.is_file() or audio_path.stat().st_size == 0:
        return json_error("语音合成未生成有效音频。", 500)

    # send_file 直接把生成的音频作为响应体返回；as_attachment=False 表示
    # 浏览器通常直接播放，而不是先弹出“下载文件”窗口。
    return send_file(
        audio_path,
        mimetype=get_audio_mime_type(audio_path),
        as_attachment=False,
        download_name=audio_path.name,
        conditional=True,
    )


@media_bp.get("/media/voices")
@login_required
def voices():
    # 如果系统语音服务暂时不可用，前端仍需要音色选项可用。
    # fallback 保存一组固定中文音色，确保页面不会出现空下拉框。
    fallback = [
        {"short_name": "zh-CN-YunxiNeural", "label": "云希 · 男声"},
        {"short_name": "zh-CN-XiaoxiaoNeural", "label": "晓晓 · 女声"},
        {"short_name": "zh-CN-YunyangNeural", "label": "云扬 · 新闻男声"},
        {"short_name": "zh-CN-XiaoyiNeural", "label": "晓伊 · 女声"},
    ]
    try:
        raw_voices = run_async(services().tts.get_voices())
        chinese_voices = []
        for voice in raw_voices:
            short_name = str(voice.get("ShortName") or "")
            locale = str(voice.get("Locale") or "")
            if not short_name.startswith("zh-CN") and locale != "zh-CN":
                # 面试系统只展示普通话中文音色，过滤其它语言和地区。
                continue
            label = str(voice.get("FriendlyName") or short_name)
            chinese_voices.append(
                {
                    "short_name": short_name,
                    "label": label,
                }
            )
        if chinese_voices:
            return jsonify({"voices": chinese_voices})
    except Exception:
        # 远端音色查询失败不向用户报错，继续返回上面的本地备用列表。
        pass
    return jsonify({"voices": fallback})
