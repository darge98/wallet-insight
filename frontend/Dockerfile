# syntax=docker/dockerfile:1

# ---- Stage 1: build -------------------------------------------------------
# package.json richiede engines.node "^22.22.3 || ^24.15.0 || >=26.0.0":
# node:24-alpine soddisfa il vincolo (24.x più recente >= 24.15.0).
FROM node:24-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

RUN npx nx build wallet

# ---- Stage 2: runtime -------------------------------------------------------
FROM nginx:alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/apps/wallet/browser /usr/share/nginx/html

EXPOSE 80
