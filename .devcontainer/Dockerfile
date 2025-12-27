# syntax=docker/dockerfile:1

# Development container based on the builder stage from the main Dockerfile
FROM mcr.microsoft.com/devcontainers/base:ubuntu

# Install development dependencies
RUN apt-get update\
    && apt-get install -y zip curl\
    && rm -rf /var/lib/apt/lists/*

# Switch to vscode user for jdk install
USER vscode

# sdkman requires bash
SHELL ["/bin/bash", "-c"]
# Java environment
ENV LANG=en_US.UTF-8
COPY .sdkmanrc .
RUN curl -s "https://get.sdkman.io?ci=true" | bash \
    && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env install \
    && echo 'source "$HOME/.sdkman/bin/sdkman-init.sh"' >> $HOME/.bashrc
