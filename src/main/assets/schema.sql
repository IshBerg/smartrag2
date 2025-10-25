-- SmartRAG v2 - Database Schema
-- Version: 2.0.0
-- Date: 2025-10-26
-- Author: YAKKI SMART Team

-- Основная таблица контента
CREATE TABLE IF NOT EXISTS content (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,                -- 'sms', 'email', 'document', etc.
    source TEXT,                        -- Источник данных
    original_text TEXT NOT NULL,        -- Оригинальный текст
    processed_text TEXT,                -- Обработанный текст
    summary TEXT,                       -- Краткое содержание
    language TEXT,                      -- Язык контента
    created_at INTEGER NOT NULL,        -- Unix timestamp создания
    imported_at INTEGER NOT NULL,       -- Unix timestamp импорта
    metadata TEXT,                      -- JSON метаданные
    embedding_id INTEGER,               -- ID вектора в Rust/SQLite
    importance REAL DEFAULT 1.0,        -- Важность документа
    UNIQUE(type, source, created_at)
);

-- Полнотекстовый поиск FTS5
CREATE VIRTUAL TABLE IF NOT EXISTS fts_content USING fts5(
    title,
    content,
    tags,
    content_id UNINDEXED,
    tokenize = 'porter unicode61'
);

-- Узлы графа (сущности)
CREATE TABLE IF NOT EXISTS graph_nodes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    node_type TEXT NOT NULL,            -- 'person', 'project', 'location', etc.
    name TEXT NOT NULL,                 -- Имя сущности
    canonical_name TEXT,                -- Нормализованное имя
    description TEXT,                   -- Описание
    properties TEXT,                    -- JSON свойства
    first_seen INTEGER,                 -- Первое упоминание
    last_seen INTEGER,                  -- Последнее упоминание
    mention_count INTEGER DEFAULT 1,
    importance REAL DEFAULT 1.0,
    UNIQUE(node_type, canonical_name)
);

-- Рёбра графа (связи)
CREATE TABLE IF NOT EXISTS graph_edges (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    from_node_id INTEGER NOT NULL,
    to_node_id INTEGER NOT NULL,
    edge_type TEXT NOT NULL,            -- 'mentions', 'manages', 'located_in', etc.
    weight REAL DEFAULT 1.0,            -- Вес связи
    properties TEXT,                    -- JSON свойства
    content_id INTEGER,                 -- Ссылка на контент
    created_at INTEGER,
    FOREIGN KEY(from_node_id) REFERENCES graph_nodes(id),
    FOREIGN KEY(to_node_id) REFERENCES graph_nodes(id),
    FOREIGN KEY(content_id) REFERENCES content(id)
);

-- Связь контента с узлами
CREATE TABLE IF NOT EXISTS content_nodes (
    content_id INTEGER NOT NULL,
    node_id INTEGER NOT NULL,
    relevance REAL DEFAULT 1.0,         -- Релевантность узла к контенту
    position INTEGER,                   -- Позиция в тексте
    context TEXT,                       -- Контекст упоминания
    PRIMARY KEY(content_id, node_id),
    FOREIGN KEY(content_id) REFERENCES content(id),
    FOREIGN KEY(node_id) REFERENCES graph_nodes(id)
);

-- Кэш частых запросов
CREATE TABLE IF NOT EXISTS search_cache (
    query_hash TEXT PRIMARY KEY,
    query_text TEXT,
    result_ids TEXT,                    -- JSON массив ID результатов
    created_at INTEGER,
    hit_count INTEGER DEFAULT 1
);

-- Таблица для векторов (fallback если Rust не доступен)
CREATE TABLE IF NOT EXISTS vectors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content_id INTEGER NOT NULL,
    vector BLOB NOT NULL,               -- Сериализованный вектор
    dimensions INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(content_id) REFERENCES content(id),
    UNIQUE(content_id)
);

-- Индексы для производительности
CREATE INDEX IF NOT EXISTS idx_content_type ON content(type);
CREATE INDEX IF NOT EXISTS idx_content_created ON content(created_at);
CREATE INDEX IF NOT EXISTS idx_content_importance ON content(importance);
CREATE INDEX IF NOT EXISTS idx_content_embedding ON content(embedding_id);

CREATE INDEX IF NOT EXISTS idx_nodes_type ON graph_nodes(node_type);
CREATE INDEX IF NOT EXISTS idx_nodes_canonical ON graph_nodes(canonical_name);
CREATE INDEX IF NOT EXISTS idx_nodes_importance ON graph_nodes(importance);

CREATE INDEX IF NOT EXISTS idx_edges_from ON graph_edges(from_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_to ON graph_edges(to_node_id);
CREATE INDEX IF NOT EXISTS idx_edges_type ON graph_edges(edge_type);

CREATE INDEX IF NOT EXISTS idx_content_nodes_content ON content_nodes(content_id);
CREATE INDEX IF NOT EXISTS idx_content_nodes_node ON content_nodes(node_id);

CREATE INDEX IF NOT EXISTS idx_vectors_content ON vectors(content_id);

-- Триггер для автообновления статистики узлов
CREATE TRIGGER IF NOT EXISTS update_node_stats
AFTER INSERT ON content_nodes
BEGIN
    UPDATE graph_nodes
    SET
        mention_count = mention_count + 1,
        last_seen = strftime('%s', 'now')
    WHERE id = NEW.node_id;
END;

-- Представления для удобства
CREATE VIEW IF NOT EXISTS important_nodes AS
SELECT * FROM graph_nodes
WHERE importance > 0.7 OR mention_count > 5
ORDER BY importance DESC, mention_count DESC;

CREATE VIEW IF NOT EXISTS recent_content AS
SELECT * FROM content
WHERE imported_at > strftime('%s', 'now', '-7 days')
ORDER BY created_at DESC;
