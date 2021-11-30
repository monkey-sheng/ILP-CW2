package uk.ac.ed.inf;


import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import uk.ac.ed.inf.AStarPathFinder.AStarPathfinder;
import uk.ac.ed.inf.TSPDeliveryPlanner.TSPDeliveryPlanner;

import java.util.*;
import java.util.stream.Collectors;

/*
 * Represents the drone itself, should encompass other objects
 */
public class Drone {
    private final String server, serverPort, dbPort;
    private final String day, month, year;
    
    private static final LongLat APPLETON_TOWER = new LongLat(-3.186874, 55.944494);
    private static final int MAX_MOVES = 1500;
    
    private final DBManager dbManager;
    private final Menus menus;
    private final What3Words what3Words;
    private final GeojsonManager geojsonManager;
    private final AStarPathfinder pathfinder;
    private final TSPDeliveryPlanner deliveryPlanner;
    
    private List<DeliveryOrder> allOrders = new ArrayList<>();
    // TODO: this will have items reduced if unable to deliver all of them
    private List<DeliveryOrder> ordersToDeliver = new ArrayList<>();
    
    public final List<DBOrder> deliveries = new ArrayList<>();  // data to record in DB
    public final List<Flightpath> flightpaths = new ArrayList<>();  // data to record in DB
    
    public Drone(String server, String serverPort, String dbPort, String day, String month,
                 String year) {
        this.server = server;
        this.serverPort = serverPort;
        this.dbPort = dbPort;
        this.day = day;
        this.month = month;
        this.year = year;
        
        this.dbManager = new DBManager(dbPort);
        dbManager.dropAndCreateTableDeliveries();
        dbManager.dropAndCreateTableFlightpath();
        
        this.menus = new Menus(server, serverPort);
        this.what3Words = new What3Words(server, serverPort);
        this.geojsonManager = new GeojsonManager(server, serverPort);
        this.pathfinder = new AStarPathfinder(geojsonManager);
        this.deliveryPlanner = new TSPDeliveryPlanner();
    }
    
    /*
     * Populates the field allOrders as well as ordersToDeliver, which can be mutated later
     */
    public void getAllOrders() {
        List<DBOrder> dbOrders = dbManager.getOrdersForDay(day, month, year);
        for (DBOrder dbOrder : dbOrders) {
            this.allOrders.add(new DeliveryOrder(dbOrder.orderNo, dbOrder.deliveryDate,
                dbOrder.customer, dbOrder.deliverTo, dbManager, menus, what3Words));
            this.ordersToDeliver.add(new DeliveryOrder(dbOrder.orderNo, dbOrder.deliveryDate,
                dbOrder.customer, dbOrder.deliverTo, dbManager, menus, what3Words));
        }
    }
    
    public void planDelivery() {
        // plan/optimise the delivery route/nodes TODO: implement greedy
        List<DeliveryOrder> toDeliver = new ArrayList<>(this.ordersToDeliver);
        List<DeliveryOrder> optimised = new ArrayList<>();
        LongLat start = APPLETON_TOWER;
        while (toDeliver.size() > 0) {
            DeliveryOrder nextOrder = getClosestFromUnvisited(start, toDeliver);
            optimised.add(nextOrder);
            toDeliver.remove(nextOrder);
            start = nextOrder.pickup1;  // starting from the pickup location
        }
        if (optimised.size() != this.ordersToDeliver.size())
            System.out.println("GREEDY OPTIMISATION RESULTED IN DIFFERENT NO. OF ORDERS");
        this.ordersToDeliver = optimised;
    }
    private DeliveryOrder getClosestFromUnvisited(LongLat fromLongLat,
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
        if (closestOrder == null)
            System.out.println("GREEDY TSP OPTIMISATION FAILED!");  // should never happen
        return closestOrder;
    }
    
    private void createDeliveryNodesGraph() {
        // adjacency matrix as maps of maps, assign to member field
        // in the process, call pathfinder to compute node distance
    }
    
    private double computeNodeDistance() {
        // use pathfinder to compute distance between 2 delivery nodes
        return 0.0;
    }
    
    
    /**
     * @return List of Flightpath that can be written to database
     */
    private List<Flightpath> tryDeliveringOrders() {
        List<Flightpath> flightpaths = new ArrayList<>();
        LongLat currentLngLat = APPLETON_TOWER;
        ////List<LongLat> allWaypoints = new ArrayList<>();
        for (DeliveryOrder order : this.ordersToDeliver) {
            List<LongLat> pickup1Waypoints =
                this.pathfinder.findPath(currentLngLat, order.pickup1).waypoints;
            String orderNo = order.orderNo;
            for (LongLat waypoint : pickup1Waypoints) {
                currentLngLat = doMoveToWaypoint(flightpaths, currentLngLat, orderNo, waypoint);
                // hover
                flightpaths.add(new Flightpath(orderNo, currentLngLat, -999, currentLngLat));
            }
            if (order.pickup2 != null) {
                List<LongLat> pickup2Waypoints =
                    this.pathfinder.findPath(currentLngLat, order.pickup1).waypoints;
                for (LongLat waypoint : pickup2Waypoints) {
                    currentLngLat = doMoveToWaypoint(flightpaths, currentLngLat, orderNo, waypoint);
                    // hover
                    flightpaths.add(new Flightpath(orderNo, currentLngLat, -999, currentLngLat));
                }
            }
            List<LongLat> deliveryWaypoints =
                this.pathfinder.findPath(currentLngLat, order.deliveryLngLat).waypoints;
            for (LongLat waypoint : deliveryWaypoints) {
                currentLngLat = doMoveToWaypoint(flightpaths, currentLngLat, orderNo, waypoint);
                // hover
                flightpaths.add(new Flightpath(orderNo, currentLngLat, -999, currentLngLat));
            }
        }
        // need to get back to APPLETON
        List<LongLat> appletonWaypoints =
            this.pathfinder.findPath(currentLngLat, APPLETON_TOWER).waypoints;
        for (LongLat waypoint : appletonWaypoints) {
            // order No not important according to piazza
            currentLngLat = doMoveToWaypoint(flightpaths, currentLngLat, "", waypoint);
        }
        return flightpaths;
    }
    
    
    /**
     * This method has the side effect of adding to flightpath list passed into it
     * @param flightpaths the list of flightpaths to add to
     * @param currentLngLat current position
     * @param orderNo needs to be written to flightpath
     * @param waypoint the (interim) destination
     * @return LongLat position after moving to waypoint
     */
    private LongLat doMoveToWaypoint(List<Flightpath> flightpaths, LongLat currentLngLat,
                               String orderNo, LongLat waypoint) {
        while (!currentLngLat.closeTo(waypoint)) {
            int angle = currentLngLat.degreeTo(waypoint);
            LongLat nextPosition = currentLngLat.nextPosition(angle);
            
            // briefly crosses no fly zone, find nearest angle that doesn't
            if (geojsonManager.lineCrossesNoFlyZone(new LongLat[]{currentLngLat,
                nextPosition})) {
                //System.out.println("briefly crosses no fly zone, adjusting angle");
                int new_angle = 0;
                for (int angle_offset = 0; angle_offset <= 90; angle_offset += 10) {
                    if (!geojsonManager.lineCrossesNoFlyZone(new LongLat[]{currentLngLat,
                        currentLngLat.nextPosition(angle + angle_offset)})) {
                        new_angle = angle + angle_offset;
                    }
                    else if (!geojsonManager.lineCrossesNoFlyZone(new LongLat[]{currentLngLat,
                        currentLngLat.nextPosition(angle - angle_offset)})) {
                        new_angle = angle - angle_offset;
                    }
                }
                if (new_angle == 0)  // should never happen
                    System.out.println("couldn't adjust angle, SOMETHING'S WRONG");
                angle = new_angle;
                nextPosition = currentLngLat.nextPosition(new_angle);
            }
            else {
                System.out.println("this move not crossing no fly zone");
            }
            if (flightpaths.size() > 0)
                if (flightpaths.get(flightpaths.size()-1).toLng != currentLngLat.longitude ||
                    flightpaths.get(flightpaths.size()-1).toLat != currentLngLat.latitude)
                    System.err.println("doMoveToWaypoint: GAP IN FLIGHTPATH! ERROR!");
            flightpaths.add(new Flightpath(orderNo, currentLngLat, angle, nextPosition));
            // advance to next position
            currentLngLat = nextPosition;
        }
        return currentLngLat;
    }
    
    public void executePlan() {
        getAllOrders();
        planDelivery();
        
//        LongLat currentLngLat = APPLETON_TOWER;
//        List<LongLat> allWaypoints = new ArrayList<>();
//        for (DeliveryOrder order : this.ordersToDeliver) {
//            allWaypoints.addAll(this.pathfinder.findPath(currentLngLat, order.pickup1).waypoints);
//            currentLngLat = order.pickup1;
//            if (order.pickup2 != null) {
//                allWaypoints.addAll(this.pathfinder.findPath(currentLngLat, order.pickup2)
//                    .waypoints);
//                currentLngLat = order.pickup2;
//            }
//            allWaypoints.addAll(pathfinder.findPath(currentLngLat,
//                order.deliveryLngLat).waypoints);
//            currentLngLat = order.deliveryLngLat;
//        }
//        allWaypoints.addAll(pathfinder.findPath(currentLngLat, APPLETON_TOWER).waypoints);
        List<Flightpath> flightpaths = tryDeliveringOrders();
        while (flightpaths.size() > MAX_MOVES) {
            System.out.printf("cannot finish delivery, need %d moves\n", flightpaths.size());
            System.out.println("reducing orders and retrying");
            // remove 1 randomly and re-plan TSP
            // Do I really need to remove the one with lowest value?
            this.ordersToDeliver.remove(new Random().nextInt(this.ordersToDeliver.size()));
            planDelivery();  // this results in mutated this.ordersToDeliver
            flightpaths = tryDeliveringOrders();
        }
        int totalValuePlaced = this.allOrders.stream().mapToInt(o -> o.totalCost).sum();
        int totalValueDelivered = this.ordersToDeliver.stream().mapToInt(o -> o.totalCost).sum();
        System.out.printf("Percentage monetary value: %f",
            (float) totalValueDelivered / totalValuePlaced);
        System.out.printf("Total No. of orders %d, delivered %d\n", this.allOrders.size(),
            this.ordersToDeliver.size());
        dbManager.writeDeliveries(this.ordersToDeliver);
        dbManager.writeFlightpath(flightpaths);
        List<Point> pathPoints = new ArrayList<>();
        if (flightpaths.get(0).fromLng != APPLETON_TOWER.longitude ||
            flightpaths.get(0).fromLat != APPLETON_TOWER.latitude)
            System.out.println("ERROR!! FLIGHT PATH NOT STARTING AT APPLETON TOWER");
        // add first starting point, should be APPLETON TOWER
        pathPoints.add(flightpaths.get(0).getFromLongLat().toPoint());
        for (Flightpath flightpath : flightpaths) {
            // every next ToLongLat should be the same as the previous FromLongLat
            // this way there should be no duplicate points
            pathPoints.add(flightpath.getToLongLat().toPoint());
        }
        LineString pathLine = LineString.fromLngLats(pathPoints);
        String pathGeojsonString =
            FeatureCollection.fromFeature(Feature.fromGeometry(pathLine)).toJson();
        geojsonManager.writeGeojsonFile(day, month, year, pathGeojsonString);
    }
}
