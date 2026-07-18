-- Database-per-service: each microservice owns exactly one schema.
-- A single MySQL container is used to reduce local resource overhead;
-- in production these would be separate instances.
CREATE DATABASE IF NOT EXISTS auth_db      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS inventory_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS orders_db    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
