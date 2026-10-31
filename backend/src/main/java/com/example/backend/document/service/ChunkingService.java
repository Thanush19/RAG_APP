package com.example.backend.document.service;

import com.example.backend.document.dto.ChunkData;
import com.example.backend.document.dto.ExtractedPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final ChunkingStrategy chunkingStrategy;

    public List<ChunkData> chunk(List<ExtractedPage> pages) {
        return chunkingStrategy.chunk(pages);
    }
}