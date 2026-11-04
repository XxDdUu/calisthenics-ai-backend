#!/bin/sh
set -e

echo "=== MinIO Initialization Started ==="

MINIO_HOST="${MINIO_HOST:-minio}"
MINIO_PORT="${MINIO_PORT:-9000}"
MINIO_URL="http://${MINIO_HOST}:${MINIO_PORT}"

echo "Connecting to MinIO server at $MINIO_URL..."

# Wait for MinIO server to be available with timeout
MAX_ATTEMPTS=30
ATTEMPT=1
until mc alias set local "$MINIO_URL" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do
  if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
    echo "ERROR: Timed out waiting for MinIO at $MINIO_URL" >&2
    exit 1
  fi
  echo "Waiting for MinIO ($ATTEMPT/$MAX_ATTEMPTS)..."
  ATTEMPT=$((ATTEMPT + 1))
  sleep 2
done

echo "MinIO alias configured successfully."

# 1. Create Buckets (idempotent)
for BUCKET in avatars exercise-media workout-media analysis-media; do
  if ! mc ls "local/$BUCKET" >/dev/null 2>&1; then
    echo "Creating bucket: $BUCKET"
    mc mb "local/$BUCKET"
  else
    echo "Bucket $BUCKET already exists."
  fi
done

# 2. Configure Anonymous Read Policies (avatars & exercise-media are public download)
echo "Setting bucket policies..."
mc anonymous set download local/avatars || true
mc anonymous set download local/exercise-media || true
mc anonymous set none local/workout-media || true
mc anonymous set none local/analysis-media || true

# 3. Configure Lifecycle Policy on analysis-media (Expire temp videos after 14 days)
echo "Configuring lifecycle policy on analysis-media..."
if ! mc ilm rule list local/analysis-media 2>/dev/null | grep -q "Enabled"; then
  echo "Adding 14-day expiration rule to analysis-media..."
  mc ilm rule add local/analysis-media --expire-days 14 || echo "ILM rule setup skipped or already active"
else
  echo "Lifecycle policy already configured on analysis-media."
fi

# 4. Create Policies
echo "Registering IAM policies..."
if [ -f /etc/minio/policies/backend-policy.json ]; then
  mc admin policy create local backend-rw-policy /etc/minio/policies/backend-policy.json 2>/dev/null || \
  mc admin policy update local backend-rw-policy /etc/minio/policies/backend-policy.json || true
fi

if [ -f /etc/minio/policies/ai-service-policy.json ]; then
  mc admin policy create local ai-service-policy /etc/minio/policies/ai-service-policy.json 2>/dev/null || \
  mc admin policy update local ai-service-policy /etc/minio/policies/ai-service-policy.json || true
fi

# 5. Create Service Accounts / Users for Backend & AI Service (Least Privilege)
if [ -n "$MINIO_BACKEND_ACCESS_KEY" ] && [ -n "$MINIO_BACKEND_SECRET_KEY" ]; then
  echo "Configuring Backend service account: $MINIO_BACKEND_ACCESS_KEY..."
  if ! mc admin user info local "$MINIO_BACKEND_ACCESS_KEY" >/dev/null 2>&1; then
    mc admin user add local "$MINIO_BACKEND_ACCESS_KEY" "$MINIO_BACKEND_SECRET_KEY" || true
  fi
  mc admin policy attach local backend-rw-policy --user "$MINIO_BACKEND_ACCESS_KEY" || \
  mc admin policy attach local readwrite --user "$MINIO_BACKEND_ACCESS_KEY" || true
fi

if [ -n "$MINIO_AI_ACCESS_KEY" ] && [ -n "$MINIO_AI_SECRET_KEY" ]; then
  echo "Configuring AI Service account: $MINIO_AI_ACCESS_KEY..."
  if ! mc admin user info local "$MINIO_AI_ACCESS_KEY" >/dev/null 2>&1; then
    mc admin user add local "$MINIO_AI_ACCESS_KEY" "$MINIO_AI_SECRET_KEY" || true
  fi
  mc admin policy attach local ai-service-policy --user "$MINIO_AI_ACCESS_KEY" || true
fi

echo "=== MinIO Initialization Completed Successfully ==="
exit 0

