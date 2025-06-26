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

##########################################
#
#     Ubuntu JDK Image
#
##########################################

FROM eclipse-temurin:21.0.7_6-jdk-noble as jdk-run

RUN groupadd engine \
    && usermod -l engine ubuntu \
    && adduser engine engine \
    && mkdir -p /opt/engine/appdata \
    && chown -R engine:engine /opt/engine

WORKDIR /opt/engine
COPY --chmod=0755 docker/entrypoint.sh ./
COPY --chown=engine:engine --from=builder \
    --exclude=cli-lib \
    --exclude=mirth-cli-launcher.jar \
    --exclude=mccommand \
    --exclude=manager-lib \
    --exclude=mirth-manager-launcher.jar \
    --exclude=mcmanager \
    /app/server/setup ./

VOLUME /opt/engine/appdata
VOLUME /opt/engine/custom-extensions
EXPOSE 8443

USER engine
ENTRYPOINT ["./entrypoint.sh"]
CMD ["./oieserver"]

##########################################
#
#     Alpine JRE Image
#
##########################################

FROM eclipse-temurin:21.0.7_6-jre-alpine as jre-run

# Alpine does not include bash by default, so we install it
RUN apk add --no-cache bash
# useradd and groupadd are not available in Alpine
RUN addgroup -S engine \
    && adduser -S -g engine engine \
    && mkdir -p /opt/engine/appdata \
    && chown -R engine:engine /opt/engine

WORKDIR /opt/engine
COPY --chmod=0755 docker/entrypoint.sh ./
COPY --chown=engine:engine --from=builder \
    --exclude=cli-lib \
    --exclude=mirth-cli-launcher.jar \
    --exclude=mccommand \
    --exclude=manager-lib \
    --exclude=mirth-manager-launcher.jar \
    --exclude=mcmanager \
    /app/server/setup ./

VOLUME /opt/engine/appdata
VOLUME /opt/engine/custom-extensions

EXPOSE 8443

USER engine
ENTRYPOINT ["./entrypoint.sh"]
CMD ["./oieserver"]