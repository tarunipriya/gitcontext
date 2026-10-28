# Multi-stage Dockerfile for GitContext Frontend
# Stage 1: Build production bundle with Node 22
FROM node:22-alpine AS builder

WORKDIR /app

# Copy package descriptors and install dependencies
COPY package.json package-lock.json ./
RUN npm ci

# Copy application sources and build distribution bundle
COPY . .
RUN npm run build

# Stage 2: Serve via unprivileged Nginx (runs as non-root user on port 8080)
FROM nginxinc/nginx-unprivileged:alpine

# Copy Nginx server configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy production build assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
