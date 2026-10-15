from __future__ import annotations

import unittest
from datetime import datetime, timedelta, timezone

from backend.app.storage.session_store import InterviewSession, SessionStore


class StubWorker:
    def __init__(self) -> None:
        self.stopped = False

    def stop(self) -> None:
        self.stopped = True


class StubAnalyzer:
    def __init__(self) -> None:
        self.closed = False

    def close(self) -> None:
        self.closed = True


def make_session(session_id: str = "session-1") -> InterviewSession:
    return InterviewSession(
        id=session_id,
        user_id="user-1",
        job_role="Engineer",
        difficulty="Intermediate",
        voice="zh-CN-YunxiNeural",
        auto_tts=True,
        analysis_fps=3.0,
        snapshot_interval=2.0,
        vision_worker=StubWorker(),
        analyzer=StubAnalyzer(),
    )


class SessionStoreTests(unittest.TestCase):
    def test_close_session_stops_worker_and_analyzer(self) -> None:
        item = make_session()

        SessionStore._close_session(item)

        self.assertTrue(item.vision_worker.stopped)
        self.assertTrue(item.analyzer.closed)

    def test_delete_enforces_ownership(self) -> None:
        store = SessionStore(vision=object())
        item = make_session()
        store._sessions[item.id] = item

        self.assertFalse(store.delete(item.id, "another-user"))
        self.assertIs(store.get(item.id, "user-1"), item)
        self.assertTrue(store.delete(item.id, "user-1"))
        self.assertIsNone(store.get(item.id))
        self.assertTrue(item.vision_worker.stopped)
        self.assertTrue(item.analyzer.closed)

    def test_cleanup_removes_expired_sessions_only(self) -> None:
        store = SessionStore(vision=object(), max_age_seconds=60)
        expired = make_session("expired")
        active = make_session("active")
        expired.created_at = (
            datetime.now(timezone.utc) - timedelta(minutes=2)
        ).isoformat()
        active.created_at = datetime.now(timezone.utc).isoformat()
        store._sessions = {expired.id: expired, active.id: active}

        removed = store.cleanup()

        self.assertEqual(removed, 1)
        self.assertIsNone(store.get(expired.id))
        self.assertIs(store.get(active.id), active)
        self.assertTrue(expired.vision_worker.stopped)
        self.assertFalse(active.vision_worker.stopped)


if __name__ == "__main__":
    unittest.main()
