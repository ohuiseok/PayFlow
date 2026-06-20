-- [H-1] reward-service 초기 스키마
-- ParentChildLink 엔티티와 RewardTask 엔티티를 기반으로 한 마이그레이션이다.

CREATE TABLE IF NOT EXISTS parent_child_links (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    parent_user_id  BIGINT      NOT NULL,
    child_user_id   BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_parent_child_links_pair UNIQUE (parent_user_id, child_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_parent_child_links_parent ON parent_child_links (parent_user_id, status);
CREATE INDEX idx_parent_child_links_child  ON parent_child_links (child_user_id, status);

CREATE TABLE IF NOT EXISTS reward_tasks (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    parent_user_id   BIGINT          NOT NULL,
    child_user_id    BIGINT          NOT NULL,
    title            VARCHAR(120)    NOT NULL,
    description      VARCHAR(1000)   NOT NULL,
    reward_amount    DECIMAL(19, 0)  NOT NULL,
    status           VARCHAR(20)     NOT NULL,
    submission_note  VARCHAR(1000),
    reject_reason    VARCHAR(500),
    transfer_id      BIGINT,
    failure_reason   VARCHAR(500),
    created_at       DATETIME(6)     NOT NULL,
    updated_at       DATETIME(6)     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_reward_tasks_parent_status ON reward_tasks (parent_user_id, status);
CREATE INDEX idx_reward_tasks_child_status  ON reward_tasks (child_user_id, status);
