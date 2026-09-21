# Mini CRM

Mini CRM 面向小微团队管理客户线索、销售阶段、跟进记录和待办提醒。本仓库采用前后端分离结构，当前不在目录整理任务中新增业务功能。

## 技术栈

- Web：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts、Axios
- API：Java 21、Spring Boot 3、Maven、Spring JDBC、PostgreSQL
- AI：Python 3.12、FastAPI；仅通过 Java API 进行服务端调用
- 数据模型：根目录 `prisma/` 仅保存 Prisma schema 和 migration
- 基础设施：Docker Compose、PostgreSQL 16、Redis 7.4

## 目录

```text
apps/web        Vue 前端
apps/api        Java Spring Boot 业务 API
apps/ai         Python FastAPI 内部 AI 服务
packages/shared 前端 TypeScript 共享类型和常量
prisma          PostgreSQL schema 和不可变 migration
infra/docker    API/AI Dockerfile
docs            产品、ER、API、架构、路线图和实施日志
scripts         工程预检脚本
archive         旧 NestJS 迁移参考，不参与运行时
```

## 本地启动

1. 复制环境变量模板：

   ```powershell
   Copy-Item .env.example .env
   ```

2. 安装前端和根级工具依赖：

   ```powershell
   pnpm install --frozen-lockfile
   ```

3. 启动 PostgreSQL 和 Redis。PostgreSQL 宿主端口为 `55432`：

   ```powershell
   docker compose up -d postgres redis
   ```

4. 应用根目录 Prisma migration：

   ```powershell
   pnpm exec prisma migrate deploy --schema prisma/schema.prisma
   ```

5. 启动 Java API：

   ```powershell
   mvn -f apps/api/pom.xml spring-boot:run
   ```

   健康检查：`http://localhost:8080/api/v1/health`；就绪检查：`http://localhost:8080/api/v1/ready`；OpenAPI：`http://localhost:8080/api/docs`。

6. 启动 AI 服务：

   ```powershell
   cd apps/ai
   python -m uvicorn main:app --reload --port 8000
   ```

7. 启动 Web：

   ```powershell
   pnpm -C apps/web dev
   ```

   前端默认地址为 `http://localhost:5173`，通过 `VITE_API_BASE_URL` 调用 Java API。

也可以使用 Compose 构建并启动 API、AI、PostgreSQL 和 Redis：

```powershell
docker compose up -d
```

## 结构整理验收

以下命令必须在仓库根目录通过：

```powershell
mvn -f apps/api/pom.xml test
pnpm -C apps/web lint
pnpm -C apps/web build
python -m compileall apps/ai
docker compose config
```

完整质量检查：

```powershell
pnpm lint
pnpm format:check
pnpm typecheck
pnpm test
pnpm test:e2e
pnpm build
```

预检脚本：

```bash
bash scripts/codex-preflight.sh
```

停止本地服务：

```powershell
docker compose down
```

架构调用关系见 [`docs/architecture.md`](docs/architecture.md)，工程约定见 [`AGENTS.md`](AGENTS.md)，实施记录见 [`docs/codex-log.md`](docs/codex-log.md)。
