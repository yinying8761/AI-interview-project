import json
from typing import Any, Dict, List, Optional

import requests

from backend.app.config import Config

# 本文件用 requests 调用 DeepSeek 的聊天补全 HTTP API。
# InterviewService 负责组织面试 Prompt，本文件只负责发请求、处理网络错误和解析响应。


class DeepSeekError(RuntimeError):
    """Raised when the DeepSeek API cannot return a usable response."""


class DeepSeekService:
    """Small client for DeepSeek's OpenAI-compatible chat completion API."""

    def __init__(
        self,
        api_key: Optional[str] = None,
        api_url: str = Config.DEEPSEEK_API_URL,
        model: str = Config.DEEPSEEK_MODEL,
        timeout: int = Config.DEEPSEEK_TIMEOUT,
    ):
        # API Key 优先使用构造参数，其次读取环境配置；strip 去掉复制粘贴产生的空格。
        self.api_key = (api_key or Config.DEEPSEEK_API_KEY).strip()
        self.api_url = api_url.rstrip("/")
        self.model = model
        self.timeout = timeout
        # Session 会复用底层 TCP 连接，连续多次模型调用比每次新建连接更高效。
        self.session = requests.Session()

        if not self.api_key:
            # 没有 Key 时尽早报清晰错误，避免后续发出必然失败的请求。
            raise ValueError(
                "未配置 DeepSeek API Key。请在项目根目录的 .env 文件中设置 "
                "DEEPSEEK_API_KEY，或在系统环境变量中配置后重试。"
            )

    def chat(
        self,
        messages: List[Dict[str, str]],
        temperature: float = 0.7,
        max_tokens: int = 1600,
        response_format: Optional[Dict[str, str]] = None,
    ) -> str:
        """Send a chat completion request and return the assistant content."""
        if not messages:
            raise ValueError("messages 不能为空")

        payload: Dict[str, Any] = {
            # stream=False 表示一次请求拿到完整回答，不接收流式小片段。
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": False,
        }
        if response_format:
            # 评估接口要求模型返回 JSON 时，把响应格式一起告诉 API。
            payload["response_format"] = response_format

        headers = {
            # Bearer Token 是 API 身份凭证；绝不能把该请求头输出到日志或前端。
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        try:
            # timeout 同时限制连接或响应时间，防止请求无限挂着占用 Flask 工作线程。
            response = self.session.post(
                self.api_url,
                headers=headers,
                json=payload,
                timeout=self.timeout,
            )
        except requests.Timeout as exc:
            # 将第三方库的底层异常统一包装成业务能识别的 DeepSeekError。
            raise DeepSeekError("DeepSeek API 请求超时，请稍后重试。") from exc
        except requests.RequestException as exc:
            raise DeepSeekError(f"无法连接 DeepSeek API：{exc}") from exc

        if response.status_code >= 400:
            # 尽量提取 DeepSeek 返回的具体错误消息，便于排查 Key、额度或参数问题。
            detail = self._extract_error_message(response)
            raise DeepSeekError(
                f"DeepSeek API 返回错误（HTTP {response.status_code}）：{detail}"
            )

        try:
            # 标准 OpenAI 兼容响应结构：choices[0].message.content 是回答正文。
            data = response.json()
            content = data["choices"][0]["message"]["content"]
        except (ValueError, KeyError, IndexError, TypeError) as exc:
            raise DeepSeekError("DeepSeek API 返回了无法解析的响应。") from exc

        if not isinstance(content, str) or not content.strip():
            raise DeepSeekError("DeepSeek API 返回了空内容。")

        return content.strip()

    def chat_json(
        self,
        messages: List[Dict[str, str]],
        temperature: float = 0.2,
        max_tokens: int = 2200,
    ) -> Dict[str, Any]:
        """Request and parse a JSON object, tolerating fenced JSON output."""
        # temperature 较低，适合评估任务，减少模型随意发挥；输出再经过 JSON 解析。
        content = self.chat(
            messages=messages,
            temperature=temperature,
            max_tokens=max_tokens,
            response_format={"type": "json_object"},
        )
        return self.parse_json_object(content)

    @staticmethod
    def parse_json_object(content: str) -> Dict[str, Any]:
        """Parse JSON that may be wrapped in Markdown code fences."""
        cleaned = content.strip()
        if cleaned.startswith("```"):
            # 有的模型会返回 ```json ... ```。这里去掉首尾代码围栏后再解析。
            first_newline = cleaned.find("\n")
            if first_newline != -1:
                cleaned = cleaned[first_newline + 1:]
            if cleaned.endswith("```"):
                cleaned = cleaned[:-3].strip()

        try:
            parsed = json.loads(cleaned)
        except json.JSONDecodeError:
            # 若前后混有解释文字，尝试截取第一个 { 到最后一个 } 之间的内容。
            start = cleaned.find("{")
            end = cleaned.rfind("}")
            if start == -1 or end <= start:
                raise DeepSeekError("模型没有返回有效的 JSON 评估结果。")
            try:
                parsed = json.loads(cleaned[start:end + 1])
            except json.JSONDecodeError as exc:
                raise DeepSeekError("模型返回的 JSON 评估结果格式错误。") from exc

        if not isinstance(parsed, dict):
            raise DeepSeekError("模型返回的评估结果必须是 JSON 对象。")
        return parsed

    @staticmethod
    def _extract_error_message(response: requests.Response) -> str:
        # 优先读取 error.message；响应不是 JSON 时退回纯文本，最后才使用“未知错误”。
        try:
            payload = response.json()
            error = payload.get("error", payload)
            if isinstance(error, dict):
                return str(error.get("message") or error)
            return str(error)
        except ValueError:
            return response.text.strip() or "未知错误"
