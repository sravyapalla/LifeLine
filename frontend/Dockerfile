FROM node:20-alpine AS build
ARG VITE_API_BASE_URL=http://localhost:8088/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
WORKDIR /workspace
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
