package uk.ac.ed.inf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class What3Words {
    public final String server, port;
    public static final HttpClient client = HttpClient.newHttpClient();
    public static final String w3wEndpoint = "http://%s:%s/words/%s/%s/%s/details.json";
    
    public What3Words(String server, String port) {
        this.server = server;
        this.port = port;
        
    }
    
    public LongLat getLongLatFromWords(String w3wString) {
        String[] words = w3wString.split("\\.");
        String w3wURL = String.format(w3wEndpoint, server, port, words[0], words[1], words[2]);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(w3wURL)).build();
        String responseStr = "";
        try {
            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (!(statusCode == 200)) {
                // may be the server or bad request, fatal error
                System.out.printf("got status code %d for URL %s, expecting 200 OK",
                    statusCode, w3wURL);
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
    
        Type w3wDetailType = new TypeToken<W3WDetail>() {}.getType();
        W3WDetail w3WDetail = new Gson().fromJson(responseStr, w3wDetailType);
        return new LongLat(w3WDetail.coordinates.lng, w3WDetail.coordinates.lat);
    }
}
