from __future__ import annotations

# 本文件负责“本地账号”的完整生命周期：注册、登录、绑定人脸、查询和删除。
# 账号正文保存在一个 JSON 文件中，人脸特征单独存成 .npy 文件。
# 文件读写不是数据库事务，因此下面会通过临时文件、回滚和线程锁尽量保证一致性。

import base64
import hashlib
import hmac
import json
import os
import tempfile
import threading
import unicodedata
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Mapping, Optional

import numpy as np

from backend.app.config import AUTH_FACES_DIR, AUTH_USERS_FILE
from backend.app.services.face_recognition import (
    FACE_VECTOR_SIZE,
    FaceRecognitionError,
    load_face_vector,
    normalize_face_vector,
    save_face_vector,
)


PASSWORD_ALGORITHM = "pbkdf2_sha256"
# 密码不直接保存。这里约定使用 PBKDF2-SHA256，并把算法名写进每条账号记录。
PASSWORD_ITERATIONS = 310_000
# 迭代次数越高，暴力猜测越困难，但注册和登录时计算也会越慢。


class AuthError(RuntimeError):
# 所有账号相关异常的父类，调用方可以统一捕获 AuthError。
    """Base error for local account operations."""


class AuthValidationError(AuthError):
# 表示用户名、密码、显示名等输入不合法。
    """Raised when account input fails validation."""


class UsernameAlreadyExists(AuthError):
# 表示规范化后的用户名已经被占用。
    """Raised when a normalized username is already registered."""


class AuthStorageError(AuthError):
# 表示本地账号文件或人脸特征文件无法正确读写。
    """Raised when the local account store cannot be read or written."""


def normalize_username(username: str) -> str:
    # NFKC 会把兼容字符统一成标准形式；strip 去掉首尾空白；casefold 做大小写折叠。
    # 因此 " Alice "、"Ａｌｉｃｅ" 等输入都能映射到同一个查找键。
    normalized = unicodedata.normalize("NFKC", str(username or "")).strip()
    return normalized.casefold()


def _utc_now() -> str:
    # 统一写入带 UTC 时区的 ISO 时间，避免服务器换时区后时间含义变化。
    return datetime.now(timezone.utc).isoformat()


class AuthService:
    # AuthService 不依赖 Flask，既可由容器创建，也可在测试中直接实例化。
    """JSON-backed user store with password and face bindings."""

    def __init__(
        self,
        users_file: str | Path = AUTH_USERS_FILE,
        faces_dir: str | Path = AUTH_FACES_DIR,
        password_iterations: int = PASSWORD_ITERATIONS,
    ) -> None:
        # 允许调用方覆盖存储位置和迭代次数，便于测试或后续迁移。
        self.users_file = Path(users_file)
        self.faces_dir = Path(faces_dir)
        # 即使外部传入很小的值，也至少使用 10,000 次迭代。
        self.password_iterations = max(10_000, int(password_iterations))
        # RLock 是可重入锁：同一线程进入一个加锁方法后，仍可安全调用另一个加锁方法。
        self._lock = threading.RLock()
        self._ensure_storage()

    def _ensure_storage(self) -> None:
        # 构造服务时就把目录和空账号文件准备好，后续方法无需反复判断目录是否存在。
        with self._lock:
            self.users_file.parent.mkdir(parents=True, exist_ok=True)
            self.faces_dir.mkdir(parents=True, exist_ok=True)
            if not self.users_file.exists():
                self._write_data_unlocked([])

    def _load_data_unlocked(self) -> Dict[str, Any]:
        # 函数名中的 unlocked 表示：调用者必须已经持有 self._lock。
        # 这里不再重复加锁，避免把锁的边界隐藏在每一层内部。
        if not self.users_file.exists():
            self._write_data_unlocked([])
        try:
            with self.users_file.open("r", encoding="utf-8") as users_file:
                data = json.load(users_file)
        except (OSError, ValueError, TypeError) as exc:
            raise AuthStorageError(
                f"读取账号数据失败：{exc}"
            ) from exc

        if not isinstance(data, dict) or not isinstance(data.get("users"), list):
            raise AuthStorageError("账号数据格式无效。")
        return data

    def _write_data_unlocked(self, users: List[Dict[str, Any]]) -> None:
        # 账号文件使用固定版本号，方便以后升级格式时识别旧数据。
        payload = {"version": 1, "users": users}
        # 先写同目录临时文件，再 os.replace 原子替换正式文件。
        # 这样程序中途退出时，正式 JSON 不会只写入一半而损坏。
        file_descriptor, temp_name = tempfile.mkstemp(
            prefix=f".{self.users_file.name}.",
            suffix=".tmp",
            dir=str(self.users_file.parent),
        )
        os.close(file_descriptor)
        temp_path = Path(temp_name)

        try:
            with temp_path.open("w", encoding="utf-8", newline="\n") as users_file:
                json.dump(
                    payload,
                    users_file,
                    ensure_ascii=False,
                    indent=2,
                )
                users_file.flush()
                # fsync 要求操作系统把缓冲区真正落盘，再执行替换。
                os.fsync(users_file.fileno())
            os.replace(temp_path, self.users_file)
        except Exception as exc:
            # 写失败时清理临时文件；清理本身失败不覆盖原始异常。
            try:
                temp_path.unlink(missing_ok=True)
            except OSError:
                pass
            raise AuthStorageError(f"保存账号数据失败：{exc}") from exc

    @staticmethod
    def _public_user(user: Mapping[str, Any]) -> Dict[str, Any]:
        # 对外只返回可公开字段，绝不把 password 哈希、salt 或内部文件路径带出服务层。
        return {
            "id": str(user.get("id", "")),
            "username": str(user.get("username", "")),
            "display_name": str(
                user.get("display_name") or user.get("username") or ""
            ),
            "created_at": str(user.get("created_at", "")),
            "face_registered": bool(user.get("face")),
        }

    @staticmethod
    def _validate_username(username: str) -> str:
        # 校验前先做与查找键相同的 Unicode 规范化，保留用户输入的原始大小写用于展示。
        value = unicodedata.normalize("NFKC", str(username or "")).strip()
        if not 3 <= len(value) <= 32:
            raise AuthValidationError("用户名长度应为 3-32 个字符。")
        if any(character.isspace() for character in value):
            raise AuthValidationError("用户名不能包含空格。")
        return value

    @staticmethod
    def _validate_password(password: str) -> str:
        # 密码允许任意字符（包括空格），这里只限制长度。
        value = str(password or "")
        if not 6 <= len(value) <= 128:
            raise AuthValidationError("密码长度应为 6-128 个字符。")
        return value

    @staticmethod
    def _validate_display_name(display_name: str, fallback: str) -> str:
        # 显示名可以为空；为空时退回用户名，并限制最大长度。
        value = unicodedata.normalize(
            "NFKC",
            str(display_name or "").strip(),
        )
        value = value or fallback
        if len(value) > 40:
            raise AuthValidationError("显示名称不能超过 40 个字符。")
        return value

    def _hash_password(self, password: str) -> Dict[str, Any]:
        # 每次注册都生成独立随机盐，使相同密码在两个账号中得到不同哈希。
        salt = os.urandom(16)
        digest = hashlib.pbkdf2_hmac(
            "sha256",
            password.encode("utf-8"),
            salt,
            self.password_iterations,
        )
        return {
            # 保存算法和参数，未来更换参数后仍能校验旧密码。
            "algorithm": PASSWORD_ALGORITHM,
            "iterations": self.password_iterations,
            "salt": base64.b64encode(salt).decode("ascii"),
            "hash": base64.b64encode(digest).decode("ascii"),
        }

    @staticmethod
    def _verify_password(password: str, record: Mapping[str, Any]) -> bool:
        # 使用记录中的盐和迭代次数重新计算摘要，再与保存值比较。
        try:
            if record.get("algorithm") != PASSWORD_ALGORITHM:
                return False
            iterations = int(record["iterations"])
            salt = base64.b64decode(
                str(record["salt"]),
                validate=True,
            )
            expected = base64.b64decode(
                str(record["hash"]),
                validate=True,
            )
            actual = hashlib.pbkdf2_hmac(
                "sha256",
                str(password).encode("utf-8"),
                salt,
                iterations,
            )
            # compare_digest 使用常量时间比较，降低根据响应耗时猜测哈希的风险。
            return hmac.compare_digest(actual, expected)
        except (KeyError, TypeError, ValueError):
            # 记录损坏或字段类型错误时统一视为验证失败，不泄露内部细节。
            return False

    @staticmethod
    def _find_user_unlocked(
        users: List[Dict[str, Any]],
        user_id: str,
    ) -> Optional[Dict[str, Any]]:
        # 按稳定的用户 ID 查找；找不到时 next 的默认值返回 None。
        return next(
            (
                user
                for user in users
                if isinstance(user, dict) and str(user.get("id")) == str(user_id)
            ),
            None,
        )

    @staticmethod
    def _find_user_by_username_unlocked(
        users: List[Dict[str, Any]],
        username_key: str,
    ) -> Optional[Dict[str, Any]]:
        # 登录和重名检查都使用规范化后的 username_key，而不是显示用的 username。
        return next(
            (
                user
                for user in users
                if isinstance(user, dict)
                and user.get("username_key") == username_key
            ),
            None,
        )

    def _face_path_for_record(self, record: Mapping[str, Any]) -> Path:
        # JSON 中只保存文件名，不保存任意绝对路径。
        # 这里拒绝带目录分隔符的名字，避免损坏数据诱导程序访问 faces_dir 之外的文件。
        vector_file = str(record.get("vector_file", ""))
        if not vector_file or Path(vector_file).name != vector_file:
            raise AuthStorageError("人脸特征文件名无效。")
        return self.faces_dir / vector_file

    def register_user(
        self,
        username: str,
        password: str,
        display_name: str = "",
        face_vector: Optional[np.ndarray] = None,
    ) -> Dict[str, Any]:
        # 第一步先在锁外校验输入和归一化人脸向量；这些步骤不读写共享的 users.json。
        # 因此让它们不占锁，可以减少其他请求等待的时间。
        clean_username = self._validate_username(username)
        # clean_username 用于界面展示；username_key 专门用于大小写无关的唯一性判断。
        username_key = normalize_username(clean_username)
        clean_password = self._validate_password(password)
        clean_display_name = self._validate_display_name(
            display_name,
            clean_username,
        )
        prepared_face = (
            # 可选注册人脸：统一转成 float32、检查 512 维，并做 L2 归一化。
            normalize_face_vector(
                face_vector,
                expected_size=FACE_VECTOR_SIZE,
            )
            if face_vector is not None
            else None
        )

        with self._lock:
            # 从读取、查重、写特征、写账号到回滚，整个过程串行执行，防止并发重名。
            data = self._load_data_unlocked()
            users = data["users"]
            if self._find_user_by_username_unlocked(users, username_key):
                raise UsernameAlreadyExists("该用户名已被注册。")

            user_id = uuid.uuid4().hex
            # 先写人脸文件，再写 JSON 索引。若 JSON 写入失败，会在下面删除人脸文件。
            face_record: Optional[Dict[str, str]] = None
            face_path: Optional[Path] = None
            if prepared_face is not None:
                # 文件以用户 ID 命名，天然与账号一一对应。
                vector_file = f"{user_id}.npy"
                face_path = self.faces_dir / vector_file
                save_face_vector(face_path, prepared_face)
                face_record = {
                    "vector_file": vector_file,
                    "registered_at": _utc_now(),
                }

            user = {
                # username 保留展示形式，username_key 保存规范化后的登录查找键。
                "id": user_id,
                "username": clean_username,
                "username_key": username_key,
                "display_name": clean_display_name,
                "created_at": _utc_now(),
                "password": self._hash_password(clean_password),
                # face 为 None 表示未绑定；否则记录文件名和绑定时间。
                "face": face_record,
            }
            users.append(user)
            try:
                self._write_data_unlocked(users)
            except Exception:
                # JSON 没保存成功，就撤销刚刚写入的孤立人脸文件。
                # 原始写盘异常继续向上抛出，调用方不会被“清理失败”掩盖。
                if face_path is not None:
                    try:
                        face_path.unlink(missing_ok=True)
                    except OSError:
                        pass
                raise
            # 返回公开字段，调用方拿不到密码哈希等内部数据。
            return self._public_user(user)

    def authenticate_password(
        self,
        username: str,
        password: str,
    ) -> Optional[Dict[str, Any]]:
        # 登录失败统一返回 None，不区分“用户不存在”和“密码错误”，避免泄露账号是否存在。
        try:
            username_key = normalize_username(username)
        except (TypeError, ValueError):
            return None
        if not username_key:
            return None

        with self._lock:
            # 读取用户列表、查找和验证密码共享同一次加锁区间。
            data = self._load_data_unlocked()
            user = self._find_user_by_username_unlocked(
                data["users"],
                username_key,
            )
            if user is None:
                return None
            if not self._verify_password(password, user.get("password", {})):
                return None
            return self._public_user(user)

    def register_face(
        self,
        user_id: str,
        face_vector: np.ndarray,
    ) -> Dict[str, Any]:
        """Create or replace the face binding for an existing account."""
        # 人脸可能已经存在；本方法既负责首次绑定，也负责替换旧特征。
        prepared_face = normalize_face_vector(
            face_vector,
            expected_size=FACE_VECTOR_SIZE,
        )

        with self._lock:
            data = self._load_data_unlocked()
            user = self._find_user_unlocked(data["users"], str(user_id))
            if user is None:
                raise AuthValidationError("当前账号不存在。")

            vector_file = f"{user_id}.npy"
            face_path = self.faces_dir / vector_file
            # 先记住旧 face 记录，万一新 JSON 写入失败要把内存中的账号对象恢复原状。
            previous_face = user.get("face")
            previous_path: Optional[Path] = None
            if previous_face:
                try:
                    # 旧记录正常情况下与 face_path 相同，但保留此逻辑可兼容旧文件命名。
                    previous_path = self._face_path_for_record(previous_face)
                except AuthStorageError:
                    previous_path = None

            # save_face_vector 内部同样使用临时文件加原子替换，避免特征文件写坏。
            save_face_vector(face_path, prepared_face)
            user["face"] = {
                "vector_file": vector_file,
                "registered_at": _utc_now(),
            }
            try:
                self._write_data_unlocked(data["users"])
            except Exception:
                # JSON 保存失败时，恢复用户的旧 face 字段并删掉本次新文件。
                if previous_face:
                    user["face"] = previous_face
                else:
                    user.pop("face", None)
                try:
                    face_path.unlink(missing_ok=True)
                except OSError:
                    pass
                raise

            if previous_path is not None and previous_path != face_path:
                # 新记录成功后，旧特征文件已不再被引用，可以安全清理。
                # 清理失败只忽略文件删除异常，不影响已经成功的绑定结果。
                try:
                    previous_path.unlink(missing_ok=True)
                except OSError:
                    pass

            return self._public_user(user)

    def get_user(self, user_id: str) -> Optional[Dict[str, Any]]:
        # 空 ID 无需访问磁盘，直接视为未找到。
        if not user_id:
            return None
        with self._lock:
            data = self._load_data_unlocked()
            user = self._find_user_unlocked(data["users"], str(user_id))
            return self._public_user(user) if user is not None else None

    def list_users(self) -> List[Dict[str, Any]]:
        # 即使 JSON 列表里混入非字典数据也跳过，保证接口层始终拿到标准公开结构。
        with self._lock:
            data = self._load_data_unlocked()
            return [
                self._public_user(user)
                for user in data["users"]
                if isinstance(user, dict)
            ]

    def list_face_gallery(self) -> Dict[str, np.ndarray]:
        # gallery 的键是用户 ID，值是该用户的 512 维人脸特征。
        # 它供登录时进行“当前人脸与所有已注册人脸”的比对。
        gallery: Dict[str, np.ndarray] = {}
        with self._lock:
            data = self._load_data_unlocked()
            for user in data["users"]:
                if not isinstance(user, dict) or not user.get("face"):
                    continue
                try:
                    vector_path = self._face_path_for_record(user["face"])
                    gallery[str(user["id"])] = load_face_vector(
                        vector_path,
                        expected_size=FACE_VECTOR_SIZE,
                    )
                except (AuthStorageError, FaceRecognitionError, KeyError):
                    # 单个用户的人脸文件缺失或损坏时跳过该用户，不影响其他人登录。
                    continue
        return gallery

    def get_face_path(self, user_id: str) -> Optional[Path]:
        # 仅返回特征文件路径，不读取向量；没有账号或没绑定人脸时返回 None。
        with self._lock:
            data = self._load_data_unlocked()
            user = self._find_user_unlocked(data["users"], str(user_id))
            if user is None or not user.get("face"):
                return None
            return self._face_path_for_record(user["face"])

    def delete_user(self, user_id: str) -> bool:
        # 删除顺序是：先从 JSON 的用户列表中移除，再尽力删除人脸文件。
        # JSON 是账号是否存在的主索引，所以它必须先更新成功。
        with self._lock:
            data = self._load_data_unlocked()
            users = data["users"]
            user = self._find_user_unlocked(users, str(user_id))
            if user is None:
                return False

            face_path = (
                # 删除账号前先按安全规则解析出对应特征路径。
                self._face_path_for_record(user["face"])
                if user.get("face")
                else None
            )
            data["users"] = [
                item for item in users if str(item.get("id")) != str(user_id)
            ]
            self._write_data_unlocked(data["users"])
            if face_path is not None:
                # 账号已从主数据移除；孤立特征文件清理失败时仍返回成功。
                try:
                    face_path.unlink(missing_ok=True)
                except OSError:
                    pass
            return True
