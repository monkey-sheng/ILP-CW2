package uk.ac.ed.inf;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class GeojsonManager {
    public final String server, port;
    public static final HttpClient client = HttpClient.newHttpClient();
    public static final String noFlyZoneEndpoint = "http://%s:%s/buildings/no-fly-zones.geojson";
    private List<Polygon> noFlyZones;
    private List<LongLat[]> noFlyZonePerimeters;
    private List<LongLat> waypoints;  // way points are vertices of no fly zones
    
    public GeojsonManager(String server, String port) {
        this.server = server;
        this.port = port;
        this.noFlyZones = getNoFlyZones();
        this.noFlyZonePerimeters = getNoFlyZonePerimeters();
        this.waypoints = getWaypoints();
    }
    
    public List<Polygon> getNoFlyZones() {
        if (this.noFlyZones != null) {
            return noFlyZones;
        }
        final String noFlyZoneURL = String.format(noFlyZoneEndpoint, server, port);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(noFlyZoneURL)).build();
        String responseStr = "";
        try {
            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (!(statusCode == 200)) {
                // may be the server or bad request, fatal error
                System.out.printf("got status code %d for URL %s, expecting 200 OK",
                    statusCode, noFlyZoneURL);
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
    
        FeatureCollection featureCollection = FeatureCollection.fromJson(responseStr);
        List<Polygon> noFlyZones = new ArrayList<>();
        // should not produce null pointer here
        for (Feature feature : featureCollection.features()) {
            Polygon zone = (Polygon) feature.geometry();
            noFlyZones.add(zone);
        }
        return noFlyZones;
    }
    
    /*
     * gets all line segments that make up the perimeters from all the polygons in no-fly-zone
     */
    public List<LongLat[]> getNoFlyZonePerimeters() {
        if (this.noFlyZonePerimeters != null) {
            return this.noFlyZonePerimeters;
        }
        List<Polygon> noFlyZones = getNoFlyZones();
        return GeometryUtils.getLineSegmentsFromPolygonList(noFlyZones);
    }
    
    /*
     * Use the vertices of no fly zone polygons as way points
     */
    public List<LongLat> getWaypoints() {
        if (this.waypoints != null) {
            return this.waypoints;
        }
        List<LongLat> waypoints = new ArrayList<>();
        List<Polygon> polygons = getNoFlyZones();
        for (Polygon polygon : polygons) {
            List<Point> coordinates = polygon.outer().coordinates();
            // the last point is the same as first
            for (Point point : coordinates.subList(0, coordinates.size() - 1)) {
                LongLat lngLat = new LongLat(point);
                waypoints.add(lngLat);
            }
        }
        return waypoints;
    }
    
    /**
     * We can basically travel along the perimeters of no fly zones,
     * which means if the line does intersect but is actually equal to a perimeter line,
     * then it doesn't count as crossing the zone. (start and end of line segment may be reversed)
     * Otherwise it is crossing.
     * @param line array of size 2 specifying the start and end for line segment
     * @return whether the line crosses the no fly zone
     */
    public boolean lineCrossesNoFlyZone(LongLat[] line) {
        for (LongLat[] perimeter : this.noFlyZonePerimeters) {
            // if the line given is actually right along a perimeter segment, no intersection
            if ((line[0].equals(perimeter[0]) && line[1].equals(perimeter[1])) ||
                (line[0].equals(perimeter[1]) && line[1].equals(perimeter[0]))) {
                return false;
            }
        }
        // the line isn't a segment of the perimeters
        // then if both start and end of line segment are vertices, definitely crossed
        if (this.waypoints.contains(line[0]) && this.waypoints.contains(line[1]))
            return true;
        // normal check
        for (LongLat[] perimeter : this.noFlyZonePerimeters) {
            if (GeometryUtils.lineSegmentIntersects(line, perimeter)) {
                return true;
            }
        }
        return false;
    }
    public boolean lineCrossesNoFlyZone(LongLat lineStart, LongLat lineEnd) {
        LongLat[] line = new LongLat[] {lineStart, lineEnd};
        return lineCrossesNoFlyZone(line);
    }
    
    public void writeGeojsonFile(String day, String month, String year, String content) {
        String fileName = String.format("drone-%s-%s-%s.geojson", day, month, year);
        try {
            FileWriter fileWriter = new FileWriter(fileName);
            fileWriter.write(content);
            fileWriter.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
