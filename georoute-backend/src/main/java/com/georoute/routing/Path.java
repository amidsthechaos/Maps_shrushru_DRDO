package com.georoute.routing;

import com.georoute.model.RoadEdge;

import java.util.ArrayList;
import java.util.List;

/** An ordered path through the graph: node ids, the directed edges used, and total cost. */
public class Path {

    public final List<Long> nodes;
    public final List<RoadEdge> edges;
    public final double cost;

    public Path(List<Long> nodes, List<RoadEdge> edges, double cost) {
        this.nodes = nodes;
        this.edges = edges;
        this.cost = cost;
    }

    /** Total geometric length in metres (sum of edge lengths). */
    public double lengthMeters() {
        double m = 0;
        for (RoadEdge e : edges) {
            m += e.getLengthM();
        }
        return m;
    }

    /** A stable signature for de-duplicating candidate paths in Yen's algorithm. */
    public String signature() {
        StringBuilder sb = new StringBuilder();
        for (Long n : nodes) {
            sb.append(n).append('>');
        }
        return sb.toString();
    }

    /** Concatenate a root path with a spur path (the shared spur node is not duplicated). */
    public static Path concat(List<Long> rootNodes, List<RoadEdge> rootEdges,
                              Path spur) {
        List<Long> nodes = new ArrayList<>(rootNodes);
        List<RoadEdge> edges = new ArrayList<>(rootEdges);
        // spur.nodes[0] == rootNodes.last (the spur node) — skip it.
        for (int i = 1; i < spur.nodes.size(); i++) {
            nodes.add(spur.nodes.get(i));
        }
        edges.addAll(spur.edges);
        double cost = 0;
        for (RoadEdge e : edges) {
            cost += e.getCost();
        }
        return new Path(nodes, edges, cost);
    }
}
