# Docker Setup for Settings Dashboard

This document explains how to build and run the Settings Dashboard using Docker.

## Quick Start

### Option 1: Using Docker Compose (Recommended)
```bash
# Build and start the application
docker-compose up --build

# Access the application at: http://localhost:3000/dashboard
```

### Option 2: Using Docker Commands
```bash
# Build the image
docker build -t settings-dashboard .

# Run the container
docker run -p 3000:3000 settings-dashboard

# Access the application at: http://localhost:3000/dashboard
```

### Option 3: Using Helper Scripts
```bash
# Make the script executable (first time only)
chmod +x docker-scripts.sh

# Build and run
./docker-scripts.sh build
./docker-scripts.sh run

# Or use docker-compose
./docker-scripts.sh compose-up
```

## Configuration

### Environment Variables

You can customize the application behavior using environment variables:

- `PORT`: Port to run the server on (default: 3000)
- `API_TARGET`: Backend API URL to proxy requests to (default: https://oie-test.quantis.health)

### Example with Custom API Target
```bash
docker run -p 3000:3000 -e API_TARGET=https://your-api-server.com settings-dashboard
```

Or with docker-compose, modify the `docker-compose.yml`:
```yaml
environment:
  - API_TARGET=https://your-api-server.com
```

## Architecture

The Docker setup includes:

1. **Multi-stage Build**: 
   - Builder stage: Installs all dependencies and builds the React app
   - Production stage: Only includes production dependencies and built files

2. **Express Server**: 
   - Serves static files from the built React app
   - Proxies API requests to the backend
   - Handles client-side routing
   - Sets proper MIME types for JavaScript modules

3. **Security Features**:
   - Runs as non-root user
   - Health checks
   - Proper cookie handling for cross-origin requests

## Troubleshooting

### MIME Type Errors
The production server explicitly sets the correct MIME types for JavaScript modules to prevent the "Expected a JavaScript-or-Wasm module script" error.

### API Proxy Issues
If you're having issues with API requests:
1. Check that the `API_TARGET` environment variable is set correctly
2. Verify the backend server is accessible from the container
3. Check the container logs: `docker logs <container-name>`

### Port Conflicts
If port 3000 is already in use:
```bash
docker run -p 3001:3000 settings-dashboard
# Access at: http://localhost:3001/dashboard
```

## Development vs Production

- **Development**: Use `npm run dev` for local development with Vite
- **Production**: Use Docker for production deployment

The Docker setup is optimized for production with:
- Smaller image size (multi-stage build)
- Security best practices
- Proper static file serving
- API proxying
- Health checks

## File Structure

```
├── Dockerfile              # Multi-stage Docker build
├── docker-compose.yml      # Docker Compose configuration
├── docker-scripts.sh       # Helper scripts
├── server.prod.js          # Production Express server
├── .dockerignore           # Files to exclude from Docker build
└── DOCKER.md              # This documentation
```
