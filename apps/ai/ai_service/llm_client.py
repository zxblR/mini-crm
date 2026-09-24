import json
import os
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from fastapi import HTTPException
from pydantic import BaseModel, ValidationError


def generate(prompt_name: str, user_data: dict[str, object], response_model: type[BaseModel]) -> BaseModel:
    base_url = os.getenv("LLM_BASE_URL", "https://api.deepseek.com/v1").rstrip("/")
    api_key = os.getenv("LLM_API_KEY", "")
    model = os.getenv("LLM_MODEL", "deepseek-chat")
    if not api_key:
        raise HTTPException(status_code=503, detail="AI provider is not configured")

    from .prompts import load_prompt

    payload = {
        "model": model,
        "temperature": 0.2,
        "max_tokens": int(os.getenv("LLM_MAX_OUTPUT_TOKENS", "768")),
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": load_prompt(prompt_name)},
            {"role": "user", "content": json.dumps(user_data, ensure_ascii=False)},
        ],
    }
    request = Request(
        f"{base_url}/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    timeout = float(os.getenv("LLM_TIMEOUT_SECONDS", "8"))
    for attempt in range(2):
        try:
            with urlopen(request, timeout=timeout) as response:
                response_body = json.loads(response.read(65536))
            content = response_body["choices"][0]["message"]["content"]
            try:
                return response_model.model_validate_json(content)
            except (ValidationError, ValueError) as exc:
                raise HTTPException(status_code=502, detail="AI provider returned an invalid response") from exc
        except HTTPError as exc:
            if attempt == 0 and exc.code in {429, 502, 503, 504}:
                continue
            raise HTTPException(status_code=503, detail="AI provider is unavailable") from exc
        except (URLError, TimeoutError, OSError) as exc:
            if attempt == 0:
                continue
            raise HTTPException(status_code=503, detail="AI provider is unavailable") from exc
        except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
            raise HTTPException(status_code=502, detail="AI provider returned an invalid response") from exc
    raise HTTPException(status_code=503, detail="AI provider is unavailable")
