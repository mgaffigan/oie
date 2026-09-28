# Code signing

Release builds sign with an EV code-signing certificate whose private key lives
in an HSM in Google Cloud KMS. Signing is a remote call, so a build machine
never holds the key.

- Jars: `jarsigner` with [jsign](https://ebourg.github.io/jsign/)'s JCA provider
  (`cert=ca` mode in `signSetupJars`, configured by
  `gcp-kms.keystore.properties`).
- Windows launchers and installers: install4j runs `authenticode-sign.sh`.
- macOS `.dmg`: unsigned, because notarization needs an Apple Developer ID.
- Unix `.sh` and `.tar.gz`: not signable; verify with the published `sha256sums`.

jsign is used rather than Google's `libkmsp11` because it is pure Java, so this
works on macOS too, where `libkmsp11` has no build.

The key is `itt-2026-ev` in key ring `itt` (project `itt-misc`), RSA 4096,
`RSA_SIGN_PKCS1_4096_SHA256`. jsign only supports RSA with PKCS#1 v1.5 padding
or EC keys, so a PSS or raw key cannot be substituted.

## Signing locally

Needs `gcloud` authenticated as a principal with `roles/cloudkms.signer` on the
key, and a [jsign jar](https://github.com/ebourg/jsign/releases).

```sh
export JSIGN_JAR=$PWD/jsign-7.5.jar

./gradlew build \
    -Pkeystore_property_file=$PWD/tools/signing/gcp-kms.keystore.properties \
    -Pkey.jarsignerargs="-J-cp -J$JSIGN_JAR -J--add-modules -Jjava.sql" \
    -Pkey.storepass="$(gcloud auth print-access-token)"

install4jc --build-selected --disable-notarization \
    --release "$(sed -n 's/^version=//p' gradle.properties)" \
    tools/install4j/oie-installer-config.install4j
```

Without `-Pkeystore_property_file` the build uses the self-signed development
certificate in `server/keystore.properties`; day-to-day builds should keep
passing `-PdisableSigning=true` and skip all of this. Add `--disable-signing`
to build installers unsigned.

Check the result with `jarsigner -verify -strict`, which unlike a plain
`-verify` exits non-zero on chain problems. The three BouncyCastle jars also
carry their own signature, which must not be stripped — the JVM checks it
before accepting them as JCE providers — and their signer is not in the JDK
truststore, so `-strict` objects to *their* block, not ours.

## Gotchas

- **The alias needs its `:RSA` suffix.** Otherwise jsign infers the key
  algorithm from a certificate its JCA provider cannot supply and fails with
  `Failed to load the certificate from`.
- **The chain ends at the root cross-signed by USERTrust RSA**, which is in the
  JDK's `cacerts`. The self-signed Sectigo code signing root is not, and
  `jarsigner` then reports a PKIX failure on every jar.
- **`windowsExecutable` is an attribute of `<codeSigning>`**, holding the whole
  command line. The neighbouring `digestSigningExecutable` key source uses a
  child element whose install4j property is misspelled
  (`windowsDigestSigningExectuable`), so a correctly spelled element is parsed
  as nothing and the signing command silently comes out empty.
- **install4j needs `install4jc -L <key>`.** Setting `INSTALL4J_LICENSE_KEY`
  alone still fails at compile time.
- install4j's own helper binaries (`.install4j/i4jdel.exe`, `i4jinst.dll`) do
  not go through the signing command and ship unsigned.

## Secrets

CI needs `GCP_LOGIN_JSON` (service account with `roles/cloudkms.signer`) and
`INSTALL4J_KEY` (install4j licence). Access tokens are minted per build, and
per executable for install4j, because they expire after an hour.

## Renewing the certificate

Create the new key version, replace `oie-codesign-chain.pem` with the newly
issued certificate followed by its issuers (the CA publishes them at the
`CA Issuers` URL in each certificate), and update the alias in both
`gcp-kms.keystore.properties` and `authenticode-sign.sh`. Released artifacts
keep verifying, because every signature is timestamped.
