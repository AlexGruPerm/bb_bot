-- Runs once on the fresh test container before Liquibase.
-- Mirrors dock/pg/initdb/2_initdb.sh: create the app schema and required extensions.
CREATE SCHEMA IF NOT EXISTS data AUTHORIZATION bb_user;

CREATE EXTENSION IF NOT EXISTS dblink;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
