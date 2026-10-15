-- Development seed data.
-- Admin password is the well-known dev value 'admin123' (BCrypt hash below);
-- rotate it per environment via the admin management flow. Never a real secret.
INSERT INTO admin_user (username, password_hash) VALUES
    ('admin', '$2a$10$7PSrjH2i1tBNKRXER7PFpePgSBhhct/IOL9W/jUh9hWTAqwe4dgBe');

-- Categories and neighborhoods below are placeholders pending the real frontend
-- dataset (6 categories, 165 neighborhoods). Replace in a follow-up migration
-- once the production-ready INSERTs are provided.
INSERT INTO category (name, sort_order) VALUES
    ('Blusas', 1),
    ('Vestidos', 2),
    ('Pantalones', 3),
    ('Faldas', 4),
    ('Abrigos', 5),
    ('Accesorios', 6);

INSERT INTO neighborhood (name, fee) VALUES
    ('Centro', 50.00);
