-- Align order status values with the rest of the domain, which stores enum
-- values in Spanish (product status, delivery/payment methods). Translates the
-- stored strings in orders.status and the order_event timeline, and swaps the
-- CHECK constraints and column default accordingly.

ALTER TABLE orders DROP CONSTRAINT orders_status_check;
ALTER TABLE order_event DROP CONSTRAINT order_event_from_status_check;
ALTER TABLE order_event DROP CONSTRAINT order_event_to_status_check;
ALTER TABLE orders ALTER COLUMN status DROP DEFAULT;

UPDATE orders SET status = CASE status
    WHEN 'pending'   THEN 'pendiente'
    WHEN 'confirmed' THEN 'confirmado'
    WHEN 'completed' THEN 'completado'
    WHEN 'delivered' THEN 'entregado'
    WHEN 'cancelled' THEN 'cancelado'
END;

UPDATE order_event SET from_status = CASE from_status
    WHEN 'pending'   THEN 'pendiente'
    WHEN 'confirmed' THEN 'confirmado'
    WHEN 'completed' THEN 'completado'
    WHEN 'delivered' THEN 'entregado'
    WHEN 'cancelled' THEN 'cancelado'
END
WHERE from_status IS NOT NULL;

UPDATE order_event SET to_status = CASE to_status
    WHEN 'pending'   THEN 'pendiente'
    WHEN 'confirmed' THEN 'confirmado'
    WHEN 'completed' THEN 'completado'
    WHEN 'delivered' THEN 'entregado'
    WHEN 'cancelled' THEN 'cancelado'
END;

ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'pendiente';
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('pendiente','confirmado','completado','entregado','cancelado'));
ALTER TABLE order_event ADD CONSTRAINT order_event_from_status_check
    CHECK (from_status IN ('pendiente','confirmado','completado','entregado','cancelado'));
ALTER TABLE order_event ADD CONSTRAINT order_event_to_status_check
    CHECK (to_status IN ('pendiente','confirmado','completado','entregado','cancelado'));
