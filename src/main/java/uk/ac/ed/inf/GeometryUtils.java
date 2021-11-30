package uk.ac.ed.inf;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import uk.ac.ed.inf.LongLat;

import java.util.ArrayList;
import java.util.List;

public class GeometryUtils {
    
    public static boolean lineSegmentIntersects(LongLat line1Start, LongLat line1End,
                                                LongLat line2Start, LongLat line2End) {
        // represent line1 as p+tr, line2 as q+us
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
    public static boolean lineSegmentIntersects(LongLat[] line1, LongLat[] line2) {
        return lineSegmentIntersects(line1[0], line1[1], line2[0], line2[1]);
    }
    
    
    public static List<LongLat[]> getLineSegmentsFromPolygonList(List<Polygon> polygonList) {
        List<LongLat[]> lineSegmentsList = new ArrayList<>();
        for (Polygon polygon : polygonList) {
            List<Point> coordinates = polygon.outer().coordinates();
            if (!coordinates.get(0).equals(coordinates.get(coordinates.size()-1))) {
                // TODO: shouldn't happen
                System.err.println("POLYGON COORDINATES NOT CLOSED, FIRST POINT != LAST POINT");
            }
            for (int i = 0; i < coordinates.size() - 1; i++) {
                lineSegmentsList.add(new LongLat[] {new LongLat(coordinates.get(i)),
                    new LongLat(coordinates.get(i+1))});
            }
        }
        return lineSegmentsList;
    }
}
