from __future__ import annotations

import unittest
from importlib import import_module
from types import SimpleNamespace
from unittest.mock import patch

from backend.app import create_app
from backend.app.services.interview_service import InterviewService
from backend.app.services.question_bank import QuestionBankService
from backend.app.storage.session_store import InterviewSession


class FakeInterviewService:
    def __init__(self) -> None:
        self.plans = []

    def reply(
        self,
        _history,
        _user_message,
        *,
        job_role,
        difficulty,
        interview_plan,
        resume_content,
    ) -> str:
        self.plans.append(
            {
                **interview_plan,
                "job_role": job_role,
                "difficulty": difficulty,
                "has_resume": bool(resume_content),
            }
        )
        return f"Question {interview_plan['question_number']}"


class FakeSessions:
    def __init__(self, item: InterviewSession) -> None:
        self.item = item

    def get(self, session_id: str, user_id: str | None = None):
        if session_id != self.item.id:
            return None
        if user_id is not None and user_id != self.item.user_id:
            return None
        return self.item


class InterviewFlowTests(unittest.TestCase):
    def setUp(self) -> None:
        self.app = create_app()
        self.app.config.update(TESTING=True)
        self.client = self.app.test_client()
        with self.client.session_transaction() as flask_session:
            flask_session["user_id"] = "user-1"

        self.interview_service = FakeInterviewService()
        self.item = InterviewSession(
            id="session-1",
            user_id="user-1",
            job_role=InterviewService.DEFAULT_JOB_ROLE,
            difficulty="中级",
            voice="zh-CN-YunxiNeural",
            auto_tts=True,
            analysis_fps=3.0,
            snapshot_interval=2.0,
            resume_id="resume-1",
            resume_filename="candidate.docx",
            resume_content=(
                "Built a Java order platform with concurrency controls, "
                "database index tuning, Redis caching, and RAG support."
            ),
            follow_up_mode="1",
            question_limit=12,
        )
        self.services = SimpleNamespace(
            auth=SimpleNamespace(get_user=lambda user_id: {"id": user_id}),
            sessions=FakeSessions(self.item),
            question_bank=QuestionBankService(seed=11),
            interview=self.interview_service,
        )
        self.common_api = import_module("backend.app.api.common")
        self.interview_api = import_module("backend.app.api.interview")

    def test_resume_interview_alternates_scenes_and_stops_after_12_questions(self) -> None:
        with (
            patch.object(self.common_api, "services", return_value=self.services),
            patch.object(self.interview_api, "services", return_value=self.services),
        ):
            opening = self.client.post("/api/interviews/session-1/opening")
            self.assertEqual(opening.status_code, 200)
            opening_payload = opening.get_json()
            self.assertEqual(opening_payload["message"]["scene_id"], "resume")
            self.assertEqual(opening_payload["session"]["question_count"], 1)

            assistant_messages = [opening_payload["message"]]
            for answer_number in range(1, 13):
                response = self.client.post(
                    "/api/interviews/session-1/messages",
                    json={"message": f"Answer {answer_number}"},
                )
                self.assertEqual(response.status_code, 200)
                payload = response.get_json()
                if answer_number < 12:
                    self.assertIsNone(payload["checkpoint"])
                    self.assertIsNotNone(payload["assistant_message"])
                    assistant_messages.append(payload["assistant_message"])
                else:
                    self.assertIsNone(payload["assistant_message"])
                    self.assertTrue(payload["checkpoint"]["required"])
                    self.assertEqual(payload["checkpoint"]["reason"], "question_limit")
                    self.assertEqual(payload["session"]["status"], "checkpoint")

        self.assertEqual(len(assistant_messages), 12)
        self.assertEqual(self.item.question_count, 12)
        self.assertEqual(
            sum(1 for message in self.item.transcript if message["role"] == "assistant"),
            12,
        )
        self.assertEqual(
            sum(1 for message in self.item.transcript if message["role"] == "user"),
            12,
        )

        modes = [message["question_mode"] for message in assistant_messages]
        self.assertEqual(modes[0:4], ["opening", "follow_up", "scene", "follow_up"])
        self.assertEqual(modes[4:8], ["resume", "follow_up", "scene", "follow_up"])
        for message in assistant_messages:
            if message["question_mode"] != "scene":
                continue
            bank_item = self.services.question_bank.get(message["bank_question_id"])
            self.assertIsNotNone(bank_item)
            self.assertEqual(message["content"], bank_item["content"])
        self.assertTrue(all(
            message["follow_up_number"] <= message["follow_up_target"] <= 3
            for message in assistant_messages
        ))
        self.assertEqual(self.interview_service.plans[0]["job_role"], "Java 后端开发工程师")
        self.assertTrue(self.interview_service.plans[0]["has_resume"])


if __name__ == "__main__":
    unittest.main()
