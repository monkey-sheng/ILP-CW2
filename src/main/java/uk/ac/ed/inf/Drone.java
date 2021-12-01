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
    
    public Drone(
        String server, String serverPort, String dbPort, String day, String month,
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
     * Actually maneuvers the drone to visit every planned waypoint, generating its actual
     * flightpath/moves. It's called try deliver because it doesn't check for MAX_MOVE.
     * @return List of Flightpath that can be written to database
     */
    private List<Flightpath> tryDeliveringOrders() {
        List<Flightpath> flightpaths = new ArrayList<>();
        LongLat currentLngLat = APPLETON_TOWER;
        List<LongLat> allWaypoints = new ArrayList<>();
        List<Boolean> needToHover = new ArrayList<>();  // corresponds to each in allWaypoints
        List<String> orderNos = new ArrayList<>();  // corresponds to each in allWaypoints
//        for (DeliveryOrder order : this.ordersToDeliver) {
//            List<LongLat> pickup1Waypoints =
//                this.pathfinder.findPath(currentLngLat, order.pickup1).waypoints;
//            String orderNo = order.orderNo;
//            allWaypoints.addAll(pickup1Waypoints);
//            for (LongLat waypoint : pickup1Waypoints) {
//                currentLngLat = doMoveToWaypoint(flightpaths, currentLngLat, orderNo, waypoint);
//                // hover
//                flightpaths.add(new Flightpath(orderNo, currentLngLat, -999, currentLngLat));
//            }
//            if (order.pickup2 != null) {
//                List<LongLat> pickup2Waypoints =
//                    this.pathfinder.findPath(currentLngLat, order.pickup2).waypoints;
//                allWaypoints.addAll(pickup2Waypoints);
//                for (LongLat waypoint : pickup2Waypoints) {
//                    currentLngLat = doMoveToWaypoint(flightpaths, currentLngLat, orderNo, waypoint);
//                    // hover
//                    flightpaths.add(new Flightpath(orderNo, currentLngLat, -999, currentLngLat));
//                }
//            }
//            List<LongLat> deliveryWaypoints =
//                this.pathfinder.findPath(currentLngLat, order.deliveryLngLat).waypoints;
//            allWaypoints.addAll(deliveryWaypoints);
//            for (LongLat waypoint : deliveryWaypoints) {
//                currentLngLat = doMoveToWaypoint(flightpaths, currentLngLat, orderNo, waypoint);
//                // hover
//                flightpaths.add(new Flightpath(orderNo, currentLngLat, -999, currentLngLat));
//            }
//        }
//        // need to get back to APPLETON
//        List<LongLat> appletonWaypoints =
//            this.pathfinder.findPath(currentLngLat, APPLETON_TOWER).waypoints;
//        allWaypoints.addAll(appletonWaypoints);
//        for (LongLat waypoint : appletonWaypoints) {
//            // order No not important according to piazza
//            currentLngLat = doMoveToWaypoint(flightpaths, currentLngLat, "", waypoint);
//        }
    
        for (DeliveryOrder order : this.ordersToDeliver) {
            //allWaypoints.addAll(this.pathfinder.findPath(currentLngLat, order.pickup1).waypoints);
            List<LongLat> pickup1Waypoints =
                this.pathfinder.findPath(currentLngLat, order.pickup1).waypoints;
            for (int i = 0; i < pickup1Waypoints.size(); i++) {
                if (i == pickup1Waypoints.size() - 1) {
                    // last point, is pickup location, need to hover
                    needToHover.add(true);
                }
                else {
                    needToHover.add(false);
                }
                orderNos.add(order.orderNo);
                allWaypoints.add(pickup1Waypoints.get(i));
            }
            currentLngLat = order.pickup1;
            if (order.pickup2 != null) {
                List<LongLat> pickup2Waypoints = this.pathfinder.findPath(currentLngLat,
                    order.pickup2).waypoints;
                for (int i = 0; i < pickup2Waypoints.size(); i++) {
                    if (i == pickup2Waypoints.size() - 1) {
                        // last point, is pickup location, need to hover
                        needToHover.add(true);
                    }
                    else {
                        needToHover.add(false);
                    }
                    orderNos.add(order.orderNo);
                    allWaypoints.add(pickup2Waypoints.get(i));
                }
                currentLngLat = order.pickup2;
            }
            List<LongLat> deliveryWaypoints =
                pathfinder.findPath(currentLngLat, order.deliveryLngLat).waypoints;
            for (int i = 0; i < deliveryWaypoints.size(); i++) {
                if (i == deliveryWaypoints.size() - 1) {
                    // last point, is pickup location, need to hover
                    needToHover.add(true);
                }
                else {
                    needToHover.add(false);
                }
                orderNos.add(order.orderNo);
                allWaypoints.add(deliveryWaypoints.get(i));
            }
            currentLngLat = order.deliveryLngLat;
        }
        List<LongLat> appletonWaypoints = pathfinder.findPath(currentLngLat, APPLETON_TOWER).waypoints;
        // no need to hover getting back to APPLETON TOWER
        for (LongLat appletonWaypoint : appletonWaypoints) {
            needToHover.add(false);
            orderNos.add("");
            allWaypoints.add(appletonWaypoint);
        }
        //TODO: populate flightpath
        currentLngLat = APPLETON_TOWER;
        for (int i = 0; i < allWaypoints.size(); i++) {
            String orderNo = orderNos.get(i);
            boolean toHover = needToHover.get(i);
            currentLngLat = doMoveToWaypoint(flightpaths, currentLngLat, orderNo, allWaypoints, i,
                toHover);
        }
        
        System.out.printf("try delivering order has %d in all way points\n", allWaypoints.size());
        LineString pathLine = LineString.fromLngLats(allWaypoints.stream().map(LongLat::toPoint).
            collect(Collectors.toList()));
        String pathGeojsonString =
            FeatureCollection.fromFeature(Feature.fromGeometry(pathLine)).toJson();
        geojsonManager.writeGeojsonFile("tryDelivery waypoints" + day, month, year,
            pathGeojsonString);
        return flightpaths;
    }
    
    private LongLat doMoveToWaypoint(List<Flightpath> flightpaths, LongLat currentLngLat,
                                     String orderNo, List<LongLat> allWaypoints, int index,
                                     boolean toHover) {
        LongLat waypoint = allWaypoints.get(index);
        
        while (!currentLngLat.closeTo(waypoint)) {
            System.out.println("WHILE LOOP START");
            double bestDistance = Double.POSITIVE_INFINITY;
            int selectedAngle1 = -999;
            int selectedAngle2 = -999;
            for (int angle1 = 0; angle1 < 360; angle1 += 10) {
                for (int angle2 = 0; angle2 < 360; angle2 += 10) {
                    LongLat tentativePosition1 = currentLngLat.nextPosition(angle1);
                    LongLat tentativePosition2 = tentativePosition1.nextPosition(angle2);
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
            
            //////////
            
//            System.out.println("WHILE LOOP START");
//            int angle = currentLngLat.degreeTo(waypoint);
//            LongLat nextPosition = currentLngLat.nextPosition(angle);
//            // if moving straight towards waypoint will briefly cross no fly zone
//            if (geojsonManager.lineCrossesNoFlyZone(currentLngLat,
//                nextPosition)) {
//                System.out.println("will briefly cross no fly zone");
//                // search for a move angle which can take me to somewhere
//                // where there is a move to a 2nd somewhere, that is cleared for !!!waypoint
//                int selectedAngle1 = -999;
//                int selectedAngle2 = -999;
//                for (int newAngle1 = 0; newAngle1 < 360; newAngle1 += 10) {
//                    boolean foundNewAngle2 = false;
//                    LongLat tentative1Next = currentLngLat.nextPosition(newAngle1);
//                    // can reach tentative1Next from current straight on
//                    if ((!geojsonManager.lineCrossesNoFlyZone(currentLngLat, tentative1Next))) {
//                        for (int newAngle2 = 0; newAngle2 < 360; newAngle2 += 10) {
//                            LongLat tentative2Next = tentative1Next.nextPosition(newAngle2);
//                            // can reach tentative2Next from tentative1Next straight on
//                            // and it is close to (current) waypoint
//                            // and it can reach nextWaypoint straight on
//                            if ((!geojsonManager.lineCrossesNoFlyZone(tentative1Next,
//                                tentative2Next))) {
//                                // case of approaching destination, but due to stiff angles hitting no fly zone
//                                // TODO: check this
//                                // currentLngLat.distanceTo(initialLngLat) > currentLngLat.distanceTo(waypoint)
//                                if (currentLngLat.distanceTo(waypoint) < 2 * LongLat.MOVE_DISTANCE) {
//                                    if (tentative2Next.closeTo(waypoint)) {
//                                        foundNewAngle2 = true;
//                                        selectedAngle2 = newAngle2;
//                                        break;
//                                    }
//                                }
//                                // case of getting blocked due to being close to previous way point
//                                else {
//                                    if (!geojsonManager.lineCrossesNoFlyZone(tentative2Next,
//                                        waypoint)) {
//                                        foundNewAngle2 = true;
//                                        selectedAngle2 = newAngle2;
//                                        break;
//                                    }
//                                }
//                            }
//                        }
//                        if (foundNewAngle2) {
//                            // the 2 selected move angles can take me to a place where I can reach
//                            // the next waypoint in a straight line,
//                            // and I am close to current waypoint
//                            selectedAngle1 = newAngle1;
//                            ////nextPosition = tentative1Next;
//                            break;
//                        }
//                    }
//                }
//                if (selectedAngle1 == -999 || selectedAngle2 == -999) {
//                    // should never happen
//                    System.err.println(selectedAngle1); System.err.println(selectedAngle2);
//                    System.err.println("CANNOT FIND AN ANGLE WHICH GIVES PATH TO NEXT 2 WAYPOINTS");
//                }
//                System.out.printf("ANGLE1 %d, ANGLE2 %d\n", selectedAngle1, selectedAngle2);
//                LongLat next1Position = currentLngLat.nextPosition(selectedAngle1);
//                LongLat next2Position = next1Position.nextPosition(selectedAngle2);
//                flightpaths.add(new Flightpath(orderNo, currentLngLat, selectedAngle1,
//                    next1Position));
//                flightpaths.add(new Flightpath(orderNo, next1Position, selectedAngle2,
//                    next2Position));
//                // has moved from current to next2Position basically
//                System.out.printf("FOUND next1Position %s next2Position %s, MOVED TO IT\n",
//                    next1Position, next2Position);
//                currentLngLat = next2Position;
//            }
//            // else doesn't cross no fly zone
//            else {
//                flightpaths.add(new Flightpath(orderNo, currentLngLat, angle, nextPosition));
//                // advance to next position
//                currentLngLat = nextPosition;
//            }
        }
        // check if need hovering
        if (toHover) {
            flightpaths.add(new Flightpath(orderNo, currentLngLat, -999, currentLngLat));
        }
        return currentLngLat;
    }
    
    /**
     * This method has the side effect of adding to flightpath list passed into it
     *
     * @param flightpaths   the list of flightpaths to add to
     * @param currentLngLat current position
     * @param orderNo       needs to be written to flightpath
     * @param waypoint      the (interim) destination
     * @return LongLat position after moving to waypoint
     */
    private LongLat doMoveToWaypoint(
        List<Flightpath> flightpaths, LongLat currentLngLat,
        String orderNo, LongLat waypoint) {
        while (!currentLngLat.closeTo(waypoint)) {
            int angle = currentLngLat.degreeTo(waypoint);
            LongLat nextPosition = currentLngLat.nextPosition(angle);
            
            // briefly crosses no fly zone, find nearest angle that doesn't
            if (geojsonManager.lineCrossesNoFlyZone(new LongLat[]{currentLngLat,
                nextPosition})) {
                System.out.println("briefly crosses no fly zone, adjusting angle");
                int newAngle = -999;
                for (int angleOffset = 0; angleOffset <= 90; angleOffset += 10) {
                    if (!geojsonManager.lineCrossesNoFlyZone(new LongLat[]{currentLngLat,
                        currentLngLat.nextPosition(angle + angleOffset)})) {
                        System.out.printf("found +angle: +%d=%d\n", angleOffset,
                            angle + angleOffset);
                        int tentativeNewAngle = (angle + angleOffset) < 0 ? angle + 360 : angle;
                        newAngle = tentativeNewAngle % 360;
                        break;
                    }
                    if (!geojsonManager.lineCrossesNoFlyZone(new LongLat[]{currentLngLat,
                        currentLngLat.nextPosition(angle - angleOffset)})) {
                        System.out.printf("found -angle: -%d=%d\n", angleOffset,
                            angle - angleOffset);
                        int tentativeNewAngle = (angle - angleOffset) < 0 ? angle + 360 : angle;
                        newAngle = tentativeNewAngle % 360;
                        break;
                    }
                }
                if (newAngle == -999) {
                    // should never happen
                    System.out.println("couldn't adjust angle, SOMETHING'S WRONG");
                }
                angle = newAngle;
                nextPosition = currentLngLat.nextPosition(newAngle);
            }

//            if (geojsonManager.lineCrossesNoFlyZone(new LongLat[] {currentLngLat, nextPosition})) {
//                // find all angles which don't cross no fly zone, and choose the one that gets
//                // closest to the waypoint
//                System.out.println("briefly crosses no fly zone, adjusting angle");
//                int selectedAngle = -999;
//                double currentClosestDistance = Double.POSITIVE_INFINITY;
//                for (int newAngle = 0; newAngle < 360; newAngle += 10) {
//                    LongLat tentativeNext = currentLngLat.nextPosition(newAngle);
//                    // if the new angle doesn't cross
//                    if (!geojsonManager.lineCrossesNoFlyZone(new LongLat[] {currentLngLat,
//                        tentativeNext})) {
//                        double tentativeDistance = tentativeNext.distanceTo(waypoint);
//                        if (tentativeDistance < currentClosestDistance) {
//                            selectedAngle = newAngle;
//                            currentClosestDistance = tentativeDistance;
//                        }
//                    }
//                }
//                if (selectedAngle == -999) {
//                    System.err.println("COULDN'T FIND AN ANGLE IN doMove");
//                }
//                nextPosition = currentLngLat.nextPosition(selectedAngle);
//                System.out.printf("adjusted next position %s\n", nextPosition);
//            }
//            else {
//                System.out.println("did not cross no fly zone");
//            }

            if (flightpaths.size() > 0) {
                if (flightpaths.get(flightpaths.size() - 1).toLng != currentLngLat.longitude ||
                    flightpaths.get(flightpaths.size() - 1).toLat != currentLngLat.latitude) {
                    System.err.println("doMoveToWaypoint: GAP IN FLIGHTPATH! ERROR!");
                }
            }
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
//        System.out.printf("plain version delivery has %d all waypoints\n", allWaypoints.size());
//        LineString pathLine = LineString.fromLngLats(allWaypoints.stream().map(LongLat::toPoint).
//            collect(Collectors.toList()));
//        String pathGeojsonString =
//            FeatureCollection.fromFeature(Feature.fromGeometry(pathLine)).toJson();
//        geojsonManager.writeGeojsonFile("raw waypoints"+day, month, year, pathGeojsonString);

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

