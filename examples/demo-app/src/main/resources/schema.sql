-- Sample data so the demo runs without any external database.
-- H2 is started in memory; see application.yml for the connection settings.

CREATE TABLE orders (
    id           BIGINT PRIMARY KEY,
    customer_id  BIGINT       NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    total_amount DECIMAL(18, 2) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO orders (id, customer_id, status, total_amount) VALUES
    (1001, 501, 'PAID',     249.90),
    (1002, 502, 'PAID',      89.00),
    (1003, 503, 'PENDING',  129.50),
    (1004, 501, 'CANCELLED', 45.00),
    (1005, 504, 'PAID',     760.25);
