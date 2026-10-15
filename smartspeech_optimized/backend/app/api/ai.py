from __future__ import annotations

# 本文件是 Python 端作为“AI 评测能力提供者”的统一入口。
# 它面向 Java 后端，不依赖 Flask 会话，
# 也不承担登录注册与后台管理。路由只负责入参校验与调用 Service，
# 具体 AI 逻辑全部复用既有服务。

import uuid
import logging
import time
from pathlib import Path
from typing import Any

from flask import Blueprint, jsonify, request, send_file

from backend.app.api.common import (
    ApiValidationError,
    image_bytes_from_request,
    json_body,
    json_error,
    services,
)
from backend.app.config import Config
from backend.app.services.deepseek_service import DeepSeekError
from backend.app.services.resume_service import ResumeError
from backend.app.services.question_bank import INTERVIEW_SCENES
from backend.app.services.score_service import fuse_interview_scores
from backend.app.storage.session_store import InterviewSession, utc_now
from backend.app.services.tts_service import (
    get_audio_mime_type,
    run_async,
)


logger = logging.getLogger(__name__)

ai_bp = Blueprint("ai", __name__)


# 简历结构化解析的系统提示词：要求模型只返回固定结构的 JSON。
RESUME_PARSE_SYSTEM_PROMPT = """
你是一名专业的简历结构化解析器。请从下面的简历文本中提取关键信息，并严格返回一个 JSON 对象。
所有字段必须基于简历原文，没有的内容使用空字符串或空数组，不得虚构。

返回 JSON 结构：
{
  "name": "姓名",
  "phone": "电话号码",
  "email": "邮箱",
  "education": ["教育经历描述"],
  "skills": ["技能关键词"],
  "projects": ["项目经历描述"],
  "work_experience": ["工作经历描述"],
  "keywords": ["用于检索的标签"],
  "summary": "一句话能力总结",
  "expected_position": "期望岗位",
  "years_of_experience": 0
}
""".strip()

# 浏览器上传音频时可接受的扩展名，限制识别输入范围。
ALLOWED_AUDIO_SUFFIXES = {
    ".wav",
    ".mp3",
    ".m4a",
    ".mp4",
    ".ogg",
    ".webm",
    ".flac",
}


def _coerce_str(value: Any, max_len: int = 200) -> str:
    # 把任意返回值转成有限长度的字符串，缺失时返回空串。
    return str(value or "").strip()[:max_len]


def _coerce_list(value: Any, max_items: int = 20, max_len: int = 500) -> list[str]:
    # 把模型返回值规范成字符串列表，剔除空项并限制数量与单条长度。
    if isinstance(value, list):
        items = value
    elif isinstance(value, str):
        items = [value]
    else:
        return []
    result: list[str] = []
    for item in items:
        text = str(item).strip()[:max_len]
        if text and text not in result:
            result.append(text)
    return result[:max_items]


def _normalize_resume_parse(data: dict[str, Any]) -> dict[str, Any]:
    # 只挑出我们需要并规整过的字段，避免把模型额外输出直接透传给调用方。
    return {
        "name": _coerce_str(data.get("name"), 40),
        "phone": _coerce_str(data.get("phone"), 40),
        "email": _coerce_str(data.get("email"), 80),
        "education": _coerce_list(data.get("education")),
        "skills": _coerce_list(data.get("skills")),
        "projects": _coerce_list(data.get("projects")),
        "work_experience": _coerce_list(data.get("work_experience")),
        "keywords": _coerce_list(data.get("keywords")),
        "summary": _coerce_str(data.get("summary"), 500),
        "expected_position": _coerce_str(data.get("expected_position"), 80),
        "years_of_experience": int(data.get("years_of_experience") or 0),
    }


@ai_bp.get("/ai/health")
def health():
    # 供 Java 后端探活：返回服务状态与关键能力是否可用。
    container = services()
    logger.info("AI 探活: capabilities=%s", container.capabilities())
    return jsonify(
        {
            "ok": True,
            "name": Config.APP_NAME,
            "capabilities": container.capabilities(),
        }
    )


@ai_bp.post("/ai/resume/parse")
def parse_resume():
    # 二选一：
    #  1) multipart：字段 file（或 resume）上传简历文件，Python 负责提取文本并解析；
    #  2) JSON：{"text": "..."}，调用方（Java）已提取文本，Python 只做结构化解析。
    text = ""
    # payload 用于承载可选的 job_role / difficulty；文件上传时为默认空对象。
    payload: dict = {}
    file = request.files.get("file") or request.files.get("resume")
    if file is not None:
        data = file.read()
        if not data:
            return json_error("简历文件为空。", 400)
        try:
            # 复用 ResumeService 的 PDF / DOCX / DOC 提取逻辑，仅提取文本不落盘。
            text = services().resumes.extract_text(file.filename or "resume", data)
            logger.info("简历解析-从文件提取文本: filename=%s, chars=%d", file.filename, len(text))
        except ResumeError as exc:
            return json_error(str(exc), 400)
    else:
        payload = json_body()
        text = str(payload.get("text") or "").strip()
        logger.info("简历解析-接收文本: chars=%d", len(text))

    if not text:
        logger.warning("简历解析-内容为空")
        return json_error("简历内容为空，请上传文件或提供文本。", 400)
    if len(text) > Config.RESUME_PROMPT_MAX_CHARS:
        # 与简历评估一致，超长内容先截断，避免撑爆模型上下文。
        logger.warning("简历解析-文本超长，已截断到 %d 字符", Config.RESUME_PROMPT_MAX_CHARS)
        text = text[: Config.RESUME_PROMPT_MAX_CHARS]
    logger.info("简历解析-待解析文本长度: %d", len(text))

    job_role = str(payload.get("job_role") or "").strip()[:60]
    difficulty = str(payload.get("difficulty") or "").strip()[:20]

    system_prompt = RESUME_PARSE_SYSTEM_PROMPT
    if job_role or difficulty:
        system_prompt += f"\n目标岗位：{job_role or '未指定'}\n面试难度：{difficulty or '未指定'}"

    user_prompt = f"请解析以下简历文本：\n\n<resume>\n{text}\n</resume>"

    try:
        # chat_json 会传 response_format=json_object 并要求模型输出 JSON 对象。
        logger.info("简历解析-调用 DeepSeek 结构化解析")
        t0 = time.perf_counter()
        data = services().deepseek.chat_json(
            [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ]
        )
        logger.info("简历解析-DeepSeek 返回成功，耗时 %.2fs", time.perf_counter() - t0)
    except Exception as exc:
        # DeepSeek 未配置、超时或返回异常时，统一转成面向调用方的错误。
        logger.error("简历解析-DeepSeek 失败: %s", exc)
        return json_error(f"简历解析失败：{exc}", 502)

    if not isinstance(data, dict):
        return json_error("简历解析结果格式异常。", 502)

    parsed = _normalize_resume_parse(data)
    warnings: list[str] = []
    if not parsed["name"] and not parsed["skills"]:
        warnings.append("未能从简历中识别出明确的姓名或技能，请检查文件格式。")
    logger.info("简历解析-完成: name=%s, skills=%d, keywords=%d, warnings=%s",
                parsed["name"], len(parsed["skills"]), len(parsed["keywords"]), warnings)

    return jsonify({"parsed": parsed, "raw_text": text, "warnings": warnings})


@ai_bp.post("/ai/tts")
def synthesize_speech():
    # 文字转语音：返回音频字节流，供 Java 转发给浏览器播放。
    # 入参：{"text": "...", "voice": "可选"}
    payload = json_body()
    text = str(payload.get("text") or "").strip()
    voice = str(payload.get("voice") or Config.DEFAULT_TTS_VOICE).strip()
    logger.info("TTS-请求: chars=%d, voice=%s", len(text), voice)
    if not text:
        return json_error("待合成文本不能为空。", 400)
    if len(text) > 5000:
        return json_error("单次语音合成文本不能超过 5000 个字符。", 400)

    try:
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
    logger.info("TTS-完成: size=%d", audio_path.stat().st_size)
    return send_file(
        audio_path,
        mimetype=get_audio_mime_type(audio_path),
        as_attachment=False,
        download_name=audio_path.name,
        conditional=True,
    )


@ai_bp.get("/ai/voices")
def list_voices():
    # 返回可用中文音色；远端查询失败时回退到固定列表，保证前端下拉可用。
    fallback = [
        {"short_name": "zh-CN-YunxiNeural", "label": "云希 · 男声"},
        {"short_name": "zh-CN-XiaoxiaoNeural", "label": "晓晓 · 女声"},
        {"short_name": "zh-CN-YunyangNeural", "label": "云扬 · 男声"},
    ]
    logger.info("音色-查询开始")
    try:
        raw_voices = run_async(services().tts.get_voices())
        chinese_voices = []
        for voice in raw_voices:
            short_name = str(voice.get("ShortName") or "")
            locale = str(voice.get("Locale") or "")
            if not short_name.startswith("zh-CN") and locale != "zh-CN":
                continue
            chinese_voices.append(
                {
                    "short_name": short_name,
                    "label": str(voice.get("FriendlyName") or short_name),
                }
            )
        if chinese_voices:
            logger.info("音色-查询成功: count=%d", len(chinese_voices))
            return jsonify({"voices": chinese_voices})
    except Exception:
        pass
    logger.info("音色-回退: 远端查询失败，返回 %d 个默认音色", len(fallback))
    return jsonify({"voices": fallback})


@ai_bp.post("/ai/asr")
def transcribe():
    # 语音转文字：接收 multipart 的 audio 文件，返回文本与分段。
    uploaded = request.files.get("audio")
    if uploaded is None:
        logger.warning("ASR-未收到音频文件")
        return json_error("请先录制或选择音频。", 400)
    logger.info("ASR-接收音频: filename=%s, size=%s", uploaded.filename, uploaded.content_length)

    original_suffix = Path(uploaded.filename or "").suffix.lower()
    if original_suffix not in ALLOWED_AUDIO_SUFFIXES:
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

    audio_path = Config.ASR_TEMP_DIR / f"asr_{uuid.uuid4().hex}{original_suffix}"
    uploaded.save(audio_path)
    try:
        if not audio_path.is_file() or audio_path.stat().st_size == 0:
            return json_error("录音文件为空，请重新录制。", 400)
        result = services().asr.transcribe(str(audio_path))
        logger.info("ASR-识别完成: chars=%d, language=%s", len(str(result.get("text") or "")), result.get("language"))
    except FileNotFoundError as exc:
        # 临时音频文件缺失（通常为上传异常），返回可重试的客户端错误
        logger.warning("ASR-音频文件缺失: %s", exc)
        return json_error("录音文件无效，请重新上传。", 400)
    except OSError as exc:
        # 文件存在但无法解析（如空/损坏的 webm），返回客户端错误
        logger.warning("ASR-音频无法解析: %s", exc)
        return json_error("录音文件无法解析，请重新录制。", 400)
    except Exception as exc:
        logger.error("ASR-识别失败: %s", exc)
        return json_error(f"语音识别失败：{exc}", 500)
    finally:
        try:
            audio_path.unlink(missing_ok=True)
        except OSError:
            pass

    segments = result.get("segments") or []
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

# ============================================================
# 会话态 AI 接口（供 Java 编排整场 AI 面试）
# 与上方无状态能力接口的区别：这些接口需要一个"AI 会话"上下文来承载
# 出题、追问、视觉累计与最终评估。Java 在开始面试时创建，结束/取消时清理。
# ============================================================


def _ai_session(session_id: str, user_id: str | None = None) -> InterviewSession | None:
    # 通过 sid 取会话；user_id 用于租户隔离，Java 可传候选人 Java 用户 ID，
    # 服务间可信调用也可不传（user_id=None 表示不按用户过滤）。
    return services().sessions.get(session_id, user_id)


def _message(
    role: str,
    content: str,
    *,
    message_type: str = "message",
    metadata: dict | None = None,
) -> dict:
    # 统一生成一条聊天消息。前端依赖 id 做列表 key，依赖 role/type 区分回答与问题。
    payload = {
        "id": uuid.uuid4().hex,
        "role": role,
        "content": content,
        "type": message_type,
        "created_at": utc_now(),
    }
    if metadata:
        payload.update(metadata)
    return payload


def _question_bank_item(question_id: str | None) -> dict | None:
    # 题库题号可能为空（例如简历题或追问），为空时直接返回 None。
    if not question_id:
        return None
    return services().question_bank.get(question_id)


def _scene(scene_index: int) -> dict:
    # 用取余让场景列表循环使用，避免数组越界。
    return dict(INTERVIEW_SCENES[scene_index % len(INTERVIEW_SCENES)])


def _opening_plan(item: InterviewSession) -> dict:
    # 计算"第一题怎么出"。follow_up_target 表示计划追加几道追问。
    target = services().question_bank.follow_up_target(item.follow_up_mode)
    if item.resume_content:
        # 有简历时，第一题优先围绕简历经历展开。
        return {
            "mode": "opening",
            "question_number": 1,
            "question_limit": item.question_limit,
            "scene_id": "resume",
            "scene_label": "简历导入",
            "scene_focus": "从候选人简历中选择最有代表性的经历开始核验。",
            "follow_up_number": 0,
            "follow_up_target": target,
            "bank_question_id": None,
        }
    selected_scene = _scene(0)
    bank_item = services().question_bank.pick(
        selected_scene["id"],
        excluded_ids=item.used_bank_question_ids,
    )
    return {
        "mode": "opening",
        "question_number": 1,
        "question_limit": item.question_limit,
        "scene_id": selected_scene["id"],
        "scene_index": 0,
        "scene_label": selected_scene["label"],
        "scene_focus": selected_scene["focus"],
        "follow_up_number": 0,
        "follow_up_target": target,
        "bank_question_id": bank_item["id"] if bank_item else None,
    }


def _next_plan(item: InterviewSession) -> dict:
    # 当前主问题还没问满追问次数时，下一条消息继续作为该主问题的追问。
    question_number = item.question_count + 1
    if item.current_follow_up_count < item.follow_up_target:
        return {
            "mode": "follow_up",
            "question_number": question_number,
            "question_limit": item.question_limit,
            "scene_id": item.current_scene_id,
            "scene_label": item.current_scene_label,
            "scene_focus": "",
            "follow_up_number": item.current_follow_up_count + 1,
            "follow_up_target": item.follow_up_target,
            "bank_question_id": item.current_bank_question_id,
        }
    # 简历项目与基础场景交替，避免连续多题只围绕一个方向。
    use_resume = bool(item.resume_content) and item.current_scene_id != "resume"
    if use_resume:
        scene_id = "resume"
    else:
        scene_index = item.scene_index + 1
        selected_scene = _scene(scene_index)
        scene_id = selected_scene["id"]
    bank_item = services().question_bank.pick(
        scene_id,
        excluded_ids=item.used_bank_question_ids,
    )
    return {
        "mode": "scene",
        "question_number": question_number,
        "question_limit": item.question_limit,
        "scene_id": scene_id,
        "scene_index": item.scene_index + 1 if not use_resume else item.scene_index,
        "scene_label": "简历导入" if use_resume else selected_scene.get("label", ""),
        "scene_focus": "",
        "follow_up_number": 0,
        "follow_up_target": target if (target := services().question_bank.follow_up_target(item.follow_up_mode)) else 0,
        "bank_question_id": bank_item["id"] if bank_item else None,
    }


def _apply_plan(item: InterviewSession, plan: dict) -> None:
    # 把计划写入会话对象。调用前必须持有 item.lock。
    item.question_count = int(plan["question_number"])
    item.current_scene_id = str(plan["scene_id"])
    item.current_scene_label = str(plan["scene_label"])
    item.scene_index = int(plan.get("scene_index", item.scene_index))
    item.current_question_mode = str(plan["mode"])
    item.current_follow_up_count = int(plan.get("follow_up_number") or 0)
    item.follow_up_target = int(plan.get("follow_up_target") or 0)
    item.current_bank_question_id = plan.get("bank_question_id")
    if item.current_bank_question_id:
        qid = str(item.current_bank_question_id)
        if qid not in item.used_bank_question_ids:
            item.used_bank_question_ids.append(qid)
    item.checkpoint_reason = None


def _question_metadata(plan: dict) -> dict:
    # 提取前端展示所需的轻量元数据，不把整个内部计划对象返回。
    return {
        "question_number": int(plan["question_number"]),
        "question_limit": int(plan["question_limit"]),
        "scene_id": str(plan["scene_id"]),
        "scene_label": str(plan["scene_label"]),
        "question_mode": str(plan["mode"]),
        "follow_up_number": int(plan.get("follow_up_number") or 0),
        "follow_up_target": int(plan.get("follow_up_target") or 0),
        "bank_question_id": plan.get("bank_question_id"),
    }


def _generate_question(
    item: InterviewSession,
    plan: dict,
    history: list[dict],
    user_message: str,
) -> str:
    # 场景主问题优先使用题库原文；追问与简历题交给大模型生成。
    bank_item = _question_bank_item(plan.get("bank_question_id"))
    if str(plan.get("mode")) == "scene" and bank_item is not None:
        return str(bank_item["content"]).strip()
    prompt_plan = dict(plan)
    prompt_plan["bank_question"] = bank_item["content"] if bank_item else ""
    return services().interview.reply(
        history,
        user_message,
        job_role=item.job_role,
        difficulty=item.difficulty,
        interview_plan=prompt_plan,
        resume_content=item.resume_content,
    )


@ai_bp.post("/ai/sessions")
def create_ai_session():
    # 创建一场 AI 面试会话。Java 在 startInterview 时调用，并保存返回的 session_id。
    payload = json_body()
    user_id = str(payload.get("user_id") or "").strip() or None
    job_role = str(payload.get("job_role") or services().interview.DEFAULT_JOB_ROLE).strip()
    difficulty = str(payload.get("difficulty") or "中级").strip()
    voice = str(payload.get("voice") or Config.DEFAULT_TTS_VOICE).strip()
    logger.info("创建 AI 会话: user_id=%s, job_role=%s, difficulty=%s", user_id, job_role, difficulty)
    auto_tts = bool(payload.get("auto_tts", True))
    follow_up_mode = str(payload.get("follow_up_mode") or "random").strip().lower()
    if follow_up_mode not in {"random", "1", "2", "3"}:
        return json_error("追问次数仅支持 random、1、2 或 3。", 400)
    try:
        analysis_fps = float(payload.get("analysis_fps") or Config.DEFAULT_VISION_FPS)
        snapshot_interval = float(payload.get("snapshot_interval") or Config.SNAPSHOT_INTERVAL_SECONDS)
    except (TypeError, ValueError):
        return json_error("分析频率参数无效。", 400)
    analysis_fps = max(1.0, min(Config.MAX_VISION_FPS, analysis_fps))
    snapshot_interval = max(0.5, min(10.0, snapshot_interval))
    if not job_role or len(job_role) > 100:
        return json_error("岗位名称应为 1-100 个字符。", 400)
    if not difficulty or len(difficulty) > 20:
        return json_error("面试难度无效。", 400)

    resume_id = str(payload.get("resume_id") or "").strip() or None
    resume = None
    if resume_id:
        resume = services().resumes.get(user_id or "ai-service", resume_id)
        if resume is None:
            return json_error("所选简历不存在。", 400)

    try:
        item = services().sessions.create(
            user_id=user_id or "ai-service",
            job_role=job_role,
            difficulty=difficulty,
            voice=voice or Config.DEFAULT_TTS_VOICE,
            auto_tts=auto_tts,
            analysis_fps=analysis_fps,
            snapshot_interval=snapshot_interval,
            resume_id=resume.id if resume else None,
            resume_filename=resume.filename if resume else None,
            resume_content=resume.content if resume else "",
            follow_up_mode=follow_up_mode,
            question_limit=Config.INTERVIEW_QUESTION_LIMIT,
        )
    except (TypeError, ValueError) as exc:
        return json_error(f"面试参数无效：{exc}", 400)

    logger.info("AI 会话已创建: session_id=%s", item.id)
    response = item.public_state()
    response["opening_required"] = True
    return jsonify(response), 201


@ai_bp.get("/ai/sessions/<session_id>")
def get_ai_session(session_id: str):
    # 读取会话公开状态，供 Java 在刷新 / 重连时恢复。
    logger.info("读取 AI 会话: session_id=%s", session_id)
    item = _ai_session(session_id)
    if item is None:
        return json_error("面试会话不存在。", 404)
    return jsonify(item.public_state())


@ai_bp.post("/ai/sessions/<session_id>/opening")
def open_ai_session(session_id: str):
    # 生成开场白与第一题，已生成则直接返回，重复调用安全。
    logger.info("AI 出题-生成首题: session_id=%s", session_id)
    item = _ai_session(session_id)
    if item is None:
        return json_error("面试会话不存在。", 404)
    with item.lock:
        existing = next(
            (m for m in item.transcript if m.get("role") == "assistant"),
            None,
        )
    if existing is not None:
        return jsonify({"message": existing, "warning": None, "session": item.public_state()})

    warning = None
    plan = _opening_plan(item)
    try:
        first_question = _generate_question(
            item, plan, [], "请开始这场模拟面试。先用一句话问候候选人，随即按当前面试计划提出第一个问题。"
        )
    except (DeepSeekError, ValueError) as exc:
        warning = f"首题生成失败，已使用备用开场问题：{exc}"
        first_question = (
            "你好，欢迎参加本次模拟面试。请结合简历中最熟悉的一段项目经历，"
            "说明你的具体职责、核心方案以及最终结果。"
            if item.resume_content
            else "你好，欢迎参加本次模拟面试。请先做一个自我介绍，重点说明与你目标岗位最匹配的一段经历。"
        )

    logger.info("AI 出题-首题完成: warning=%s", warning)
    record = _message("assistant", first_question, message_type="question", metadata=_question_metadata(plan))
    with item.lock:
        _apply_plan(item, plan)
        item.transcript.append(record)
    return jsonify({"message": record, "warning": warning, "session": item.public_state()})


@ai_bp.post("/ai/sessions/<session_id>/messages")
def send_ai_message(session_id: str):
    # 接收候选人回答（通常为 ASR 文本），生成下一题或进入检查点。
    item = _ai_session(session_id)
    if item is None:
        return json_error("面试会话不存在。", 404)
    if item.status != "active":
        return json_error("当前面试已结束。", 409)

    payload = json_body()
    user_message = str(payload.get("message") or "").strip()
    logger.info("AI 消息-接收回答: chars=%d", len(user_message))
    if not user_message:
        return json_error("回答内容不能为空。", 400)
    if len(user_message) > 5000:
        return json_error("单次回答不能超过 5000 个字符。", 400)

    with item.lock:
        if item.question_count == 0:
            return json_error("请先生成首道面试问题。", 409)
        at_question_limit = item.question_count >= item.question_limit
        history = list(item.transcript)
        plan = None if at_question_limit else _next_plan(item)

    answer = ""
    if plan is not None:
        try:
            answer = _generate_question(item, plan, history, user_message)
        except (DeepSeekError, ValueError) as exc:
            return json_error(str(exc), 502)

    user_record = _message("user", user_message, message_type="answer")
    assistant_record = None
    with item.lock:
        if item.status != "active":
            return json_error("当前面试状态已变化，请刷新页面后重试。", 409)
        item.transcript.append(user_record)
        if at_question_limit:
            item.status = "checkpoint"
            item.checkpoint_reason = "question_limit"
        else:
            assistant_record = _message(
                "assistant", answer, message_type="question", metadata=_question_metadata(plan)
            )
            item.transcript.append(assistant_record)
            _apply_plan(item, plan)

    checkpoint = None
    if item.status == "checkpoint":
        checkpoint = {
            "required": True,
            "reason": item.checkpoint_reason,
            "question_count": item.question_count,
            "question_limit": item.question_limit,
        }
    logger.info("AI 消息-已处理: checkpoint=%s", checkpoint is not None)
    return jsonify(
        {
            "user_message": user_record,
            "assistant_message": assistant_record,
            "checkpoint": checkpoint,
            "session": item.public_state(),
        }
    )


@ai_bp.post("/ai/sessions/<session_id>/frames")
def analyze_ai_frame(session_id: str):
    # 接收摄像头帧并入队视觉分析，返回实时指标。
    logger.info("视觉帧-接收: session_id=%s", session_id)
    item = _ai_session(session_id)
    if item is None:
        return json_error("面试会话不存在。", 404)
    if item.status != "active" or item.analyzer is None:
        return json_error("当前面试未处于进行状态。", 409)
    try:
        image_bytes = image_bytes_from_request()
        if item.vision_worker is None:
            return json_error("视觉分析服务尚未就绪。", 503)
        accepted, replaced = item.vision_worker.submit(image_bytes)
        if not accepted:
            return json_error("视觉分析服务已停止。", 409)
        metrics = item.analyzer.get_live_metrics()
        vision = item.vision_worker.status()
    except (ApiValidationError, ValueError) as exc:
        return json_error(str(exc), 400)
    except Exception as exc:
        return json_error(f"视觉分析失败：{exc}", 500)
    logger.info("视觉帧-已入队: accepted=%s, replaced=%s", accepted, replaced)
    return jsonify({"accepted": True, "replaced_pending": replaced, "metrics": metrics, "vision": vision})


@ai_bp.get("/ai/sessions/<session_id>/metrics")
def get_ai_metrics(session_id: str):
    # 前端定期拉取实时视觉指标。
    logger.info("视觉指标-拉取: session_id=%s", session_id)
    item = _ai_session(session_id)
    if item is None:
        return json_error("面试会话不存在。", 404)
    if item.analyzer is None:
        return jsonify({"metrics": {}})
    return jsonify(
        {
            "metrics": item.analyzer.get_live_metrics(),
            "vision": item.vision_worker.status() if item.vision_worker is not None else {},
        }
    )


@ai_bp.post("/ai/evaluate")
def evaluate_transcript():
    """
    评测外部传入的问答记录（不经过会话出题流程）。

    Java 主平台按自己的题库出题收卷，与 Python 侧的出题系统并不同源。
    若复用会话的 /finish，大模型会拿 Python 自己的题目去配对 Java 考生的答案，
    产生“答非所问”的误判，导致报告与输入不对应。
    本接口让调用方把真实作答的“题目 + 回答”直接传入，
    使评测结果与考生实际回答的题目对齐，报告可追溯到输入。
    """
    payload = json_body()
    raw_pairs = payload.get("qa_pairs")
    if not isinstance(raw_pairs, list) or not raw_pairs:
        return json_error("qa_pairs 不能为空。", 400)

    history = []
    answer_count = 0
    for index, item in enumerate(raw_pairs, start=1):
        if not isinstance(item, dict):
            continue
        question = str(item.get("question") or "").strip()
        answer = str(item.get("answer") or "").strip()
        if not answer:
            # 未作答的题目不进入评测，避免无意义地拉低得分
            continue
        if question:
            history.append({
                "role": "assistant",
                "content": question,
                "type": "question",
                "question_number": index,
            })
        history.append({"role": "user", "content": answer, "type": "answer"})
        answer_count += 1

    if answer_count == 0:
        return json_error("没有有效回答可供评测。", 400)

    job_role = str(payload.get("job_role") or "").strip()
    difficulty = str(payload.get("difficulty") or "中级").strip()
    video_summary = payload.get("video_summary")

    logger.info("AI 独立评测-开始: 题目数=%d, 有效回答=%d", len(raw_pairs), answer_count)
    t0 = time.perf_counter()
    try:
        evaluation = services().interview.evaluate(
            history,
            job_role=job_role,
            difficulty=difficulty,
        )
    except DeepSeekError as exc:
        return json_error(str(exc), 502)
    except ValueError as exc:
        return json_error(str(exc), 400)

    score_fusion = fuse_interview_scores(
        evaluation.get("overall_score"),
        video_summary if isinstance(video_summary, dict) else None,
    )
    logger.info("AI 独立评测-完成: 耗时=%.2fs, overall=%s, final_score=%s",
                time.perf_counter() - t0,
                evaluation.get("overall_score"),
                score_fusion.get("final_score"))
    return jsonify({
        "question_evaluation": evaluation,
        "score_fusion": score_fusion,
        "answer_count": answer_count,
    })


@ai_bp.post("/ai/sessions/<session_id>/finish")
def finish_ai_session(session_id: str):
    # 结束并评估，返回最终报告；重复调用直接返回已有报告，避免重复扣费。
    logger.info("AI 评估-开始: session_id=%s", session_id)
    item = _ai_session(session_id)
    if item is None:
        return json_error("面试会话不存在。", 404)
    with item.lock:
        if item.status == "finished" and item.report is not None:
            return jsonify({"report": item.report})
        answer_count = sum(1 for m in item.transcript if m.get("role") == "user")
        if answer_count == 0:
            return json_error("请至少完成一次回答后再结束面试。", 400)
        if item.video_summary is None and item.analyzer is not None:
            if item.vision_worker is not None:
                item.vision_worker.stop()
            item.video_summary = item.analyzer.stop_session()
        item.status = "evaluating"
        history = list(item.transcript)
        video_summary = item.video_summary

    logger.info("AI 评估-调用 DeepSeek 评测: 回答数=%d", len(history))
    t0 = time.perf_counter()
    try:
        evaluation = services().interview.evaluate(
            history,
            job_role=item.job_role,
            difficulty=item.difficulty,
            resume_filename=item.resume_filename or "",
            resume_content=item.resume_content,
        )
    except DeepSeekError as exc:
        with item.lock:
            item.status = "stopped"
        return json_error(str(exc), 502)
    except ValueError as exc:
        with item.lock:
            item.status = "stopped"
        return json_error(str(exc), 400)

    score_fusion = fuse_interview_scores(evaluation.get("overall_score"), video_summary)
    logger.info("AI 评估-完成: 耗时=%.2fs, overall=%s, final_score=%s",
                time.perf_counter() - t0, evaluation.get("overall_score"), score_fusion.get("final_score"))
    finished_at = utc_now()
    report = {
        "version": 1,
        "session_id": item.id,
        "created_at": item.created_at,
        "finished_at": finished_at,
        "job_role": item.job_role,
        "difficulty": item.difficulty,
        "resume": ({"id": item.resume_id, "filename": item.resume_filename} if item.resume_id else None),
        "follow_up_mode": item.follow_up_mode,
        "question_limit": item.question_limit,
        "duration_seconds": (video_summary.get("duration_seconds", 0) if video_summary else 0),
        "transcript": history,
        "question_evaluation": evaluation,
        "video_summary": video_summary,
        "score_fusion": score_fusion,
        "privacy": {
            "stores_video_frames": False,
            "stores_face_images": False,
            "stores_audio": False,
        },
    }
    try:
        services().reports.save(item.user_id, report)
    except OSError as exc:
        with item.lock:
            item.status = "stopped"
        return json_error(f"评估报告保存失败：{exc}", 500)

    with item.lock:
        item.report = report
        item.finished_at = finished_at
        item.status = "finished"
    return jsonify({"report": report})


@ai_bp.delete("/ai/sessions/<session_id>")
def delete_ai_session(session_id: str):
    # 放弃本轮面试：丢弃对话与由视觉采集的指标，不生成报告。
    logger.info("删除 AI 会话: session_id=%s", session_id)
    item = _ai_session(session_id)
    if item is None:
        return json_error("面试会话不存在。", 404)
    # 以会话自身持有的 user_id 删除，确保服务间调用也能正确清理。
    services().sessions.delete(session_id, item.user_id)
    return jsonify({"ok": True})
