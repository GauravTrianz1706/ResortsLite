#!/bin/bash
set -e
set -o pipefail

# ============================================================
# deploy-image.sh — Deploy ResortsLite to AWS EKS
# Usage: ./scripts/deploy-image.sh
# Run from the repository root directory
# ============================================================

APP_NAME="resortslite"
NAMESPACE="resortslite"
MANIFESTS_DIR="kubernetes"

echo "=============================================="
echo "  ResortsLite — AWS EKS Deployment Script"
echo "=============================================="
echo ""

# ── AWS / EKS configuration ──────────────────────────────────
read -rp "Enter AWS Region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
    echo "ERROR: AWS Region is required." >&2
    exit 1
fi

read -rp "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "ERROR: EKS Cluster Name is required." >&2
    exit 1
fi

read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "ERROR: Docker image URI is required." >&2
    exit 1
fi

echo ""
echo "--- Application Environment Variables ---"
echo "Press Enter to skip any optional variable."
echo ""

read -rp "Enter REDIS_HOST (ElastiCache primary endpoint) [localhost]: " REDIS_HOST
REDIS_HOST="${REDIS_HOST:-localhost}"

read -rp "Enter REDIS_PORT [6379]: " REDIS_PORT
REDIS_PORT="${REDIS_PORT:-6379}"

read -rp "Enter PAYMENT_API_URL [http://payment-service:9090/payments/charge]: " PAYMENT_API_URL
PAYMENT_API_URL="${PAYMENT_API_URL:-http://payment-service:9090/payments/charge}"

echo ""
echo "--- Deployment Summary ---"
echo "  AWS Region   : $AWS_REGION"
echo "  EKS Cluster  : $CLUSTER_NAME"
echo "  Image URI    : $IMAGE_URI"
echo "  Namespace    : $NAMESPACE"
echo "  REDIS_HOST   : $REDIS_HOST"
echo "  REDIS_PORT   : $REDIS_PORT"
echo ""

read -rp "Proceed with deployment? [y/N]: " CONFIRM
if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
    echo "Deployment cancelled."
    exit 0
fi

# ── Configure kubectl ────────────────────────────────────────
echo ""
echo "Configuring kubectl for EKS cluster '${CLUSTER_NAME}'..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
echo "kubectl configured."

# ── Verify cluster connectivity ──────────────────────────────
echo ""
echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster." >&2; exit 1; }

# ── Patch manifests with runtime values ──────────────────────
echo ""
echo "Patching Kubernetes manifests..."

# Work on copies to avoid modifying originals
cp "${MANIFESTS_DIR}/deployment.yaml" /tmp/deployment-patched.yaml

sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g'         /tmp/deployment-patched.yaml
sed -i 's|{{REDIS_HOST}}|'"$REDIS_HOST"'|g'       /tmp/deployment-patched.yaml
sed -i 's|{{REDIS_PORT}}|'"$REDIS_PORT"'|g'       /tmp/deployment-patched.yaml
sed -i 's|{{PAYMENT_API_URL}}|'"$PAYMENT_API_URL"'|g' /tmp/deployment-patched.yaml

echo "Manifests patched."

# ── Apply manifests ──────────────────────────────────────────
echo ""
echo "Applying Kubernetes manifests..."

echo "  [1/4] Applying namespace..."
kubectl apply -f "${MANIFESTS_DIR}/namespace.yaml"

echo "  [2/4] Applying deployment..."
kubectl apply -f /tmp/deployment-patched.yaml

echo "  [3/4] Applying service..."
kubectl apply -f "${MANIFESTS_DIR}/service.yaml"

echo "  [4/4] Applying ingress..."
kubectl apply -f "${MANIFESTS_DIR}/ingress.yaml"

echo "All manifests applied."

# ── Wait for rollout ─────────────────────────────────────────
echo ""
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/"${APP_NAME}" -n "${NAMESPACE}" --timeout=300s
echo "Deployment rollout complete."

# ── Verify resources ─────────────────────────────────────────
echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n "${NAMESPACE}"

# ── Display application URL ──────────────────────────────────
echo ""
echo "Fetching application ingress URL..."
INGRESS_HOST=$(kubectl get ingress "${APP_NAME}-ingress" -n "${NAMESPACE}" \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")

if [ "$INGRESS_HOST" != "pending" ] && [ -n "$INGRESS_HOST" ]; then
    echo "  Application URL: http://${INGRESS_HOST}"
else
    echo "  Ingress hostname is still provisioning. Run:"
    echo "  kubectl get ingress -n ${NAMESPACE}"
fi

echo ""
echo "=============================================="
echo "  Deployment Complete!"
echo "  Namespace : ${NAMESPACE}"
echo "  Image     : ${IMAGE_URI}"
echo "=============================================="
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}"
