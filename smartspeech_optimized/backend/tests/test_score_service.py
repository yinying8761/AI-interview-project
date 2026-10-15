from __future__ import annotations

import unittest

from backend.app.services.score_service import fuse_interview_scores


class ScoreServiceTests(unittest.TestCase):
    def test_rejects_weights_that_do_not_sum_to_one(self) -> None:
        with self.assertRaises(ValueError):
            fuse_interview_scores(
                80,
                None,
                question_weight=0.7,
                video_weight=0.2,
            )

    def test_clamps_question_score_to_valid_range(self) -> None:
        low = fuse_interview_scores(-5, None)
        high = fuse_interview_scores(130, None)

        self.assertEqual(low["question_score"], 0.0)
        self.assertEqual(high["question_score"], 100.0)
        self.assertIsNone(low["final_score"])

    def test_combines_question_expression_and_attention_scores(self) -> None:
        result = fuse_interview_scores(
            90,
            {
                "video_available": True,
                "face_frames": 30,
                "expression_score": 80,
                "attention_score": 70,
            },
        )

        self.assertTrue(result["available"])
        self.assertEqual(result["expression_attention_score"], 75.0)
        self.assertEqual(result["final_score"], 87.0)
        self.assertEqual(result["video_sample_count"], 30)

    def test_skips_final_score_when_video_sample_is_insufficient(self) -> None:
        result = fuse_interview_scores(
            92,
            {
                "video_available": False,
                "face_frames": 4,
                "expression_score": 88,
                "attention_score": 85,
            },
        )

        self.assertFalse(result["available"])
        self.assertIsNone(result["final_score"])
        self.assertEqual(result["question_score"], 92.0)


if __name__ == "__main__":
    unittest.main()
