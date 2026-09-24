from fastapi.testclient import TestClient

from ai_service import routes
from main import app


client = TestClient(app)
HEADERS = {"Authorization": "Bearer test-token"}


def test_requires_service_token(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_TOKEN", "test-token")
    response = client.post("/internal/v1/intent-score", json={"customer_text": "hello"})
    assert response.status_code == 401


def test_strict_validation_rejects_unknown_fields(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_TOKEN", "test-token")
    response = client.post(
        "/internal/v1/intent-score",
        headers=HEADERS,
        json={"customer_text": "hello", "unexpected": "instruction"},
    )
    assert response.status_code == 422


def test_route_validates_mock_llm_response(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_TOKEN", "test-token")

    async def malformed(*_args, **_kwargs):
        return {"score": "not-a-number"}

    monkeypatch.setattr(routes, "complete_json", malformed)
    response = client.post("/internal/v1/intent-score", headers=HEADERS, json={"customer_text": "hello"})
    assert response.status_code == 502


def test_route_returns_mock_llm_result(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_TOKEN", "test-token")

    async def mock_result(*_args, **_kwargs):
        return {"score": 80, "level": "high", "reasons": ["asked for pricing"], "confidence": 0.9}

    monkeypatch.setattr(routes, "complete_json", mock_result)
    response = client.post("/internal/v1/intent-score", headers=HEADERS, json={"customer_text": "asked for pricing"})
    assert response.status_code == 200
    assert response.json()["score"] == 80
