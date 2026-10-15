from __future__ import annotations

# 本文件负责读取本地 JSON 题库、按面试场景筛选题目并随机抽题。
# 它不调用大模型；只有“场景主问题”会直接使用这里的题库原文。

import json
import random
import threading
from pathlib import Path
from typing import Any, Dict, Iterable, List

from backend.app.config import Config


class QuestionBankError(RuntimeError):
    """Raised when the local question bank cannot be loaded."""


SCENE_TOPIC_MAP: Dict[str, tuple[str, ...]] = {
    # 左侧是前端/面试流程使用的场景 id，右侧是题库 category 对应的 topic。
    # 一个场景可以匹配多个 topic，以便扩大候选题目范围。
    "multithreading_processes": ("multithreading_concurrency", "jvm_os"),
    "concurrency": ("multithreading_concurrency",),
    "exception_handling": ("exception_handling",),
    "database_index_optimization": ("database",),
    "redis_cache": ("redis",),
    "rag_principles": ("rag",),
    "algorithms_data_structures": ("algorithms_data_structures",),
    "teamwork_project_management": ("teamwork", "project_experience"),
    "learning_ai_engineering": ("ai_engineering", "backend_framework"),
}

INTERVIEW_SCENES: tuple[Dict[str, str], ...] = (
    # 这些场景组成默认面试轮转顺序。每个场景包含编号、显示名称和考察重点。
    {
        "id": "multithreading_processes",
        "label": "多线程与多进程",
        "focus": "线程与进程模型、线程池、锁、并发安全和任务调度。",
    },
    {
        "id": "concurrency",
        "label": "并发编程",
        "focus": "并发控制、可见性、原子性、异步任务和高并发问题定位。",
    },
    {
        "id": "exception_handling",
        "label": "项目异常处理",
        "focus": "异常边界、幂等、重试、降级、补偿与线上故障排查。",
    },
    {
        "id": "database_index_optimization",
        "label": "数据库索引与优化",
        "focus": "索引设计、执行计划、事务隔离和慢查询优化。",
    },
    {
        "id": "redis_cache",
        "label": "Redis 缓存技术",
        "focus": "缓存一致性、穿透、击穿、雪崩、分布式锁与数据结构选型。",
    },
    {
        "id": "rag_principles",
        "label": "RAG 技术原理",
        "focus": "切片、向量化、召回、重排、上下文构建和效果评估。",
    },
    {
        "id": "algorithms_data_structures",
        "label": "数据结构与算法",
        "focus": "常用数据结构、复杂度、排序和工程问题中的算法选择。",
    },
    {
        "id": "teamwork_project_management",
        "label": "团队协作与项目管理",
        "focus": "需求拆解、协作沟通、风险控制、复盘和个人贡献。",
    },
    {
        "id": "learning_ai_engineering",
        "label": "新技术学习与 AI 工程化",
        "focus": "技术学习路径、AI 应用落地、评估、成本、稳定性与工程治理。",
    },
)


class QuestionBankService:
    """Loads a categorized JSON bank and samples questions by interview scene."""

    def __init__(
        self,
        source_file: str | Path = Config.QUESTION_BANK_FILE,
        seed: int | None = None,
    ) -> None:
        # source_file 是题库 JSON 路径；seed 可让测试中的随机结果可重复。
        self.source_file = Path(source_file)
        self._random = random.Random(seed)
        self._lock = threading.RLock()
        self._questions: List[Dict[str, Any]] = []
        self._categories: List[Dict[str, Any]] = []
        self._by_id: Dict[str, Dict[str, Any]] = {}
        # 实例创建后立即读取题库，配置错误会在应用启动阶段尽早暴露。
        self.reload()

    def reload(self) -> None:
        # 重新加载会先读取外部 JSON，原题库在解析成功前保持不变。
        if not self.source_file.is_file():
            raise QuestionBankError(f"题库文件不存在：{self.source_file}")
        try:
            payload = json.loads(self.source_file.read_text(encoding="utf-8"))
        except (OSError, ValueError, TypeError) as exc:
            raise QuestionBankError(f"读取题库失败：{exc}") from exc

        categories = payload.get("categories") if isinstance(payload, dict) else None
        if not isinstance(categories, list):
            raise QuestionBankError("题库格式无效：缺少 categories 数组。")

        clean_categories: List[Dict[str, Any]] = []
        # 三类临时容器分别保存“分类列表”“扁平题目列表”和“按 id 快速查询表”。
        questions: List[Dict[str, Any]] = []
        by_id: Dict[str, Dict[str, Any]] = {}
        for category in categories:
            # 对每个分类逐项检查类型和必填字段，跳过损坏的数据，不让整份题库失败。
            if not isinstance(category, dict):
                continue
            category_id = str(category.get("id") or "").strip()
            category_name = str(category.get("name") or "").strip()
            topic = str(category.get("topic") or "general").strip()
            raw_questions = category.get("questions")
            if not category_id or not category_name or not isinstance(raw_questions, list):
                continue
            clean_questions = []
            for raw in raw_questions:
                if not isinstance(raw, dict):
                    continue
                question_id = str(raw.get("id") or "").strip()
                content = str(raw.get("content") or "").strip()
                if not question_id or not content or question_id in by_id:
                    # 少 id、少内容或 id 重复的题目都视为无效。
                    continue
                item = {
                    "id": question_id,
                    "content": content,
                    "category_id": category_id,
                    "category": category_name,
                    "topic": topic,
                }
                clean_questions.append({"id": question_id, "content": content})
                by_id[question_id] = item
                questions.append(item)
            clean_categories.append(
                {
                    "id": category_id,
                    "name": category_name,
                    "topic": topic,
                    "question_count": len(clean_questions),
                    "questions": clean_questions,
                }
            )

        if not questions:
            raise QuestionBankError("题库中没有可用问题。")

        with self._lock:
            # 只有全部校验完成后，才一次性替换正在使用的数据，保证并发读取安全。
            self._categories = clean_categories
            self._questions = questions
            self._by_id = by_id

    def summary(self) -> Dict[str, Any]:
        # 返回题库数量概况，不包含全部题目正文，适合健康检查接口。
        with self._lock:
            return {
                "question_count": len(self._questions),
                "category_count": len(self._categories),
                "categories": [
                    {
                        "id": item["id"],
                        "name": item["name"],
                        "topic": item["topic"],
                        "question_count": item["question_count"],
                    }
                    for item in self._categories
                ],
            }

    def follow_up_target(self, mode: str) -> int:
        """Return the follow-up count for one scene, capped at three."""
        normalized = str(mode or "random").strip().lower()
        if normalized in {"1", "2", "3"}:
            return int(normalized)
        # random 模式每次在 1 到 3 次之间随机选择。
        return self._random.randint(1, 3)

    def get(self, question_id: str) -> Dict[str, Any] | None:
        # 返回字典副本，调用方修改结果不会意外改动题库内部记录。
        with self._lock:
            item = self._by_id.get(str(question_id))
            return dict(item) if item is not None else None

    def pick(
        self,
        scene_id: str,
        *,
        excluded_ids: Iterable[str] = (),
    ) -> Dict[str, Any] | None:
        """Sample an unused question matching a configured interview scene."""
        topics = SCENE_TOPIC_MAP.get(scene_id, ())
        # excluded_ids 是本场已经问过的题，转成 set 后查找更快。
        excluded = {str(item) for item in excluded_ids}
        with self._lock:
            # 先找场景匹配且没用过的题。
            candidates = [
                item
                for item in self._questions
                if item["topic"] in topics and item["id"] not in excluded
            ]
            if not candidates:
                # 该场景题目全部用完时，退而选择其它场景中未使用的题。
                candidates = [
                    item for item in self._questions if item["id"] not in excluded
                ]
            return dict(self._random.choice(candidates)) if candidates else None
