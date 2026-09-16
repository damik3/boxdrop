#!/bin/sh
set -eu

MINIO_URL="${MINIO_URL:-http://minio:9000}"
MINIO_BUCKET="${MINIO_BUCKET:-dropbox-clone}"
WEBHOOK_ARN="${WEBHOOK_ARN:-arn:minio:sqs::1:webhook}"

i=0
until mc alias set local "$MINIO_URL" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"; do
  i=$((i + 1))
  if [ "$i" -gt 30 ]; then
    echo "minio-init: could not reach MinIO at $MINIO_URL"
    exit 1
  fi
  sleep 2
done

mc mb --ignore-existing "local/${MINIO_BUCKET}"

if mc event list "local/${MINIO_BUCKET}" | grep -q "$WEBHOOK_ARN"; then
  echo "minio-init: PUT webhook already registered on ${MINIO_BUCKET}"
else
  mc event add "local/${MINIO_BUCKET}" "$WEBHOOK_ARN" --event put
  echo "minio-init: registered PUT events on ${MINIO_BUCKET} -> ${WEBHOOK_ARN}"
fi

mc event list "local/${MINIO_BUCKET}"
