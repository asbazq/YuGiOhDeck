package com.card.Yugioh.service;

public class ImageFetchAlreadyRunningException extends IllegalStateException {
    public ImageFetchAlreadyRunningException() {
        super("Card image fetch is already running.");
    }
}
