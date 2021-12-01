package uk.ac.ed.inf.AStarPathFinder;

import uk.ac.ed.inf.LongLat;

import java.util.List;


/**
 * The result of AStarPathfinder#findPath. Encapsulates the distance and the path nodes to visit.
 */
public class PathfinderResult {
    public final double distance;  // total distance to travel through the waypoints
    // waypoints to go to after starting at start and before arriving at goal
    public final List<LongLat> waypoints;
    
    public PathfinderResult(double distance, List<LongLat> waypoints) {
        this.distance = distance;
        this.waypoints = waypoints;
    }
}
