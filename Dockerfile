# syntax=docker/dockerfile:1.7-labs

# Stages:
# 1. Builder Stage: Compiles the application and resolves dependencies.  Produces
#    JAR files that can be deployed.
#      1a. Install dependencies
#      1b. Build the application
# 2. Runner Stage: Creates a lightweight image that runs the application using the JRE.

FROM ubuntu:24.04 AS builder
WORKDIR /app

# Stage 1a: Install dependencies
RUN apt update\
    # https://github.com/adoptium/containers/blob/main/21/jdk/ubuntu/noble/Dockerfile for packages
    && apt install -y --no-install-recommends curl wget fontconfig ca-certificates p11-kit binutils tzdata locales \
    && echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen; locale-gen en_US.UTF-8 \
    && wget --progress=dot:giga -O /tmp/azul_jdk.deb "https://cdn.azul.com/zulu/bin/zulu8.84.0.15-ca-fx-jdk8.0.442-linux_amd64.deb" \
    && apt install -y --no-install-recommends /tmp/azul_jdk.deb \
    && wget --progress=dot:giga -O /tmp/apache-ant.tar.gz "https://dlcdn.apache.org/ant/binaries/apache-ant-1.10.15-bin.tar.gz" \
    && tar -xzf /tmp/apache-ant.tar.gz -C /opt/ \
    && rm -rf /tmp/azul_jdk.deb /tmp/apache-ant.tar.gz /var/lib/apt/lists/*

ENV PATH="/opt/apache-ant-1.10.15/bin:${PATH}"

# Stage 1b: Build the application
# Copy the entire source tree (excluding .dockerignore files), and build
COPY --exclude=docker . .
WORKDIR /app/server
RUN ANT_OPTS="-Dfile.encoding=UTF8" ant -f mirth-build.xml -DdisableSigning=true

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
ENTRYPOINT ["./configure-from-env.sh"]
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
ENTRYPOINT ["./configure-from-env.sh"]
CMD ["./oieserver"]
