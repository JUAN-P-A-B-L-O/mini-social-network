CREATE TABLE posts (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 5000 AND content ~ '[^[:space:]]'),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PUBLISHED', 'PENDING_MODERATION', 'REJECTED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Supports status filtering and deterministic newest-first feed pagination.
CREATE INDEX idx_posts_status_created_id ON posts (status, created_at DESC, id DESC);
