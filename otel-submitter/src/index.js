import { MirthClient } from './mirthClient.js';
import { OtelExporter } from './otelExporter.js';
import { MessagePoller } from './messagePoller.js';
import { config } from './config.js';

async function main() {
    console.log('='.repeat(60));
    console.log('OpenTelemetry Submitter for Mirth Connect');
    console.log('='.repeat(60));
    console.log();
    console.log(`Mirth URL: ${config.mirth.url}`);
    console.log(`Poll Interval: ${config.polling.intervalMs}ms`);
    console.log();

    // Create components
    const mirthClient = new MirthClient();
    const otelExporter = new OtelExporter();
    let messagePoller = null;

    // Handle graceful shutdown
    const shutdown = async (signal) => {
        console.log(`\n[Main] Received ${signal}, shutting down...`);
        
        if (messagePoller) {
            messagePoller.stop();
        }
        
        await mirthClient.logout();
        await otelExporter.shutdown();
        
        process.exit(0);
    };

    process.on('SIGINT', () => shutdown('SIGINT'));
    process.on('SIGTERM', () => shutdown('SIGTERM'));

    try {
        // Login to Mirth
        console.log('[Main] Connecting to Mirth Connect...');
        await mirthClient.login();

        // Initialize OpenTelemetry with server name
        otelExporter.updateServiceName(mirthClient.serverName);

        // Create and initialize poller
        messagePoller = new MessagePoller(mirthClient, otelExporter);
        await messagePoller.initialize();

        // Start polling
        console.log('[Main] Starting message monitoring...');
        console.log();
        messagePoller.start(config.polling.intervalMs);

    } catch (error) {
        console.error('[Main] Fatal error:', error);
        process.exit(1);
    }
}

main();
