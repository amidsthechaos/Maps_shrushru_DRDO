export interface RouteResult {
  routeIndex: number;
  label: string; // "Fastest Route" | "Alternate 1" | ...
  totalDistanceMeters: number;
  estimatedTimeMinutes: number;
  coordinates: [number, number][]; // [[lng, lat], ...] GeoJSON order
}

export interface RouteResponse {
  type: 'FeatureCollection';
  features: GeoJsonFeature[];
}

export interface GeoJsonFeature {
  type: 'Feature';
  properties: {
    routeIndex: number;
    label: string;
    totalDistanceMeters: number;
    estimatedTimeMinutes: number;
  };
  geometry: {
    type: 'LineString';
    coordinates: [number, number][];
  };
}

export type RoutingAlgorithm = 'astar' | 'dijkstra';
