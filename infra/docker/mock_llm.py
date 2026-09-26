import json

from fastapi import FastAPI, Request

app = FastAPI(title="Mini CRM Mock LLM")


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "mock-llm"}


@app.post("/v1/chat/completions")
async def chat_completions(request: Request) -> dict:
    payload = await request.json()
    messages = payload.get("messages", [])
    content = messages[1].get("content", "") if isinstance(messages, list) and len(messages) > 1 else ""
    normalized = str(content).lower().replace("-", " ").replace("_", " ")
    if "return json only with: channel" in normalized:
        result = {"channel": "message", "subject": "Follow-up", "script": "Hello, following up on our conversation.", "personalizationNotes": ["mock"]}
    elif "return json only with: shouldwakeup" in normalized:
        result = {"shouldWakeUp": True, "reason": "Mock inactivity reason.", "suggestedChannel": "message", "suggestedAtHours": 24}
    elif "return json only with: summary" in normalized:
        result = {"summary": "Mock follow-up summary.", "keyPoints": ["mock point"], "risks": [], "openQuestions": []}
    elif "return json only with: action" in normalized:
        result = {"action": "Schedule a follow-up call.", "rationale": "Mock recommendation.", "priority": "medium", "dueInHours": 24}
    else:
        result = {"score": 50, "level": "medium", "reasons": ["mock"], "confidence": 0.5}
    return {"choices": [{"message": {"content": json.dumps(result)}}]}
