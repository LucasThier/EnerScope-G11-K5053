-- V1: users table
CREATE TABLE app_user (
    id            UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active        BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    mail          VARCHAR(320)             NOT NULL    UNIQUE,
    first_name    VARCHAR(60)              NOT NULL,
    last_name     VARCHAR(60)              NOT NULL,
    password_hash VARCHAR(255)             NOT NULL
);

CREATE INDEX idx_app_user_mail ON app_user (LOWER(mail));
