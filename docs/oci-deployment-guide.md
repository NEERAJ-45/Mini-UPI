# RevPay — Oracle Cloud Free Tier Deployment Guide

Complete end-to-end walkthrough: from zero to a running RevPay stack on OCI with automated CI/CD.

---

## Overview — What You're Building

```
GitHub Push → GitHub Actions → GHCR (image registry) → SSH → Oracle Cloud VM
                                                                      │
                                                              ┌───────┴────────┐
                                                              │  Docker network │
                                                              │    "upi-net"    │
                                                              │                 │
                                                              │  postgres       │
                                                              │  redis          │
                                                              │  kafka          │
                                                              │  zookeeper      │
                                                              │  nginx          │
                                                              │  prometheus     │
                                                              │  grafana        │
                                                              │  zipkin         │
                                                              │                 │
                                                              │  user-service   │
                                                              │  wallet-service │
                                                              │  txn-service    │
                                                              │  notif-service  │
                                                              │  api-gateway    │
                                                              └─────────────────┘
```

---

## Phase 1 — Create an Oracle Cloud Account

1. Go to **[cloud.oracle.com](https://cloud.oracle.com)** → click **Start for free**
2. Fill in your details — you need a **credit/debit card** for identity verification (you will NOT be charged on the Always Free tier)
3. Choose your **Home Region** — pick one close to your users (e.g., `ap-mumbai-1` for India). **This cannot be changed later.**
4. Complete email verification and sign in

> [!IMPORTANT]
> Oracle's Always Free tier gives you **4 ARM Ampere A1 OCPUs + 24 GB RAM** total — enough to run your entire RevPay stack comfortably. Use this shape, not the AMD micro instances.

---

## Phase 2 — Provision the VM

### 2.1 Create the Instance

1. In the OCI Console → **Compute** → **Instances** → **Create Instance**
2. **Name**: `revpay-server`
3. **Image**: Click **Change image**
   - Select **Canonical Ubuntu**
   - Choose **Ubuntu 22.04** (LTS, recommended)
   - Click **Select image**
4. **Shape**: Click **Change shape**
   - Select **Ampere** (ARM-based)
   - Shape: **VM.Standard.A1.Flex**
   - OCPUs: **4** | Memory: **24 GB** ← maximizes the free allocation
   - Click **Select shape**
5. **Primary VNIC** — leave defaults (new VCN will be created automatically)
6. **Add SSH keys**:
   - Select **Generate a key pair for me**
   - Click **Save private key** → downloads `ssh-key-<date>.key`
   - **Save this file — you cannot download it again**
7. Click **Create**

Wait ~2 minutes for the instance to reach **RUNNING** state.

### 2.2 Note Your Public IP

In the instance details page:
- Copy the **Public IP address** (e.g., `132.145.x.x`)
- This is your `OCI_HOST` secret value

---

## Phase 3 — Open Firewall Ports (OCI Has Two Layers)

> [!WARNING]
> OCI has **two independent firewall layers**. You must open ports in **both**. Forgetting the VCN layer is the #1 gotcha — your app will run but be unreachable.

### Layer 1 — VCN Security List (OCI Console)

1. **Networking** → **Virtual Cloud Networks** → click your VCN
2. Click **Security Lists** → **Default Security List**
3. Click **Add Ingress Rules** and add each row:

| Source CIDR | Protocol | Port | Description |
|---|---|---|---|
| `0.0.0.0/0` | TCP | `22` | SSH |
| `0.0.0.0/0` | TCP | `80` | Nginx HTTP |
| `0.0.0.0/0` | TCP | `443` | Nginx HTTPS |
| `0.0.0.0/0` | TCP | `8080` | API Gateway |
| `0.0.0.0/0` | TCP | `8081` | User Service |
| `0.0.0.0/0` | TCP | `8082` | Wallet Service |
| `0.0.0.0/0` | TCP | `8083` | Transaction Service |
| `0.0.0.0/0` | TCP | `8084` | Notification Service |

4. Click **Add Ingress Rules**

### Layer 2 — OS-level Firewall (Done by the setup script)

The `oci-setup.sh` script handles this automatically. It detects Ubuntu vs Oracle Linux and runs the appropriate commands. No manual action needed here.

---

## Phase 4 — SSH Into the VM and Run the Bootstrap Script

### 4.1 Fix SSH Key Permissions (Linux/Mac)
```bash
chmod 400 ~/Downloads/ssh-key-<date>.key
```

On **Windows**, open PowerShell as Administrator:
```powershell
icacls "$env:USERPROFILE\Downloads\ssh-key-<date>.key" /inheritance:r /grant:r "$env:USERNAME:R"
```

### 4.2 Connect to the VM
```bash
# Ubuntu image → user is "ubuntu"
ssh -i ~/Downloads/ssh-key-<date>.key ubuntu@<YOUR_OCI_PUBLIC_IP>

# Oracle Linux image → user is "opc"
ssh -i ~/Downloads/ssh-key-<date>.key opc@<YOUR_OCI_PUBLIC_IP>
```

### 4.3 Run the Bootstrap Script

From your local machine, stream the script directly over SSH:
```bash
ssh -i ~/Downloads/ssh-key-<date>.key ubuntu@<YOUR_OCI_PUBLIC_IP> 'bash -s' \
  < infra/oci-setup.sh
```

Or copy it first and run:
```bash
scp -i ~/Downloads/ssh-key-<date>.key \
  infra/oci-setup.sh ubuntu@<YOUR_OCI_PUBLIC_IP>:~/

ssh -i ~/Downloads/ssh-key-<date>.key ubuntu@<YOUR_OCI_PUBLIC_IP>
chmod +x oci-setup.sh && ./oci-setup.sh
```

The script will:
- ✅ Update system packages
- ✅ Install Docker + Docker Compose v2
- ✅ Open OS firewall ports
- ✅ Create `/opt/revpay/` directory (permissions-locked)
- ✅ Create `/opt/revpay/.env` template
- ✅ Create the `upi-net` Docker network

---

## Phase 5 — Fill in Secrets on the VM

SSH into the VM and edit the `.env` file:
```bash
nano /opt/revpay/.env
```

Replace every `REPLACE_ME` value:

```env
POSTGRES_USER=revpay
POSTGRES_PASSWORD=<strong-random-password>
POSTGRES_HOST=postgres
POSTGRES_PORT=5432

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=<strong-random-password>

KAFKA_BOOTSTRAP_SERVERS=kafka:29092

JWT_SECRET=<minimum-256-bit-random-string>
JWT_EXPIRY_MS=86400000

USER_SERVICE_PORT=8081
WALLET_SERVICE_PORT=8082
TRANSACTION_SERVICE_PORT=8083
NOTIFICATION_SERVICE_PORT=8084
GATEWAY_PORT=8080
```

Save with `Ctrl+O`, exit with `Ctrl+X`.

Verify permissions (should show `-rw-------`):
```bash
ls -la /opt/revpay/.env
```

> [!TIP]
> Generate strong secrets locally:
> ```bash
> # Strong password
> openssl rand -base64 32
> # JWT secret (256-bit)
> openssl rand -hex 64
> ```

---

## Phase 6 — Log In to GHCR on the VM

GitHub Container Registry requires authentication to pull your private images.

### 6.1 Create a Personal Access Token (PAT)

1. GitHub → **Settings** → **Developer settings** → **Personal access tokens** → **Tokens (classic)**
2. Click **Generate new token (classic)**
3. Note: `revpay-oci-pull`
4. Expiration: `No expiration` (or set a reminder to rotate)
5. Scopes: check **`read:packages`** only
6. Click **Generate token** — copy the token immediately

### 6.2 Login on the VM
```bash
# On the OCI VM
echo "<YOUR_PAT_TOKEN>" | docker login ghcr.io -u <YOUR_GITHUB_USERNAME> --password-stdin
```

Expected output: `Login Succeeded`

---

## Phase 7 — Start the Infrastructure Stack

Copy `docker-compose.yml` to the VM:
```bash
# From your local machine
scp -i ~/Downloads/ssh-key-<date>.key \
  docker-compose.yml ubuntu@<YOUR_OCI_PUBLIC_IP>:/opt/revpay/
```

SSH in and start infra services:
```bash
ssh -i ~/Downloads/ssh-key-<date>.key ubuntu@<YOUR_OCI_PUBLIC_IP>

cd /opt/revpay

docker compose --env-file .env -f docker-compose.yml up -d \
  postgres redis kafka zookeeper nginx prometheus grafana zipkin

# Verify everything is up
docker compose --env-file .env -f docker-compose.yml ps
```

Wait ~30 seconds and check health:
```bash
# Postgres ready?
docker exec postgres pg_isready -U revpay

# Redis ready?
docker exec redis redis-cli -a <REDIS_PASSWORD> ping
# Expected: PONG

# Kafka ready?
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

---

## Phase 8 — Configure GitHub Secrets

In your GitHub repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Secret Name | Value | Where to get it |
|---|---|---|
| `OCI_HOST` | `132.145.x.x` | OCI Console → Instance Details → Public IP |
| `OCI_USER` | `ubuntu` or `opc` | Ubuntu image → `ubuntu`, Oracle Linux → `opc` |
| `OCI_SSH_KEY` | Contents of `.key` file | Open the `.key` file in a text editor, paste everything |
| `POSTGRES_USER` | Same as in `.env` | Your choice |
| `POSTGRES_PASSWORD` | Same as in `.env` | Your choice |
| `REDIS_PASSWORD` | Same as in `.env` | Your choice |
| `JWT_SECRET` | Same as in `.env` | Your choice |

> [!IMPORTANT]
> **For `OCI_SSH_KEY`**: paste the full content including the `-----BEGIN...-----` and `-----END...-----` lines. The `appleboy/ssh-action` action handles PEM format natively.

---

## Phase 9 — Configure GitHub Environment (Optional but Recommended)

The `deploy` job uses `environment: production`. This lets you add a **manual approval gate**.

1. GitHub → **Settings** → **Environments** → **New environment**
2. Name: `production`
3. **Required reviewers**: add yourself
4. Click **Save protection rules**

Now every deploy will pause and wait for your approval before SSHing to the VM.

---

## Phase 10 — Trigger Your First Deploy

```bash
# Make a small change to user-service and push
echo "# trigger" >> user-service/src/main/resources/application.properties
git add -A
git commit -m "ci: trigger first OCI deploy"
git push origin main
```

Watch the pipeline at: `github.com/<your-repo>/actions`

You should see:
1. `ci-user-service.yml` triggered (because `user-service/**` changed)
2. **Job 1: Build & Test** → runs Testcontainers
3. **Job 2: Docker Build & Push** → image appears in your GitHub Packages
4. **Job 3: Deploy to Oracle Cloud VM** → SSH into OCI, pull image, start container, health check passes ✅

On the VM, verify:
```bash
# Container running with correct SHA tag?
docker ps

# Health check passes?
curl http://localhost:8081/actuator/health

# Secrets not visible in docker inspect?
docker inspect user-service | grep -A5 '"Env"'
# Should show env var NAMES but values come from --env-file, not visible in Args
```

---

## Troubleshooting

### ❌ SSH connection refused
- Check your OCI VCN Security List has port 22 open
- Verify the correct username (`ubuntu` vs `opc`)
- Ensure the `.key` file has correct permissions (`chmod 400`)

### ❌ `docker pull` fails in the deploy job
```bash
# On the VM — re-authenticate to GHCR
docker login ghcr.io -u <github_username> -p <PAT>
```

### ❌ Health check fails / rollback triggers
```bash
# Check container logs
docker logs user-service --tail 100

# Check if .env was loaded (env vars present?)
docker exec user-service env | grep POSTGRES
```

### ❌ Service unreachable from browser even though container is UP
- OCI VCN Security List might be missing the port — re-check Phase 3
- OS firewall might have blocked it — run `sudo ufw status` (Ubuntu) or `sudo firewall-cmd --list-ports` (Oracle Linux)
- Check Nginx config is routing to the right backend port

### ❌ `upi-net` network not found
```bash
docker network create upi-net
```

### ❌ Out of disk space (ARM A1 free tier has ~47 GB boot volume)
```bash
# Clean up unused images
docker image prune -a -f --filter "until=72h"
# Clean up stopped containers
docker container prune -f
```

---

## Secret Rotation Checklist

When rotating secrets (passwords, JWT secret, PAT):

1. Update `/opt/revpay/.env` on the VM
2. Update GitHub Secrets in repo settings
3. Restart affected services:
   ```bash
   docker restart user-service wallet-service transaction-service notification-service api-gateway
   ```
4. Rotate the GHCR PAT: create new → login on VM → delete old token

---

## Architecture on the VM (After Full Deploy)

```
Public Internet
      │
      ▼ :80 / :443
   [ Nginx ] ─── reverse proxy ──► api-gateway :8080
                                         │
                              ┌──────────┼──────────┐
                              ▼          ▼          ▼
                        user :8081  wallet:8082  txn:8083
                                                    │
                                             notif:8084
                              │          │          │
                              └──────────┼──────────┘
                                         ▼
                                   [ postgres ]
                                   [ redis    ]
                                   [ kafka    ]

Observability:
   prometheus :9090   grafana :3000   zipkin :9411
```
