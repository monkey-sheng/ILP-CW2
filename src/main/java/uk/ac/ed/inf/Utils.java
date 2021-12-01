package uk.ac.ed.inf;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for HTTP and some mathematical calculations, should not be instantiated,
 * methods are all static.
 */
public class Utils {
    
    public static final HttpClient client = HttpClient.newHttpClient();
    
    /**
     * @param line1Start starting point of first line segment.
     * @param line1End ending point of first  line segment.
     * @param line2Start starting point of second line segment.
     * @param line2End ending point of second line segment.
     * @return True if there is a point that belongs to both line segments.
     */
    public static boolean lineSegmentIntersects(LongLat line1Start, LongLat line1End,
                                                LongLat line2Start, LongLat line2End) {
        // represent line1 as p+t*r, line2 as q+u*s where t and u are scalars and other are vectors
        LongLat p = line1Start;
        LongLat q = line2Start;
        LongLat r = line1End.minus(line1Start);
        LongLat s = line2End.minus(line2Start);
        double rsCross = r.crossProduct(s);
        if (rsCross != 0) {
            double t = q.minus(p).crossProduct(s) / rsCross;
            double u = q.minus(p).crossProduct(r) / rsCross;
            // strictly less than, start/end point of segment meeting doesn't count as intersection
            // this depends on the caller doing some checks, see the caller implementation
            return 0 < t && t < 1 && 0 < u && u < 1;
        }
        return false;
    }
    
    /**
     * @param line1 First line segment to check.
     * @param line2 Second line segment to check.
     * @return True if there is a point that belongs to both line segments.
     */
    public static boolean lineSegmentIntersects(LongLat[] line1, LongLat[] line2) {
        return lineSegmentIntersects(line1[0], line1[1], line2[0], line2[1]);
    }
    
    /**
     * Gets a list of size 2 array representing line segments of perimeters of the polygons.
     * @param polygonList A list of polygons.
     * @return The line segments that make up the perimeters of the list of polygons.
     */
    public static List<LongLat[]> getLineSegmentsFromPolygonList(List<Polygon> polygonList) {
        List<LongLat[]> lineSegmentsList = new ArrayList<>();
        for (Polygon polygon : polygonList) {
            List<Point> coordinates = polygon.outer().coordinates();
            if (!coordinates.get(0).equals(coordinates.get(coordinates.size()-1))) {
                // shouldn't happen
                System.err.println("POLYGON COORDINATES NOT CLOSED, FIRST POINT != LAST POINT");
            }
            for (int i = 0; i < coordinates.size() - 1; i++) {
                lineSegmentsList.add(new LongLat[] {new LongLat(coordinates.get(i)),
                    new LongLat(coordinates.get(i+1))});
            }
        }
        return lineSegmentsList;
    }
    
    /**
     * Performs HTTP request, will exit the program if errors encountered.
     * @param server The server name.
     * @param port The port of server.
     * @param URL The URL endpoint to reach.
     * @return A String HTTP response from server.
     */
    public static String sendHttpRequest(String server, String port, String URL) {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(URL)).build();
        String responseStr = "";
        try {
            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (!(statusCode == 200)) {
                // may be the server or bad request, fatal error
                System.err.printf("got status code %d for URL %s, expecting 200 OK",
                    statusCode, URL);
                System.exit(1); // Exit the application
            }
            responseStr = response.body();
        }
        catch (java.net.ConnectException e) {
            System.err.println("Fatal error: Unable to connect to " +
                server + " at port " + port + ".");
            System.exit(1); // Exit the application
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1); // Exit the application
        }
        return responseStr;
    }
}
