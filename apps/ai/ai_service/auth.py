from __future__ import annotations

import secrets

from fastapi import Header, HTTPException, status


def require_service_token(authorization: str | None = Header(default=None)) -> None:
    """Authenticate Java-to-AI traffic without exposing the LLM credential."""
    from os import environ

    expected = environ.get("AI_SERVICE_TOKEN", "")
    scheme, _, token = (authorization or "").partition(" ")
    if not expected or scheme.lower() != "bearer" or not secrets.compare_digest(token, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="unauthorized")
