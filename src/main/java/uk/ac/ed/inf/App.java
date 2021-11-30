package uk.ac.ed.inf;


import java.util.List;
import java.util.stream.Collectors;

public class App
{
    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
        String day = args[0];
        String month = args[1];
        String year = args[2];
        String serverPort = args[3];
        String dbPort = args[4];
        System.out.printf("running server at %s, running database at %s, for " +
            "%s-%s-%s\n", serverPort, dbPort, day, month, year);
        
//        DBManager dbManager = new DBManager(dbPort);
//        // according to piazza, this will always be localhost
//        Menus menus = new Menus("localhost", serverPort);
//        dbManager.dropAndCreateTableDeliveries();
//        dbManager.dropAndCreateTableFlightpath();
//        List<DBOrder> ordersForDay = dbManager.getOrdersForDay(day, month, year);
//        List<String> orderNos =
//            ordersForDay.stream().map(o -> o.orderNo).collect(Collectors.toList());
//        for (String orderNo : orderNos) {
//            List<String> items = dbManager.getOrderItemsForNo(orderNo);
//            System.out.println(items);
//            List<String> locations = menus.getItemsLocations(items);
//            What3Words what3Words = new What3Words("localhost", serverPort);
//            for (String location : locations) {
//                LongLat itemLongLat = what3Words.getLongLatFromWords(location);
//                System.out.println(itemLongLat);
//            }
//        }
//
//        GeojsonManager geojsonManager = new GeojsonManager("localhost", serverPort);
//        List<LongLat[]> perimeters = geojsonManager.getNoFlyZonePerimeters();
//        System.out.println(GeometryUtils.lineSegmentIntersects(perimeters.get(0),
//            perimeters.get(1)));  // false no intersection with following line seg
//        System.out.println(GeometryUtils.lineSegmentIntersects(perimeters.get(0),
//            perimeters.get(0)));  // false no intersection with self
//        System.out.println(GeometryUtils.lineSegmentIntersects(perimeters.get(1),
//            perimeters.get(0)));  // false no intersection with previous line seg
//        System.out.println(GeometryUtils.lineSegmentIntersects(new LongLat[] {new LongLat(0.5,0.5),
//                new LongLat(1.5,1.5)},
//            new LongLat[] {new LongLat(0,2), new LongLat(2,0)}));
        
        Drone drone = new Drone("localhost", serverPort, dbPort, day, month, year);
        drone.executePlan();
    }
}
