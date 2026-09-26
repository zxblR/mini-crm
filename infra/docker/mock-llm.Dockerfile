FROM python:3.12-slim

WORKDIR /app
COPY apps/ai/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY infra/docker/mock_llm.py mock_llm.py

EXPOSE 9000
CMD ["uvicorn", "mock_llm:app", "--host", "0.0.0.0", "--port", "9000"]
