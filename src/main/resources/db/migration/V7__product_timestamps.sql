-- Product sortability for the admin panel. created_at already exists (V1);
-- add a nullable modified_date stamped on every update. Existing rows keep
-- modified_date NULL (never modified since this column was introduced).
ALTER TABLE product ADD COLUMN modified_date TIMESTAMPTZ;
