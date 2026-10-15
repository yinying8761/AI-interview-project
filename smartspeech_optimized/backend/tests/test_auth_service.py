from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import numpy as np

from backend.app.services.auth_service import (
    AuthService,
    UsernameAlreadyExists,
)
from backend.app.services.face_recognition import load_face_vector


class AuthServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        root = Path(self.temporary_directory.name)
        self.users_file = root / "users.json"
        self.faces_dir = root / "faces"
        self.service = AuthService(
            users_file=self.users_file,
            faces_dir=self.faces_dir,
            password_iterations=10_000,
        )

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_registers_hashes_and_authenticates_user(self) -> None:
        vector = np.zeros(512, dtype=np.float32)
        vector[0] = 1.0

        user = self.service.register_user(
            "Alice",
            "secret-pass",
            "Alice Zhang",
            face_vector=vector,
        )

        self.assertNotIn("password", user)
        self.assertTrue(user["face_registered"])
        stored = json.loads(self.users_file.read_text(encoding="utf-8"))
        record = stored["users"][0]
        self.assertNotIn("secret-pass", self.users_file.read_text("utf-8"))
        self.assertEqual(record["password"]["algorithm"], "pbkdf2_sha256")
        self.assertIsNone(
            self.service.authenticate_password("alice", "wrong-pass")
        )
        authenticated = self.service.authenticate_password(
            "alice",
            "secret-pass",
        )
        self.assertEqual(authenticated["id"], user["id"])

    def test_normalized_username_cannot_be_registered_twice(self) -> None:
        self.service.register_user("Alice", "secret-pass")

        with self.assertRaises(UsernameAlreadyExists):
            self.service.register_user("  ALICE  ", "another-pass")

    def test_face_binding_can_be_replaced_and_deleted(self) -> None:
        user = self.service.register_user("Bob", "secret-pass")
        first = np.zeros(512, dtype=np.float32)
        first[0] = 1.0
        second = np.zeros(512, dtype=np.float32)
        second[1] = 1.0

        updated = self.service.register_face(user["id"], first)
        self.assertTrue(updated["face_registered"])
        first_path = self.service.get_face_path(user["id"])
        np.testing.assert_allclose(load_face_vector(first_path), first)

        self.service.register_face(user["id"], second)
        second_path = self.service.get_face_path(user["id"])
        np.testing.assert_allclose(load_face_vector(second_path), second)

        self.assertTrue(self.service.delete_user(user["id"]))
        self.assertFalse(second_path.exists())
        self.assertIsNone(self.service.get_user(user["id"]))


if __name__ == "__main__":
    unittest.main()
