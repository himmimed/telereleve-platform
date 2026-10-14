# syntax=docker/dockerfile:1

# --- Build stage -----------------------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /workspace

# Dependencies first: this layer is cached as long as the lock file does not change.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
# Unit tests are not run here: they run in CI / `ng test`.
RUN npx ng build --configuration production

# --- Runtime stage: static files + reverse proxy to the backend ------------------------------
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
