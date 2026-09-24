from datetime import datetime
from typing import Literal

import re

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
        alias_generator=lambda value: re.sub(r"_([a-z])", lambda match: match.group(1).upper(), value),
        populate_by_name=True,
    )


class LeadContext(StrictModel):
    lead_id: str = Field(min_length=1, max_length=36)
    status: Literal["new", "contacted", "qualified", "proposal", "negotiation", "won", "lost"]
    source: str = Field(max_length=60)
    industry: str | None = Field(default=None, max_length=80)
    region: str | None = Field(default=None, max_length=100)
    notes: str | None = Field(default=None, max_length=1000)
    created_at: datetime
    next_follow_up_at: datetime | None = None
    last_interaction_at: datetime | None = None
    follow_ups: list[dict[str, str | None]] = Field(default_factory=list, max_length=20)
    open_tasks: list[dict[str, str | None]] = Field(default_factory=list, max_length=10)


class IntentScoreRequest(StrictModel):
    context: LeadContext


class IntentFactor(StrictModel):
    name: str = Field(min_length=1, max_length=40)
    score: int = Field(ge=0, le=10)
    evidence: str = Field(max_length=240)


class IntentScoreResponse(StrictModel):
    score: int = Field(ge=0, le=100)
    band: Literal["low", "medium", "high"]
    confidence: float = Field(ge=0, le=1)
    factors: list[IntentFactor] = Field(max_length=8)
    caveats: list[str] = Field(max_length=8)


class FollowUpSummaryRequest(StrictModel):
    context: LeadContext


class FollowUpSummaryResponse(StrictModel):
    summary: str = Field(min_length=1, max_length=1200)
    key_points: list[str] = Field(max_length=8)
    outcomes: list[str] = Field(max_length=8)
    open_questions: list[str] = Field(max_length=8)
    last_interaction_at: datetime | None


class NextActionRequest(StrictModel):
    context: LeadContext
    intent_score: int | None = Field(default=None, ge=0, le=100)


class NextActionResponse(StrictModel):
    action_type: Literal["schedule_call", "send_proposal", "confirm_need", "follow_up", "close"]
    title: str = Field(min_length=1, max_length=200)
    rationale: str = Field(min_length=1, max_length=600)
    suggested_due_at: datetime | None
    priority: Literal["low", "medium", "high"]
    prerequisites: list[str] = Field(max_length=6)


class ScriptRequest(StrictModel):
    context: LeadContext
    customer_type: Literal["new_lead", "price_sensitive", "existing_customer", "decision_maker", "unknown"]
    channel: Literal["call", "wechat", "email"]
    objective: str = Field(min_length=1, max_length=300)
    objection: str | None = Field(default=None, max_length=300)


class ObjectionReply(StrictModel):
    objection: str = Field(min_length=1, max_length=200)
    reply: str = Field(min_length=1, max_length=600)


class ScriptResponse(StrictModel):
    channel: Literal["call", "wechat", "email"]
    opening: str = Field(min_length=1, max_length=800)
    objection_replies: list[ObjectionReply] = Field(max_length=5)
    closing: str = Field(min_length=1, max_length=500)
    disclaimer: str = Field(max_length=200)


class WakeUpRequest(StrictModel):
    context: LeadContext
    inactivity_days: int = Field(ge=14, le=3650)


class WakeUpResponse(StrictModel):
    should_wake_up: bool
    inactivity_days: int = Field(ge=0, le=3650)
    reason: str = Field(min_length=1, max_length=300)
    recommended_channel: Literal["call", "wechat", "email"]
    message: str = Field(min_length=1, max_length=1000)
    suggested_due_at: datetime | None
