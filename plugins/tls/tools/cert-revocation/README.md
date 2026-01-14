# CRL setup validation tools

This tool directory is meant to test and validate the correct use of revoked certificates. The included scripts
generate a CA, issue two TLS certificates, and revoke one of them. Also generated are a bunch of additional files.

## Setup

### `/etc/hosts`

In order to send hostname requests to the Caddy server, the following entries should be added to `/etc/hosts`

```text
127.0.0.1 valid.crl.caddy
127.0.0.1 revoked.crl.caddy
```

## Usage

The `./minilab.sh` generates the necessary file bundle. Run this first. This script does not have to be run again.

**Important file paths:**
- `tools/cert-revocation/mini-ca/demoCA/certs/ca.crt`
  - The Root CA certificate
- `tools/cert-revocation/mini-ca/demoCA/crl/ca.crl`
  - The revocation list file
- `tools/cert-revocation/mini-ca/server1.crt` and `tools/cert-revocation/mini-ca/server1.key`
  - Certificate and private key for the valid cert
- `tools/cert-revocation/mini-ca/server2.crt` and `tools/cert-revocation/mini-ca/server2.key`
    - Certificate and private key for the revoked cert
- `tools/cert-revocation/mini-ca/truststore.jks`
    - `JKS` keystore with the rot CA cert for easier Java usage

To validate the correctness of the generated bundle separately from the plugin and Java code, run `./verify.sh`.

The bundle is designed to be used with the [Caddy container](../../docker/compose.yaml) to serve two HTTP endpoints:
1. `https://valid.crl.caddy` - is served with a valid TLS certificate and responds with `HTTP 200` and `Hai with valid cert`
2. `https://revoked.crl.caddy` - is served with a revoked TLS certificate and responds with `HTTP 200` and `Hai with revoked cert`
