package com.georoute.service;

import org.springframework.stereotype.Component;

/** Tracks background Shapefile → PostGIS ingest so API calls can fail fast while loading. */
@Component
public class DataLoadStatus {

    private volatile boolean loading;
    private volatile boolean ready;
    private volatile String message = "Road data not loaded yet";

    public boolean isLoading() {
        return loading;
    }

    public boolean isReady() {
        return ready;
    }

    public String getMessage() {
        return message;
    }

    public void startLoading() {
        loading = true;
        ready = false;
        message = "Loading road network into PostGIS…";
    }

    public void finishLoading(int nodes, int edges) {
        loading = false;
        ready = true;
        message = "Road network ready (" + nodes + " nodes, " + edges + " edges)";
    }

    public void finishSkipped() {
        loading = false;
        ready = true;
        message = "Road network already loaded";
    }

    public void failLoading(String error) {
        loading = false;
        ready = false;
        message = "Road load failed: " + error;
    }

    public void failMissingShapefile(String path) {
        loading = false;
        ready = false;
        message = "Shapefile not found: " + path;
    }
}
