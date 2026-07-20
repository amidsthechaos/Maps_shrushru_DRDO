package com.georoute.routing;

import java.util.Locale;

/** Selectable shortest-path engine. */
public enum Algorithm {
    ASTAR,
    DIJKSTRA;

    public static Algorithm fromString(String s) {
        if (s == null) {
            return ASTAR;
        }
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "dijkstra" -> DIJKSTRA;
            case "astar", "a*", "a-star" -> ASTAR;
            default -> ASTAR;
        };
    }
}
