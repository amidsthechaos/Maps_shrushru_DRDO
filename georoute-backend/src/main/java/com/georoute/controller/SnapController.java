package com.georoute.controller;

import com.georoute.dto.SnapError;
import com.georoute.dto.SnapRequest;
import com.georoute.model.SnapResult;
import com.georoute.service.DataNotReadyException;
import com.georoute.service.NoRoadNearbyException;
import com.georoute.service.RoadSnapService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/snap")
public class SnapController {

    private final RoadSnapService snapService;

    public SnapController(RoadSnapService snapService) {
        this.snapService = snapService;
    }

    @PostMapping
    public ResponseEntity<?> snap(@RequestBody SnapRequest request) {
        try {
            SnapResult result = snapService.snap(request.getLat(), request.getLng());
            return ResponseEntity.ok(result);
        } catch (NoRoadNearbyException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new SnapError("NO_ROAD_NEARBY", e.getMessage()));
        } catch (DataNotReadyException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SnapError("DATA_NOT_READY", e.getMessage()));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new SnapError("SNAP_TIMEOUT",
                            "Snap query timed out — try again, or use a smaller road extract"));
        }
    }
}
