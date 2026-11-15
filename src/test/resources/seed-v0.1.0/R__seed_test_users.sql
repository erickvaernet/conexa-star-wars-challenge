-- Test users. This folder is read only when SEED_ENABLED is true.
--
-- The password of "demo" is published in the README; only its BCrypt hash is here.
--
-- It is a repeatable migration (prefix R__), not a numbered one: test data is not a step in the
-- history of the schema, so it can be switched on at any point without getting in the way of the
-- numbered migrations. Flyway runs it again only if this file changes, and ON CONFLICT keeps a
-- user that already exists as it is, so it never duplicates.
INSERT INTO users (username, password_hash, role, created_at)
VALUES ('demo', '$2a$10$N15JnEJKP/fqwovzIsce3uPhi.R3Tl3vmOgQnHUJ5EnC7wXGCiRBa', 'USER', now())
ON CONFLICT (username) DO NOTHING;
