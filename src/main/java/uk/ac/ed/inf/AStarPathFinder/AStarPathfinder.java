package uk.ac.ed.inf.AStarPathFinder;


import uk.ac.ed.inf.GeojsonManager;
import uk.ac.ed.inf.GeometryUtils;
import uk.ac.ed.inf.LongLat;

import java.util.*;
import java.util.stream.Collectors;

/*
 * Pathfinder using A* algorithm, finds waypoints for a given start and a given goal.
 * This means it takes into account the no-fly-zone, and will give a list of waypoints
 * for the drone to fly to, before reaching the goal.
 */
public class AStarPathfinder {
    public final GeojsonManager geojsonManager;
    // in the form of graph[ll1][ll2] = distance from ll1 to ll2, +inf if cannot directly go to
    public final Map<LongLat, Map<LongLat, Double>> waypointGraph;
    // this would be waypointGraph with start and goal node
    private Map<LongLat, Map<LongLat, Double>> pathGraph;
    ////private Map<LongLat, Double> closedSet = new HashMap<>();
    ////private PriorityQueue<LongLat> openSet;
    // records the path in reversed order
    ////private Map<LongLat, LongLat> cameFrom = new HashMap<>();
    
    public AStarPathfinder(GeojsonManager geojsonManager) {
        this.geojsonManager = geojsonManager;
        List<LongLat> waypoints = geojsonManager.getWaypoints();
        List<LongLat[]> perimeters = geojsonManager.getNoFlyZonePerimeters();
        
        Map<LongLat, Map<LongLat, Double>> waypointGraph = new HashMap<>();
        // populate the adjacency matrix/map
        for (LongLat waypoint : waypoints) {
            waypointGraph.put(waypoint, new HashMap<>());
            for (LongLat otherWaypoint : waypoints) {
                // same waypoint
                if (waypoint.equals(otherWaypoint)) {
                    waypointGraph.get(waypoint).put(otherWaypoint, 0.0);
                }
                else {
                    if (geojsonManager.lineCrossesNoFlyZone(new LongLat[]
                        {waypoint, otherWaypoint})) {
                        // no direct path, edge is +inf
                        waypointGraph.get(waypoint).put(otherWaypoint, Double.POSITIVE_INFINITY);
                    }
                    else {
                        // edge is the distance
                        waypointGraph.get(waypoint).put(otherWaypoint,
                            waypoint.distanceTo(otherWaypoint));
                    }
                }
            }
        }
        this.waypointGraph = waypointGraph;
    }
    
    private Map<LongLat, Map<LongLat, Double>> getPathGraph(LongLat start, LongLat goal) {
        List<LongLat> waypoints = geojsonManager.getWaypoints();
        // System.out.printf("waypoints list length %s\n", waypoints.size());
        // make a deep copy then add start and goal nodes to it
        Map<LongLat, Map<LongLat, Double>> pathGraph =
            this.waypointGraph.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e->new HashMap<>(e.getValue())));
        pathGraph.put(start, new HashMap<>());
        pathGraph.put(goal, new HashMap<>());
        for (LongLat waypoint : waypoints) {
            // connect the start node with others
            if (geojsonManager.lineCrossesNoFlyZone(new LongLat[]
                {start, waypoint})) {
                // no direct path, edge is +inf
                pathGraph.get(start).put(waypoint, Double.POSITIVE_INFINITY);
                pathGraph.get(waypoint).put(start, Double.POSITIVE_INFINITY);
            }
            else {
                // edge is the distance
                pathGraph.get(start).put(waypoint, start.distanceTo(waypoint));
                pathGraph.get(waypoint).put(start, waypoint.distanceTo(start));
            }
            // connect the goal node with others
            if (geojsonManager.lineCrossesNoFlyZone(new LongLat[]
                {waypoint, goal})) {
                // no direct path, edge is +inf
                pathGraph.get(waypoint).put(goal, Double.POSITIVE_INFINITY);
                pathGraph.get(goal).put(waypoint, Double.POSITIVE_INFINITY);
            }
            else {
                // edge is the distance
                pathGraph.get(waypoint).put(goal, goal.distanceTo(waypoint));
                pathGraph.get(goal).put(waypoint, waypoint.distanceTo(goal));
            }
        }
        // connect start node with goal node
        double startToGoalDistance = geojsonManager.lineCrossesNoFlyZone(new LongLat[]
            {start, goal}) ? Double.POSITIVE_INFINITY : start.distanceTo(goal);

        pathGraph.get(start).put(goal, startToGoalDistance);
        pathGraph.get(goal).put(start, startToGoalDistance);
        // connect with self
        pathGraph.get(start).put(start, 0.0);
        pathGraph.get(goal).put(goal, 0.0);
        // System.out.printf("pathGraph keys length %s\n", pathGraph.size());
        // System.out.printf("start node num of edges %s\n", pathGraph.get(start).size());
        // System.out.printf("goal node num of edges %s\n", pathGraph.get(goal).size());
        return pathGraph;
    }
    
    /**
     * @param start starting point/LongLat
     * @param goal to reach point/LongLat
     * @return result containing the distance/cost, and list of way points in between.
     */
    public PathfinderResult findPath(LongLat start, LongLat goal) {
        // System.out.printf("Finding path from %s to %s", start, goal);
        this.pathGraph = getPathGraph(start, goal);
        Map<LongLat, Double> closedSet = new HashMap<>();
        PriorityQueue<LongLat> openSet = new PriorityQueue<>(
            new AStarNodeComparator(closedSet, goal));
        closedSet.put(start, 0.0);
        openSet.add(start);
        // key is the result of travelling from value
        Map<LongLat, LongLat> cameFrom = new HashMap<>();
        while (!openSet.isEmpty()) {
            // System.out.printf("openSet size %s\n", openSet.size());
            LongLat current = openSet.poll();
            // System.out.printf("current from priority queue %s", current);
            if (current.equals(goal))
                break;
            for (LongLat neighbour : pathGraph.get(current).keySet()) {
                double newCost = closedSet.get(current) + pathGraph.get(current).get(neighbour);
                if (!closedSet.containsKey(neighbour) || newCost < closedSet.get(neighbour)) {
                    closedSet.put(neighbour, newCost);
                    openSet.add(neighbour);
                    cameFrom.put(neighbour, current);
                }
            }
        }
        // reconstruct path, will be singleton list of goal if directly going from start to goal
        List<LongLat> path = new ArrayList<>();
        LongLat thisNode = goal;
        while (thisNode != start) {
            path.add(thisNode);
            thisNode = cameFrom.get(thisNode);
        }
        Collections.reverse(path);
        return new PathfinderResult(closedSet.get(goal), path);
    }
}

class AStarNodeComparator implements Comparator<LongLat> {
    private final Map<LongLat, Double> closedSet;
    private final LongLat goal;
    public AStarNodeComparator(Map<LongLat, Double> closedSet, LongLat goal) {
        this.closedSet = closedSet;
        this.goal = goal;
    }
    
    /**
     * Compares priority using the f cost (= g cost + h cost).
     * Retrieves g cost from closeSet and add euclidean distance between them.
     * @param longLat1 one element to compare
     * @param longLat2 the other element to compare
     * @return negative if longLat1 < longLat2, otherwise positive, or 0 if equal
     */
    @Override
    public int compare(LongLat longLat1, LongLat longLat2) {
        // g cost from closeSet, h cost is distance
        double fCost1 = closedSet.get(longLat1) + longLat1.distanceTo(goal);
        double fCost2 = closedSet.get(longLat2) + longLat2.distanceTo(goal);
        return Double.compare(fCost1, fCost2);
    }
}
