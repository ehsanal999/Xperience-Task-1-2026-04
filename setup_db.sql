-- === For psql CLI (run: psql -U postgres -f setup_db.sql) ===
CREATE DATABASE hero;
\c hero
CREATE SCHEMA hero;

-- === For pgAdmin's Query Tool (\c does NOT work there) ===
-- Step A: with Query Tool open against the "postgres" (default) database, run:
--   CREATE DATABASE hero;
-- Step B: right-click the new "hero" database in the left tree -> Query Tool
--         (this opens a NEW query tool connected to "hero"), then run:
--   CREATE SCHEMA hero;
