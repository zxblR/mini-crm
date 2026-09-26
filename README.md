# Mini CRM

Mini CRM 面向小微团队管理客户线索、销售阶段、跟进记录和待办提醒。

## 技术栈

- Web：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts、Axios
- API：Java 21、Spring Boot 3、Maven、Spring JDBC、PostgreSQL
- AI：Python 3.12、FastAPI；仅通过 Java API 进行服务端调用
- 数据模型：根目录 `prisma/` 仅保存 Prisma schema 和 migration
- 基础设施：Docker Compose、PostgreSQL 16、Redis 7.4、nginx

## Docker 一键启动

先复制环境模板：

```powershell
Copy-Item .env.example .env
```

启动全部服务：

```powershell
docker compose up -d --build
```

默认首次启动会自动灌入演示数据（`SEED_ON_START=true`）。如需跳过 seed，使用：`SEED_ON_START=false docker compose up -d`。

启动完成后访问 [http://localhost:8080](http://localhost:8080)。Compose 会按以下顺序工作：

1. PostgreSQL 通过健康检查后，`migrate` 一次性容器执行根目录 Prisma migration。
2. migration 成功后启动 Java API 和 FastAPI。
3. nginx Web 容器提供静态文件，并把 `/api/v1` 同域反代到 API。

服务端口：

| 服务 | 容器端口 | 宿主机端口 |
| --- | ---: | ---: |
| web | 80 | 8080 |
| postgres | 5432 | 55432 |
| redis | 6379 | 6379 |
| api | 8080 | 仅 Compose 内网 |
| ai | 8000 | 仅 Compose 内网 |
| mock-llm | 9000 | 仅 Compose 内网 |

默认 LLM 是 Compose 内部的 `mock-llm`，对应 `.env` 中的 `LLM_BASE_URL=http://mock-llm:9000`，不产生外部模型费用。切换 DeepSeek 时，将 `LLM_BASE_URL` 改为真实 OpenAI-compatible 地址，并设置 `LLM_API_KEY` 与 `LLM_MODEL`。

数据库 migration 每次启动都会检查已应用版本。默认首次启动会自动灌入演示数据（`SEED_ON_START=true`）；生产部署应设置 `SEED_ON_START=false`，并先人工审阅 seed 数据。seed 不创建默认登录账号。

查看服务状态和日志：

```powershell
docker compose ps
docker compose logs -f api
```

停止服务：

```powershell
docker compose down
```

## 本地开发（不使用 Compose）

```powershell
pnpm install --frozen-lockfile
pnpm -C apps/web dev
mvn -f apps/api/pom.xml spring-boot:run
cd apps/ai
python -m uvicorn main:app --reload --port 8000
```

前端 Axios 默认使用同域 `/api/v1`；Vite 开发服务器通过 `VITE_DEV_API_TARGET` 代理到本地 Java API。

## 阶段 9 人工验收

在仓库根目录执行：

```powershell
docker compose config
docker compose up -d --build
docker compose ps
Invoke-WebRequest http://localhost:8080/api/v1/health
docker compose logs migrate
docker compose logs api
docker compose down
```

结构和运行时质量检查：

```powershell
mvn -f apps/api/pom.xml test
pnpm -C apps/web lint
pnpm -C apps/web build
python -m compileall apps/ai
```

架构调用关系见 [docs/architecture.md](docs/architecture.md)，工程约定见 [AGENTS.md](AGENTS.md)，实施记录见 [docs/codex-log.md](docs/codex-log.md)。
