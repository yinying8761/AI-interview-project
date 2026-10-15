from __future__ import annotations

import unittest

from backend.app.services.deepseek_service import (
    DeepSeekError,
    DeepSeekService,
)


class DeepSeekParsingTests(unittest.TestCase):
    def test_parses_plain_json_object(self) -> None:
        result = DeepSeekService.parse_json_object('{"score": 88}')
        self.assertEqual(result, {"score": 88})

    def test_parses_markdown_fenced_json(self) -> None:
        content = '```json\n{"score": 91, "level": "good"}\n```'
        result = DeepSeekService.parse_json_object(content)
        self.assertEqual(result["score"], 91)

    def test_extracts_json_object_from_surrounding_text(self) -> None:
        content = 'Evaluation follows:\n{"score": 75}\nEnd.'
        result = DeepSeekService.parse_json_object(content)
        self.assertEqual(result, {"score": 75})

    def test_rejects_non_object_json(self) -> None:
        with self.assertRaises(DeepSeekError):
            DeepSeekService.parse_json_object("[1, 2, 3]")

    def test_rejects_invalid_json(self) -> None:
        with self.assertRaises(DeepSeekError):
            DeepSeekService.parse_json_object("not json")


if __name__ == "__main__":
    unittest.main()
