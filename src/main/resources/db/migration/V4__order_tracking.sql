-- Order tracking (Phase 2): delivery milestones and an auditable status timeline.
-- Extends the order status flow to pending -> confirmed -> completed -> delivered,
-- with cancelled as a terminal branch from pending.

ALTER TABLE orders
    ADD COLUMN confirmed_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN delivered_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE orders DROP CONSTRAINT orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('pending','confirmed','completed','delivered','cancelled'));

-- Append-only log: one row per status change, oldest first by occurred_at.
-- from_status is null only for the creation event (null -> pending).
CREATE TABLE order_event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID NOT NULL DEFAULT gen_random_uuid(),
    order_id    BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(20)
                CHECK (from_status IN ('pending','confirmed','completed','delivered','cancelled')),
    to_status   VARCHAR(20) NOT NULL
                CHECK (to_status IN ('pending','confirmed','completed','delivered','cancelled')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_event_public_id UNIQUE (public_id)
);
CREATE INDEX idx_order_event_order ON order_event (order_id);

-- Backfill the creation event for orders that predate this table so their
-- timeline is never empty.
INSERT INTO order_event (order_id, from_status, to_status, occurred_at)
SELECT id, NULL, 'pending', created_at FROM orders;
