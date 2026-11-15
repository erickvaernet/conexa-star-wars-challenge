-- Users that can log in. It is the only table: the Star Wars data is not stored.
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    -- BCrypt hash of the password. The password itself is never stored.
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
