CREATE TABLE media (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL CHECK (length(trim(file_name)) > 0),
    content_type TEXT NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    size BIGINT NOT NULL CHECK (size > 0),
    bucket TEXT NOT NULL CHECK (length(bucket) > 0),
    object_key TEXT NOT NULL CHECK (length(object_key) > 0),
    status TEXT NOT NULL CHECK (status IN ('PENDING_UPLOAD', 'UPLOADED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= created_at),
    UNIQUE (bucket, object_key)
);
