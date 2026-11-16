#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
resources=src/main/resources
if [ -f "$resources/privateKey" ] && [ -f "$resources/publicKey" ]; then
  exit 0
fi
if [ -e "$resources/privateKey" ] || [ -e "$resources/publicKey" ]; then
  echo "Ein unvollständiges Schlüsselpaar ist vorhanden. Beide Dateien prüfen und bei Bedarf entfernen." >&2
  exit 1
fi

umask 077
scratch=$(mktemp -d "$resources/.demo-keys.XXXXXX")
trap 'rm -rf "$scratch"' EXIT
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$scratch/key.pem"
openssl pkcs8 -topk8 -nocrypt -in "$scratch/key.pem" -outform DER -out "$scratch/privateKey"
openssl pkey -in "$scratch/key.pem" -pubout -outform DER -out "$scratch/publicKey"
mv "$scratch/publicKey" "$resources/publicKey"
mv "$scratch/privateKey" "$resources/privateKey"
