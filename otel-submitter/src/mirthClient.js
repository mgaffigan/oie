import fetch from 'node-fetch';
import https from 'https';
import { XMLParser } from 'fast-xml-parser';
import { config, getApiBaseUrl } from './config.js';

// Allow self-signed certificates
const agent = new https.Agent({
    rejectUnauthorized: false,
});

const xmlParser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: '@_',
    textNodeName: '#text',
    parseTagValue: true,
    trimValues: true,
});

/**
 * Mirth Connect API Client
 */
export class MirthClient {
    constructor() {
        this.baseUrl = getApiBaseUrl();
        this.sessionCookie = null;
        this.serverName = 'mirth'; // Default, will be updated after login
    }

    /**
     * Get common headers for API requests
     */
    getHeaders(contentType = 'application/xml') {
        const headers = {
            'X-Requested-With': 'openintegrationengine-client',
            'Accept': 'application/xml',
            'User-Agent': 'OTEL-Submitter/1.0',
        };
        if (contentType) {
            headers['Content-Type'] = contentType;
        }
        if (this.sessionCookie) {
            headers['Cookie'] = this.sessionCookie;
        }
        return headers;
    }

    /**
     * Parse XML response
     */
    parseXml(xmlString) {
        return xmlParser.parse(xmlString);
    }

    /**
     * Login to Mirth Connect
     */
    async login() {
        const url = `${this.baseUrl}/users/_login`;
        const body = `username=${encodeURIComponent(config.mirth.username)}&password=${encodeURIComponent(config.mirth.password)}`;

        const response = await fetch(url, {
            method: 'POST',
            headers: {
                ...this.getHeaders('application/x-www-form-urlencoded'),
            },
            body,
            agent,
        });

        if (!response.ok) {
            throw new Error(`Login failed: ${response.status} ${response.statusText}`);
        }

        // Extract session cookie
        const setCookie = response.headers.get('set-cookie');
        if (setCookie) {
            const match = setCookie.match(/JSESSIONID=[^;]+/);
            if (match) {
                this.sessionCookie = match[0];
            }
        }

        const xml = await response.text();
        const parsed = this.parseXml(xml);

        if (parsed['com.mirth.connect.model.LoginStatus']?.status !== 'SUCCESS') {
            throw new Error(`Login failed: ${parsed['com.mirth.connect.model.LoginStatus']?.message || 'Unknown error'}`);
        }

        console.log('[MirthClient] Login successful');

        // Fetch server name
        await this.fetchServerName();

        return true;
    }

    /**
     * Fetch server settings to get server name
     */
    async fetchServerName() {
        try {
            const settings = await this.getServerSettings();
            if (settings?.serverSettings?.serverName) {
                this.serverName = settings.serverSettings.serverName;
            }
            console.log(`[MirthClient] Server name: ${this.serverName}`);
        } catch (error) {
            console.warn('[MirthClient] Could not fetch server name, using default:', error.message);
        }
    }

    /**
     * Generic GET request
     */
    async get(path) {
        const url = `${this.baseUrl}${path}`;
        const response = await fetch(url, {
            method: 'GET',
            headers: this.getHeaders(),
            agent,
        });

        if (!response.ok) {
            throw new Error(`GET ${path} failed: ${response.status} ${response.statusText}`);
        }

        const xml = await response.text();
        return this.parseXml(xml);
    }

    /**
     * Generic POST request with XML body
     */
    async post(path, body = null) {
        const url = `${this.baseUrl}${path}`;
        const response = await fetch(url, {
            method: 'POST',
            headers: this.getHeaders(),
            body,
            agent,
        });

        if (!response.ok) {
            throw new Error(`POST ${path} failed: ${response.status} ${response.statusText}`);
        }

        const xml = await response.text();
        return xml ? this.parseXml(xml) : null;
    }

    /**
     * Get server settings
     */
    async getServerSettings() {
        return this.get('/server/settings');
    }

    /**
     * Get dashboard channel info (initial status)
     */
    async getDashboardChannelInfo(fetchSize = 100) {
        return this.get(`/channels/statuses/initial?fetchSize=${fetchSize}`);
    }

    /**
     * Get channel statuses
     */
    async getChannelStatuses(channelIds = null) {
        if (channelIds && channelIds.length > 0) {
            const params = channelIds.map(id => `channelId=${encodeURIComponent(id)}`).join('&');
            return this.get(`/channels/statuses?${params}`);
        }
        return this.get('/channels/statuses');
    }

    /**
     * Get all channels
     */
    async getChannels(channelIds = null) {
        if (channelIds && channelIds.length > 0) {
            const params = channelIds.map(id => `channelId=${encodeURIComponent(id)}`).join('&');
            return this.get(`/channels?${params}`);
        }
        return this.get('/channels');
    }

    /**
     * Get single channel
     */
    async getChannel(channelId) {
        return this.get(`/channels/${encodeURIComponent(channelId)}`);
    }

    /**
     * Get metadata columns for a channel
     */
    async getMetaDataColumns(channelId) {
        return this.get(`/channels/${encodeURIComponent(channelId)}/metaDataColumns`);
    }

    /**
     * Get connector names for a channel
     */
    async getConnectorNames(channelId) {
        return this.get(`/channels/${encodeURIComponent(channelId)}/connectorNames`);
    }

    /**
     * Search messages for a channel
     * @param {string} channelId 
     * @param {Object} options - Query options
     */
    async getMessages(channelId, options = {}) {
        const params = new URLSearchParams();
        
        if (options.startDate) {
            params.append('startDate', options.startDate);
        }
        if (options.endDate) {
            params.append('endDate', options.endDate);
        }
        if (options.minMessageId) {
            params.append('minMessageId', options.minMessageId);
        }
        if (options.maxMessageId) {
            params.append('maxMessageId', options.maxMessageId);
        }
        if (options.offset !== undefined) {
            params.append('offset', options.offset);
        }
        if (options.limit !== undefined) {
            params.append('limit', options.limit);
        }
        if (options.includeContent !== undefined) {
            params.append('includeContent', options.includeContent);
        }
        // Add status filters if provided
        if (options.statuses && options.statuses.length > 0) {
            for (const status of options.statuses) {
                params.append('status', status);
            }
        }

        const queryString = params.toString();
        const path = `/channels/${encodeURIComponent(channelId)}/messages${queryString ? '?' + queryString : ''}`;
        return this.get(path);
    }

    /**
     * Get a single message with full content
     */
    async getMessage(channelId, messageId) {
        return this.get(`/channels/${encodeURIComponent(channelId)}/messages/${messageId}`);
    }

    /**
     * Logout from Mirth Connect
     */
    async logout() {
        try {
            await this.post('/users/_logout');
            this.sessionCookie = null;
            console.log('[MirthClient] Logout successful');
        } catch (error) {
            console.warn('[MirthClient] Logout error:', error.message);
        }
    }
}

/**
 * Normalize XML list to array - handles single item vs multiple items
 */
export function normalizeList(data) {
    if (!data) return [];
    if (Array.isArray(data)) return data;
    return [data];
}
