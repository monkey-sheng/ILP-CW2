package uk.ac.ed.inf;


/**
 * Represents the Flightpath record to be written to database, encapsulates all the data required.
 */
public class Flightpath {
    public final String orderNo;
    public final double fromLng, fromLat;
    public final int angle;
    public final double toLng, toLat;
    
    // TODO
    public Flightpath(String orderNo, double fromLng, double fromLat, int angle, double toLng,
                      double toLat) {
        this.orderNo = orderNo;
        this.fromLng = fromLng;
        this.fromLat = fromLat;
        this.angle = angle;
        this.toLng = toLng;
        this.toLat = toLat;
    }
    
    /**
     * @param orderNo the order to which the drone is associated when this flightpath took place.
     * @param from The position of drone flying from.
     * @param angle the angle in which drone was flying.
     * @param to The position of drone flying to.
     */
    public Flightpath(String orderNo, LongLat from, int angle, LongLat to) {
        this.orderNo = orderNo;
        this.fromLng = from.longitude;
        this.fromLat = from.latitude;
        this.angle = angle;
        this.toLng = to.longitude;
        this.toLat = to.latitude;
    }
    
    public LongLat getFromLongLat() {
        return new LongLat(this.fromLng, this.fromLat);
    }
    
    public LongLat getToLongLat() {
        return new LongLat(this.toLng, this.toLat);
    }
}
