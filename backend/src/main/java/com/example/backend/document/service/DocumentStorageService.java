package com.example.backend.document.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface DocumentStorageService {

    String upload(MultipartFile file);

    void delete(String storageKey);

    InputStream download(String storageKey);

}