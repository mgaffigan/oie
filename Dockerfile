# syntax=docker/dockerfile:1.7-labs

# Stages:
# 1. Builder Stage: Compiles the application and resolves dependencies.  Produces
#    JAR files that can be deployed.
#      1a. Install dependencies
#      1b. Build the application
# 2. Runner Stage: Creates a lightweight image that runs the application using the JRE.

FROM ubuntu:noble-20250415.1 AS builder
WORKDIR /app
# sdkman requires bash
SHELL ["/bin/bash", "-c"]

# Stage 1a: Install dependencies
# Install necessary tools
COPY .sdkmanrc .
RUN apt-get update\
    && apt-get install -y zip curl\
    && curl -s "https://get.sdkman.io?ci=true" | bash \
    && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env install \
    && rm -rf /var/lib/apt/lists/*

# Stage 1b: Build the application
# Copy the entire source tree (excluding .dockerignore files), and build
COPY --exclude=docker . .
WORKDIR /app/server
RUN source "$HOME/.sdkman/bin/sdkman-init.sh" \
    && ANT_OPTS="-Dfile.encoding=UTF8" ant -f mirth-build.xml -DdisableSigning=true

# Stage 2b: JDK runtime container
FROM eclipse-temurin:21.0.7_6-jdk-noble as jdk-run

RUN groupadd mirth \
    && usermod -l mirth ubuntu \
    && adduser mirth mirth \
    && mkdir -p /opt/connect/appdata \
    && chown -R mirth:mirth /opt/connect

WORKDIR /opt/connect
COPY --chown=mirth:mirth --from=builder \
    --exclude=cli-lib \
    --exclude=mirth-cli-launcher.jar \
    --exclude=mccommand \
    --exclude=manager-lib \
    --exclude=mirth-manager-launcher.jar \
    --exclude=mcmanager \
    /app/server/setup ./

VOLUME /opt/connect/appdata
VOLUME /opt/connect/custom-extensions
EXPOSE 8443

USER mirth
ENTRYPOINT [ "/opt/connect/entrypoint.sh" ]
CMD ["/opt/connect/mirth-connect.sh"]

# Stage 2b: JRE runtime container
FROM eclipse-temurin:21.0.7_6-jre-alpine as jre-run

# Alpine does not include bash by default, so we install it
RUN apk add --no-cache bash
# useradd and groupadd are not available in Alpine
RUN addgroup -S mirth \
    && adduser -S -g mirth mirth \
    && mkdir -p /opt/connect/appdata \
    && chown -R mirth:mirth /opt/connect

WORKDIR /opt/connect
COPY --chown=mirth:mirth --from=builder \
    --exclude=cli-lib \
    --exclude=mirth-cli-launcher.jar \
    --exclude=mccommand \
    --exclude=manager-lib \
    --exclude=mirth-manager-launcher.jar \
    --exclude=mcmanager \
    /app/server/setup ./

VOLUME /opt/connect/appdata
VOLUME /opt/connect/custom-extensions
EXPOSE 8443

USER mirth
ENTRYPOINT [ "/opt/connect/entrypoint.sh" ]
CMD ["/opt/connect/mirth-connect.sh"]
