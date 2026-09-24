from __future__ import annotations

import json

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import ValidationError

from .auth import require_service_token
from .llm import LlmError, complete_json
from .models import (
    FollowUpSummaryRequest,
    FollowUpSummaryResult,
    IntentScoreRequest,
    IntentScoreResult,
    NextActionRequest,
    NextActionResult,
    ScriptRequest,
    ScriptResult,
    WakeUpRequest,
    WakeUpResult,
)

router = APIRouter(prefix="/internal/v1", dependencies=[Depends(require_service_token)])


async def _run(prompt_type: str, values: dict[str, str], result_type):
    try:
        return result_type.model_validate(await complete_json(prompt_type, values))
    except LlmError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="AI provider unavailable") from exc
    except ValidationError as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="AI provider returned malformed data") from exc


@router.post("/intent-score", response_model=IntentScoreResult)
async def intent_score(request: IntentScoreRequest) -> IntentScoreResult:
    return await _run("intent-score", {"customer_text": request.customer_text, "recent_follow_ups": json.dumps(request.recent_follow_ups)}, IntentScoreResult)


@router.post("/follow-up-summary", response_model=FollowUpSummaryResult)
async def follow_up_summary(request: FollowUpSummaryRequest) -> FollowUpSummaryResult:
    return await _run("follow-up-summary", {"customer_text": request.customer_text, "recent_follow_ups": json.dumps(request.recent_follow_ups)}, FollowUpSummaryResult)


@router.post("/next-action", response_model=NextActionResult)
async def next_action(request: NextActionRequest) -> NextActionResult:
    return await _run("next-action", {"customer_text": request.customer_text, "recent_follow_ups": json.dumps(request.recent_follow_ups), "stage": request.stage}, NextActionResult)


@router.post("/script", response_model=ScriptResult)
async def script(request: ScriptRequest) -> ScriptResult:
    return await _run("script", {"customer_text": request.customer_text, "next_action": request.next_action}, ScriptResult)


@router.post("/wake-up", response_model=WakeUpResult)
async def wake_up(request: WakeUpRequest) -> WakeUpResult:
    return await _run("wake-up", {"customer_text": request.customer_text, "last_follow_up": request.last_follow_up or ""}, WakeUpResult)
