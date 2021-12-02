package uk.ac.ed.inf;


import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import uk.ac.ed.inf.AStarPathFinder.AStarPathfinder;

import java.util.*;


/**
 * Represents the drone itself, should encompass other class instances
 */
public class Drone {
    private final String day, month, year;
    
    private static final LongLat APPLETON_TOWER = new LongLat(-3.186874, 55.944494);
    private static final int MAX_MOVES = 1500;  // as required
    
    private final DBManager dbManager;
    private final Menus menus;
    private final What3WordsManager what3WordsManager;
    private final GeojsonManager geojsonManager;
    private final AStarPathfinder pathfinder;
    
    private final List<DeliveryOrder> allOrders = new ArrayList<>();
    
    // this will have items reduced and retry deliveries if unable to deliver all of them
    // its elements' order will also be optimised with greedy TSP later
    // it will eventually hold all the orders that are delivered
    private List<DeliveryOrder> ordersToDeliver = new ArrayList<>();
    
    /**
     * @param server Server name.
     * @param serverPort Port of server.
     * @param dbPort Port of database.
     * @param day Day of date to plan delivery.
     * @param month Month of date to plan delivery.
     * @param year Year of date to plan delivery.
     */
    public Drone(
        String server, String serverPort, String dbPort, String day, String month,
        String year) {
        this.day = day;
        this.month = month;
        this.year = year;
        
        this.dbManager = new DBManager(dbPort);
        
        this.menus = new Menus(server, serverPort);
        this.what3WordsManager = new What3WordsManager(server, serverPort);
        this.geojsonManager = new GeojsonManager(server, serverPort);
        this.pathfinder = new AStarPathfinder(geojsonManager);
    }

    /**
     * Populates the field allOrders as well as ordersToDeliver, which can be mutated later
     */
    private void getAllOrders() {
        List<DBOrder> dbOrders = dbManager.getOrdersForDay(day, month, year);
        for (DBOrder dbOrder : dbOrders) {
            this.allOrders.add(new DeliveryOrder(dbOrder.orderNo, dbOrder.deliveryDate,
                dbOrder.customer, dbOrder.deliverTo, dbManager, menus, what3WordsManager));
            this.ordersToDeliver.add(new DeliveryOrder(dbOrder.orderNo, dbOrder.deliveryDate,
                dbOrder.customer, dbOrder.deliverTo, dbManager, menus, what3WordsManager));
        }
    }
    
    /**
     * Implements 2-opt optimisation for TSP. Has side effect of changing this.orderToDeliver.
     */
    private void Tsp2OptOptimisation() {
        List<DeliveryOrder> toDeliver = new ArrayList<>(this.ordersToDeliver);
        List<DeliveryOrder> tentativeToDeliver;
        double currentTripDistance = getTripDistance(toDeliver);
        Random random = new Random();
        for (int i = 0; i < 200; i++) {
            int order1Index = random.nextInt(toDeliver.size());
            int order2Index = random.nextInt(toDeliver.size());
            DeliveryOrder order1 = toDeliver.get(order1Index);
            DeliveryOrder order2 = toDeliver.get(order2Index);
            tentativeToDeliver = new ArrayList<>(toDeliver);
            tentativeToDeliver.set(order1Index, order2);
            tentativeToDeliver.set(order2Index, order1);
            double newTripDistance = getTripDistance(tentativeToDeliver);
            if (newTripDistance < currentTripDistance) {
                // accept new trip
                toDeliver = tentativeToDeliver;
                currentTripDistance = newTripDistance;
            }
        }
        this.ordersToDeliver = toDeliver;
    }
    
    /**
     * NOTE: 2-opt seems to be performing better, therefore use Tsp2OptOptimisation instead.
     * This one also works.
     * Implements greedy optimisation for TSP.
     */
    private void TspGreedyOptimisation() {
        // old version of plan delivery
        List<DeliveryOrder> toDeliver = new ArrayList<>(this.ordersToDeliver);
        List<DeliveryOrder> optimised = new ArrayList<>();
        LongLat start = APPLETON_TOWER;
        while (toDeliver.size() > 0) {
            DeliveryOrder nextOrder = getClosestFromUnvisited(start, toDeliver);
            optimised.add(nextOrder);
            toDeliver.remove(nextOrder);
            start = nextOrder.getPickup1();  // starting from the pickup location
        }
        if (optimised.size() != this.ordersToDeliver.size())
            System.err.println("GREEDY OPTIMISATION RESULTED IN DIFFERENT NO. OF ORDERS");
        this.ordersToDeliver = optimised;
    }
    
    /**
     * @param ordersToDeliver The list of orders to calculate total trip (euclidean) distance
     * @return The total trip (euclidean) distance
     */
    private double getTripDistance(List<DeliveryOrder> ordersToDeliver) {
        LongLat currentPosition = APPLETON_TOWER;
        double distance = 0;
        for (DeliveryOrder order : ordersToDeliver) {
            double cost = currentPosition.distanceTo(order.getPickup1());
            currentPosition = order.getPickup1();
            if (order.getPickup2() != null) {
                cost += currentPosition.distanceTo(order.getPickup2());
                currentPosition = order.getPickup2();
            }
            cost += currentPosition.distanceTo(order.deliveryLngLat);
            currentPosition = order.deliveryLngLat;
            distance += cost;
        }
        return distance + currentPosition.distanceTo(APPLETON_TOWER);  // get back
    }
    
    /**
     * Plans/optimises the delivery route/nodes, used to implement greedy TSP heuristics.
     * Turns out 2-opt is better, using that one instead.
     * The real problem is Travelling Thief Problem, but to reduce complexity,
     * treat it as TSP and use TSP heuristics.
     */
    private void planDelivery() {
        // TspGreedyOptimisation();  // it seems 2 opt is performing better
        Tsp2OptOptimisation();
    }
    
    /**
     * Implements the greedy heuristic of TSP by choosing the closest unvisited to the
     * current starting position.
     *
     * @param fromLongLat The position to start from
     * @param unvisited The list of (remaining) orders to deliver
     * @return The chosen next DeliveryOrder
     */
    private DeliveryOrder getClosestFromUnvisited(
        LongLat fromLongLat,
        List<DeliveryOrder> unvisited) {
        double closestDistance = Double.POSITIVE_INFINITY;
        DeliveryOrder closestOrder = null;
        for (DeliveryOrder order : unvisited) {
            // distance to the final stopping LongLat for the delivery order
            double currentDistance = fromLongLat.distanceTo(order.deliveryLngLat);
            if (currentDistance < closestDistance) {
                closestDistance = currentDistance;
                closestOrder = order;
            }
            fromLongLat = order.deliveryLngLat;
        }
        if (closestOrder == null) {
            // should never happen unless given empty list
            System.err.printf("GREEDY TSP OPTIMISATION FAILED! unvisited list length %d\n",
                unvisited.size());
        }
        return closestOrder;
    }
   
    /**
     * Actually maneuvers the drone to visit every planned waypoint, generating its actual
     * flightpath/moves. It's called try delivering because it doesn't check for MAX_MOVE.
     * The caller should do extra work to ensure that and other restrictions.
     *
     * @return List of Flightpath that can be written to database.
     */
    private List<Flightpath> tryDeliveringOrders() {
        List<Flightpath> flightpaths = new ArrayList<>();
        LongLat currentLngLat = APPLETON_TOWER;  // starting point, as required
        List<LongLat> allWaypoints = new ArrayList<>();
        List<Boolean> needToHover = new ArrayList<>();  // corresponds to each in allWaypoints
        List<String> orderNos = new ArrayList<>();  // corresponds to each in allWaypoints
    
        for (DeliveryOrder order : this.ordersToDeliver) {
            //allWaypoints.addAll(this.pathfinder.findPath(currentLngLat, order.pickup1).waypoints);
            List<LongLat> pickup1Waypoints =
                this.pathfinder.findPath(currentLngLat, order.getPickup1()).waypoints;
            processPathfinderWaypoints(allWaypoints, needToHover, orderNos, order,
                pickup1Waypoints);
            currentLngLat = order.getPickup1();
            if (order.getPickup2() != null) {
                List<LongLat> pickup2Waypoints = this.pathfinder.findPath(currentLngLat,
                    order.getPickup2()).waypoints;
                processPathfinderWaypoints(allWaypoints, needToHover, orderNos, order, pickup2Waypoints);
                currentLngLat = order.getPickup2();
            }
            List<LongLat> deliveryWaypoints =
                pathfinder.findPath(currentLngLat, order.deliveryLngLat).waypoints;
            processPathfinderWaypoints(allWaypoints, needToHover, orderNos, order, deliveryWaypoints);
            currentLngLat = order.deliveryLngLat;
        }
        List<LongLat> appletonWaypoints = pathfinder.findPath(currentLngLat, APPLETON_TOWER).waypoints;
        // no need to hover, getting back to APPLETON TOWER
        for (LongLat appletonWaypoint : appletonWaypoints) {
            needToHover.add(false);
            orderNos.add("");
            allWaypoints.add(appletonWaypoint);
        }
        currentLngLat = APPLETON_TOWER;  // starting at appleton tower as asked
        for (int i = 0; i < allWaypoints.size(); i++) {
            String orderNo = orderNos.get(i);
            boolean toHover = needToHover.get(i);
            LongLat waypoint = allWaypoints.get(i);
            // actually get the movements needed to be closeTo the waypoint
            currentLngLat = doMoveToWaypoint(flightpaths, currentLngLat, orderNo, waypoint,
                toHover);
        }
        System.out.printf("try delivering order has %d way points in total\n", allWaypoints.size());
        return flightpaths;
    }
    
    /**
     * @param allWaypoints Add waypoints to this list.
     * @param needToHover Add boolean to indicate whether or not to hover at corresponding waypoint.
     * @param orderNos List of orderNo of deliveries, corresponding to each waypoint
     * @param order The delivery order associated with the movement/waypoint
     * @param waypointsList List of waypoints as returned by pathfinder#findPath.
     */
    private static void processPathfinderWaypoints(List<LongLat> allWaypoints,
                                                   List<Boolean> needToHover, List<String> orderNos,
                                                   DeliveryOrder order, List<LongLat> waypointsList) {
        for (int i = 0; i < waypointsList.size(); i++) {
            if (i == waypointsList.size() - 1) {
                // last point, is pickup location, need to hover
                needToHover.add(true);
            }
            else {
                needToHover.add(false);
            }
            orderNos.add(order.orderNo);
            allWaypoints.add(waypointsList.get(i));
        }
    }
    
    /**
     * @param flightpaths The flightpath list to add the movements/flightpath to.
     * @param currentLngLat Starting point LongLat.
     * @param orderNo The orderNo. of delivery order associated with this flightpath.
     * @param waypoint The waypoint to go to.
     * @param toHover Whether or not drone needs to hover after reaching the final waypoint
     * @return The position LongLat after actually moving the drone to the waypoint.
     */
    private LongLat doMoveToWaypoint(List<Flightpath> flightpaths, LongLat currentLngLat,
                                     String orderNo, LongLat waypoint, boolean toHover) {
        while (!currentLngLat.closeTo(waypoint)) {
            double bestDistance = Double.POSITIVE_INFINITY;
            int selectedAngle1 = -999;
            int selectedAngle2 = -999;
            for (int angle1 = 0; angle1 < 360; angle1 += 10) {
                for (int angle2 = 0; angle2 < 360; angle2 += 10) {
                    LongLat tentativePosition1 = currentLngLat.nextPosition(angle1);
                    LongLat tentativePosition2 = tentativePosition1.nextPosition(angle2);
                    if (!(tentativePosition1.isConfined() && tentativePosition2.isConfined())) {
                        // if we are close to the confinement border, there is a risk of getting
                        // outside when we are doing 2 step movements
                        System.out.println("Almost got out of the confinement area");
                        continue;
                    }
                    // the result of 2 step greedy search needs to be not blocked by no fly zone
                    // or it might get stuck/blocked by zone, like 1 step greedy
                    if (!geojsonManager.lineCrossesNoFlyZone(currentLngLat, tentativePosition1) &&
                        !geojsonManager.lineCrossesNoFlyZone(tentativePosition1,
                            tentativePosition2)) {
                        if (!geojsonManager.lineCrossesNoFlyZone(tentativePosition2, waypoint)) {
                            double distanceToWaypoint = tentativePosition2.distanceTo(waypoint);
                            if (distanceToWaypoint < bestDistance) {
                                bestDistance = distanceToWaypoint;
                                selectedAngle1 = angle1;
                                selectedAngle2 = angle2;
                            }
                        }
                    }
                }
            }
            if (selectedAngle1 == -999) {
                System.err.println("SHOULD NOT HAPPEN, 2 STEP GREEDY CANNOT FIND STEPS");
            }
            LongLat next1LngLat = currentLngLat.nextPosition(selectedAngle1);
            LongLat next2LngLat = next1LngLat.nextPosition(selectedAngle2);
            flightpaths.add(new Flightpath(orderNo, currentLngLat, selectedAngle1, next1LngLat));
            flightpaths.add(new Flightpath(orderNo, next1LngLat, selectedAngle2, next2LngLat));
            currentLngLat = next2LngLat;
        }
        // check if drone needs to hover
        if (toHover) {
            flightpaths.add(new Flightpath(orderNo, currentLngLat, -999, currentLngLat));
        }
        return currentLngLat;
    }
    
    /**
     * The driving method of drone, which starts off everything else.
     * Call this method in program entry point.
     */
    public void performDeliveries() {
        getAllOrders();
        planDelivery();  // performed TSP greedy optimisation here

        List<Flightpath> flightpaths = tryDeliveringOrders();
        while (flightpaths.size() > MAX_MOVES) {
            System.out.printf("cannot finish delivery, need %d moves\n", flightpaths.size());
            System.out.println("reducing orders and retrying");
            // remove most cost ineffective order and re-plan TSP
            // don't really need to remove the one with lowest value
            removeMostCostIneffectiveOrder();
            
            planDelivery();  // this results in mutated this.ordersToDeliver
            flightpaths = tryDeliveringOrders();
        }
        System.out.printf("Completed delivery with %d moves\n", flightpaths.size());
        int totalValuePlaced = this.allOrders.stream().mapToInt(o -> o.totalCost).sum();
        int totalValueDelivered = this.ordersToDeliver.stream().mapToInt(o -> o.totalCost).sum();
        float percentageValue = (float) totalValueDelivered / totalValuePlaced;
        System.out.printf(
            "Total value of placed order: %d, of delivered order: %d, Percentage monetary value: %f\n",
            totalValuePlaced, totalValueDelivered, percentageValue);
        System.out.printf("Total No. of orders %d, delivered %d\n", this.allOrders.size(),
            this.ordersToDeliver.size());
        
        dbManager.writeDeliveries(this.ordersToDeliver);
        dbManager.writeFlightpath(flightpaths);
        
        List<Point> pathPoints = new ArrayList<>();
        if (flightpaths.get(0).fromLng != APPLETON_TOWER.longitude ||
            flightpaths.get(0).fromLat != APPLETON_TOWER.latitude) {
            // should not happen
            System.err.println("ERROR!! FLIGHT PATH NOT STARTING AT APPLETON TOWER");
        }
        // add first starting point, should be APPLETON TOWER
        pathPoints.add(flightpaths.get(0).getFromLongLat().toPoint());
        for (Flightpath flightpath : flightpaths) {
            // every next ToLongLat should be the same as the previous FromLongLat
            // this way there should be no duplicate points
            pathPoints.add(flightpath.getToLongLat().toPoint());
        }
        
        // write the flightpath visualisation geojson file
        LineString pathLine = LineString.fromLngLats(pathPoints);
        String pathGeojsonString =
            FeatureCollection.fromFeature(Feature.fromGeometry(pathLine)).toJson();
        geojsonManager.writeGeojsonFile(day, month, year, pathGeojsonString);
    }
    
    /**
     * Remove an order which is most cost ineffective,
     * i.e. lowest (monetary value / euclidean distance)
     * This is called by planDelivery as part of the optimisation
     */
    private void removeMostCostIneffectiveOrder() {
        LongLat currentPosition = APPLETON_TOWER;
        double ratio = Double.POSITIVE_INFINITY;
        DeliveryOrder mostIneffectiveOrder = null;
        for (DeliveryOrder order : this.ordersToDeliver) {
            double totalDistance = currentPosition.distanceTo(order.getPickup1());
            currentPosition = order.getPickup1();
            if (order.getPickup2() != null) {
                totalDistance += currentPosition.distanceTo(order.getPickup2());
                currentPosition = order.getPickup2();
            }
            totalDistance += currentPosition.distanceTo(order.deliveryLngLat);
            currentPosition = order.deliveryLngLat;
            double currentRatio = order.totalCost / totalDistance;
            if (currentRatio < ratio) {
                mostIneffectiveOrder = order;
                ratio = currentRatio;
            }
        }
        if (mostIneffectiveOrder == null) {
            System.err.println("COULDN'T FIND COST INEFFECTIVE ORDER");
        }
        boolean success = this.ordersToDeliver.remove(mostIneffectiveOrder);
        if (!success)
            System.err.println("COULDN'T REMOVE COST INEFFECTIVE ORDER");
    }
}

