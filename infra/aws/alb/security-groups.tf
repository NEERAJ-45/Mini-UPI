# ════════════════════════════════════════════════════════════════════
# RevPay — ALB Security Groups (Terraform Stub)
# ════════════════════════════════════════════════════════════════════
# Controls what traffic can reach the ALB and what the ALB can reach.
# ════════════════════════════════════════════════════════════════════

# ── ALB Security Group ────────────────────────────────────────────
# Allows internet traffic IN on 80/443
# Allows ALB to reach EC2 instances on 8080

resource "aws_security_group" "alb" {
  name        = "revpay-alb-sg"
  description = "Security group for RevPay ALB"
  vpc_id      = var.vpc_id

  # Inbound: Allow HTTP from anywhere
  ingress {
    description = "HTTP from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Inbound: Allow HTTPS from anywhere
  ingress {
    description = "HTTPS from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Outbound: Allow ALB to reach EC2 instances on service ports
  egress {
    description = "To EC2 instances (API Gateway)"
    from_port   = 8080
    to_port     = 8084
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]   # TODO: Restrict to VPC CIDR
  }

  tags = {
    Name    = "revpay-alb-sg"
    Project = "revpay"
  }
}

# ── EC2 Security Group ────────────────────────────────────────────
# Allows traffic FROM ALB on service ports
# Allows SSH for debugging

resource "aws_security_group" "ec2_services" {
  name        = "revpay-ec2-services-sg"
  description = "Security group for EC2 instances running RevPay containers"
  vpc_id      = var.vpc_id

  # Inbound: Allow ALB to reach API Gateway
  ingress {
    description     = "API Gateway from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Inbound: Allow ALB health checks on all service ports
  ingress {
    description     = "Health checks from ALB"
    from_port       = 8081
    to_port         = 8084
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Inbound: SSH for debugging (restrict to your IP in production)
  ingress {
    description = "SSH access"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]   # TODO: Restrict to your IP
  }

  # Outbound: Allow all (services need to reach internet, RDS, etc.)
  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "revpay-ec2-services-sg"
    Project = "revpay"
  }
}

# ── Inter-Service Communication (within EC2) ──────────────────────
# Since all containers run on the same EC2 instance(s),
# inter-service calls (Transaction → Wallet via Feign) happen
# over localhost or Docker network. No security group rules needed.
#
# If you scale to multiple EC2 instances:
#   - Add ingress rules for ports 8081-8084 from ec2_services SG to itself
#   - Or use an internal ALB for service-to-service routing
