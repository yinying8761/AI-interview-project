from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any, Dict, List

from docx import Document


DEFAULT_SOURCE = "科大讯飞消费者BG面试题（整合版）.docx"
DEFAULT_DESTINATION = "data/interview_question_bank.json"
CATEGORY_PATTERN = re.compile(r"^[一二三四五六七八九十百]+、(.+)$")
QUESTION_PATTERN = re.compile(r"^\s*(\d+)[、.．]\s*(.+)$")


def classify_topic(name: str) -> str:
    rules = (
        (("多线程", "JUC", "并发"), "multithreading_concurrency"),
        (("异常",), "exception_handling"),
        (("MySQL", "数据库"), "database"),
        (("Redis", "缓存"), "redis"),
        (("RAG",), "rag"),
        (("数据结构", "排序", "算法"), "algorithms_data_structures"),
        (("团队协作", "项目管理", "项目经历"), "teamwork"),
        (("AI", "Agent", "大模型"), "ai_engineering"),
        (("Spring", "反射", "异步", "定时任务"), "backend_framework"),
        (("JVM", "操作系统"), "jvm_os"),
        (("Docker", "部署"), "deployment"),
        (("项目架构", "项目亮点", "云购"), "project_experience"),
        (("个人情况", "职业规划", "HR"), "hr_career"),
        (("Java",), "java_basics"),
    )
    for keywords, topic in rules:
        if any(keyword.lower() in name.lower() for keyword in keywords):
            return topic
    return "general"


def parse_question_bank(source_file: Path) -> Dict[str, Any]:
    document = Document(str(source_file))
    categories: List[Dict[str, Any]] = []
    current: Dict[str, Any] | None = None

    for paragraph in document.paragraphs:
        text = paragraph.text.strip()
        if not text:
            continue
        category_match = CATEGORY_PATTERN.match(text)
        if category_match and not QUESTION_PATTERN.match(text):
            current = {
                "id": f"category-{len(categories) + 1:02d}",
                "name": category_match.group(1).strip(),
                "topic": "",
                "scenario": "",
                "questions": [],
            }
            current["topic"] = classify_topic(current["name"])
            categories.append(current)
            continue
        if current is None:
            continue
        if text.startswith("场景："):
            current["scenario"] = text.removeprefix("场景：").strip()
            continue
        question_match = QUESTION_PATTERN.match(text)
        if question_match:
            current["questions"].append(
                {
                    "id": f"q{int(question_match.group(1)):03d}",
                    "content": question_match.group(2).strip(),
                }
            )

    categories = [item for item in categories if item["questions"]]
    return {
        "version": 1,
        "source_file": source_file.name,
        "categories": categories,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="将 DOCX 面试题导入结构化 JSON 题库")
    parser.add_argument("--source", default=DEFAULT_SOURCE, help="源 DOCX 文件")
    parser.add_argument("--destination", default=DEFAULT_DESTINATION, help="目标 JSON 文件")
    arguments = parser.parse_args()

    source_file = Path(arguments.source).resolve()
    destination = Path(arguments.destination).resolve()
    if not source_file.is_file():
        raise SystemExit(f"源题库不存在：{source_file}")

    payload = parse_question_bank(source_file)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    question_count = sum(
        len(category["questions"]) for category in payload["categories"]
    )
    print(
        f"已导入 {question_count} 道题、{len(payload['categories'])} 个分类："
        f"{destination}"
    )


if __name__ == "__main__":
    main()
