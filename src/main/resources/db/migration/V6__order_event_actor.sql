-- Record which admin performed each order transition. Nullable: the creation
-- event (null -> pending) comes from the public checkout and has no admin.
ALTER TABLE order_event ADD COLUMN actor VARCHAR(80);
