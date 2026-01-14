#!/usr/bin/env bash
set -euo pipefail

# fresh workspace
WORKDIR="$(pwd)/mini-ca"
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"/demoCA/{certs,crl,newcerts,private}
touch "$WORKDIR"/demoCA/index.txt
echo 1000 > "$WORKDIR"/demoCA/serial
echo 1000 > "$WORKDIR"/demoCA/crlnumber

# minimal OpenSSL CA config
cat > "$WORKDIR/openssl.cnf" <<'EOF'
[ ca ]
default_ca = CA_default

[ CA_default ]
dir               = ./demoCA
certs             = $dir/certs
crl_dir           = $dir/crl
database          = $dir/index.txt
new_certs_dir     = $dir/newcerts
certificate       = $dir/certs/ca.crt
serial            = $dir/serial
crlnumber         = $dir/crlnumber
crl               = $dir/crl/ca.crl
private_key       = $dir/private/ca.key
RANDFILE          = $dir/private/.rand

# policy: keep it permissive for testing
policy            = policy_loose
x509_extensions   = v3_end_entity
copy_extensions   = copy
default_md        = sha256
default_days      = 825
unique_subject    = no
email_in_dn       = no
name_opt          = ca_default
cert_opt          = ca_default
default_crl_days  = 30

[ policy_loose ]
commonName              = supplied
organizationName        = optional
countryName             = optional
stateOrProvinceName     = optional
localityName            = optional
organizationalUnitName  = optional

[ req ]
default_bits        = 2048
distinguished_name  = req_dn
string_mask         = utf8only
default_md          = sha256
prompt              = no

[ req_dn ]
CN = placeholder

# CA cert extensions
[v3_ca]
basicConstraints = critical, CA:true, pathlen:0
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer

# End-entity cert extensions
[v3_end_entity]
basicConstraints = critical, CA:false
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
crlDistributionPoints = URI:http://example.test/crl/ca.crl

# CRL extensions
[ crl_ext ]
authorityKeyIdentifier = keyid:always
EOF

pushd "$WORKDIR" >/dev/null

# 1) Create CA key + self-signed cert
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out demoCA/private/ca.key
openssl req -new -x509 -key demoCA/private/ca.key -sha256 -days 3650 \
  -subj "/C=EE/O=Test CA/CN=Test Root CA" \
  -config openssl.cnf -extensions v3_ca \
  -out demoCA/certs/ca.crt

# 2) Create two end-entity keys + CSRs
for n in 1 2; do
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out server${n}.key
  openssl req -new -key server${n}.key \
    -subj "/C=EE/O=Test Org/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,DNS:valid.crl.caddy,DNS:revoked.crl.caddy,DNS:mtls.caddy,IP:127.0.0.1" \
    -config openssl.cnf -out server${n}.csr
done

# 3) Issue two certs using the CA (keeps the CA index/database updated)
openssl ca -batch -config openssl.cnf -extensions v3_end_entity \
  -in server1.csr -out server1.crt
openssl ca -batch -config openssl.cnf -extensions v3_end_entity \
  -in server2.csr -out server2.crt

# 4) Revoke one of them (server2)
openssl ca -config openssl.cnf -revoke server2.crt -crl_reason cessationOfOperation

# 5) Generate CRL (PEM), plus a DER copy (handy for Java)
openssl ca -config openssl.cnf -gencrl -crldays 30 -out demoCA/crl/ca.crl
openssl crl -in demoCA/crl/ca.crl -outform DER -out demoCA/crl/ca.crl.der

# quick local verification examples
echo "== Basic verify (no CRL check):"
openssl verify -CAfile demoCA/certs/ca.crt server1.crt server2.crt || true

echo "== Verify WITH CRL check (server2 should fail):"
openssl verify -crl_check -CAfile demoCA/certs/ca.crt -CRLfile demoCA/crl/ca.crl server1.crt server2.crt || true

# Generate a truststore for easy Java stuffs
keytool -importcert -noprompt -trustcacerts \
  -file demoCA/certs/ca.crt \
  -alias test-root-ca \
  -keystore truststore.p12 -storepass changeit

popd >/dev/null

echo "Done. Artifacts in: $WORKDIR"
