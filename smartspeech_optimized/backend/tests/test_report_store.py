from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from backend.app.storage.report_store import ReportStore


class ReportStoreTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.store = ReportStore(Path(self.temporary_directory.name))

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_save_get_and_list_reports(self) -> None:
        report = {
            "session_id": "abc",
            "created_at": "2026-01-01T00:00:00+00:00",
            "job_role": "Engineer",
            "score_fusion": {"final_score": 86.5},
            "question_evaluation": {"level": "良好", "summary": "Good"},
        }

        path = self.store.save("user-1", report)

        self.assertTrue(path.is_file())
        self.assertEqual(self.store.get("user-1", "abc"), report)
        listing = self.store.list("user-1")
        self.assertEqual(len(listing), 1)
        self.assertEqual(listing[0]["final_score"], 86.5)

    def test_invalid_report_is_ignored(self) -> None:
        directory = Path(self.temporary_directory.name) / "user-1"
        directory.mkdir()
        (directory / "broken.json").write_text("{", encoding="utf-8")

        self.assertEqual(self.store.list("user-1"), [])
        self.assertIsNone(self.store.get("user-1", "broken"))

    def test_save_requires_session_id(self) -> None:
        with self.assertRaises(ValueError):
            self.store.save("user-1", {"created_at": "now"})


if __name__ == "__main__":
    unittest.main()
