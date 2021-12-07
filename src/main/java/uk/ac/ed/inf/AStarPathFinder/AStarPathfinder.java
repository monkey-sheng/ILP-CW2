package uk.ac.ed.inf.AStarPathFinder;


import uk.ac.ed.inf.GeojsonManager;
import uk.ac.ed.inf.LongLat;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pathfinder using A* algorithm, finds waypoints for a given start and a given goal.
 * This means it takes into account the no-fly-zone, and will give a list of waypoints
 * for the drone to fly to, before reaching the goal.
 * Waypoints are in fact vertices of the no fly zone polygons.
 */
public class AStarPathfinder {
    private final GeojsonManager geojsonManager;
    // in the form of graph[ll1][ll2] = distance from ll1 to ll2, +inf if cannot directly go to
    private final Map<LongLat, Map<LongLat, Double>> waypointGraph;
    // map[start][goal] -> the cached pathGraph
    private final Map<LongLat, Map<LongLat, Map<LongLat, Map<LongLat, Double>>>> pathGraphCache =
        new HashMap<>();
    // map[start][goal] -> the cached pathfinderResult
    private final Map<LongLat, Map<LongLat, PathfinderResult>> findPathCache = new HashMap<>();
    
    /**
     * Initialises an A* pathfinder.
     * It will create a waypoint graph from the no fly zone polygon vertices, and will try to avoid
     * connecting waypoints/nodes that are hard for the drone to maneuver from and to, since the
     * drone can move only in a stiff manner (limited turning angles and fixed movement distance).
     *
     * @param geojsonManager the GeojsonManager to be used for this object.
     */
    public AStarPathfinder(GeojsonManager geojsonManager) {
        this.geojsonManager = geojsonManager;
        List<LongLat> waypoints = geojsonManager.getWaypoints();
        
        Map<LongLat, Map<LongLat, Double>> waypointGraph = new HashMap<>();
        // populate the adjacency matrix/map
        // check: DON'T CONNECT any vertices that are not in the clear
    
        for (LongLat waypoint : waypoints) {
            waypointGraph.put(waypoint, new HashMap<>());
            for (LongLat otherWaypoint : waypoints) {
                // same waypoint
                if (waypoint.equals(otherWaypoint)) {
                    waypointGraph.get(waypoint).put(otherWaypoint, 0.0);
                }
                else {
                    if (geojsonManager.lineCrossesNoFlyZone(waypoint, otherWaypoint)) {
                        // no direct path, edge is +inf
                        waypointGraph.get(waypoint).put(otherWaypoint, Double.POSITIVE_INFINITY);
                    }
                    // has a direct path between the two, check if it is hard for drone to move to
                    else {
                        // probe the surroundings
                        if (isHardToMoveTo(waypoint, otherWaypoint)) {
                            // treat it as if no direct path, edge is +inf
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
        }
        this.waypointGraph = waypointGraph;
    }
    
    /**
     * Using straight line probes with the move distance as length to check its surroundings.
     * Note that going from a vertex into the polygon doesn't count as crossing no fly zone,
     * which ought not be the case, but without proper point-in-polygon detection, there is a
     * workaround, which is to lower the number of crossing count.
     * @param waypoint1 Starting waypoint
     * @param waypoint2 Ending waypoint
     * @return Whether or not it is considered hard to move
     */
    private boolean isHardToMoveTo(LongLat waypoint1, LongLat waypoint2) {
        int angle = waypoint1.degreeTo(waypoint2);
        int crossCount = 0;
        for (int offset = -90; offset <= 90; offset += 10) {
            LongLat pseudoEndLngLat = waypoint1.nextPosition(angle + offset);
            if (geojsonManager.lineCrossesNoFlyZone(waypoint1, pseudoEndLngLat)) {
                crossCount += 1;
            }
        }
        return (crossCount >= 3);  // 3 is purely judicious at the moment, but should work
    }
    
    /**
     * Returns a weighted path graph, constructed with nodes from all waypoints plus the starting
     * and goal node. However, the edge cost is only a heuristic for the actual path cost,
     * since the drone cannot move in arbitrary straight lines.
     * @param start the starting location.
     * @param goal the goal/destination location.
     * @return A map in the form that pathGraph[point1][point2] is the edge cost of
     * point1 to point2.
     */
    private Map<LongLat, Map<LongLat, Double>> getPathGraph(LongLat start, LongLat goal) {
        if (this.pathGraphCache.containsKey(start)) {
            if (pathGraphCache.get(start).containsKey(goal)) {
                return pathGraphCache.get(start).get(goal);
            }
        }
        else {
            this.pathGraphCache.put(start, new HashMap<>());
        }
        List<LongLat> waypoints = geojsonManager.getWaypoints();
        // make a copy then add start and goal nodes to it
        Map<LongLat, Map<LongLat, Double>> pathGraph =
            this.waypointGraph.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e->new HashMap<>(e.getValue())));
        pathGraph.put(start, new HashMap<>());
        pathGraph.put(goal, new HashMap<>());
        for (LongLat waypoint : waypoints) {
            // connect the start node with others
            connectWithGraphNode(start, pathGraph, waypoint, start.distanceTo(waypoint),
                waypoint.distanceTo(start));
            // connect the goal node with others
            connectWithGraphNode(waypoint, pathGraph, goal, goal.distanceTo(waypoint),
                waypoint.distanceTo(goal));
        }
        // connect start node with goal node
        double startToGoalDistance = geojsonManager.lineCrossesNoFlyZone(new LongLat[]
            {start, goal}) ? Double.POSITIVE_INFINITY : start.distanceTo(goal);

        pathGraph.get(start).put(goal, startToGoalDistance);
        pathGraph.get(goal).put(start, startToGoalDistance);
        // connect with self
        pathGraph.get(start).put(start, 0.0);
        pathGraph.get(goal).put(goal, 0.0);
        
        this.pathGraphCache.get(start).put(goal, pathGraph);
        return pathGraph;
    }
    
    /**
     * Utility method used by getPathGraph, intended to connect the start and goal node to the
     * existing waypoint graph for use in findPath.
     * @param nodeToAdd The node to add to the graph.
     * @param pathGraph The existing graph.
     * @param waypointInGraph The waypoint of pathGraph which is to be connected with.
     * @param distanceFromNode Distance from nodeToAdd to waypointInGraph.
     * @param distanceToNode Distance from waypointInGraph to nodeToAdd.
     */
    private void connectWithGraphNode(LongLat nodeToAdd, Map<LongLat, Map<LongLat, Double>> pathGraph,
                                      LongLat waypointInGraph,
                                      double distanceFromNode, double distanceToNode) {
        if (geojsonManager.lineCrossesNoFlyZone(nodeToAdd, waypointInGraph)) {
            // no direct path, edge is +inf
            pathGraph.get(nodeToAdd).put(waypointInGraph, Double.POSITIVE_INFINITY);
            pathGraph.get(waypointInGraph).put(nodeToAdd, Double.POSITIVE_INFINITY);
        }
        else {
            // edge is the distance
            pathGraph.get(nodeToAdd).put(waypointInGraph, distanceFromNode);
            pathGraph.get(waypointInGraph).put(nodeToAdd, distanceToNode);
        }
    }
    
    /**
     * Find a path from start to goal, the returned result is the nodes to visit to reach goal,
     * including goal node itself, but not the start node.
     * @param start starting point/LongLat
     * @param goal to reach point/LongLat
     * @return result containing the distance/cost, and list of way points in between.
     */
    public PathfinderResult findPath(LongLat start, LongLat goal) {
        if (this.findPathCache.containsKey(start)) {
            if (findPathCache.get(start).containsKey(goal)) {
                return findPathCache.get(start).get(goal);
            }
        }
        else {
            this.findPathCache.put(start, new HashMap<>());
        }
        // this would be waypointGraph with start and goal node added
        Map<LongLat, Map<LongLat, Double>> pathGraph = getPathGraph(start, goal);
        Map<LongLat, Double> closedSet = new HashMap<>();
        // open set is a priority queue based on the f cost of nodes
        PriorityQueue<LongLat> openSet = new PriorityQueue<>(
            new AStarNodeComparator(closedSet, goal));
        closedSet.put(start, 0.0);
        openSet.add(start);
        // key is the result of travelling from value, used when reconstructing the path
        Map<LongLat, LongLat> cameFrom = new HashMap<>();
        while (!openSet.isEmpty()) {
            LongLat current = openSet.poll();
            if (current.equals(goal))
                break;
            // for each neighbour node of current node
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
        Collections.reverse(path);  // it is now in the correct visiting order
        
        PathfinderResult result = new PathfinderResult(closedSet.get(goal), path);
        this.findPathCache.get(start).put(goal, result);
        return result;
    }
}


/**
 * This comparator compares A* nodes based on their corresponding f cost.
 */
class AStarNodeComparator implements Comparator<LongLat> {
    private final Map<LongLat, Double> closedSet;
    private final LongLat goal;
    
    /**
     * The closed set needs to be mutated/updated by the pathfinding algorithm.
     * @param closedSet the (referenced) closed set which contains the g costs.
     * @param goal the goal node to which h cost is computed.
     */
    public AStarNodeComparator(Map<LongLat, Double> closedSet, LongLat goal) {
        this.closedSet = closedSet;
        this.goal = goal;
    }
    
    /**
     * Compares priority using the f cost (= g cost + h cost).
     * Retrieves g cost from closeSet and use euclidean distance between nodes as h cost.
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
