from __future__ import annotations

import unittest

from backend.app import create_app


class HealthApiTests(unittest.TestCase):
    def setUp(self) -> None:
        app = create_app()
        app.config.update(TESTING=True)
        self.client = app.test_client()

    def test_health_returns_name_and_capabilities(self) -> None:
        response = self.client.get("/api/health")

        self.assertEqual(response.status_code, 200)
        payload = response.get_json()
        self.assertTrue(payload["ok"])
        self.assertEqual(payload["name"], "智能体模拟面试系统")
        self.assertFalse(payload["authenticated"])
        self.assertIn("deepseek", payload["capabilities"])
        self.assertIn("mediapipe", payload["capabilities"])

    def test_protected_route_requires_login(self) -> None:
        response = self.client.post(
            "/api/interviews",
            json={"job_role": "Engineer"},
        )

        self.assertEqual(response.status_code, 401)
        self.assertIn("登录", response.get_json()["error"])


if __name__ == "__main__":
    unittest.main()
