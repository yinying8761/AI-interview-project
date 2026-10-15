from __future__ import annotations

import threading
import time
import unittest

from backend.app.services.vision.worker import LatestFrameWorker


class FakeAnalyzer:
    session_id = "test-session"

    def __init__(self) -> None:
        self.warmed_up = threading.Event()
        self.closed = False

    def warm_up(self) -> None:
        self.warmed_up.set()

    def close(self) -> None:
        self.closed = True


class BlockingVision:
    def __init__(self) -> None:
        self.started = threading.Event()
        self.release = threading.Event()
        self.frames: list[bytes] = []
        self._lock = threading.Lock()

    def process_frame(self, _analyzer, image_bytes: bytes) -> dict:
        with self._lock:
            self.frames.append(image_bytes)
        self.started.set()
        self.release.wait(timeout=3.0)
        return {"processed": len(self.frames)}


def wait_until(predicate, timeout: float = 2.0) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return True
        time.sleep(0.01)
    return predicate()


class LatestFrameWorkerTests(unittest.TestCase):
    def test_keeps_only_latest_pending_frame(self) -> None:
        analyzer = FakeAnalyzer()
        vision = BlockingVision()
        worker = LatestFrameWorker(analyzer, vision)

        worker.start()
        self.assertTrue(analyzer.warmed_up.wait(timeout=2.0))
        self.assertTrue(worker.submit(b"first")[0])
        self.assertTrue(vision.started.wait(timeout=2.0))

        self.assertTrue(worker.submit(b"second")[0])
        accepted, replaced = worker.submit(b"third")
        self.assertTrue(accepted)
        self.assertTrue(replaced)
        self.assertTrue(worker.status()["pending"])

        vision.release.set()
        self.assertTrue(
            wait_until(
                lambda: worker.status()["processed_frames"] >= 2,
            )
        )
        worker.stop()

        status = worker.status()
        self.assertEqual(vision.frames, [b"first", b"third"])
        self.assertEqual(status["received_frames"], 3)
        self.assertEqual(status["processed_frames"], 2)
        self.assertEqual(status["dropped_frames"], 1)
        self.assertEqual(status["state"], "stopped")

    def test_rejects_empty_frame(self) -> None:
        worker = LatestFrameWorker(FakeAnalyzer(), BlockingVision())
        with self.assertRaises(ValueError):
            worker.submit(b"")

    def test_stop_is_idempotent(self) -> None:
        analyzer = FakeAnalyzer()
        worker = LatestFrameWorker(analyzer, BlockingVision())
        worker.start()
        self.assertTrue(analyzer.warmed_up.wait(timeout=2.0))

        worker.stop()
        worker.stop()

        self.assertEqual(worker.status()["state"], "stopped")
        self.assertFalse(worker.status()["accepting"])


if __name__ == "__main__":
    unittest.main()
