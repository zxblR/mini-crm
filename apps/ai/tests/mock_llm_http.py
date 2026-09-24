"""Local-only OpenAI-compatible mock used by tests and compose development."""

import json

from fastapi import FastAPI, Request

app = FastAPI()


@app.post("/v1/chat/completions")
async def chat_completions(request: Request) -> dict:
    payload = await request.json()
    messages = payload.get("messages", [])
    user_content = ""
    if isinstance(messages, list) and len(messages) > 1:
        content = messages[1].get("content", "")
        user_content = content if isinstance(content, str) else str(content)
    normalized_content = user_content.lower().replace("-", " ").replace("_", " ")

    if "return json only with: channel" in normalized_content:
        result = {
            "channel": "message",
            "subject": "Follow-up",
            "script": "Hello, following up on our conversation.",
            "personalizationNotes": ["mock"],
        }
    elif "return json only with: shouldwakeup" in normalized_content:
        result = {
            "shouldWakeUp": True,
            "reason": "Mock inactivity reason.",
            "suggestedChannel": "message",
            "suggestedAtHours": 24,
        }
    elif "return json only with: summary" in normalized_content:
        result = {
            "summary": "Mock follow-up summary.",
            "keyPoints": ["mock point"],
            "risks": [],
            "openQuestions": [],
        }
    elif "return json only with: action" in normalized_content:
        result = {
            "action": "Schedule a follow-up call.",
            "rationale": "Mock recommendation.",
            "priority": "medium",
            "dueInHours": 24,
        }
    elif "return json only with: score" in normalized_content:
        result = {"score": 50, "level": "medium", "reasons": ["mock"], "confidence": 0.5}
    else:
        result = {"score": 50, "level": "medium", "reasons": ["mock"], "confidence": 0.5}

    return {
        "choices": [
            {
                "message": {
                    "content": json.dumps(result)
                }
            }
        ]
    }
