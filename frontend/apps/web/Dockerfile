# posts-web — Next.js 15 standalone on node:22-alpine (non-root).
# Build context: frontend/ (pnpm workspace root)
#   docker build -f apps/web/Dockerfile -t posts-web:local .

FROM node:22-alpine AS base
RUN apk add --no-cache libc6-compat
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@10.33.4 --activate

FROM base AS deps
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml tsconfig.base.json ./
COPY apps/web/package.json ./apps/web/
COPY packages/ui/package.json ./packages/ui/
COPY packages/domain/package.json ./packages/domain/
COPY packages/api-client/package.json ./packages/api-client/
RUN pnpm install --frozen-lockfile

FROM base AS builder
COPY --from=deps /app/ ./
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
RUN pnpm --filter @posts/web build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

RUN addgroup --system --gid 1001 nodejs \
    && adduser --system --uid 1001 nextjs

# Standalone output (with outputFileTracingRoot = frontend/) places server at apps/web/server.js
COPY --from=builder --chown=nextjs:nodejs /app/apps/web/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/apps/web/.next/static ./apps/web/.next/static

USER nextjs
EXPOSE 3000

LABEL org.opencontainers.image.title="posts-web" \
      org.opencontainers.image.description="Posts SMM platform — web (Next.js)" \
      org.opencontainers.image.source="https://github.com/Viacheslav666/posts" \
      org.opencontainers.image.url="https://github.com/Viacheslav666/posts" \
      org.opencontainers.image.licenses="Proprietary" \
      org.opencontainers.image.vendor="Posts"

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -qO- http://127.0.0.1:3000/health >/dev/null || exit 1

CMD ["node", "apps/web/server.js"]
