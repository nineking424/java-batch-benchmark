#!/bin/bash
#
# Kubernetes Deployment Script for Java Batch Benchmark
# Usage: ./deploy.sh [options]
#
# Options:
#   --with-oracle    Deploy Oracle DB container (for testing)
#   --build          Build Docker image before deploying
#   --run            Run the benchmark job after deployment
#   --clean          Clean up all resources
#   --logs           Show benchmark job logs
#   --help           Show this help message
#

set -e

# Configuration
NAMESPACE="benchmark"
IMAGE_NAME="java-batch-benchmark"
IMAGE_TAG="1.0.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_help() {
    head -20 "$0" | tail -17
    exit 0
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed"
        exit 1
    fi

    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi

    log_success "Prerequisites check passed"
}

# Build Docker image
build_image() {
    log_info "Building Docker image: ${IMAGE_NAME}:${IMAGE_TAG}"
    cd "$PROJECT_DIR"

    if command -v docker &> /dev/null; then
        docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" .
        log_success "Docker image built successfully"
    elif command -v podman &> /dev/null; then
        podman build -t "${IMAGE_NAME}:${IMAGE_TAG}" .
        log_success "Podman image built successfully"
    else
        log_error "Neither docker nor podman is available"
        exit 1
    fi
}

# Create namespace
create_namespace() {
    log_info "Creating namespace: ${NAMESPACE}"
    kubectl apply -f "${SCRIPT_DIR}/job.yaml" | head -1 || true
    log_success "Namespace created/verified"
}

# Deploy Oracle DB (optional)
deploy_oracle() {
    log_info "Deploying Oracle Database..."
    kubectl apply -f "${SCRIPT_DIR}/oracle-db.yaml"
    log_success "Oracle DB deployment initiated"

    log_info "Waiting for Oracle DB to be ready (this may take 5-10 minutes)..."
    kubectl wait --for=condition=ready pod -l app=oracle-db -n "${NAMESPACE}" --timeout=600s || {
        log_warn "Oracle DB is still starting. Check status with: kubectl get pods -n ${NAMESPACE}"
    }
}

# Deploy benchmark resources
deploy_benchmark() {
    log_info "Deploying benchmark resources..."

    # Apply ConfigMap and Secret
    kubectl apply -f "${SCRIPT_DIR}/configmap.yaml"
    kubectl apply -f "${SCRIPT_DIR}/secret.yaml"

    log_success "Benchmark resources deployed"
}

# Run benchmark job
run_benchmark() {
    log_info "Starting benchmark job..."

    # Delete existing job if any
    kubectl delete job java-batch-benchmark -n "${NAMESPACE}" --ignore-not-found=true

    # Apply job (excludes CronJob from job.yaml)
    kubectl apply -f "${SCRIPT_DIR}/job.yaml"

    log_success "Benchmark job started"
    log_info "Monitor with: kubectl logs -f job/java-batch-benchmark -n ${NAMESPACE}"
}

# Show logs
show_logs() {
    log_info "Showing benchmark job logs..."
    kubectl logs -f job/java-batch-benchmark -n "${NAMESPACE}" || {
        log_warn "No logs available. Job may not have started yet."
        kubectl get pods -n "${NAMESPACE}" -l app=java-batch-benchmark
    }
}

# Clean up resources
cleanup() {
    log_info "Cleaning up all resources..."

    kubectl delete namespace "${NAMESPACE}" --ignore-not-found=true

    log_success "All resources cleaned up"
}

# Show status
show_status() {
    log_info "Current status in namespace: ${NAMESPACE}"
    echo ""

    echo "=== Pods ==="
    kubectl get pods -n "${NAMESPACE}" 2>/dev/null || echo "Namespace not found"
    echo ""

    echo "=== Jobs ==="
    kubectl get jobs -n "${NAMESPACE}" 2>/dev/null || echo "No jobs found"
    echo ""

    echo "=== Services ==="
    kubectl get services -n "${NAMESPACE}" 2>/dev/null || echo "No services found"
}

# Main
main() {
    local with_oracle=false
    local build=false
    local run=false
    local clean=false
    local logs=false
    local status=false

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --with-oracle)
                with_oracle=true
                shift
                ;;
            --build)
                build=true
                shift
                ;;
            --run)
                run=true
                shift
                ;;
            --clean)
                clean=true
                shift
                ;;
            --logs)
                logs=true
                shift
                ;;
            --status)
                status=true
                shift
                ;;
            --help|-h)
                show_help
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                ;;
        esac
    done

    # Execute based on flags
    check_prerequisites

    if $clean; then
        cleanup
        exit 0
    fi

    if $logs; then
        show_logs
        exit 0
    fi

    if $status; then
        show_status
        exit 0
    fi

    if $build; then
        build_image
    fi

    # Default deployment flow
    create_namespace

    if $with_oracle; then
        deploy_oracle
    fi

    deploy_benchmark

    if $run; then
        run_benchmark
    else
        log_info "Resources deployed. Run benchmark with: $0 --run"
    fi

    echo ""
    show_status
}

main "$@"
