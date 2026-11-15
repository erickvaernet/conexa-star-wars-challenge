-- Test users. This folder is read only when SEED_ENABLED is true.
--
-- There is one of each role: "demo", a common user, and "admin", an administrator. Their
-- passwords are published in the README; only their BCrypt hashes are here.
--
-- It is a repeatable migration (prefix R__), not a numbered one: test data is not a step in the
-- history of the schema, so it can be switched on at any point without getting in the way of the
-- numbered migrations. Flyway runs it again only if this file changes, and ON CONFLICT keeps a
-- user that already exists as it is, so it never duplicates. That is how "admin" reaches a
-- database that ran this file when it only had "demo": the file changed, so it runs again, adds
-- the user that is missing and leaves the other one alone.
INSERT INTO users (username, password_hash, role, created_at)
VALUES ('demo', '$2a$10$N15JnEJKP/fqwovzIsce3uPhi.R3Tl3vmOgQnHUJ5EnC7wXGCiRBa', 'USER', now())
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, password_hash, role, created_at)
VALUES ('admin', '$2a$10$90lbAHaAw36BJMJrf8fV9utneeSI.YOiOEOjJkeWFYuiXhZACgqXq', 'ADMIN', now())
ON CONFLICT (username) DO NOTHING;
