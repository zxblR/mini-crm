import os
import json
import unittest
from datetime import datetime, timezone
from unittest.mock import patch

from fastapi.testclient import TestClient

from main import app
from ai_service.schemas import IntentScoreResponse


class InternalAiTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        self.headers = {"Authorization": "Bearer test-secret"}
        self.context = {
            "lead_id": "00000000-0000-0000-0000-000000000001",
            "status": "qualified",
            "source": "website",
            "created_at": datetime.now(timezone.utc).isoformat(),
            "follow_ups": [],
            "open_tasks": [],
        }

    def test_requires_service_token(self) -> None:
        response = self.client.post("/internal/v1/intent-score", json={"context": self.context})
        self.assertEqual(response.status_code, 401)

    def test_intent_score_uses_mock_llm(self) -> None:
        expected = IntentScoreResponse(
            score=70,
            band="high",
            confidence=0.8,
            factors=[],
            caveats=[],
        )
        with patch.dict(os.environ, {"AI_SERVICE_TOKEN": "test-secret"}), patch(
            "ai_service.routes.generate", return_value=expected
        ):
            response = self.client.post(
                "/internal/v1/intent-score",
                headers=self.headers,
                json={"context": self.context},
            )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["score"], 70)

    def test_extra_fields_are_rejected(self) -> None:
        with patch.dict(os.environ, {"AI_SERVICE_TOKEN": "test-secret"}):
            response = self.client.post(
                "/internal/v1/intent-score",
                headers=self.headers,
                json={"context": self.context, "unexpected": True},
            )
        self.assertEqual(response.status_code, 422)

    def test_llm_http_client_is_mockable_without_paid_provider(self) -> None:
        class MockResponse:
            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return None

            def read(self, _limit):
                return json.dumps({
                    "choices": [{"message": {"content": json.dumps({
                        "score": 70, "band": "high", "confidence": 0.8,
                        "factors": [], "caveats": []
                    })}}]
                }).encode()

        with patch.dict(os.environ, {
            "AI_SERVICE_TOKEN": "test-secret",
            "LLM_API_KEY": "mock-key",
            "LLM_BASE_URL": "http://mock-llm",
        }), patch("ai_service.llm_client.urlopen", return_value=MockResponse()):
            response = self.client.post(
                "/internal/v1/intent-score",
                headers=self.headers,
                json={"context": self.context},
            )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["band"], "high")


if __name__ == "__main__":
    unittest.main()
