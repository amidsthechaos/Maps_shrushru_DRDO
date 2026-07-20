package com.georoute.service;

/** Thrown when no road exists within the snap search radius of a clicked point. */
public class NoRoadNearbyException extends RuntimeException {
    public NoRoadNearbyException(String message) {
        super(message);
    }
}
