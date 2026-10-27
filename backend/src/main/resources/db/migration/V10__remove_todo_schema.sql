-- ============================================================
-- V10: Remove Todo-specific schema
-- Keep authentication tables:
--   users
--   refresh_tokens
--
-- RAG-specific tables will be introduced in later migrations.
-- ============================================================


-- ------------------------------------------------------------
-- 1. Remove task_tags first because it references tasks/tags
-- ------------------------------------------------------------

DROP TABLE IF EXISTS task_tags;


-- ------------------------------------------------------------
-- 2. Remove tags
-- ------------------------------------------------------------

DROP TABLE IF EXISTS tags;


-- ------------------------------------------------------------
-- 3. Remove tasks
--    This also removes:
--      - category_id
--      - parent_task_id
--      - task-related indexes/constraints
-- ------------------------------------------------------------

DROP TABLE IF EXISTS tasks;


-- ------------------------------------------------------------
-- 4. Remove categories
-- ------------------------------------------------------------

DROP TABLE IF EXISTS categories;


-- ------------------------------------------------------------
-- 5. Update application metadata
-- ------------------------------------------------------------

UPDATE schema_info
SET application_name = 'rag-backend';