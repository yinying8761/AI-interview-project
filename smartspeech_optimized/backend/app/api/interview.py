from __future__ import annotations

# 本文件是模拟面试的核心 API 入口，串起了完整业务链路：
# 创建会话 -> 生成首题 -> 持续接收回答与摄像头帧 -> 生成追问 -> 结束评估 -> 保存/查看报告。
# 视图函数只负责 HTTP 和流程控制，题库、模型、会话、视觉、报告分别调用对应 Service。

from typing import Any
from uuid import uuid4

from flask import Blueprint, jsonify, session

from backend.app.api.common import (
    ApiValidationError,
    image_bytes_from_request,
    json_body,
    json_error,
    login_required,
    services,
)
from backend.app.config import Config
from backend.app.services.deepseek_service import DeepSeekError
from backend.app.services.question_bank import INTERVIEW_SCENES
from backend.app.services.score_service import fuse_interview_scores
from backend.app.storage.session_store import InterviewSession, utc_now


# 这个蓝图由 api/__init__.py 收集，再由 app/__init__.py 挂到 /api 前缀下。
interview_bp = Blueprint("interview", __name__)


def _owned_session(session_id: str) -> InterviewSession | None:
    # 查询会话时同时带上前端传来的 session_id 和当前登录用户 user_id。
    # 这样用户即使猜到别人的面试编号，也无法读取或修改对方的会话。
    return services().sessions.get(session_id, session["user_id"])


def _message(
    role: str,
    content: str,
    *,
    message_type: str = "message",
    metadata: dict[str, Any] | None = None,
) -> dict[str, Any]:
    # 统一生成一条聊天消息。前端依赖 id 做列表 key，依赖 role/type 区分
    # 候选人回答、AI 问题和系统消息；metadata 可附带题号、场景等额外信息。
    payload = {
        "id": uuid4().hex,
        "role": role,
        "content": content,
        "type": message_type,
        "created_at": utc_now(),
    }
    if metadata:
        payload.update(metadata)
    return payload


def _question_bank_item(question_id: str | None) -> dict[str, Any] | None:
    # question_id 可能为空（例如简历题或追问）；为空时直接返回 None。
    if not question_id:
        return None
    return services().question_bank.get(question_id)


def _scene(scene_index: int) -> dict[str, str]:
    # 用取余让场景列表循环使用，场景用完后从头开始，不会数组越界。
    return dict(INTERVIEW_SCENES[scene_index % len(INTERVIEW_SCENES)])


def _opening_plan(item: InterviewSession) -> dict[str, Any]:
    # 计算“第一题怎么出”。follow_up_target 表示这场面试计划追加几道追问。
    target = services().question_bank.follow_up_target(item.follow_up_mode)
    if item.resume_content:
        # 如果用户绑定了简历，第一题优先围绕简历经历展开，而不是固定题库。
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
    # 没有简历时，从第一个基础场景的题库中随机选题。
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


def _next_plan(item: InterviewSession) -> dict[str, Any]:
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
        scene_index = item.scene_index
        scene_label = "简历项目"
        scene_focus = "继续核验简历中其他项目、职责、技术取舍和结果。"
        bank_item = None
    else:
        # 切换到一个新基础场景，并从该场景中挑一道没有使用过的题。
        scene_index = item.scene_index + 1
        selected_scene = _scene(scene_index)
        scene_id = selected_scene["id"]
        scene_label = selected_scene["label"]
        scene_focus = selected_scene["focus"]
        bank_item = services().question_bank.pick(
            scene_id,
            excluded_ids=item.used_bank_question_ids,
        )

    return {
        "mode": "resume" if use_resume else "scene",
        "question_number": question_number,
        "question_limit": item.question_limit,
        "scene_id": scene_id,
        "scene_index": scene_index,
        "scene_label": scene_label,
        "scene_focus": scene_focus,
        "follow_up_number": 0,
        "follow_up_target": services().question_bank.follow_up_target(
            item.follow_up_mode
        ),
        "bank_question_id": bank_item["id"] if bank_item else None,
    }


def _apply_plan(item: InterviewSession, plan: dict[str, Any]) -> None:
    # 把计划真正写入会话对象。调用此函数时，外部必须已经持有 item.lock，
    # 防止两个并发请求同时修改题号、场景或已用题库编号。
    item.question_count = int(plan["question_number"])
    item.current_scene_id = str(plan["scene_id"])
    item.current_scene_label = str(plan["scene_label"])
    item.scene_index = int(plan.get("scene_index", item.scene_index))
    item.current_question_mode = str(plan["mode"])
    item.current_follow_up_count = int(plan.get("follow_up_number") or 0)
    item.follow_up_target = int(plan.get("follow_up_target") or 0)
    item.current_bank_question_id = plan.get("bank_question_id")
    if item.current_bank_question_id:
        # 记录已经使用过的题库题号，后续 pick() 会避开它们，减少重复。
        question_id = str(item.current_bank_question_id)
        if question_id not in item.used_bank_question_ids:
            item.used_bank_question_ids.append(question_id)
    item.checkpoint_reason = None


def _question_metadata(plan: dict[str, Any]) -> dict[str, Any]:
    # 提取前端展示所需的轻量元数据，不把整个内部计划对象直接返回。
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
    plan: dict[str, Any],
    history: list[dict[str, Any]],
    user_message: str,
) -> str:
    # bank_item 是本题对应的题库记录；简历题和追问通常没有题库记录。
    bank_item = _question_bank_item(plan.get("bank_question_id"))
    # 场景主问题直接使用题库原文，避免模型受上一轮回答影响而偏离场景。
    if str(plan.get("mode")) == "scene" and bank_item is not None:
        return str(bank_item["content"]).strip()

    prompt_plan = dict(plan)
    prompt_plan["bank_question"] = bank_item["content"] if bank_item else ""
    # scene 模式若有题库原文就直接返回；其它模式把完整计划和历史对话交给大模型，
    # 由 InterviewService 组织 Prompt，请求 DeepSeek 生成下一题。
    return services().interview.reply(
        history,
        user_message,
        job_role=item.job_role,
        difficulty=item.difficulty,
        interview_plan=prompt_plan,
        resume_content=item.resume_content,
    )


@interview_bp.post("/interviews")
@login_required
def create_interview():
    # 创建面试时只做参数清理与校验，不立即生成首题。
    # 这样浏览器能快速拿到会话编号，随后通过 /opening 单独等待模型生成。
    payload = json_body()
    job_role = str(
        payload.get("job_role")
        or services().interview.DEFAULT_JOB_ROLE
    ).strip()
    difficulty = str(payload.get("difficulty") or "中级").strip()
    voice = str(payload.get("voice") or Config.DEFAULT_TTS_VOICE).strip()
    auto_tts = bool(payload.get("auto_tts", True))
    follow_up_mode = str(payload.get("follow_up_mode") or "random").strip().lower()
    if follow_up_mode not in {"random", "1", "2", "3"}:
        return json_error("追问次数仅支持 random、1、2 或 3。", 400)
    # 摄像头分析频率限制在 1.0 到 Config.MAX_VISION_FPS 之间；
    # max(1.0, min(上限, 用户值)) 的写法同时处理过小和过大的输入。
    analysis_fps = max(
        1.0,
        min(
            Config.MAX_VISION_FPS,
            float(payload.get("analysis_fps") or Config.DEFAULT_VISION_FPS),
        ),
    )
    snapshot_interval = max(
        # 截图保存分析结果的间隔限制在 0.5 到 10 秒之间。
        0.5,
        min(
            10.0,
            float(
                payload.get("snapshot_interval")
                or Config.SNAPSHOT_INTERVAL_SECONDS
            ),
        ),
    )

    if not job_role or len(job_role) > 100:
        return json_error("岗位名称应为 1-100 个字符。", 400)
    if not difficulty or len(difficulty) > 20:
        return json_error("面试难度无效。", 400)

    resume_id = str(payload.get("resume_id") or "").strip() or None
    resume = None
    if resume_id:
        # 只能使用当前登录用户自己的简历，防止通过伪造 resume_id 读取他人资料。
        resume = services().resumes.get(str(session["user_id"]), resume_id)
        if resume is None:
            return json_error("所选简历不存在或不属于当前账号。", 400)

    try:
        # SessionStore 会创建一个带锁的 InterviewSession 对象，并启动视觉分析器。
        # 简历的文本内容会复制到会话中，后续模型生成题目时可直接使用。
        item = services().sessions.create(
            user_id=str(session["user_id"]),
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

    # 会话先返回，首题由 /opening 异步生成，避免候选人等待大模型时才看到画面。
    response = item.public_state()
    # 告知前端：创建成功，但第一道问题还需要再调用 /opening。
    response["opening_required"] = True
    return jsonify(response), 201


@interview_bp.post("/interviews/<session_id>/opening")
@login_required
def open_interview(session_id: str):
    """生成开场白与第一道题，已生成则直接返回，重复调用安全。"""
    item = _owned_session(session_id)
    if item is None:
        return json_error("面试会话不存在或不属于当前账号。", 404)

    with item.lock:
        # 加锁读取，避免与另一个“生成首题”请求同时修改 transcript。
        # 若已有 assistant 消息，说明首题已经生成，直接返回旧记录，保证重复调用安全。
        existing = next(
            (
                message
                for message in item.transcript
                if message.get("role") == "assistant"
            ),
            None,
        )
    if existing is not None:
        return jsonify({"message": existing, "warning": None, "session": item.public_state()})

    warning = None
    # 先生成计划，再根据计划生成题目；生成模型请求期间不长期占用会话锁。
    plan = _opening_plan(item)
    try:
        first_question = _generate_question(
            item,
            plan,
            [],
            (
                "请开始这场模拟面试。先用一句话问候候选人，"
                "随即严格按当前面试计划提出第一个问题。"
            ),
        )
    except (DeepSeekError, ValueError) as exc:
        # 大模型暂时不可用时仍给候选人一个可继续作答的备用问题，
        # 同时把 warning 返回前端，不把整场面试卡在开场阶段。
        warning = f"首题生成失败，已使用备用开场问题：{exc}"
        if item.resume_content:
            first_question = (
                "你好，欢迎参加本次模拟面试。请结合简历中你最熟悉的一段项目经历，"
                "说明你的具体职责、采用的核心方案以及最终结果。"
            )
        else:
            first_question = (
                "你好，欢迎参加本次模拟面试。请先做一个自我介绍，"
                "重点说明与你目标岗位最匹配的一段经历。"
            )

    record = _message(
        "assistant",
        first_question,
        message_type="question",
        metadata=_question_metadata(plan),
    )
    with item.lock:
        # 题目生成成功后，在锁内一次性更新题号和聊天记录。
        _apply_plan(item, plan)
        item.transcript.append(record)

    return jsonify(
        {
            "message": record,
            "warning": warning,
            "session": item.public_state(),
        }
    )


@interview_bp.get("/interviews/<session_id>")
@login_required
def get_interview(session_id: str):
    # 页面刷新或轮询时读取会话公开状态，包含聊天记录、进度和当前状态。
    item = _owned_session(session_id)
    if item is None:
        return json_error("面试会话不存在或不属于当前账号。", 404)
    return jsonify(item.public_state())


@interview_bp.get("/interviews/<session_id>/metrics")
@login_required
def get_interview_metrics(session_id: str):
    # 前端定期拉取实时视觉指标。analyzer 为空表示没有启用摄像头分析。
    item = _owned_session(session_id)
    if item is None:
        return json_error("面试会话不存在或不属于当前账号。", 404)
    if item.analyzer is None:
        return jsonify({"metrics": {}})
    return jsonify(
        {
            "metrics": item.analyzer.get_live_metrics(),
            "vision": (
                item.vision_worker.status()
                if item.vision_worker is not None
                else {}
            ),
        }
    )


@interview_bp.delete("/interviews/<session_id>")
@login_required
def delete_interview(session_id: str):
    """放弃本轮面试：丢弃采样指标与对话，不生成报告。"""
    # SessionStore 删除失败通常表示会话不存在，或该会话不属于当前用户。
    if not services().sessions.delete(session_id, str(session["user_id"])):
        return json_error("面试会话不存在或不属于当前账号。", 404)
    return jsonify({"ok": True})


@interview_bp.post("/interviews/<session_id>/frames")
@login_required
def analyze_interview_frame(session_id: str):
    # 浏览器把摄像头帧发到这里。接口先确认会话仍进行中，再把图片放入视觉队列。
    item = _owned_session(session_id)
    if item is None:
        return json_error("面试会话不存在或不属于当前账号。", 404)
    if item.status != "active" or item.analyzer is None:
        return json_error("当前面试未处于进行状态。", 409)

    try:
        image_bytes = image_bytes_from_request()
        if item.vision_worker is None:
            return json_error("视觉分析服务尚未就绪。", 503)
        # submit 只负责入队，不会在本请求里等待人脸/表情模型算完。
        # accepted=False 表示后台工作线程已经停止；replaced=True 表示上一帧还没处理完，
        # 新帧替换了待处理帧，优先保证实时性。
        accepted, replaced = item.vision_worker.submit(image_bytes)
        if not accepted:
            return json_error("视觉分析服务已停止。", 409)
        metrics = item.analyzer.get_live_metrics()
        vision = item.vision_worker.status()
    except ApiValidationError as exc:
        return json_error(str(exc), 400)
    except ValueError as exc:
        return json_error(str(exc), 400)
    except Exception as exc:
        return json_error(f"视觉分析失败：{exc}", 500)

    return jsonify(
        {
            "accepted": True,
            "replaced_pending": replaced,
            "metrics": metrics,
            "vision": vision,
        }
    )


@interview_bp.post("/interviews/<session_id>/messages")
@login_required
def send_interview_message(session_id: str):
    # 这是面试进行中最关键的接口：保存候选人回答，并决定下一题或进入检查点。
    item = _owned_session(session_id)
    if item is None:
        return json_error("面试会话不存在或不属于当前账号。", 404)
    if item.status != "active":
        return json_error("当前面试已结束。", 409)

    payload = json_body()
    user_message = str(payload.get("message") or "").strip()
    if not user_message:
        return json_error("回答内容不能为空。", 400)
    if len(user_message) > 5000:
        return json_error("单次回答不能超过 5000 个字符。", 400)

    with item.lock:
        if item.question_count == 0:
            return json_error("请先生成首道面试问题。", 409)
        # 锁内快速制作一份当前状态快照：history 用于交给大模型，
        # at_question_limit 判断答完本题后是否达到整场题目上限。
        at_question_limit = item.question_count >= item.question_limit
        history = list(item.transcript)
        plan = None if at_question_limit else _next_plan(item)

    answer = ""
    if plan is not None:
        try:
            # 模型调用耗时较长，因此放在锁外。此时其它请求仍可读取会话状态。
            answer = _generate_question(
                item,
                plan,
                history,
                user_message,
            )
        except DeepSeekError as exc:
            return json_error(str(exc), 502)
        except ValueError as exc:
            return json_error(str(exc), 400)

    user_record = _message("user", user_message, message_type="answer")
    assistant_record = None
    with item.lock:
        # 模型调用期间状态可能被“结束面试”接口改变，提交前必须重新检查。
        if item.status != "active":
            return json_error("当前面试状态已变化，请刷新页面后重试。", 409)
        item.transcript.append(user_record)
        if at_question_limit:
            # 已达上限：不再生成下一题，把状态改成 checkpoint，让前端询问用户
            # 是继续加题还是结束并生成报告。
            item.status = "checkpoint"
            item.checkpoint_reason = "question_limit"
        else:
            assistant_record = _message(
                "assistant",
                answer,
                message_type="question",
                metadata=_question_metadata(plan),
            )
            item.transcript.append(assistant_record)
            _apply_plan(item, plan)

    checkpoint = None
    if item.status == "checkpoint":
        # checkpoint 是一个明确的“等待用户决定”信号，而不是普通错误。
        checkpoint = {
            "required": True,
            "reason": item.checkpoint_reason,
            "question_count": item.question_count,
            "question_limit": item.question_limit,
        }

    return jsonify(
        {
            "user_message": user_record,
            "assistant_message": assistant_record,
            "checkpoint": checkpoint,
            "session": item.public_state(),
        }
    )


@interview_bp.post("/interviews/<session_id>/finish")
@login_required
def finish_interview(session_id: str):
    # 结束接口可能因网络重试而重复调用；已有报告时直接返回，避免重复扣费评估。
    item = _owned_session(session_id)
    if item is None:
        return json_error("面试会话不存在或不属于当前账号。", 404)

    with item.lock:
        if item.status == "finished" and item.report is not None:
            return jsonify({"report": item.report})

        # 至少一条 user 消息才允许评估，否则没有回答可用于评分。
        answer_count = sum(
            1 for message in item.transcript if message.get("role") == "user"
        )
        if answer_count == 0:
            return json_error("请至少完成一次回答后再结束面试。", 400)

        if item.video_summary is None and item.analyzer is not None:
            # 停止视觉工作线程并汇总本场表情、注意力等指标。
            # stop_session 返回的是统计数据，不保存原始视频或人脸图片。
            if item.vision_worker is not None:
                item.vision_worker.stop()
            item.video_summary = item.analyzer.stop_session()
        item.status = "evaluating"
        # 先复制必要数据到局部变量，后面调用大模型时不再一直占用会话锁。
        history = list(item.transcript)
        video_summary = item.video_summary

    try:
        # 将整场问答、岗位、难度和简历交给 InterviewService 做最终评估。
        evaluation = services().interview.evaluate(
            history,
            job_role=item.job_role,
            difficulty=item.difficulty,
            resume_filename=item.resume_filename or "",
            resume_content=item.resume_content,
        )
    except DeepSeekError as exc:
        with item.lock:
            # 模型失败时把状态设为 stopped，用户可以重新点击结束并重试。
            item.status = "stopped"
        return json_error(str(exc), 502)
    except ValueError as exc:
        with item.lock:
            item.status = "stopped"
        return json_error(str(exc), 400)

    score_fusion = fuse_interview_scores(
        # 把“回答内容得分”和“视频表现得分”按规则融合成最终成绩。
        evaluation.get("overall_score"),
        video_summary,
    )
    finished_at = utc_now()
    # report 是保存到磁盘的最终快照，包含文字评估、视觉汇总和融合分数。
    report = {
        "version": 1,
        "session_id": item.id,
        "created_at": item.created_at,
        "finished_at": finished_at,
        "job_role": item.job_role,
        "difficulty": item.difficulty,
        "resume": (
            {"id": item.resume_id, "filename": item.resume_filename}
            if item.resume_id
            else None
        ),
        "follow_up_mode": item.follow_up_mode,
        "question_limit": item.question_limit,
        "duration_seconds": (
            video_summary.get("duration_seconds", 0)
            if video_summary
            else 0
        ),
        "transcript": history,
        "question_evaluation": evaluation,
        "video_summary": video_summary,
        "score_fusion": score_fusion,
        "privacy": {
            # 明确记录本系统不持久化原始视频帧、人脸图片和音频。
            "stores_video_frames": False,
            "stores_face_images": False,
            "stores_audio": False,
        },
    }

    try:
        # 报告按用户隔离保存；保存失败则不让会话进入 finished 状态。
        services().reports.save(str(session["user_id"]), report)
    except OSError as exc:
        with item.lock:
            item.status = "stopped"
        return json_error(f"评估报告保存失败：{exc}", 500)

    with item.lock:
        # 磁盘保存成功后，再同步更新内存会话和最终状态。
        item.report = report
        item.finished_at = finished_at
        item.status = "finished"

    return jsonify({"report": report})


@interview_bp.get("/reports")
@login_required
def list_reports():
    # 只列出当前登录用户的报告摘要，不能看到其他用户的数据。
    reports = services().reports.list(str(session["user_id"]))
    return jsonify({"reports": reports})


@interview_bp.get("/reports/<session_id>")
@login_required
def get_report(session_id: str):
    # 优先从磁盘报告存储读取。
    report = services().reports.get(str(session["user_id"]), session_id)
    if report is None:
        # 兼容保存后尚未重新读取的场景：当前内存会话里可能已有同一报告。
        item = _owned_session(session_id)
        if item is not None and item.report is not None:
            report = item.report
    if report is None:
        return json_error("评估报告不存在。", 404)
    return jsonify({"report": report})
