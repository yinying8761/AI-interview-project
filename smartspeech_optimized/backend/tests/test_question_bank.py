from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from backend.app.config import Config
from backend.app.services.question_bank import QuestionBankService


class QuestionBankServiceTests(unittest.TestCase):
    def test_bundled_bank_contains_expected_questions_and_categories(self) -> None:
        service = QuestionBankService(Config.QUESTION_BANK_FILE, seed=7)
        summary = service.summary()

        self.assertEqual(summary["question_count"], 123)
        self.assertEqual(summary["category_count"], 22)
        self.assertTrue(summary["categories"])

    def test_random_follow_up_target_is_always_between_one_and_three(self) -> None:
        service = QuestionBankService(Config.QUESTION_BANK_FILE, seed=7)

        targets = {service.follow_up_target("random") for _ in range(100)}

        self.assertTrue(targets.issubset({1, 2, 3}))
        self.assertEqual(service.follow_up_target("3"), 3)
        self.assertEqual(service.follow_up_target("invalid"), service.follow_up_target("random"))

    def test_pick_never_returns_an_excluded_question(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "bank.json"
            source.write_text(
                json.dumps(
                    {
                        "categories": [
                            {
                                "id": "concurrency",
                                "name": "Concurrency",
                                "topic": "multithreading_concurrency",
                                "questions": [
                                    {"id": "q-1", "content": "Question one"},
                                    {"id": "q-2", "content": "Question two"},
                                ],
                            },
                            {
                                "id": "database",
                                "name": "Database",
                                "topic": "database",
                                "questions": [
                                    {"id": "q-3", "content": "Question three"},
                                ],
                            },
                        ]
                    }
                ),
                encoding="utf-8",
            )
            service = QuestionBankService(source, seed=3)

            first = service.pick("concurrency", excluded_ids={"q-1"})
            self.assertEqual(first["id"], "q-2")

            fallback = service.pick(
                "concurrency",
                excluded_ids={"q-1", "q-2"},
            )
            self.assertEqual(fallback["id"], "q-3")

            exhausted = service.pick(
                "concurrency",
                excluded_ids={"q-1", "q-2", "q-3"},
            )
            self.assertIsNone(exhausted)


if __name__ == "__main__":
    unittest.main()
