package com.example.backend.document.service;

import com.example.backend.document.dto.ChunkData;
import com.example.backend.document.dto.ExtractedPage;

import java.util.List;

public interface ChunkingStrategy {

    List<ChunkData> chunk(List<ExtractedPage> pages);
}