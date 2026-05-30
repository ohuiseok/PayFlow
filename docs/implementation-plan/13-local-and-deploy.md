# 13. Local And Deploy

이 문서는 로컬 실행과 EC2 배포 전략이다.

## 로컬 실행

로컬에 MySQL이 이미 있는 경우:

```bash
docker compose -f docker-compose.infra.yml up -d redis kafka
```

로컬 MySQL을 사용하지 않고 Docker MySQL을 쓰는 경우:

```env
MYSQL_PORT=3307
```

```bash
docker compose -f docker-compose.infra.yml up -d
```

전체 Docker 실행:

```bash
docker compose up -d
```

정산 서비스 포함:

```bash
docker compose --profile settlement up -d
```

## 로컬 MySQL DB 생성

로컬 MySQL을 사용할 경우 아래 DB를 생성한다.

```sql
CREATE DATABASE payflow_user;
CREATE DATABASE payflow_wallet;
CREATE DATABASE payflow_transfer;
CREATE DATABASE payflow_ledger;
CREATE DATABASE payflow_settlement;
```

계정:

```sql
CREATE USER 'payflow'@'%' IDENTIFIED BY 'payflow';
GRANT ALL PRIVILEGES ON payflow_user.* TO 'payflow'@'%';
GRANT ALL PRIVILEGES ON payflow_wallet.* TO 'payflow'@'%';
GRANT ALL PRIVILEGES ON payflow_transfer.* TO 'payflow'@'%';
GRANT ALL PRIVILEGES ON payflow_ledger.* TO 'payflow'@'%';
GRANT ALL PRIVILEGES ON payflow_settlement.* TO 'payflow'@'%';
FLUSH PRIVILEGES;
```

## EC2 배포 기준

기본 사양:

```text
EC2: t3.medium
RAM: 4GB
Storage: gp3 30GB 이상
OS: Ubuntu 22.04 or 24.04
```

기본 실행 서비스:

```text
nginx
api-gateway
user-service
wallet-service
transfer-service
ledger-service
mysql
redis
kafka
```

profile 실행:

```text
settlement-service
```

## 메모리 기준

기본 실행 제한 합산:

```text
약 3.5GB
```

주의:

```text
부하 테스트는 t3.medium에서 신뢰하기 어렵다.
정확한 성능 테스트는 t3.large 이상에서 수행한다.
```

## Swap 권장

EC2에는 2GB swap을 권장한다.

명령 예시:

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

영구 적용:

```bash
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## GitHub Actions 배포 방향

`.env`는 Git에 올리지 않는다.

GitHub Secrets:

```text
EC2_HOST
EC2_USER
EC2_SSH_KEY
MYSQL_ROOT_PASSWORD
MYSQL_USER
MYSQL_PASSWORD
JWT_SECRET
```

배포 흐름:

```text
1. main branch push
2. GitHub Actions에서 각 서비스 bootJar
3. Docker image build
4. EC2로 파일 전송 또는 git pull
5. EC2에서 .env 생성
6. docker compose up -d --build
7. health check
```

초기에는 단순 배포로 시작한다.

```bash
git pull
docker compose up -d --build
```

## Health Check

각 서비스:

```text
/actuator/health
```

확인 순서:

```text
mysql
redis
kafka
user-service
wallet-service
transfer-service
ledger-service
api-gateway
nginx
```

