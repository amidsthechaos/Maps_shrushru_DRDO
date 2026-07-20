package com.georoute.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A directed traversal of a road segment in the in-memory graph.
 *
 * <p>The underlying DB row is undirected (a {@code LineString} from its source node to its
 * target node). When the segment is two-way it is expanded into two {@code RoadEdge}
 * entries — one in each direction — so the routing engines only ever follow
 * {@code sourceId -> targetId}. {@code reversed} records whether this directed edge runs
 * against the stored geometry, so the rendered polyline can be flipped accordingly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoadEdge {
    /** Primary key of the originating row in {@code road_edges}. */
    private long id;
    private long sourceId;
    private long targetId;
    private String roadName;
    private String roadType;
    private double lengthM;
    private double speedKmh;
    private boolean oneway;
    /** Traversal cost (seconds): {@code lengthM / (speedKmh in m/s)}. */
    private double cost;
    /** True when this directed edge runs opposite to the stored geometry orientation. */
    private boolean reversed;
    /** Optional geometry vertices in travel direction, each {@code [lng, lat]}. */
    private List<double[]> coordinates;

    public RoadEdge(long id, long sourceId, long targetId, String roadName, String roadType,
                    double lengthM, double speedKmh, boolean oneway, double cost, boolean reversed) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.roadName = roadName;
        this.roadType = roadType;
        this.lengthM = lengthM;
        this.speedKmh = speedKmh;
        this.oneway = oneway;
        this.cost = cost;
        this.reversed = reversed;
    }
}
