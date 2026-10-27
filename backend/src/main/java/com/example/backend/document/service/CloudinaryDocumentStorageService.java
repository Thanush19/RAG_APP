package com.example.backend.document.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudinaryDocumentStorageService implements DocumentStorageService {

    private final Cloudinary cloudinary;

    @Override
    public String upload(MultipartFile file) {

        try {
            String storageKey = "documents/" + UUID.randomUUID();

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "raw",
                            "public_id", storageKey
                    )
            );

            return (String) result.get("public_id");

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to upload document to storage",
                    exception
            );
        }
    }

    @Override
    public void delete(String storageKey) {

        try {
            cloudinary.uploader().destroy(
                    storageKey,
                    ObjectUtils.asMap(
                            "resource_type", "raw"
                    )
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to delete document from storage",
                    exception
            );
        }
    }

    @Override
    public InputStream download(String storageKey) {

        try {
            String downloadUrl = cloudinary.url()
                    .resourceType("raw")
                    .secure(true)
                    .generate(storageKey);

            return new URL(downloadUrl).openStream();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to download document from storage",
                    exception
            );
        }
    }
}