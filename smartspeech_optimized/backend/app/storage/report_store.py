from __future__ import annotations

# 本文件负责把最终面试报告保存成磁盘上的 JSON 文件。
# 路径格式为 REPORT_DIR/用户ID/会话ID.json，因此天然按用户隔离数据。

import json
import threading
from pathlib import Path
from typing import Any, Dict, List

from backend.app.config import Config


class ReportStore:
    def __init__(self, root: Path = Config.REPORT_DIR) -> None:
        # 启动时确保报告根目录存在；即使目录已存在也不会报错。
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()

    def save(self, user_id: str, report: Dict[str, Any]) -> Path:
        # 每份报告必须带 session_id，它同时是报告的文件名。
        session_id = str(report.get("session_id") or "").strip()
        if not session_id:
            raise ValueError("报告缺少 session_id。")
        destination = self.root / str(user_id) / f"{session_id}.json"
        destination.parent.mkdir(parents=True, exist_ok=True)
        # 先写 .tmp 临时文件，完整写入后再 replace 成正式文件。
        # 这能避免程序在写到一半时崩溃，留下一个无法解析的半截 JSON。
        temporary = destination.with_suffix(".tmp")
        with self._lock:
            temporary.write_text(
                json.dumps(report, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            temporary.replace(destination)
        return destination

    def get(self, user_id: str, session_id: str) -> Dict[str, Any] | None:
        # 根据用户和会话编号直接拼出文件路径。
        path = self.root / str(user_id) / f"{session_id}.json"
        if not path.is_file():
            return None
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            # 文件读到一半失败或 JSON 格式损坏时返回 None，不让整个接口崩溃。
            return None
        return payload if isinstance(payload, dict) else None

    def list(self, user_id: str, limit: int = 50) -> List[Dict[str, Any]]:
        # 没有上传过报告的目录直接视为空列表。
        directory = self.root / str(user_id)
        if not directory.is_dir():
            return []

        reports: List[Dict[str, Any]] = []
        # 逐个读取完整报告，再提取列表页真正需要的摘要字段，减少网络传输。
        for path in directory.glob("*.json"):
            try:
                payload = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                continue
            if not isinstance(payload, dict):
                continue
            score_fusion = payload.get("score_fusion") or {}
            evaluation = payload.get("question_evaluation") or {}
            # 某个旧报告缺少新字段时使用空字符串、0 或 None，保证响应结构稳定。
            reports.append(
                {
                    "session_id": payload.get("session_id", path.stem),
                    "created_at": payload.get("created_at", ""),
                    "job_role": payload.get("job_role", ""),
                    "difficulty": payload.get("difficulty", ""),
                    "duration_seconds": payload.get("duration_seconds", 0),
                    "question_score": score_fusion.get("question_score"),
                    "expression_score": score_fusion.get("expression_score"),
                    "attention_score": score_fusion.get("attention_score"),
                    "final_score": score_fusion.get("final_score"),
                    "level": evaluation.get("level", ""),
                    "summary": evaluation.get("summary", ""),
                }
            )

        # created_at 是 ISO 时间字符串，可直接按字符串从新到旧排序。
        reports.sort(key=lambda item: item.get("created_at", ""), reverse=True)
        # max(1, int(limit)) 防止 0 或负数；切片最多返回指定数量。
        return reports[: max(1, int(limit))]
