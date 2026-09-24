from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class IntentScoreRequest(StrictModel):
    customer_text: str = Field(min_length=0, max_length=12000)
    recent_follow_ups: list[str] = Field(default_factory=list, max_length=50)


class FollowUpSummaryRequest(StrictModel):
    customer_text: str = Field(min_length=0, max_length=12000)
    recent_follow_ups: list[str] = Field(default_factory=list, max_length=50)


class NextActionRequest(StrictModel):
    customer_text: str = Field(min_length=0, max_length=12000)
    recent_follow_ups: list[str] = Field(default_factory=list, max_length=50)
    stage: str = Field(min_length=1, max_length=64)


class ScriptRequest(StrictModel):
    customer_text: str = Field(min_length=0, max_length=12000)
    next_action: str = Field(min_length=1, max_length=4000)


class WakeUpRequest(StrictModel):
    customer_text: str = Field(min_length=0, max_length=12000)
    last_follow_up: str | None = Field(default=None, max_length=4000)


class IntentScoreResult(StrictModel):
    score: int = Field(ge=0, le=100)
    level: Literal["low", "medium", "high"]
    reasons: list[str] = Field(max_length=10)
    confidence: float = Field(ge=0, le=1)


class FollowUpSummaryResult(StrictModel):
    summary: str = Field(min_length=1, max_length=4000)
    keyPoints: list[str] = Field(max_length=20)
    risks: list[str] = Field(max_length=20)
    openQuestions: list[str] = Field(max_length=20)


class NextActionResult(StrictModel):
    action: str = Field(min_length=1, max_length=1000)
    rationale: str = Field(min_length=1, max_length=2000)
    priority: Literal["low", "medium", "high"]
    dueInHours: int = Field(gt=0, le=8760)


class ScriptResult(StrictModel):
    channel: Literal["phone", "email", "message"]
    subject: str = Field(max_length=300)
    script: str = Field(min_length=1, max_length=5000)
    personalizationNotes: list[str] = Field(max_length=20)


class WakeUpResult(StrictModel):
    shouldWakeUp: bool
    reason: str = Field(min_length=1, max_length=1000)
    suggestedChannel: Literal["phone", "email", "message", "none"]
    suggestedAtHours: int = Field(ge=0, le=8760)
