import opentelemetry, { trace, SpanKind, SpanStatusCode, context, ROOT_CONTEXT } from '@opentelemetry/api';
import {
    BasicTracerProvider,
    BatchSpanProcessor,
} from '@opentelemetry/sdk-trace-base';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-grpc';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';
import { config } from './config.js';
import crypto from 'crypto';

/**
 * Terminal statuses for message processing
 */
const TERMINAL_STATUSES = new Set(['SENT', 'FILTERED', 'ERROR', 'TRANSFORMED']);

/**
 * Non-terminal statuses (message still in progress)
 */
const IN_PROGRESS_STATUSES = new Set(['RECEIVED', 'QUEUED', 'PENDING']);

/**
 * OpenTelemetry exporter for Mirth Connect messages
 */
export class OtelExporter {
    constructor(serviceName = 'mirth') {
        this.serviceName = serviceName;
        this.provider = null;
        this.tracer = null;
    }

    /**
     * Initialize the OpenTelemetry provider
     */
    initialize() {
        const resource = resourceFromAttributes({
            [ATTR_SERVICE_NAME]: this.serviceName,
        });

        // OTLP exporter for Jaeger/other collectors
        const otlpEndpoint = config.otel.endpoint || 'http://localhost:4317';
        console.log(`[OtelExporter] Using OTLP endpoint: ${otlpEndpoint}`);
        
        const otlpExporter = new OTLPTraceExporter({
            url: otlpEndpoint,
        });

        // Create provider with BatchSpanProcessor for better performance
        this.provider = new BasicTracerProvider({
            resource,
            spanProcessors: [
                new BatchSpanProcessor(otlpExporter, {
                    maxQueueSize: 1000,
                    maxExportBatchSize: 100,
                    scheduledDelayMillis: 1000,
                }),
            ],
        });

        // Set this provider as the global tracer provider
        opentelemetry.trace.setGlobalTracerProvider(this.provider);
        this.tracer = trace.getTracer('otel-submitter', '1.0.0');

        console.log(`[OtelExporter] Initialized with service name: ${this.serviceName}`);
    }

    /**
     * Update the service name (e.g., after fetching from server)
     */
    updateServiceName(serviceName) {
        this.serviceName = serviceName || this.serviceName;
        // Initialize (or re-initialize) with the service name
        this.initialize();
    }

    /**
     * Generate a deterministic trace ID from channel and message IDs
     * This allows us to correlate spans across channels without storage
     */
    generateTraceId(channelId, messageId) {
        const input = `${channelId}:${messageId}`;
        const hash = crypto.createHash('sha256').update(input).digest('hex');
        // Trace ID is 32 hex chars (16 bytes)
        return hash.substring(0, 32);
    }

    /**
     * Generate a deterministic span ID from channel, message, and metadata IDs
     */
    generateSpanId(channelId, messageId, metaDataId) {
        const input = `${channelId}:${messageId}:${metaDataId}`;
        const hash = crypto.createHash('sha256').update(input).digest('hex');
        // Span ID is 16 hex chars (8 bytes)
        return hash.substring(0, 16);
    }

    /**
     * Check if a message is fully completed (all connectors in terminal state)
     */
    isMessageCompleted(message) {
        const connectorMessages = message.connectorMessages;
        if (!connectorMessages || !connectorMessages.entry) {
            return false;
        }

        const entries = Array.isArray(connectorMessages.entry) 
            ? connectorMessages.entry 
            : [connectorMessages.entry];

        for (const entry of entries) {
            const connectorMessage = entry.connectorMessage;
            if (!connectorMessage) continue;

            const status = connectorMessage.status;
            if (IN_PROGRESS_STATUSES.has(status)) {
                return false;
            }
        }

        return entries.length > 0;
    }

    /**
     * Extract source channel/message IDs from source map for parent linking
     */
    extractSourceInfo(connectorMessage) {
        const sourceMapContent = connectorMessage.sourceMapContent;
        if (!sourceMapContent || !sourceMapContent.map) {
            return null;
        }

        const map = sourceMapContent.map;
        const entries = map.entry ? (Array.isArray(map.entry) ? map.entry : [map.entry]) : [];

        let sourceChannelId = null;
        let sourceMessageId = null;

        for (const entry of entries) {
            if (!entry.string) continue;
            const keys = Array.isArray(entry.string) ? entry.string : [entry.string];
            
            // The entry format is [key, value] in the string array
            for (let i = 0; i < keys.length; i += 2) {
                const key = keys[i];
                const value = keys[i + 1];
                
                if (key === 'sourceChannelId') {
                    sourceChannelId = value;
                } else if (key === 'sourceMessageId') {
                    sourceMessageId = parseInt(value, 10);
                }
            }
        }

        if (sourceChannelId && sourceMessageId) {
            return { sourceChannelId, sourceMessageId };
        }

        return null;
    }

    /**
     * Extract metadata attributes from connector message
     * Includes promoted metadata columns and OTEL_ prefixed values
     */
    extractMetadataAttributes(connectorMessage, metadataColumns) {
        const attributes = {};
        const metaDataMap = connectorMessage.metaDataMap;
        
        if (!metaDataMap || !metaDataMap.entry) {
            return attributes;
        }

        const entries = Array.isArray(metaDataMap.entry) 
            ? metaDataMap.entry 
            : [metaDataMap.entry];

        // Get the set of promoted column names
        const promotedColumns = new Set(
            metadataColumns.map(col => col.name?.toUpperCase() || col.NAME?.toUpperCase())
        );

        for (const entry of entries) {
            // Entry structure: { string: 'key', ... value type ... }
            const key = entry.string;
            if (!key) continue;

            // Find the value (could be various types)
            let value = null;
            for (const [k, v] of Object.entries(entry)) {
                if (k !== 'string' && v !== undefined) {
                    value = v;
                    break;
                }
            }

            if (value === null || value === undefined) continue;

            // Include if it's a promoted column or starts with OTEL_
            const upperKey = key.toUpperCase();
            if (promotedColumns.has(upperKey) || key.startsWith('OTEL_')) {
                attributes[`mirth.metadata.${key}`] = String(value);
            }
        }

        // Also extract from sourceMap for OTEL_ prefixed values
        const sourceMapContent = connectorMessage.sourceMapContent;
        if (sourceMapContent?.map?.entry) {
            const sourceEntries = Array.isArray(sourceMapContent.map.entry) 
                ? sourceMapContent.map.entry 
                : [sourceMapContent.map.entry];

            for (const entry of sourceEntries) {
                if (!entry.string) continue;
                const strings = Array.isArray(entry.string) ? entry.string : [entry.string];
                
                for (let i = 0; i < strings.length; i += 2) {
                    const key = strings[i];
                    const value = strings[i + 1];
                    
                    if (key && key.startsWith('OTEL_') && value) {
                        attributes[`mirth.source.${key}`] = String(value);
                    }
                }
            }
        }

        return attributes;
    }

    /**
     * Convert Mirth status to OTEL span status
     */
    getSpanStatus(status) {
        switch (status) {
            case 'SENT':
            case 'FILTERED':
            case 'TRANSFORMED':
                return { code: SpanStatusCode.OK };
            case 'ERROR':
                return { code: SpanStatusCode.ERROR, message: 'Message processing error' };
            default:
                return { code: SpanStatusCode.UNSET };
        }
    }

    /**
     * Parse Mirth calendar date to JavaScript Date
     */
    parseDate(calendarObj) {
        if (!calendarObj) return null;
        
        // Handle the Mirth calendar XML format
        if (calendarObj.time) {
            return new Date(parseInt(calendarObj.time, 10));
        }
        
        // Try parsing as ISO string
        if (typeof calendarObj === 'string') {
            return new Date(calendarObj);
        }

        return null;
    }

    /**
     * Export a completed message as OpenTelemetry spans
     */
    exportMessage(message, channelName, metadataColumns = []) {
        if (!this.tracer) {
            console.warn('[OtelExporter] Tracer not initialized');
            return;
        }

        const channelId = message.channelId;
        const messageId = message.messageId;
        const connectorMessages = message.connectorMessages;

        if (!connectorMessages || !connectorMessages.entry) {
            console.warn(`[OtelExporter] No connector messages for ${channelId}:${messageId}`);
            return;
        }

        const entries = Array.isArray(connectorMessages.entry) 
            ? connectorMessages.entry 
            : [connectorMessages.entry];

        // Sort by metaDataId to process source (0) first
        entries.sort((a, b) => {
            const aId = a.connectorMessage?.metaDataId ?? 0;
            const bId = b.connectorMessage?.metaDataId ?? 0;
            return aId - bId;
        });

        // Find source connector (metaDataId = 0)
        const sourceEntry = entries.find(e => e.connectorMessage?.metaDataId === 0);
        const sourceConnectorMessage = sourceEntry?.connectorMessage;

        if (!sourceConnectorMessage) {
            console.warn(`[OtelExporter] No source connector for ${channelId}:${messageId}`);
            return;
        }

        // Check for parent span (from sourceChannelId/sourceMessageId)
        const sourceInfo = this.extractSourceInfo(sourceConnectorMessage);
        let parentTraceId = null;
        let parentSpanId = null;

        if (sourceInfo) {
            // This message is a child of another message
            parentTraceId = this.generateTraceId(sourceInfo.sourceChannelId, sourceInfo.sourceMessageId);
            // Parent span would be a destination connector (metaDataId > 0) but we use source for simplicity
            // Actually, the parent is the destination that sent to this channel
            // For now, we'll use the source connector of the parent message
            parentSpanId = this.generateSpanId(sourceInfo.sourceChannelId, sourceInfo.sourceMessageId, 0);
        }

        // Generate IDs for this message
        const traceId = sourceInfo 
            ? parentTraceId  // Use parent's trace ID to link spans
            : this.generateTraceId(channelId, messageId);
        
        const sourceSpanId = this.generateSpanId(channelId, messageId, 0);

        // Create source span (SERVER kind - receiving a message)
        const sourceStartTime = this.parseDate(sourceConnectorMessage.receivedDate);
        // Ensure end time is after start time (add 1ms minimum duration if same)
        let sourceEndTime = this.parseDate(sourceConnectorMessage.responseDate) || sourceStartTime;
        if (sourceEndTime && sourceStartTime && sourceEndTime.getTime() <= sourceStartTime.getTime()) {
            sourceEndTime = new Date(sourceStartTime.getTime() + 1);
        }

        const sourceAttributes = {
            'mirth.channel.id': channelId,
            'mirth.channel.name': channelName || message.channelName || 'unknown',
            'mirth.message.id': String(messageId),
            'mirth.connector.name': sourceConnectorMessage.connectorName || 'Source',
            'mirth.connector.metadata_id': '0',
            'mirth.status': sourceConnectorMessage.status,
            ...this.extractMetadataAttributes(sourceConnectorMessage, metadataColumns),
        };

        if (sourceInfo) {
            sourceAttributes['mirth.source.channel_id'] = sourceInfo.sourceChannelId;
            sourceAttributes['mirth.source.message_id'] = String(sourceInfo.sourceMessageId);
        }

        console.log(`[OtelExporter] Exporting message ${channelId}:${messageId}`);
        console.log(`  Trace ID: ${traceId}`);
        console.log(`  Source span: ${sourceSpanId} (${sourceConnectorMessage.status})`);
        if (sourceInfo) {
            console.log(`  Parent: ${sourceInfo.sourceChannelId}:${sourceInfo.sourceMessageId}`);
        }

        // Create source span (SERVER kind - receiving a message)
        const sourceSpan = this.tracer.startSpan(
            `${channelName || 'channel'} receive`,
            {
                kind: SpanKind.SERVER,
                attributes: sourceAttributes,
                startTime: sourceStartTime || undefined,
            }
        );
        sourceSpan.setStatus(this.getSpanStatus(sourceConnectorMessage.status));
        
        // Get the context with the source span active (for child spans)
        const sourceContext = trace.setSpan(ROOT_CONTEXT, sourceSpan);

        // Create destination spans (CLIENT kind - sending to destinations)
        // These are children of the source span
        for (const entry of entries) {
            const connectorMessage = entry.connectorMessage;
            if (!connectorMessage || connectorMessage.metaDataId === 0) continue;

            const destSpanId = this.generateSpanId(channelId, messageId, connectorMessage.metaDataId);
            const destStartTime = this.parseDate(connectorMessage.sendDate) || this.parseDate(connectorMessage.receivedDate);
            // Ensure end time is after start time (add 1ms minimum duration if same)
            let destEndTime = this.parseDate(connectorMessage.responseDate) || destStartTime;
            if (destEndTime && destStartTime && destEndTime.getTime() <= destStartTime.getTime()) {
                destEndTime = new Date(destStartTime.getTime() + 1);
            }

            const destAttributes = {
                'mirth.channel.id': channelId,
                'mirth.channel.name': channelName || message.channelName || 'unknown',
                'mirth.message.id': String(messageId),
                'mirth.connector.name': connectorMessage.connectorName || `Destination ${connectorMessage.metaDataId}`,
                'mirth.connector.metadata_id': String(connectorMessage.metaDataId),
                'mirth.status': connectorMessage.status,
                ...this.extractMetadataAttributes(connectorMessage, metadataColumns),
            };

            console.log(`  Dest span: ${destSpanId} - ${connectorMessage.connectorName} (${connectorMessage.status})`);

            // Start destination span as child of source span using explicit parent context
            const destSpan = this.tracer.startSpan(
                `${connectorMessage.connectorName || 'destination'} send`,
                {
                    kind: SpanKind.CLIENT,
                    attributes: destAttributes,
                    startTime: destStartTime || undefined,
                },
                sourceContext  // Pass the source span's context as parent
            );
            destSpan.setStatus(this.getSpanStatus(connectorMessage.status));
            if (destEndTime) {
                destSpan.end(destEndTime);
            } else {
                destSpan.end();
            }
        }

        // End the source span after all destinations
        if (sourceEndTime) {
            sourceSpan.end(sourceEndTime);
        } else {
            sourceSpan.end();
        }
    }

    /**
     * Shutdown the provider
     */
    async shutdown() {
        if (this.provider) {
            await this.provider.shutdown();
            console.log('[OtelExporter] Shutdown complete');
        }
    }
}

export { TERMINAL_STATUSES, IN_PROGRESS_STATUSES };
