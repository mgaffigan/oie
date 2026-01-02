import 'dotenv/config';

export const config = {
    mirth: {
        url: process.env.MIRTH_URL || 'https://localhost:8443',
        apiVersion: process.env.MIRTH_API_VERSION || '4.5.2',
        username: process.env.MIRTH_USERNAME || 'admin',
        password: process.env.MIRTH_PASSWORD || 'admin',
    },
    polling: {
        intervalMs: parseInt(process.env.POLL_INTERVAL_MS || '15000', 10),
    },
    otel: {
        // Future: OTLP endpoint configuration
        endpoint: process.env.OTEL_EXPORTER_OTLP_ENDPOINT,
    },
};

export function getApiBaseUrl() {
    return `${config.mirth.url}/api`;
}
