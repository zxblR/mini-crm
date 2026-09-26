FROM node:22-alpine

WORKDIR /workspace
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/package.json
COPY packages/shared/package.json packages/shared/package.json
COPY prisma prisma
RUN corepack enable \
    && pnpm config set registry https://registry.npmmirror.com \
    && pnpm install --frozen-lockfile --prod=false
