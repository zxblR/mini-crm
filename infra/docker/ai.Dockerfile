FROM python:3.12-slim

WORKDIR /app
COPY apps/ai/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY apps/ai/main.py main.py
COPY apps/ai/ai_service ai_service
COPY apps/ai/prompts prompts

EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
