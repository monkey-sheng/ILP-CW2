package uk.ac.ed.inf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Menus {
    public final String server;
    public final String port;
    public final int DELIVERY_CHARGE = 50;  // +50p for every delivery
    
    public static final HttpClient client = HttpClient.newHttpClient();
    // format this endpoint with the corresponding port
    // according to piazza, this will always be localhost
    public static final String menusEndpoint = "http://%s:%s/menus/menus.json";
    // the formatted URL for menus.json
    public final String menusURL;
    
    // according to a piazza question, "each item is sold by exactly one shop".
    // so using item names as keys should not be a problem
    private Map<String, Integer> itemPenceMap = new HashMap<>();  // price of item
    private Map<String, String> itemLocationMap = new HashMap<>();  // where is the item sold
    
    public Menus(String server, String port) {
        this.server = server;
        this.port = port;
        this.menusURL = String.format(menusEndpoint, server, port);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(menusURL)).build();
        String responseStr = "";
        try {
            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (!(statusCode == 200)) {
                // may be the server or bad request, fatal error
                System.out.printf("got status code %d for URL %s, expecting 200 OK",
                    statusCode, menusURL);
                System.exit(1); // Exit the application
            }
            responseStr = response.body();
        }
        catch (java.net.ConnectException e) {
            System.out.println("Fatal error: Unable to connect to " +
                server + " at port " + port + ".");
            System.exit(1); // Exit the application
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1); // Exit the application
        }
        
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
    public int getDeliveryCost(String... items) {
        int cost = DELIVERY_CHARGE;
        for (String item : items) {
            // what if item not in server response and the map
            // error handling not needed according to piazza, working with valid input good enough
            cost += this.itemPenceMap.get(item);
        }
        return cost;
    }
    
    public int getDeliveryCost(List<String> items) {
        int cost = DELIVERY_CHARGE;
        for (String item : items) {
            // what if item not in server response and the map
            // error handling not needed according to piazza, working with valid input good enough
            cost += this.itemPenceMap.get(item);
        }
        return cost;
    }
    
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
