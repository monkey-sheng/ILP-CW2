package uk.ac.ed.inf;


import java.util.ArrayList;
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
        
        DBManager dbManager = new DBManager(dbPort);
        // according to piazza, this will always be localhost
        Menus menus = new Menus("localhost", serverPort);
        dbManager.dropAndCreateTableDeliveries();
        dbManager.dropAndCreateTableFlightpath();
        List<DBOrder> ordersForDay = dbManager.getOrdersForDay(day, month, year);
        List<String> orderNos =
            ordersForDay.stream().map(o -> o.orderNo).collect(Collectors.toList());
        
        List<String> locations = menus.getItemsLocation(orderNos);
    }
}
