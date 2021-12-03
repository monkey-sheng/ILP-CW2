package uk.ac.ed.inf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Responsible for marshalling, processing and retrieving data for order items.
 * Should only be instantiated once per drone/webserver.
 */
public class Menus {
    private static final int DELIVERY_CHARGE = 50;  // +50p for every delivery
    
    // format this endpoint with the corresponding port
    // according to piazza, this will always be localhost
    private static final String menusEndpoint = "http://%s:%s/menus/menus.json";
    
    // according to a piazza question, "each item is sold by exactly one shop".
    // so using item names as keys should not be a problem
    private final Map<String, Integer> itemPenceMap = new HashMap<>();  // price of item
    private final Map<String, String> itemLocationMap = new HashMap<>();  // where is the item sold
    
    /**
     * @param server Server name.
     * @param port Port of server.
     */
    public Menus(String server, String port) {
        String responseStr = Utils.sendHttpRequest(server, port,
            String.format(menusEndpoint, server, port));

        // perform deserialization of the response
        Type menuEntryListType = new TypeToken<List<MenuEntry>>() {}.getType();
        ArrayList<MenuEntry> menuEntries = new Gson().fromJson(responseStr, menuEntryListType);
        for (MenuEntry entry : menuEntries) {
            String location = entry.location;
            for (MenuEntry.MenuItem menuItem : entry.menu) {
                // store price of each item
                itemPenceMap.put(menuItem.item, menuItem.pence);
                // store location (w3w) of item
                itemLocationMap.put(menuItem.item, location);
            }
        }
    }

    /**
     * Calculates cost in pence of having all of these items delivered by drone,
     * including the standard delivery charge of 50p per delivery.
     * @param items array of items for which to calculate the total cost in pence.
     * @return the delivery cost of the provided list of items.
     */
    public int getDeliveryCost(List<String> items) {
        int cost = DELIVERY_CHARGE;
        for (String item : items) {
            cost += this.itemPenceMap.get(item);
        }
        return cost;
    }
    
    /**
     * @param items List of items, intended to be a list of items from one order.
     * @return A list of w3w location strings, there should be of size either 1 or 2, if used
     * with items from one order.
     */
    public List<String> getItemsLocations(List<String> items) {
        ArrayList<String> locations = new ArrayList<>();
        for (String item : items) {
            // shouldn't get null as a result if item is valid
            String location = itemLocationMap.get(item);
            if (!locations.contains(location)) {
                locations.add(location);
            }
        }
        return locations;
    }
}
