CREATE TABLE document_chunks (
                                 id UUID PRIMARY KEY,

                                 document_id UUID NOT NULL,

                                 chunk_index INTEGER NOT NULL,

                                 page_number INTEGER,

                                 text TEXT NOT NULL,

                                 qdrant_point_id UUID,

                                 created_at TIMESTAMPTZ NOT NULL,

                                 CONSTRAINT fk_document_chunks_document
                                     FOREIGN KEY (document_id)
                                         REFERENCES documents(id)
                                         ON DELETE CASCADE,

                                 CONSTRAINT uk_document_chunks_document_index
                                     UNIQUE (document_id, chunk_index),

                                 CONSTRAINT chk_document_chunks_chunk_index
                                     CHECK (chunk_index >= 0),

                                 CONSTRAINT chk_document_chunks_page_number
                                     CHECK (page_number IS NULL OR page_number > 0)
);

CREATE INDEX idx_document_chunks_document_id
    ON document_chunks(document_id);