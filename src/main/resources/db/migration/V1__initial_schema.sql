-- Second Lap initial schema.
-- Identity convention: internal BIGINT PK (id) + opaque public UUID (public_id).
-- FKs reference the internal BIGINT. Enum columns are stored as Spanish strings
-- and guarded by CHECK constraints so the database is the source of truth.

CREATE TABLE category (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id  UUID NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_category_public_id UNIQUE (public_id)
);

CREATE TABLE product (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID NOT NULL DEFAULT gen_random_uuid(),
    name         VARCHAR(200) NOT NULL,
    description  TEXT NOT NULL DEFAULT '',
    price        NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    category_id  BIGINT NOT NULL REFERENCES category(id),
    size         VARCHAR(40) NOT NULL,
    condition    VARCHAR(20) NOT NULL CHECK (condition IN ('Como nuevo','Excelente','Buen estado')),
    brand        VARCHAR(120),
    color        VARCHAR(60),
    measurements VARCHAR(200),
    status       VARCHAR(20) NOT NULL DEFAULT 'disponible'
                 CHECK (status IN ('disponible','apartado','vendido')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_public_id UNIQUE (public_id)
);
CREATE INDEX idx_product_category ON product (category_id);
CREATE INDEX idx_product_status ON product (status);

CREATE TABLE product_image (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    url        VARCHAR(500) NOT NULL,
    position   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_image_position UNIQUE (product_id, position)
);
CREATE INDEX idx_product_image_product ON product_image (product_id);

CREATE TABLE neighborhood (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    name      VARCHAR(120) NOT NULL,
    fee       NUMERIC(10,2) NOT NULL CHECK (fee >= 0),
    CONSTRAINT uq_neighborhood_public_id UNIQUE (public_id),
    CONSTRAINT uq_neighborhood_name UNIQUE (name)
);

-- whatsapp is the natural dedup key: 10 digits, enforced UNIQUE at the DB level.
CREATE TABLE customer (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id  UUID NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(160) NOT NULL,
    whatsapp   VARCHAR(10) NOT NULL CHECK (whatsapp ~ '^[0-9]{10}$'),
    comments   TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_public_id UNIQUE (public_id),
    CONSTRAINT uq_customer_whatsapp UNIQUE (whatsapp)
);

-- Address belongs to the order (orders are account-less and shipments are one-off).
CREATE TABLE address (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid(),
    street          VARCHAR(200) NOT NULL,
    between_streets VARCHAR(200),
    neighborhood    VARCHAR(120) NOT NULL,
    city            VARCHAR(120) NOT NULL,
    state           VARCHAR(120),
    postal_code     VARCHAR(10) NOT NULL,
    reference_notes VARCHAR(300),
    maps_link       VARCHAR(500),
    shipping_fee    NUMERIC(10,2),
    CONSTRAINT uq_address_public_id UNIQUE (public_id)
);

CREATE TABLE orders (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid(),
    customer_id     BIGINT NOT NULL REFERENCES customer(id),
    address_id      BIGINT REFERENCES address(id),
    delivery_method VARCHAR(20) NOT NULL CHECK (delivery_method IN ('recoger','domicilio','paqueteria')),
    payment_method  VARCHAR(20) NOT NULL CHECK (payment_method IN ('transferencia','efectivo','tarjeta')),
    subtotal        NUMERIC(10,2) NOT NULL CHECK (subtotal >= 0),
    shipping_fee    NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    total           NUMERIC(10,2) NOT NULL CHECK (total >= 0),
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','confirmed','cancelled')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_public_id UNIQUE (public_id),
    CONSTRAINT chk_order_address_delivery CHECK (
        (delivery_method = 'recoger' AND address_id IS NULL)
        OR (delivery_method IN ('domicilio','paqueteria') AND address_id IS NOT NULL)
    )
);
CREATE INDEX idx_order_customer ON orders (customer_id);
CREATE INDEX idx_order_status ON orders (status);

CREATE TABLE order_item (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id      UUID NOT NULL DEFAULT gen_random_uuid(),
    order_id       BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id     BIGINT NOT NULL REFERENCES product(id),
    price_snapshot NUMERIC(10,2) NOT NULL CHECK (price_snapshot >= 0),
    CONSTRAINT uq_order_item_public_id UNIQUE (public_id),
    CONSTRAINT uq_order_item_order_product UNIQUE (order_id, product_id)
);
CREATE INDEX idx_order_item_order ON order_item (order_id);
CREATE INDEX idx_order_item_product ON order_item (product_id);

CREATE TABLE admin_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID NOT NULL DEFAULT gen_random_uuid(),
    username      VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_admin_user_public_id UNIQUE (public_id),
    CONSTRAINT uq_admin_user_username UNIQUE (username)
);
