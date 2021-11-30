package uk.ac.ed.inf;


import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import uk.ac.ed.inf.AStarPathFinder.AStarPathfinder;
import uk.ac.ed.inf.TSPDeliveryPlanner.TSPDeliveryPlanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private List<DeliveryOrder> ordersToDeliver;
    
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
     * Populates the field allOrders
     */
    public void getAllOrders() {
        List<DBOrder> dbOrders = dbManager.getOrdersForDay(day, month, year);
        for (DBOrder dbOrder : dbOrders) {
            this.allOrders.add(new DeliveryOrder(dbOrder.orderNo, dbOrder.deliveryDate,
                dbOrder.customer, dbOrder.deliverTo, dbManager, menus, what3Words));
        }
    }
    
    public void planDelivery() {
        // plan the delivery route/nodes TODO: implement 2 opt
        this.ordersToDeliver = this.allOrders;
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
            if (geojsonManager.lineCrossesNoFlyZone(new LongLat[]{currentLngLat,
                nextPosition})) {
                // briefly crosses no fly zone, find nearest angle that doesn't
                System.out.println("briefly crosses no fly zone, adjusting angle");
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
            flightpaths.add(new Flightpath(orderNo, currentLngLat, angle, nextPosition));
            // advance to next position
            currentLngLat = nextPosition;
        }
        return currentLngLat;
    }
    
    public void executePlan() {
        // TODO: Hover at shops and delivery
        // actually follow the planned route and waypoints, record flightpath
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
        if (flightpaths.size() > MAX_MOVES) {
            System.out.printf("cannot finish delivery, need %d moves\n", flightpaths.size());
            System.out.println("reducing orders and retrying");
        }
        
//        LineString pathLine =
//            LineString.fromLngLats(allWaypoints.stream().map(LongLat::toPoint).collect(
//                Collectors.toList()));
//        String pathGeojsonString =
//            FeatureCollection.fromFeature(Feature.fromGeometry(pathLine)).toJson();
//        geojsonManager.writeGeojsonFile(day, month, year, pathGeojsonString);
    }
}
