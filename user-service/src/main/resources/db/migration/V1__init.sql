-- [H-1] user-service 초기 스키마
-- ddl-auto=update에서 Flyway validate로 전환하기 위한 마이그레이션이다.
-- 이 파일은 User 엔티티의 @Entity/@Column 정의를 그대로 DDL로 변환한 것이다.

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    phone_number VARCHAR(20)    NOT NULL,
    password    VARCHAR(255)    NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    role        VARCHAR(30)     NOT NULL,
    status      VARCHAR(30)     NOT NULL,
    created_at  DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_phone_number UNIQUE (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
