export interface SnapResult {
  snappedLat: number;
  snappedLng: number;
  nodeId: number;
  nearestRoadName: string;
  distanceToRoadM: number;
}

export interface SnapError {
  error: 'NO_ROAD_NEARBY';
  message: string;
}
