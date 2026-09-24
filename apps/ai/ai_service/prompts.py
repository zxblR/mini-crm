from pathlib import Path

PROMPT_ROOT = Path(__file__).resolve().parent.parent / "prompts"


def load_prompt(name: str) -> str:
    allowed = {"intent-score", "follow-up-summary", "next-action", "script", "wake-up"}
    if name not in allowed:
        raise ValueError("Unknown prompt")
    path = PROMPT_ROOT / name / "v1.md"
    return path.read_text(encoding="utf-8")
