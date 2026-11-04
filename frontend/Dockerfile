# Multi-stage Dockerfile for WWTRADING Angular Frontend (Production Node.js Serving)
# Stage 1: Build Angular application
FROM node:22-alpine AS build

WORKDIR /app

# Enable pnpm matching packageManager in package.json (pnpm@11.19.0)
RUN corepack enable && corepack prepare pnpm@11.19.0 --activate

# Copy package manifests for optimal layer caching
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./

# Install dependencies strictly from lockfile
RUN pnpm install --frozen-lockfile

# Copy application source code and configuration
COPY angular.json tsconfig*.json tailwind.config.js postcss.config.js ./
COPY scripts ./scripts
COPY public ./public
COPY src ./src

# Build Angular production bundle
RUN pnpm run build

# Generate SEO files if script exists
RUN pnpm run seo:generate || true

# Stage 2: Serve with Lightweight Node.js Production Server (No Nginx)
FROM node:22-alpine AS runtime

WORKDIR /app

# Run as non-root node user for container security
USER node

# Copy built Angular distribution and production server script
COPY --chown=node:node --from=build /app/dist/wwtrading/browser ./dist/wwtrading/browser
COPY --chown=node:node server.mjs ./server.mjs

ENV NODE_ENV=production
ENV PORT=3000
ENV HOST=0.0.0.0
ENV DIST_DIR=dist/wwtrading/browser

EXPOSE 3000

# Healthcheck targeting native /healthz endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:3000/healthz || exit 1

# Start the native Node.js production server
CMD ["node", "server.mjs"]
