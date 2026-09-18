#!/usr/bin/env bash
# Relivus 启动脚本（systemd）
set -euo pipefail
systemctl start relivus-backend
echo "Waiting for health..."
until curl -sf http://127.0.0.1:8080/actuator/health > /dev/null; do sleep 2; done
echo "Relivus is up: http://localhost"