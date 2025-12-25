#!/usr/bin/env node

import net from 'net';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { promisify } from 'util';
import { exec } from 'child_process';
import crypto from 'crypto';
import { createRequire } from 'module';

const execAsync = promisify(exec);

const LISTEN_PATH = process.env.LISTEN_PATH || '/tmp/node-rpc.sock';
const VERBOSE = process.env.VERBOSE === 'true';

// Store active environments
const environments = new Map();

class Environment {
  constructor(envId, envDir) {
    this.envId = envId;
    this.envDir = envDir;
    this.requireFn = null;
  }

  async initialize(packages) {
    // Create environment directory
    await fs.promises.mkdir(this.envDir, { recursive: true });

    // Create package.json for the environment
    const packageJsonPath = path.join(this.envDir, 'package.json');
    const packageJson = {
      name: `env-${this.envId}`,
      version: '1.0.0',
      type: 'commonjs',
      dependencies: packages || {}
    };
    
    await fs.promises.writeFile(
      packageJsonPath,
      JSON.stringify(packageJson, null, 2)
    );

    // Install npm packages if specified
    if (packages && Object.keys(packages).length > 0) {
      // Run npm install synchronously
      try {
        const { stdout, stderr } = await execAsync('npm install', {
          cwd: this.envDir,
          timeout: 300000 // 5 minute timeout
        });
        console.log(`[${this.envId}] npm install output:`, stdout);
        if (stderr) console.error(`[${this.envId}] npm install stderr:`, stderr);
      } catch (error) {
        throw new Error(`Failed to install packages: ${error.message}`);
      }
    }

    // Create require function for this environment - Node handles module resolution
    this.requireFn = createRequire(packageJsonPath);
  }

  async execute(script, args, callback) {
    // Allow concurrent execution to support re-entrant calls (e.g., callbacks)
    // Each execution is independent and Node.js handles async properly
    try {
      // Create an async function with the script
      // Use Function constructor to execute in current context with access to require
      const AsyncFunction = async function () {}.constructor;
      
      // Get parameter names from args object keys
      const paramNames = args ? Object.keys(args) : [];
      const paramValues = args ? Object.values(args) : [];
      
      // Create function with individual parameters plus require and callback
      const fn = new AsyncFunction('require', 'callback', ...paramNames, script);
      
      // Execute with the environment's require function, callback, and destructured args
      const result = await fn(this.requireFn, callback, ...paramValues);

      return result;
    } catch (error) {
      throw error;
    }
  }

  async dispose() {
    // Clean up environment directory
    try {
      await fs.promises.rm(this.envDir, { recursive: true, force: true });
    } catch (error) {
      console.error(`Failed to remove environment directory ${this.envDir}:`, error);
    }
  }
}

class JsonRpcServer {
  constructor() {
    this.server = null;
    this.clients = new Set();
    this.callbackRequestId = 0;
    this.callbackRequests = new Map();
  }

  start() {
    // Remove existing socket if it exists
    if (fs.existsSync(LISTEN_PATH)) {
      fs.unlinkSync(LISTEN_PATH);
    }

    this.server = net.createServer((socket) => {
      console.log('Client connected');
      if (VERBOSE) {
        console.log('Socket details:', {
          remoteAddress: socket.remoteAddress,
          remotePort: socket.remotePort,
          localAddress: socket.localAddress,
          localPort: socket.localPort
        });
      }
      this.clients.add(socket);

      let buffer = '';

      socket.on('data', (data) => {
        buffer += data.toString();
        if (VERBOSE) {
          console.log('Received data chunk:', JSON.stringify(data.toString()));
        }
        
        // Process complete JSON-RPC messages (newline-delimited)
        let newlineIndex;
        while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
          const message = buffer.slice(0, newlineIndex);
          buffer = buffer.slice(newlineIndex + 1);
          
          if (message.trim()) {
            this.handleMessage(message, socket);
          }
        }
      });

      socket.on('error', (err) => {
        console.error('Socket error:', err);
        this.clients.delete(socket);
      });
      
      socket.on('close', (hadError) => {
        if (VERBOSE) {
          console.log('Socket closed', { hadError });
        }
      });

      socket.on('end', () => {
        console.log('Client disconnected');
        this.clients.delete(socket);
      });

      socket.on('error', (err) => {
        console.error('Socket error:', err);
        this.clients.delete(socket);
      });
    });

    this.server.listen(LISTEN_PATH, () => {
      console.log(`JSON-RPC server listening on ${LISTEN_PATH}`);
    });

    // Handle graceful shutdown
    const shutdown = async () => {
      console.log('\nShutting down server...');
      
      // Close all client connections
      for (const client of this.clients) {
        client.end();
      }
      
      // Dispose all environments
      for (const [envId, env] of environments) {
        await env.dispose();
      }
      
      // Close server
      this.server.close(() => {
        console.log('Server closed');
        process.exit(0);
      });
    };

    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
  }

  async handleMessage(message, socket) {
    let request;
    try {
      request = JSON.parse(message);
      if (VERBOSE) {
        console.log('Received request:', JSON.stringify(request, null, 2));
      }
    } catch (error) {
      this.sendError(socket, null, -32700, 'Parse error');
      return;
    }

    const { jsonrpc, method, params, id, result, error } = request;

    // Check if this is a response to a callback request
    if (id && !method && this.callbackRequests.has(id)) {
      const { resolve, reject } = this.callbackRequests.get(id);
      this.callbackRequests.delete(id);
      
      if (error) {
        reject(new Error(`${error.code}: ${error.message}`));
      } else {
        resolve(result);
      }
      return;
    }

    if (jsonrpc !== '2.0') {
      this.sendError(socket, id, -32600, 'Invalid Request: jsonrpc must be "2.0"');
      return;
    }

    try {
      let result;
      switch (method) {
        case 'initialize':
          result = await this.handleInitialize(params);
          break;
        case 'execute':
          result = await this.handleExecute(params, socket);
          break;
        case 'dispose':
          result = await this.handleDispose(params);
          break;
        default:
          this.sendError(socket, id, -32601, `Method not found: ${method}`);
          return;
      }
      
      this.sendResponse(socket, id, result);
    } catch (error) {
      console.error(`Error handling ${method}:`, error);
      this.sendError(socket, id, -32603, error.message);
    }
  }

  async handleInitialize(params) {
    const { envOptions } = params || {};
    const packages = envOptions?.packages || {};

    // Generate unique environment ID
    const envId = crypto.randomUUID();
    const envDir = path.join(os.tmpdir(), 'node-rpc-env', envId);

    const env = new Environment(envId, envDir);
    
    try {
      await env.initialize(packages);
      environments.set(envId, env);
      
      return { envId };
    } catch (error) {
      // Clean up on failure
      await env.dispose();
      throw error;
    }
  }

  async handleExecute(params, socket) {
    const { envId, callbackEnvId, script, args } = params || {};

    if (!envId) {
      throw new Error('Invalid params: envId is required');
    }

    const env = environments.get(envId);
    if (!env) {
      throw new Error('Invalid params: Environment not found');
    }

    if (!script) {
      throw new Error('Invalid params: script is required');
    }

    // Create callback function for script to call back to client
    const callback = async (script, args) => {
      if (!callbackEnvId) {
        throw new Error('Cannot callback: callbackEnvId not provided');
      }
      
      // Send execute request back to client (executes client-side)
      const requestId = ++this.callbackRequestId;
      const request = {
        jsonrpc: '2.0',
        method: 'execute',
        params: {
          script,
          args
        },
        id: requestId
      };
      
      // Create promise to wait for response
      const response = await new Promise((resolve, reject) => {
        this.callbackRequests.set(requestId, { resolve, reject });
        socket.write(JSON.stringify(request) + '\n');
      });
      
      // Unwrap returnValue from response
      return response.returnValue;
    };

    const result = await env.execute(script, args, callback);
    
    return { returnValue: result };
  }

  async handleDispose(params) {
    const { envId } = params || {};

    if (!envId) {
      throw new Error('Invalid params: envId is required');
    }

    const env = environments.get(envId);
    if (!env) {
      throw new Error('Invalid params: Environment not found');
    }

    await env.dispose();
    environments.delete(envId);

    return { disposed: true };
  }

  sendResponse(socket, id, result) {
    const response = {
      jsonrpc: '2.0',
      result,
      id
    };
    if (VERBOSE) {
      console.log('Sending response:', JSON.stringify(response, null, 2));
    }
    socket.write(JSON.stringify(response) + '\n');
  }

  sendError(socket, id, code, message) {
    const response = {
      jsonrpc: '2.0',
      error: {
        code,
        message
      },
      id
    };
    if (VERBOSE) {
      console.log('Sending error:', JSON.stringify(response, null, 2));
    }
    socket.write(JSON.stringify(response) + '\n');
  }
}

// Start the server
const server = new JsonRpcServer();
server.start();
