package uk.ac.ed.inf;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for operating with Geojson files, such as retrieving and processing data of geojson
 * files, writing geojson files and related geo-computation.
 */
public class GeojsonManager {
    public final String server, port;
    //public static final HttpClient client = HttpClient.newHttpClient();
    public static final String noFlyZoneEndpoint = "http://%s:%s/buildings/no-fly-zones.geojson";
    private final List<Polygon> noFlyZones;
    private final List<LongLat[]> noFlyZonePerimeters;
    private final List<LongLat> waypoints;  // way points are vertices of no fly zones
    
    
    /**
     * @param server the server name, localhost.
     * @param port the port of server.
     */
    public GeojsonManager(String server, String port) {
        this.server = server;
        this.port = port;
        this.noFlyZones = getNoFlyZones();
        this.noFlyZonePerimeters = getNoFlyZonePerimeters();
        this.waypoints = getWaypoints();
    }
    
    /**
     * Perform an HTTP request to server and marshall its response, constructing a list of Polygons.
     * @return A list of polygons representing the no fly zone
     */
    public List<Polygon> getNoFlyZones() {
        if (this.noFlyZones != null) {
            return noFlyZones;
        }
        final String noFlyZoneURL = String.format(noFlyZoneEndpoint, server, port);
        String responseStr = Utils.sendHttpRequest(server, port, noFlyZoneURL);
    
        FeatureCollection featureCollection = FeatureCollection.fromJson(responseStr);
        List<Polygon> noFlyZones = new ArrayList<>();
        // should not produce null pointer here
        assert featureCollection.features() != null;
        for (Feature feature : featureCollection.features()) {
            Polygon zone = (Polygon) feature.geometry();
            noFlyZones.add(zone);
        }
        return noFlyZones;
    }
    
    /**
     * Gets all line segments that make up the perimeters from all the polygons in no-fly-zone.
     * @return A list of perimeter line segments represented as a size 2 array of LongLat.
     */
    public List<LongLat[]> getNoFlyZonePerimeters() {
        if (this.noFlyZonePerimeters != null) {
            return this.noFlyZonePerimeters;
        }
        List<Polygon> noFlyZones = getNoFlyZones();
        return Utils.getLineSegmentsFromPolygonList(noFlyZones);
    }
    
    /**
     * Use the vertices of no fly zone polygons as way points
     * @return A list of LongLat which represent each of the vertices of the no fly zone polygon.
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
     * Checks if a line segment is crossing the defined no fly zone. Starting and/or ending right
     * on the polygons' perimeters does NOT count as crossing.
     * @param lineStart Starting position of line segment.
     * @param lineEnd Ending position of line segment.
     * @return Whether the line crosses the no fly zone.
     */
    public boolean lineCrossesNoFlyZone(LongLat lineStart, LongLat lineEnd) {
        for (LongLat[] perimeter : this.noFlyZonePerimeters) {
            // if the line given is actually right along a perimeter segment, no intersection
            if ((lineStart.equals(perimeter[0]) && lineEnd.equals(perimeter[1])) ||
                (lineStart.equals(perimeter[1]) && lineEnd.equals(perimeter[0]))) {
                return false;
            }
        }
        // the line isn't a segment of the perimeters
        // then if both start and end of line segment are vertices, definitely crossed
        if (this.waypoints.contains(lineStart) && this.waypoints.contains(lineEnd))
            return true;
        // normal check
        for (LongLat[] perimeter : this.noFlyZonePerimeters) {
            if (Utils.lineSegmentIntersects(new LongLat[] {lineStart, lineEnd}, perimeter)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * @param line Array of size 2 specifying the start and end for line segment.
     * @return Whether the line crosses the no fly zone.
     */
    public boolean lineCrossesNoFlyZone(LongLat[] line) {
        return lineCrossesNoFlyZone(line[0], line[1]);
    }
    
    
    /**
     * @param day 2 character day of date, e.g. 02 or 29.
     * @param month 2 character month of date, e.g. 02 or 11.
     * @param year 4 character year of date, e.g. 2022 or 2023.
     * @param content The Json content to be written to file.
     */
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
