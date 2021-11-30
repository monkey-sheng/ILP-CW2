package uk.ac.ed.inf;

public class Flightpath {
    public String orderNo;
    public double fromLng, fromLat;
    public int angle;
    public double toLng, toLat;
    
    public Flightpath(String orderNo, double fromLng, double fromLat, int angle, double toLng,
                      double toLat) {
        this.orderNo = orderNo;
        this.fromLng = fromLng;
        this.fromLat = fromLat;
        this.angle = angle;
        this.toLng = toLng;
        this.toLat = toLat;
    }
    public Flightpath(String orderNo, LongLat from, int angle, LongLat to) {
        this.orderNo = orderNo;
        this.fromLng = from.longitude;
        this.fromLat = from.latitude;
        this.angle = angle;
        this.toLng = to.longitude;
        this.toLat = to.latitude;
    }
}
