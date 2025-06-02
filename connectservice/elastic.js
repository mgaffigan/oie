import { Client } from '@elastic/elasticsearch';

export const client = new Client({
    node: process.env.ELASTICSEARCH_URL || 'http://localhost:9200',
    auth: process.env.ELASTICSEARCH_USERNAME ? {
        username: process.env.ELASTICSEARCH_USERNAME,
        password: process.env.ELASTICSEARCH_PASSWORD || 'changeme',
    } : undefined,
    tls: {
        rejectUnauthorized: process.env.ELASTICSEARCH_IGNORETLSERRORS && !!JSON.parse(process.env.ELASTICSEARCH_IGNORETLSERRORS),
    },
    requestTimeout: process.env.ELASTICSEARCH_TIMEOUT ? JSON.parse(process.env.ELASTICSEARCH_TIMEOUT) : 30000,
});
