# Build stage
FROM node:24-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production

# Production stage
FROM node:24-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S otel && \
    adduser -S otel -u 1001 -G otel

# Copy dependencies from builder
COPY --from=builder /app/node_modules ./node_modules

# Copy application source
COPY package*.json ./
COPY src ./src

# Create directory for state file and set permissions
RUN mkdir -p /app/data && chown -R otel:otel /app

# Use non-root user
USER otel

# Environment variables (can be overridden at runtime)
ENV NODE_ENV=production \
    MIRTH_URL=https://mirth:8443 \
    MIRTH_API_VERSION=4.5.2 \
    MIRTH_USERNAME=admin \
    MIRTH_PASSWORD=admin \
    POLL_INTERVAL_MS=15000 \
    OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317 \
    DATA_DIR=/app/data

# Volume for persistent state
VOLUME ["/app/data"]

CMD ["node", "src/index.js"]
