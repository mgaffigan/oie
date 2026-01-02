import { normalizeList } from './mirthClient.js';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// State file path - use DATA_DIR env var if set (for Docker volumes), otherwise use app directory
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, '..');
const STATE_FILE = path.join(DATA_DIR, '.poll-state.json');

/**
 * Format date for Mirth API query (ISO 8601 with timezone)
 * Example format: 2015-10-21T07:28:00.000-0700
 */
function formatDateForMirth(date) {
    const pad = (n, width = 2) => String(n).padStart(width, '0');
    
    const year = date.getFullYear();
    const month = pad(date.getMonth() + 1);
    const day = pad(date.getDate());
    const hours = pad(date.getHours());
    const minutes = pad(date.getMinutes());
    const seconds = pad(date.getSeconds());
    const ms = pad(date.getMilliseconds(), 3);
    
    // Get timezone offset in minutes (negative means ahead of UTC)
    const tzOffset = date.getTimezoneOffset();
    const tzSign = tzOffset <= 0 ? '+' : '-';
    const tzHours = pad(Math.floor(Math.abs(tzOffset) / 60));
    const tzMins = pad(Math.abs(tzOffset) % 60);
    
    const formatted = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}.${ms}${tzSign}${tzHours}${tzMins}`;
    return formatted;
}

/**
 * Message poller for Mirth Connect
 * Monitors channel statistics and retrieves completed messages
 */
export class MessagePoller {
    constructor(mirthClient, otelExporter) {
        this.client = mirthClient;
        this.exporter = otelExporter;
        
        // Track last poll time per channel (for response date filtering)
        this.channelLastPollTime = new Map();
        
        // Cache channel metadata columns
        this.channelMetadata = new Map();
        
        // Cache channel names
        this.channelNames = new Map();
        
        // Previous statistics for change detection
        this.previousStats = new Map();
        
        // Polling state
        this.pollTimer = null;
        this.isPolling = false;
        
        // Load persisted state
        this.loadState();
    }

    /**
     * Load persisted poll state from disk
     */
    loadState() {
        try {
            if (fs.existsSync(STATE_FILE)) {
                const data = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
                if (data.channelLastPollTime) {
                    for (const [channelId, timestamp] of Object.entries(data.channelLastPollTime)) {
                        this.channelLastPollTime.set(channelId, new Date(timestamp));
                    }
                    console.log(`[MessagePoller] Loaded state for ${this.channelLastPollTime.size} channels`);
                }
            }
        } catch (error) {
            console.warn('[MessagePoller] Could not load state file:', error.message);
        }
    }

    /**
     * Save poll state to disk
     */
    saveState() {
        try {
            const data = {
                channelLastPollTime: {},
                savedAt: new Date().toISOString(),
            };
            for (const [channelId, date] of this.channelLastPollTime) {
                data.channelLastPollTime[channelId] = date.toISOString();
            }
            fs.writeFileSync(STATE_FILE, JSON.stringify(data, null, 2));
        } catch (error) {
            console.warn('[MessagePoller] Could not save state file:', error.message);
        }
    }

    /**
     * Initialize the poller - fetch initial channel info
     */
    async initialize() {
        console.log('[MessagePoller] Initializing...');
        
        // Fetch all channels to cache metadata
        const channelsResponse = await this.client.getChannels();
        const channels = normalizeList(channelsResponse?.list?.channel);
        
        for (const channel of channels) {
            const channelId = channel.id;
            const channelName = channel.name;
            
            this.channelNames.set(channelId, channelName);
            
            // Cache metadata columns from channel properties
            const metadataColumns = [];
            const props = channel.properties;
            if (props?.metaDataColumns?.metaDataColumn) {
                const cols = normalizeList(props.metaDataColumns.metaDataColumn);
                metadataColumns.push(...cols);
            }
            this.channelMetadata.set(channelId, metadataColumns);
            
            console.log(`[MessagePoller] Channel: ${channelName} (${channelId}) - ${metadataColumns.length} metadata columns`);
        }
        
        console.log(`[MessagePoller] Initialized with ${channels.length} channels`);
    }

    /**
     * Get initial dashboard statistics
     */
    async getInitialStats() {
        const response = await this.client.getDashboardChannelInfo(1000);
        return this.parseChannelStats(response);
    }

    /**
     * Parse channel statistics from dashboard response
     */
    parseChannelStats(response) {
        const stats = new Map();
        
        // Handle DashboardChannelInfo structure
        const dashboardInfo = response?.dashboardChannelInfo;
        if (!dashboardInfo) {
            return stats;
        }

        const statusList = dashboardInfo.dashboardStatuses;
        if (!statusList?.dashboardStatus) {
            return stats;
        }

        const statuses = normalizeList(statusList.dashboardStatus);
        
        for (const status of statuses) {
            const channelId = status.channelId;
            if (!channelId) continue;

            // Sum up the statistics
            const statistics = status.statistics;
            if (!statistics?.entry) continue;

            const entries = normalizeList(statistics.entry);
            const channelStats = {};
            
            for (const entry of entries) {
                // Entry format: { 'com.mirth.connect.donkey.model.message.Status': 'SENT', long: 123 }
                const statusKey = entry['com.mirth.connect.donkey.model.message.Status'];
                const count = entry.long || entry.int || 0;
                if (statusKey) {
                    channelStats[statusKey] = parseInt(count, 10);
                }
            }

            stats.set(channelId, {
                name: status.name,
                stats: channelStats,
                queued: parseInt(status.queued || 0, 10),
            });
        }

        return stats;
    }

    /**
     * Detect which channels have new completed messages
     */
    detectChangedChannels(currentStats) {
        const changedChannels = [];

        for (const [channelId, current] of currentStats) {
            const previous = this.previousStats.get(channelId);
            
            if (!previous) {
                // New channel, check if it has any completed messages
                const completedCount = (current.stats.SENT || 0) + 
                                       (current.stats.FILTERED || 0) + 
                                       (current.stats.ERROR || 0) +
                                       (current.stats.TRANSFORMED || 0);
                if (completedCount > 0) {
                    changedChannels.push(channelId);
                }
            } else {
                // Check if completed counts increased
                const prevCompleted = (previous.stats.SENT || 0) + 
                                      (previous.stats.FILTERED || 0) + 
                                      (previous.stats.ERROR || 0) +
                                      (previous.stats.TRANSFORMED || 0);
                const currCompleted = (current.stats.SENT || 0) + 
                                      (current.stats.FILTERED || 0) + 
                                      (current.stats.ERROR || 0) +
                                      (current.stats.TRANSFORMED || 0);
                
                if (currCompleted > prevCompleted) {
                    changedChannels.push(channelId);
                }
            }
        }

        return changedChannels;
    }

    /**
     * Fetch and process completed messages for a channel
     */
    async processChannelMessages(channelId) {
        const channelName = this.channelNames.get(channelId) || 'unknown';
        const metadataColumns = this.channelMetadata.get(channelId) || [];
        
        // Get the last poll time for this channel
        let startDate = this.channelLastPollTime.get(channelId);
        const now = new Date();
        
        if (!startDate) {
            // First poll for this channel with no persisted state - start from now
            // This means we only export NEW messages going forward
            startDate = now;
            console.log(`[MessagePoller] First poll for ${channelName} - starting from now (no historical backfill)`);
        }

        const formattedStartDate = formatDateForMirth(startDate);
        console.log(`[MessagePoller] Fetching messages for ${channelName} since ${formattedStartDate}`);

        try {
            // Query for completed messages since last poll
            const response = await this.client.getMessages(channelId, {
                startDate: formattedStartDate,
                includeContent: true,
                limit: 100,
                // We want all terminal statuses
                statuses: ['SENT', 'FILTERED', 'ERROR', 'TRANSFORMED'],
            });

            const messages = normalizeList(response?.list?.message);
            console.log(`[MessagePoller] Found ${messages.length} messages for ${channelName}`);

            let processedCount = 0;
            let latestResponseDate = startDate;

            for (const message of messages) {
                // Check if message is fully completed
                if (!this.exporter.isMessageCompleted(message)) {
                    continue;
                }

                // Export to OpenTelemetry
                this.exporter.exportMessage(message, channelName, metadataColumns);
                processedCount++;

                // Track the latest response date for pagination
                const connectorMessages = message.connectorMessages;
                if (connectorMessages?.entry) {
                    const entries = normalizeList(connectorMessages.entry);
                    for (const entry of entries) {
                        const cm = entry.connectorMessage;
                        if (cm?.responseDate?.time) {
                            const responseDate = new Date(parseInt(cm.responseDate.time, 10));
                            if (responseDate > latestResponseDate) {
                                latestResponseDate = responseDate;
                            }
                        }
                    }
                }
            }

            // Update last poll time (add 1ms to avoid re-fetching the same message)
            this.channelLastPollTime.set(channelId, new Date(latestResponseDate.getTime() + 1));
            
            // Persist state after each channel update
            this.saveState();

            if (processedCount > 0) {
                console.log(`[MessagePoller] Exported ${processedCount} messages for ${channelName}`);
            }

        } catch (error) {
            console.error(`[MessagePoller] Error processing ${channelName}:`, error.message);
        }
    }

    /**
     * Run a single poll cycle
     */
    async poll() {
        if (this.isPolling) {
            console.log('[MessagePoller] Poll already in progress, skipping');
            return;
        }

        this.isPolling = true;

        try {
            // Get current stats
            const currentStats = await this.getInitialStats();
            
            // Detect channels with new messages
            const changedChannels = this.detectChangedChannels(currentStats);
            
            if (changedChannels.length > 0) {
                console.log(`[MessagePoller] ${changedChannels.length} channels have new messages`);
                
                // Process each changed channel
                for (const channelId of changedChannels) {
                    await this.processChannelMessages(channelId);
                }
            }

            // Update previous stats
            this.previousStats = currentStats;

        } catch (error) {
            console.error('[MessagePoller] Poll error:', error.message);
        } finally {
            this.isPolling = false;
        }
    }

    /**
     * Start polling
     */
    start(intervalMs) {
        if (this.pollTimer) {
            console.warn('[MessagePoller] Already started');
            return;
        }

        console.log(`[MessagePoller] Starting with ${intervalMs}ms interval`);
        
        // Initial poll
        this.poll();
        
        // Schedule recurring polls
        this.pollTimer = setInterval(() => this.poll(), intervalMs);
    }

    /**
     * Stop polling
     */
    stop() {
        if (this.pollTimer) {
            clearInterval(this.pollTimer);
            this.pollTimer = null;
            console.log('[MessagePoller] Stopped');
        }
    }
}
