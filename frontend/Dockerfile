# 1) 빌드: Vite로 정적 파일을 만든다 (호스트에 Node 불필요)
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# 2) 서빙: nginx가 정적 파일을 내려주고 /api를 백엔드로, /media를 MediaMTX로 프록시한다
FROM nginx:1.29-alpine

# 시작할 때 RECORDING_MAX_UPLOAD_SIZE를 nginx 형식으로 바꾸고(docker/), 템플릿에 값을 채운다
COPY --chmod=755 docker/15-upload-size.envsh /docker-entrypoint.d/15-upload-size.envsh
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

ENV BACKEND_URL=http://backend:8080 \
    MEDIAMTX_URL=http://mediamtx:8889 \
    RECORDING_MAX_UPLOAD_SIZE=500MB

EXPOSE 80
