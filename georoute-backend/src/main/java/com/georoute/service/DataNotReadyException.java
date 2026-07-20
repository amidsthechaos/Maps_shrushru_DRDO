package com.georoute.service;

/** Thrown when road-network ingest is still running or not yet complete. */
public class DataNotReadyException extends RuntimeException {
    public DataNotReadyException(String message) {
        super(message);
    }
}
