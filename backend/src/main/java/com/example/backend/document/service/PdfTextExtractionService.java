package com.example.backend.document.service;

import com.example.backend.document.dto.ExtractedPage;
import com.example.backend.document.entity.Document;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfTextExtractionService {

    private final DocumentStorageService documentStorageService;

    public List<ExtractedPage> extract(Document document) {

        try (InputStream inputStream =
                     documentStorageService.download(document.getStorageKey())) {

            byte[] pdfBytes = inputStream.readAllBytes();

            try (PDDocument pdfDocument = Loader.loadPDF(pdfBytes)) {

                PDFTextStripper textStripper = new PDFTextStripper();

                List<ExtractedPage> pages = new ArrayList<>();

                for (int pageNumber = 1;
                     pageNumber <= pdfDocument.getNumberOfPages();
                     pageNumber++) {

                    textStripper.setStartPage(pageNumber);
                    textStripper.setEndPage(pageNumber);

                    String text = textStripper.getText(pdfDocument).trim();

                    pages.add(
                            new ExtractedPage(
                                    pageNumber,
                                    text
                            )
                    );
                }

                return pages;
            }

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to extract text from PDF",
                    exception
            );
        }
    }
}