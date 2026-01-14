#!/usr/bin/env bash

function validate() {
    local url=$1
    openssl s_client -connect "$url" -showcerts < /dev/null \
      | sed -n '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/p' \
      > peer.crt

    openssl verify \
      -CAfile mini-ca/demoCA/certs/ca.crt \
      -CRLfile mini-ca/demoCA/crl/ca.crl \
      -crl_check peer.crt

    rm peer.crt
}

echo "######################"
echo
echo "Validating the valid certificate"
echo
echo "######################"
echo
validate "valid.crl.caddy:9443"
echo "^^^^^^^^^^^^"
echo "The last line should say \"peer.crt: OK\""

echo
echo "===================================================================="
echo
echo "######################"
echo
echo "Validating the revoked certificate"
echo
echo "######################"
echo
validate "revoked.crl.caddy:9443"
echo "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^"
echo "The last line should say \"error peer.crt: verification failed\""
