# OpenTelemetry Submitter for Mirth Connect

This Node.js application monitors Mirth Connect (Open Integration Engine) for completed messages and exports them as OpenTelemetry spans to Jaeger or other OTLP-compatible collectors.

## Features

- **Polling-based monitoring**: Watches channel statistics every 15 seconds (configurable) for new completed messages
- **Full message tracing**: Creates SERVER spans for source connectors and CLIENT spans for destinations
- **Cross-channel correlation**: Automatically links spans when messages are routed between channels via VM Dispatcher (using `sourceChannelId`/`sourceMessageId`)
- **Deterministic span IDs**: Uses hash-based IDs so spans can be correlated without external storage
- **Metadata propagation**: Includes promoted custom metadata columns and any values prefixed with `OTEL_` as span attributes
- **OTLP export**: Sends traces to Jaeger or any OTLP gRPC-compatible collector
- **Persistent state**: Tracks last exported message per channel to avoid duplicates across restarts
- **Docker support**: Runs as a sidecar container alongside Mirth Connect

## Prerequisites

- Node.js 18+ (or Docker)
- Mirth Connect server with API access
- User account with `DASHBOARD_VIEW` and `MESSAGES_VIEW` permissions
- OTLP-compatible collector (e.g., Jaeger)

## Installation

```bash
cd otel-submitter
npm install
```

## Configuration

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MIRTH_URL` | `https://localhost:8443` | Mirth Connect server URL |
| `MIRTH_API_VERSION` | `4.5.2` | API version path segment |
| `MIRTH_USERNAME` | `admin` | Login username |
| `MIRTH_PASSWORD` | `admin` | Login password |
| `POLL_INTERVAL_MS` | `15000` | Polling interval in milliseconds |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP gRPC endpoint |
| `DATA_DIR` | `.` | Directory for persistent state file |

## Running

### Local Development

```bash
# Production
npm start

# Development (with file watching)
npm run dev
```

### Docker

```bash
# Build the image
docker build -t otel-submitter .

# Run with Jaeger
docker run -d \
  --name otel-submitter \
  --network mirth-network \
  -e MIRTH_URL=https://mirth:8443 \
  -e MIRTH_USERNAME=admin \
  -e MIRTH_PASSWORD=admin \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317 \
  -v otel-state:/app/data \
  otel-submitter
```

### Docker Compose (with Jaeger)

```yaml
services:
  jaeger:
    image: jaegertracing/all-in-one:latest
    environment:
      - COLLECTOR_OTLP_ENABLED=true
    ports:
      - "16686:16686"  # Jaeger UI
      - "4317:4317"    # OTLP gRPC
      - "4318:4318"    # OTLP HTTP

  otel-submitter:
    build: ./otel-submitter
    environment:
      - MIRTH_URL=https://mirth:8443
      - MIRTH_USERNAME=admin
      - MIRTH_PASSWORD=admin
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
    volumes:
      - otel-state:/app/data
    depends_on:
      - jaeger

volumes:
  otel-state:
```

## How It Works

### Polling Strategy

1. Every poll interval, the app fetches `/channels/statuses/initial` to get current message counts
2. Compares counts against previous poll to detect channels with new completed messages
3. For channels with changes, queries messages with `startDate` filter (tracking last response date per channel)
4. Only processes messages where all connectors are in terminal states (SENT, FILTERED, ERROR, TRANSFORMED)

### Span Structure

For each completed message:

- **Source Span** (SERVER kind): Represents the message being received
  - Span name: `{channelName} receive`
  - Start time: `receivedDate`
  - End time: `responseDate`
  
- **Destination Spans** (CLIENT kind): One per destination connector
  - Span name: `{connectorName} send`
  - Start time: `sendDate` or `receivedDate`
  - End time: `responseDate`

### Cross-Channel Linking

When a message is sent via VM Dispatcher to another channel, the source map contains:
- `sourceChannelId`: The sending channel's ID
- `sourceMessageId`: The sending message's ID

The submitter uses these to generate matching trace/span IDs, linking the child message's trace to the parent.

### Span Attributes

All spans include:
- `mirth.channel.id`: Channel UUID
- `mirth.channel.name`: Human-readable channel name
- `mirth.message.id`: Message ID
- `mirth.connector.name`: Connector name
- `mirth.connector.metadata_id`: Connector's metadata ID (0 = source)
- `mirth.status`: Final status (SENT, FILTERED, ERROR, etc.)

Metadata attributes (for promoted columns or OTEL_ prefixed):
- `mirth.metadata.{columnName}`: Custom metadata column value
- `mirth.source.OTEL_*`: Source map values prefixed with OTEL_

## Current Limitations

- **Single instance**: Not designed for horizontal scaling (would need distributed locking)
- **No historical backfill**: First run starts from "now" - use `--backfill` flag if needed (not yet implemented)

## Future Enhancements

- OTLP HTTP exporter option
- Configurable metadata extraction rules
- Historical message backfill option
- Metrics export (message counts, processing times)
- Kubernetes deployment manifests
