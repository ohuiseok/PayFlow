FROM node:22-alpine AS build

WORKDIR /app

ARG EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
ARG EXPO_PUBLIC_USE_DUMMY_DATA=false

ENV EXPO_PUBLIC_API_BASE_URL=${EXPO_PUBLIC_API_BASE_URL}
ENV EXPO_PUBLIC_USE_DUMMY_DATA=${EXPO_PUBLIC_USE_DUMMY_DATA}

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build:web

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --retries=12 CMD wget -qO- http://127.0.0.1/health >/dev/null 2>&1 || exit 1
