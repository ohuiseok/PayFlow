# 15. Local And Deploy

로컬 환경은 MVP 서비스를 빠르게 실행하고 테스트하는 데 집중한다.

## Local Services

MySQL

api-gateway

user-service

wallet-service

banking-service

transfer-service

reward-service

ledger-service

nginx

## Docker Compose

기본 실행:

```bash
docker compose up -d
```

종료:

```bash
docker compose down
```

로그 확인:

```bash
docker compose logs -f api-gateway
```

## Databases

필요한 database:

`payflow_user`

`payflow_wallet`

`payflow_banking`

`payflow_transfer`

`payflow_reward`

`payflow_ledger`

## Environment Variables

`SPRING_DATASOURCE_URL`

`SPRING_DATASOURCE_USERNAME`

`SPRING_DATASOURCE_PASSWORD`

`JWT_SECRET`

`SERVICE_BASE_URL`

## Health Check

각 서비스는 `/actuator/health`를 제공한다.

nginx는 api-gateway로 요청을 전달한다.

## Deploy Notes

포트폴리오 제출용 배포는 단일 VM 또는 로컬 Docker Compose로 충분하다.

README에는 실행 순서, 테스트 명령, 주요 API 호출 예시를 포함한다.
