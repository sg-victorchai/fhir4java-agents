-- Schema initialization script for PostgreSQL TestContainers
-- Creates required schemas before Flyway migrations run

CREATE SCHEMA IF NOT EXISTS fhir;
CREATE SCHEMA IF NOT EXISTS careplan;
