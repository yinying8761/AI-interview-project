from __future__ import annotations

import unittest

from backend.app.config import Config
from backend.app.services.interview_service import InterviewService


class FakeClient:
    def __init__(self) -> None:
        self.messages = None

    def chat(self, messages, temperature=0.7):
        self.messages = messages
        return "Next interview question"

    def chat_json(self, _messages):
        return {
            "overall_score": 88,
            "level": "",
            "dimensions": {"professional": 91, "logic": 120},
            "summary": "",
            "strengths": "clear structure",
            "improvements": ["add metrics", ""],
            "question_reviews": [{"question": "Q", "assessment": "A"}],
            "next_steps": None,
        }


class InterviewServiceTests(unittest.TestCase):
    def test_reply_builds_context_with_role_and_difficulty(self) -> None:
        client = FakeClient()
        service = InterviewService(client=client)

        answer = service.reply(
            [{"role": "assistant", "content": "Question"}],
            "My answer",
            job_role="Data Engineer",
            difficulty="Senior",
        )

        self.assertEqual(answer, "Next interview question")
        self.assertIn("Data Engineer", client.messages[0]["content"])
        self.assertIn("Senior", client.messages[0]["content"])
        self.assertEqual(client.messages[-1]["content"], "My answer")

    def test_recent_messages_keeps_only_valid_roles_with_limit(self) -> None:
        history = []
        for index in range(Config.CHAT_CONTEXT_MESSAGES + 5):
            history.append(
                {
                    "role": "user" if index % 2 == 0 else "assistant",
                    "content": f"message-{index}",
                }
            )
        history.extend(
            [
                {"role": "system", "content": "ignored"},
                {"role": "user", "content": None},
            ]
        )

        result = InterviewService._recent_messages(history)

        self.assertLessEqual(len(result), Config.CHAT_CONTEXT_MESSAGES)
        self.assertTrue(
            all(item["role"] in {"user", "assistant"} for item in result)
        )

    def test_evaluate_normalizes_scores_and_list_fields(self) -> None:
        service = InterviewService(client=FakeClient())

        result = service.evaluate(
            [
                {"role": "assistant", "content": "Question"},
                {"role": "user", "content": "Answer"},
            ]
        )

        self.assertEqual(result["overall_score"], 88)
        self.assertEqual(result["level"], "良好")
        self.assertEqual(result["dimensions"]["logic"], 100)
        self.assertEqual(result["strengths"], ["clear structure"])
        self.assertEqual(result["improvements"], ["add metrics"])
        self.assertEqual(result["next_steps"], [])
        self.assertTrue(result["summary"])

    def test_evaluate_rejects_empty_transcript(self) -> None:
        service = InterviewService(client=FakeClient())
        with self.assertRaises(ValueError):
            service.evaluate([])


if __name__ == "__main__":
    unittest.main()
