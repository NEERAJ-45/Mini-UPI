#!/bin/bash
# ════════════════════════════════════════════════════════════════════
#  infra/oci-setup.sh
#  One-time bootstrap script for Oracle Cloud Free Tier VM.
#  Run manually over SSH after provisioning the instance.
#
#  Tested on:
#    • Oracle Linux 8/9  (default user: opc)
#    • Ubuntu 22.04/24.04 on OCI (default user: ubuntu)
#
#  Usage:
#    ssh -i ~/.ssh/oci_key.pem opc@<OCI_PUBLIC_IP> 'bash -s' < oci-setup.sh
# ════════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Detect OS and set package manager ────────────────────────────────
if command -v dnf &>/dev/null; then
  PKG_MGR="dnf"
  echo "▶ Detected Oracle Linux (dnf)"
elif command -v apt-get &>/dev/null; then
  PKG_MGR="apt-get"
  echo "▶ Detected Ubuntu/Debian (apt-get)"
else
  echo "❌ Unsupported OS — only Oracle Linux (dnf) and Ubuntu (apt-get) supported"
  exit 1
fi

# ── 1. System update ─────────────────────────────────────────────────
echo "▶ Updating system packages..."
if [ "$PKG_MGR" = "dnf" ]; then
  sudo dnf update -y
else
  sudo apt-get update -y
  sudo apt-get upgrade -y
fi

# ── 2. Install Docker ────────────────────────────────────────────────
echo "▶ Installing Docker..."
if [ "$PKG_MGR" = "dnf" ]; then
  # Oracle Linux: use Docker's official repo
  sudo dnf install -y dnf-plugins-core
  sudo dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
  sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
  # Ubuntu: use Docker's official repo
  sudo apt-get install -y ca-certificates curl gnupg lsb-release
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
  sudo apt-get update -y
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

# Enable and start Docker
sudo systemctl enable --now docker

# Add current user to docker group (avoids needing sudo for every docker command)
CURRENT_USER=$(whoami)
sudo usermod -aG docker "$CURRENT_USER"
echo "✅ Docker installed — you may need to re-login for group change to take effect"
echo "   (run 'newgrp docker' in current session to apply immediately)"

# ── 3. Open firewall ports (OCI has both OS-level and VCN-level firewall) ──
echo "▶ Opening firewall ports (OS-level)..."
if [ "$PKG_MGR" = "dnf" ]; then
  # Oracle Linux uses firewalld
  sudo systemctl enable --now firewalld
  sudo firewall-cmd --permanent --add-port=8080/tcp   # api-gateway
  sudo firewall-cmd --permanent --add-port=8081/tcp   # user-service
  sudo firewall-cmd --permanent --add-port=8082/tcp   # wallet-service
  sudo firewall-cmd --permanent --add-port=8083/tcp   # transaction-service
  sudo firewall-cmd --permanent --add-port=8084/tcp   # notification-service
  sudo firewall-cmd --permanent --add-port=80/tcp     # nginx HTTP
  sudo firewall-cmd --permanent --add-port=443/tcp    # nginx HTTPS
  sudo firewall-cmd --reload
else
  # Ubuntu uses ufw
  sudo ufw allow 8080/tcp
  sudo ufw allow 8081/tcp
  sudo ufw allow 8082/tcp
  sudo ufw allow 8083/tcp
  sudo ufw allow 8084/tcp
  sudo ufw allow 80/tcp
  sudo ufw allow 443/tcp
  sudo ufw --force enable
fi
echo "⚠  REMINDER: Also open these ports in the OCI Console:"
echo "   Networking → VCNs → <your-vcn> → Security Lists → Ingress Rules"
echo "   Add TCP rules for ports: 22, 80, 443, 8080-8084"

# ── 4. Create app directory with locked permissions ──────────────────
echo "▶ Creating /opt/revpay directory..."
sudo mkdir -p /opt/revpay
sudo chown "$CURRENT_USER:$CURRENT_USER" /opt/revpay
chmod 700 /opt/revpay

# ── 5. Create .env file template ────────────────────────────────────
echo "▶ Creating /opt/revpay/.env template..."
cat > /opt/revpay/.env << 'EOF'
# ── Database ─────────────────────────────────────────────────────────
POSTGRES_USER=REPLACE_ME
POSTGRES_PASSWORD=REPLACE_ME
POSTGRES_HOST=postgres
POSTGRES_PORT=5432

# ── Redis ─────────────────────────────────────────────────────────────
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=REPLACE_ME

# ── Kafka ─────────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS=kafka:29092

# ── JWT ───────────────────────────────────────────────────────────────
JWT_SECRET=REPLACE_ME
JWT_EXPIRY_MS=86400000

# ── Service Ports ─────────────────────────────────────────────────────
USER_SERVICE_PORT=8081
WALLET_SERVICE_PORT=8082
TRANSACTION_SERVICE_PORT=8083
NOTIFICATION_SERVICE_PORT=8084
GATEWAY_PORT=8080
EOF

# Lock .env to owner-only read/write — secrets never visible in docker inspect args
chmod 600 /opt/revpay/.env
echo "✅ /opt/revpay/.env created — FILL IN the REPLACE_ME values before starting services"

# ── 6. Create the shared Docker network ─────────────────────────────
echo "▶ Creating Docker network 'upi-net'..."
# Run with newgrp so the group change is effective in this session
newgrp docker <<DOCKERGRP
docker network create upi-net 2>/dev/null && echo "✅ upi-net created" || echo "ℹ  upi-net already exists"
DOCKERGRP

# ── 7. GHCR login hint ───────────────────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════════════"
echo "  Next steps:"
echo ""
echo "  1. Fill in /opt/revpay/.env with real secrets:"
echo "       nano /opt/revpay/.env"
echo ""
echo "  2. Log in to GHCR so the VM can pull private images:"
echo "       docker login ghcr.io -u <your-github-username>"
echo "       (use a Personal Access Token with 'read:packages' scope)"
echo ""
echo "  3. Copy docker-compose.yml to the VM and start infra stack:"
echo "       scp docker-compose.yml opc@<OCI_IP>:/opt/revpay/"
echo "       docker compose --env-file /opt/revpay/.env -f /opt/revpay/docker-compose.yml up -d \\"
echo "         postgres redis kafka zookeeper nginx prometheus grafana zipkin"
echo ""
echo "  4. Configure GitHub Secrets (Settings → Secrets → Actions):"
echo "       OCI_HOST  = <Public IP from OCI Console>"
echo "       OCI_USER  = opc  (Oracle Linux)  OR  ubuntu  (Ubuntu image)"
echo "       OCI_SSH_KEY = <paste content of your .pem / private key file>"
echo ""
echo "  5. Push a change to trigger the pipeline — enjoy zero-downtime deploys!"
echo "════════════════════════════════════════════════════════════"
