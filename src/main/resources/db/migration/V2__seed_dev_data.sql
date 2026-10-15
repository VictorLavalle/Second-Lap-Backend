-- Development seed: admin account only.
-- Password is the well-known dev value 'admin123' (BCrypt hash below); rotate it
-- per environment via the admin management flow. Never a real secret.
-- Catalog data (categories, neighborhoods, products) lives in V3.
INSERT INTO admin_user (username, password_hash) VALUES
    ('admin', '$2a$10$7PSrjH2i1tBNKRXER7PFpePgSBhhct/IOL9W/jUh9hWTAqwe4dgBe');
