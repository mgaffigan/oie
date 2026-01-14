#!/bin/bash

# Docker helper scripts for settings-dashboard

case "$1" in
  "build")
    echo "🔨 Building Docker image..."
    docker build -t settings-dashboard .
    ;;
  "run")
    echo "🚀 Running Docker container..."
    docker run -p 3000:3000 --name settings-dashboard-container settings-dashboard
    ;;
  "run-detached")
    echo "🚀 Running Docker container in background..."
    docker run -d -p 3000:3000 --name settings-dashboard-container settings-dashboard
    ;;
  "stop")
    echo "🛑 Stopping Docker container..."
    docker stop settings-dashboard-container
    ;;
  "remove")
    echo "🗑️ Removing Docker container..."
    docker rm settings-dashboard-container
    ;;
  "logs")
    echo "📋 Showing Docker container logs..."
    docker logs -f settings-dashboard-container
    ;;
  "clean")
    echo "🧹 Cleaning up Docker resources..."
    docker stop settings-dashboard-container 2>/dev/null || true
    docker rm settings-dashboard-container 2>/dev/null || true
    docker rmi settings-dashboard 2>/dev/null || true
    ;;
  "compose-up")
    echo "🚀 Starting with docker-compose..."
    docker-compose up --build
    ;;
  "compose-down")
    echo "🛑 Stopping docker-compose..."
    docker-compose down
    ;;
  *)
    echo "Usage: $0 {build|run|run-detached|stop|remove|logs|clean|compose-up|compose-down}"
    echo ""
    echo "Commands:"
    echo "  build         - Build the Docker image"
    echo "  run           - Run the container (foreground)"
    echo "  run-detached  - Run the container (background)"
    echo "  stop          - Stop the running container"
    echo "  remove        - Remove the container"
    echo "  logs          - Show container logs"
    echo "  clean         - Clean up all Docker resources"
    echo "  compose-up    - Start with docker-compose"
    echo "  compose-down  - Stop docker-compose"
    exit 1
    ;;
esac
