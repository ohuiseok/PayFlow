# Interview Q&A

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

PayFlow를 면접에서 설명할 때 사용할 수 있는 질문/답변 정리입니다.

## Q. 이 프로젝트를 한 문장으로 설명해보세요.

PayFlow는 기관 담당자가 청년 참여자에게 미션을 주고, 완료 승인 후 기관 지갑에서 청년 지갑으로 지원금을 지급하는 MSA 기반 지갑/결제 시스템입니다. 핵심은 화면 기능보다 잔액 정합성, 멱등성, 송금 실패 복구, 이벤트 기반 원장 기록을 구현한 점입니다.

## Q. 왜 MSA로 나눴나요?

돈의 이동을 다루는 흐름에서 책임을 명확히 보여주기 위해서입니다. user-service는 사용자, wallet-service는 잔액, transfer-service는 송금 상태, reward-service는 미션, ledger-service는 원장을 담당합니다. 특히 wallet-service를 잔액의 단일 진실 공급원으로 둬서 다른 서비스가 직접 잔액을 수정하지 않도록 했습니다.

## Q. 서비스별 DB를 나누면 join이 불편하지 않나요?

불편합니다. 대신 그 불편함이 서비스 경계를 강제합니다. 다른 서비스의 데이터를 직접 join하지 않고 필요한 정보는 API 호출이나 이벤트로 가져옵니다. 포트폴리오에서는 이 제약을 통해 MSA에서 데이터 소유권을 어떻게 다루는지 보여주고 싶었습니다.

## Q. 멱등성은 어떻게 처리했나요?

두 단계로 처리했습니다.

첫째, API 요청 단계에서는 `Idempotency-Key`와 `requestHash`를 저장합니다. 같은 key와 같은 body면 기존 결과를 반환하고, 같은 key에 다른 body면 충돌로 처리합니다.

둘째, 지갑 반영 단계에서는 `referenceType` + `referenceId`로 같은 원천 거래가 지갑에 두 번 반영되지 않게 했습니다. API 멱등성과 잔액 반영 멱등성을 분리한 것이 핵심입니다.

## Q. 송금 중 장애가 나면 어떻게 되나요?

출금 전 장애라면 송금을 `FAILED`로 종료합니다. 출금은 성공했는데 입금이 실패한 경우는 이미 돈이 움직였기 때문에 단순 실패로 끝내지 않고 `COMPENSATION_REQUIRED` 상태로 격리합니다. 이후 refund API가 sender wallet에 보상 입금을 수행하고 성공하면 `COMPENSATED`로 전환합니다.

## Q. Redis lock은 왜 사용했나요?

같은 sender wallet에 대해 동시에 여러 송금이 들어오면 출금 순서와 잔액 검증이 경쟁할 수 있습니다. wallet-service의 DB transaction과 row lock이 최종 방어선이고, transfer-service에서는 sender wallet 단위 Redis lock으로 동시 송금 조율 비용을 줄였습니다.

## Q. Kafka를 어디에 사용했나요?

송금 완료/실패 이벤트를 transfer-service에서 발행하고 ledger-service가 소비합니다. 송금 실행 자체는 wallet-service와 동기 HTTP로 처리하고, 원장 기록과 실패 추적은 Kafka 이벤트로 비동기 처리했습니다.

## Q. 왜 transactional outbox를 사용했나요?

송금 상태를 DB에 성공으로 저장한 뒤 Kafka 발행이 실패하면 ledger-service가 원장 기록을 만들 수 없습니다. 그래서 transfer 상태 변경과 outbox event 저장을 같은 DB 트랜잭션에 묶었습니다. 이후 relay가 outbox를 읽어 Kafka로 발행하고, 실패하면 retry 상태로 남깁니다.

## Q. 이벤트가 중복 소비되면 원장이 중복되지 않나요?

ledger-service는 transferId 또는 source key 기준으로 중복 처리를 합니다. Kafka는 at-least-once 전달 가능성이 있으므로 consumer 쪽에서도 idempotent하게 처리해야 합니다.

## Q. Open Banking 연동에서 가장 중요하게 본 점은 무엇인가요?

HTTP 응답 성공과 금융 거래 성공을 분리한 점입니다. 은행 API가 timeout이 나거나 처리 중 상태를 반환할 수 있기 때문에 이를 바로 실패로 처리하지 않고 `UNKNOWN`, `BANK_PROCESSING` 같은 상태로 저장한 뒤 result-check로 최종화합니다.

## Q. 민감 정보는 어떻게 처리했나요?

Open Banking token은 암호화해 저장하고, 원본 계좌번호는 저장하지 않는 방향으로 설계했습니다. API log도 raw request/response payload 대신 요청 ID, 응답 코드, key 목록처럼 운영 추적에 필요한 메타데이터만 남기는 방향으로 정리했습니다.

## Q. 이 프로젝트의 한계는 무엇인가요?

운영 수준으로 보려면 아직 보완할 것이 있습니다. DB migration 도구, Testcontainers 기반 통합 테스트, DLQ, metrics/alert, token refresh, 배포 전략, 더 정교한 외부 HTTP retry/timeout 정책이 필요합니다. 현재는 포트폴리오 MVP로 핵심 설계 의도를 보여주는 데 초점을 맞췄습니다.

## Q. 다시 만든다면 무엇을 개선하겠나요?

초기부터 Flyway/Liquibase를 도입하고, Kafka/Redis/MySQL을 Testcontainers로 묶은 통합 테스트를 먼저 만들겠습니다. 또한 outbox lag, compensation count, wallet duplicate reference count 같은 운영 지표를 actuator metrics로 노출해 장애를 더 빨리 파악할 수 있게 하겠습니다.

