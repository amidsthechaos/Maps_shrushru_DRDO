package com.georoute.controller;

import com.georoute.dto.GeoJson;
import com.georoute.dto.RouteRequest;
import com.georoute.dto.SnapError;
import com.georoute.model.RouteResult;
import com.georoute.model.SnapResult;
import com.georoute.routing.Algorithm;
import com.georoute.service.DataNotReadyException;
import com.georoute.service.NoRoadNearbyException;
import com.georoute.service.RoadSnapService;
import com.georoute.service.RoutingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
//for error log. 
@Slf4j
@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final RoutingService routingService;
    private final RoadSnapService snapService;

    public RouteController(RoutingService routingService, RoadSnapService snapService) {
        this.routingService = routingService;
        this.snapService = snapService;
    }

    @PostMapping
    public ResponseEntity<?> route(@RequestBody RouteRequest request) {
        try {
            SnapResult source = snapService.snap(request.getSourceLat(), request.getSourceLng());
            SnapResult dest = snapService.snap(request.getDestLat(), request.getDestLng());

            Algorithm algorithm = Algorithm.fromString(request.getAlgorithm());
            List<RouteResult> routes = routingService.route(
                    source.getNodeId(), dest.getNodeId(), request.getNumAlternatePaths(), algorithm);

            return ResponseEntity.ok(GeoJson.fromRoutes(routes));
        } catch (NoRoadNearbyException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new SnapError("NO_ROAD_NEARBY", e.getMessage()));
        } catch (DataNotReadyException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SnapError("DATA_NOT_READY", e.getMessage()));
        } catch (DataAccessException e) {
            log.error("Route DB error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new SnapError("ROUTE_DB_ERROR",
                            "Database query failed or timed out during routing"));
        } catch (Exception e) {
            log.error("Route failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new SnapError("ROUTE_FAILED", e.getMessage()));
        }
    }
}
