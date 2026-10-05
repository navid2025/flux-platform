CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT,
    status VARCHAR(32),
    total_amount DECIMAL(12, 2)
);

INSERT INTO orders (id, customer_id, status, total_amount) VALUES (1, 10, 'PAID', 120.00);
INSERT INTO orders (id, customer_id, status, total_amount) VALUES (2, 11, 'PAID', 80.50);
INSERT INTO orders (id, customer_id, status, total_amount) VALUES (3, 12, 'NEW', 15.00);
