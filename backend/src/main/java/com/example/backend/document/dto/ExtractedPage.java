package com.example.backend.document.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExtractedPage {

    private int pageNumber;
    private String text;
}