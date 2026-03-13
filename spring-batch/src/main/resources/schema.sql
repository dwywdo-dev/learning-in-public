CREATE TABLE IF NOT EXISTS accounts (
    account_id  BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    balance     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
