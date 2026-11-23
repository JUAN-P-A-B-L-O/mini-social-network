ALTER TABLE posts
    ADD COLUMN media_id UUID,
    ADD CONSTRAINT posts_media_id_unique UNIQUE (media_id),
    DROP CONSTRAINT posts_status_check,
    ADD CONSTRAINT posts_status_check
        CHECK (status IN ('PUBLISHED', 'PENDING_MODERATION', 'REJECTED', 'REVIEW_REQUIRED', 'APPROVED'));

DROP INDEX idx_posts_status_created_id;
CREATE INDEX idx_posts_visible_created_id ON posts (created_at DESC, id DESC)
    WHERE status IN ('PUBLISHED', 'APPROVED');

CREATE TABLE image_moderation_outbox (
    event_id UUID PRIMARY KEY,
    post_id UUID NOT NULL UNIQUE REFERENCES posts (id),
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL CHECK (attempts >= 0),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_image_moderation_outbox_due ON image_moderation_outbox (next_attempt_at, event_id)
    WHERE published_at IS NULL;
