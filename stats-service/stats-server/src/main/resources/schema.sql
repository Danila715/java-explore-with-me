-- Создание таблицы для хранения информации о запросах к эндпоинтам
CREATE TABLE IF NOT EXISTS endpoint_hits (
    id BIGSERIAL PRIMARY KEY,
    app VARCHAR(255) NOT NULL,
    uri VARCHAR(512) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    timestamp TIMESTAMP NOT NULL
);

-- Создание индексов для оптимизации запросов
CREATE INDEX IF NOT EXISTS idx_endpoint_hits_timestamp ON endpoint_hits(timestamp);
CREATE INDEX IF NOT EXISTS idx_endpoint_hits_uri ON endpoint_hits(uri);
CREATE INDEX IF NOT EXISTS idx_endpoint_hits_app ON endpoint_hits(app);
CREATE INDEX IF NOT EXISTS idx_endpoint_hits_ip ON endpoint_hits(ip);