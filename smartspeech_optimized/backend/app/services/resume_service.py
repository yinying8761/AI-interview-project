from __future__ import annotations

# 本文件负责简历上传后的完整处理：检查格式和大小、提取文字、
# 清洗文本，并把每个用户最新一份解析结果原子地写入磁盘。

import hashlib
import io
import json
import os
import re
import tempfile
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict

from docx import Document
from pypdf import PdfReader

from backend.app.config import Config


class ResumeError(ValueError):
    """Raised when an uploaded resume cannot be accepted or parsed."""

# frozen=True 让创建后的简历对象不可修改，避免读取途中字段被意外改动。

@dataclass(frozen=True)
class ResumeDocument:
    # 这里保存简历的服务端数据，包括完整解析文本和所属用户。
    id: str
    user_id: str
    filename: str
    extension: str
    content: str
    size: int
    uploaded_at: str

    def public_state(self) -> Dict[str, Any]:
        # 返回给前端时只暴露预览，不返回完整简历正文。
        # 先把所有连续空白压成一个空格，再截取前 180 个字符。
        preview = re.sub(r"\s+", " ", self.content).strip()[:180]
        return {
            "id": self.id,
            "filename": self.filename,
            "extension": self.extension,
            "size": self.size,
            "uploaded_at": self.uploaded_at,
            "extracted_chars": len(self.content),
            "preview": preview,
        }

    def to_dict(self) -> Dict[str, Any]:
        # 写磁盘时保留 user_id 和完整 content；version 方便未来升级文件格式。
        return {
            "version": 1,
            **self.public_state(),
            "user_id": self.user_id,
            "content": self.content,
        }

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "ResumeDocument":
        # 读取旧 JSON 时做最低限度校验；解析文本为空说明数据不可用。
        content = str(payload.get("content") or "").strip()
        if not content:
            raise ResumeError("简历解析结果为空，请重新上传。")
        return cls(
            id=str(payload.get("id") or ""),
            user_id=str(payload.get("user_id") or ""),
            filename=str(payload.get("filename") or "resume"),
            extension=str(payload.get("extension") or ""),
            content=content,
            size=max(0, int(payload.get("size") or 0)),
            uploaded_at=str(payload.get("uploaded_at") or ""),
        )


class ResumeService:
    """Extracts resume text and persists only the latest parsed copy per user."""

    ALLOWED_EXTENSIONS = {"pdf", "doc", "docx"}

    def __init__(
        self,
        data_dir: str | Path = Config.RESUME_DATA_DIR,
        temp_dir: str | Path = Config.RESUME_TEMP_DIR,
        max_size: int = Config.MAX_RESUME_SIZE,
        max_chars: int = Config.RESUME_MAX_EXTRACTED_CHARS,
    ) -> None:
        # data_dir 保存解析结果，temp_dir 只存放 DOC 转换时的临时文件。
        self.data_dir = Path(data_dir)
        self.temp_dir = Path(temp_dir)
        self.max_size = max(1024, int(max_size))
        # 限制提取字符数，防止超大文本撑爆后续大模型 Prompt。
        self.max_chars = max(1000, int(max_chars))
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.temp_dir.mkdir(parents=True, exist_ok=True)

    def save(self, user_id: str, filename: str, data: bytes) -> ResumeDocument:
        # 文件名只取最后一段并清除控制字符，避免 ../../ 之类路径穿越。
        clean_name = self._safe_filename(filename)
        extension = Path(clean_name).suffix.lower().lstrip(".")
        if extension not in self.ALLOWED_EXTENSIONS:
            raise ResumeError("仅支持 PDF、DOC 和 DOCX 格式的简历。")
        if not data:
            raise ResumeError("未收到简历文件内容。")
        if len(data) > self.max_size:
            # 把字节数换算成 MB 后写入错误提示。
            raise ResumeError(
                f"简历不能超过 {self.max_size // (1024 * 1024)} MB。"
            )

        # 根据扩展名选择 DOCX、PDF 或 DOC 解析器。
        content = self._extract(data, extension)
        if len(content) < 30:
            # 扫描件 PDF 通常提取不到文字，需要用户转成可复制文本的格式。
            raise ResumeError("未能从简历中提取到足够文本，请确认文件不是扫描图片。")

        # 生成随机业务流程 id 和 UTC 上传时间。
        item = ResumeDocument(
            id=uuid.uuid4().hex,
            user_id=str(user_id),
            filename=clean_name,
            extension=extension,
            content=content,
            size=len(data),
            uploaded_at=datetime.now(timezone.utc).isoformat(),
        )
        # 每个用户只保留 latest.json，新简历会原子替换旧简历。
        self._write_latest(item)
        return item

    def latest(self, user_id: str) -> ResumeDocument | None:
        # 用户目录中只读取 latest.json；不存在表示尚未上传。
        path = self._user_dir(user_id) / "latest.json"
        if not path.is_file():
            return None
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
            item = ResumeDocument.from_dict(payload)
        except (OSError, ValueError, TypeError, json.JSONDecodeError):
            # 文件损坏或字段类型错误都视为没有可用简历，不让接口崩溃。
            return None
        if item.user_id != str(user_id):
            # 第二层归属检查，防止目录或文件被意外替换后读取到他人数据。
            return None
        return item

    def get(self, user_id: str, resume_id: str) -> ResumeDocument | None:
        # 当前设计每位用户只有一份最新简历，因此 id 不匹配就返回 None。
        item = self.latest(user_id)
        if item is None or item.id != str(resume_id):
            return None
        return item

    def delete(self, user_id: str) -> bool:
        # 删除最新简历文件。文件本来就不存在时返回 False 供 API 返回 404。
        path = self._user_dir(user_id) / "latest.json"
        if not path.is_file():
            return False
        try:
            path.unlink()
        except OSError as exc:
            raise ResumeError(f"删除简历失败：{exc}") from exc
        return True

    def extract_text(self, filename: str, data: bytes) -> str:
        """仅提取简历文本，不落盘、不生成版本记录，供 /api/ai/resume/parse 使用。"""
        if not data:
            raise ResumeError("简历文件为空。")
        extension = Path(filename).suffix.lower().lstrip(".")
        if extension not in self.ALLOWED_EXTENSIONS:
            raise ResumeError("请上传 PDF、DOC 或 DOCX 格式的简历。")
        return self._extract(data, extension)

    def _extract(self, data: bytes, extension: str) -> str:
        try:
            # 扩展名在 save() 中已经白名单校验，这里只负责分发。
            if extension == "docx":
                raw_text = self._extract_docx(data)
            elif extension == "pdf":
                raw_text = self._extract_pdf(data)
            else:
                raw_text = self._extract_doc(data)
        except ResumeError:
            raise
        except Exception as exc:
            # 把第三方解析库的复杂异常包装成用户能理解的 ResumeError。
            raise ResumeError(f"简历解析失败：{exc}") from exc
        # 所有解析器都经过统一的换行、空白和大长度处理。
        return self._normalize_text(raw_text)

    @staticmethod
    def _extract_docx(data: bytes) -> str:
        # python-docx 可直接从内存字节读取，不必先写临时文件。
        document = Document(io.BytesIO(data))
        blocks = [paragraph.text.strip() for paragraph in document.paragraphs]
        for table in document.tables:
            # 很多简历的重要信息在表格中，因此逐行拼成“单元格 | 单元格”。
            for row in table.rows:
                values = [cell.text.strip() for cell in row.cells if cell.text.strip()]
                if values:
                    blocks.append(" | ".join(values))
        return "\n".join(item for item in blocks if item)

    @staticmethod
    def _extract_pdf(data: bytes) -> str:
        # pypdf 同样直接从内存读取 PDF 页对象。
        reader = PdfReader(io.BytesIO(data))
        if reader.is_encrypted:
            # 先尝试使用空密码解密；失败说明文档确实受密码保护。
            try:
                reader.decrypt("")
            except Exception as exc:
                raise ResumeError("PDF 已加密，请上传未加密的简历。") from exc
        return "\n".join((page.extract_text() or "") for page in reader.pages)

    def _extract_doc(self, data: bytes) -> str:
        # 旧版 .doc 没有纯 Python 解析器，本项目通过本机 Microsoft Word COM 转换。
        try:
            import pythoncom
            import win32com.client
        except ImportError as exc:
            raise ResumeError(
                "当前环境无法解析 DOC 文件，请将简历另存为 DOCX 或 PDF 后上传。"
            ) from exc

        temp_path = self.temp_dir / f"resume-{uuid.uuid4().hex}.doc"
        word = None
        document = None
        pythoncom.CoInitialize()
        try:
            # Word 必须在磁盘上打开文件，因此先写随机临时文件。
            temp_path.write_bytes(data)
            # DispatchEx 创建独立的 Word 进程，并隐藏窗口、关闭交互弹窗。
            word = win32com.client.DispatchEx("Word.Application")
            word.Visible = False
            word.DisplayAlerts = 0
            document = word.Documents.Open(
                str(temp_path),
                ReadOnly=True,
                AddToRecentFiles=False,
                Visible=False,
            )
            return str(document.Content.Text or "")
        except Exception as exc:
            raise ResumeError(
                "DOC 解析需要本机安装 Microsoft Word，请安装 Word 或转为 DOCX/PDF。"
            ) from exc
        finally:
            # 无论成功或异常，都关闭文档、退出 Word、删除临时文件并释放 COM。
            if document is not None:
                try:
                    document.Close(False)
                except Exception:
                    pass
            if word is not None:
                try:
                    word.Quit()
                except Exception:
                    pass
            try:
                if temp_path.exists():
                    os.remove(temp_path)
            finally:
                pythoncom.CoUninitialize()

    def _normalize_text(self, value: str) -> str:
        # 去掉 NUL 和多余空格，把不同系统的换行统一成 \n。
        text = str(value or "").replace("\x00", "")
        text = text.replace("\r\n", "\n").replace("\r", "\n")
        lines = [re.sub(r"[ \t\f\v]+", " ", line).strip() for line in text.split("\n")]
        compact = "\n".join(line for line in lines if line)
        # 最多保留两个连续空行，最后按配置限制字符总数。
        compact = re.sub(r"\n{3,}", "\n\n", compact).strip()
        return compact[: self.max_chars]

    def _write_latest(self, item: ResumeDocument) -> None:
        # 先在同目录创建临时文件，完整写入后再原子替换 latest.json。
        # 这样进程中途失败时，旧简历仍然可用，不会留下半截 JSON。
        user_dir = self._user_dir(item.user_id)
        user_dir.mkdir(parents=True, exist_ok=True)
        destination = user_dir / "latest.json"
        fd, temp_name = tempfile.mkstemp(
            prefix="resume-",
            suffix=".tmp",
            dir=str(user_dir),
        )
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                json.dump(item.to_dict(), handle, ensure_ascii=False, indent=2)
                handle.write("\n")
            os.replace(temp_name, destination)
        except Exception:
            try:
                os.remove(temp_name)
            except OSError:
                pass
            raise

    def _user_dir(self, user_id: str) -> Path:
        # 不直接把 user_id 当目录名，而是取 SHA-256 摘要前 24 位。
        # 这样既避开特殊字符问题，也让磁盘目录不直接暴露账号编号。
        digest = hashlib.sha256(str(user_id).encode("utf-8")).hexdigest()[:24]
        return self.data_dir / digest

    @staticmethod
    def _safe_filename(filename: str) -> str:
        # Path(...).name 去掉路径部分，正则清除换行等控制字符，并限制长度。
        name = Path(str(filename or "resume")).name.strip()
        name = re.sub(r"[\x00-\x1f\x7f]+", "", name)
        return name[:180] or "resume"
