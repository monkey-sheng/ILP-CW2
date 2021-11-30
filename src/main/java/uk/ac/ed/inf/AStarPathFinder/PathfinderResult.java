package uk.ac.ed.inf.AStarPathFinder;

import uk.ac.ed.inf.LongLat;

import java.util.List;

public class PathfinderResult {
    public double distance;  // total distance to travel through the waypoints
    // waypoints to go to after starting at start and before arriving at goal
    public List<LongLat> waypoints;
    
    public PathfinderResult(double distance, List<LongLat> waypoints) {
        this.distance = distance;
        this.waypoints = waypoints;
    }
}
