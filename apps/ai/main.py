from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="Mini CRM AI Service", version="0.1.0")


class FollowUpSuggestionRequest(BaseModel):
    lead_name: str = Field(min_length=1, max_length=120)
    context: str = Field(default="", max_length=4000)


class FollowUpSuggestionResponse(BaseModel):
    suggestion: str
    service: str = "ai-python"


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "ai-python"}


@app.post("/v1/follow-up-suggestions", response_model=FollowUpSuggestionResponse)
def suggest_follow_up(request: FollowUpSuggestionRequest) -> FollowUpSuggestionResponse:
    context = request.context.strip()
    suffix = f"。结合已知信息：{context}" if context else ""
    return FollowUpSuggestionResponse(
        suggestion=f"建议联系{request.lead_name}，确认当前需求、预算和下一步时间{suffix}"
    )
