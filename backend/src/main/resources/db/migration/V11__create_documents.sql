CREATE TABLE documents (
                           id UUID PRIMARY KEY,
                           user_id UUID NOT NULL,

                           file_name VARCHAR(255) NOT NULL,
                           file_type VARCHAR(100) NOT NULL,
                           file_size BIGINT NOT NULL,

                           status VARCHAR(20) NOT NULL,

                           created_at TIMESTAMPTZ NOT NULL,
                           updated_at TIMESTAMPTZ NOT NULL,

                           CONSTRAINT fk_documents_user
                               FOREIGN KEY (user_id)
                                   REFERENCES users(id)
                                   ON DELETE CASCADE,

                           CONSTRAINT chk_documents_status
                               CHECK (status IN (
                                                 'UPLOADED',
                                                 'PROCESSING',
                                                 'READY',
                                                 'FAILED'
                                   ))
);

CREATE INDEX idx_documents_user_id
    ON documents(user_id);

CREATE INDEX idx_documents_user_id_created_at
    ON documents(user_id, created_at);

CREATE INDEX idx_documents_status
    ON documents(status);