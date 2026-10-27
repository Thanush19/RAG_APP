package com.example.backend.document.service;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorageService {

    String upload(MultipartFile file);

    void delete(String storageKey);
}