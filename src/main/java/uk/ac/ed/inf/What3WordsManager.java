package uk.ac.ed.inf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

public class What3WordsManager {
    public final String server, port;
    public static final String w3wEndpoint = "http://%s:%s/words/%s/%s/%s/details.json";
    
    /**
     * Constructs an instance which will use the given server and port to fetch data.
     * @param server The server name.
     * @param port The port of server.
     */
    public What3WordsManager(String server, String port) {
        this.server = server;
        this.port = port;
        
    }
    
    /**
     * @param w3wString A valid w3w string (three words seperated by '.').
     * @return a LongLat object whose coordinates correspond to that of the w3w string.
     */
    public LongLat getLongLatFromWords(String w3wString) {
        // w3w has 3 words separated by '.', should not be any error unless invalid data given
        String[] words = w3wString.split("\\.");
        String w3wURL = String.format(w3wEndpoint, server, port, words[0], words[1], words[2]);
        String responseStr = Utils.sendHttpRequest(server, port, w3wURL);
        Type w3wDetailType = new TypeToken<W3WDetail>() {}.getType();
        W3WDetail w3WDetail = new Gson().fromJson(responseStr, w3wDetailType);
        return new LongLat(w3WDetail.coordinates.lng, w3WDetail.coordinates.lat);
    }
}
