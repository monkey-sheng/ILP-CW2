package uk.ac.ed.inf;

/**
 * Represents a point with its longitude and latitude
 */
public class LongLat {
    public double longitude;
    public double latitude;
    
    // drone confinement area constants
    private static final double LONGITUDE_CONFINEMENT_MIN = -3.192473;
    private static final double LONGITUDE_CONFINEMENT_MAX = -3.184319;
    private static final double LATITUDE_CONFINEMENT_MIN = 55.942617;
    private static final double LATITUDE_CONFINEMENT_MAX = 55.946233;
    
    // used as part of the definition of 2 points being close to each other, in degrees
    private static final double DISTANCE_TOLERANCE = 0.00015;
    
    // distance of every move for the drone, in degrees
    private static final double MOVE_DISTANCE = 0.00015;
    
    /**
     * Construct a point with given longitude and latitude.
     *
     * @param longitude longitude of the point.
     * @param latitude  latitude of the point.
     */
    public LongLat(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }
    
    /**
     * Whether or not the point is <i>strictly</i> within the defined confinement area.
     *
     * @return True if within confinement, false if not.
     */
    public boolean isConfined() {
        return this.longitude > LONGITUDE_CONFINEMENT_MIN &&
        this.longitude < LONGITUDE_CONFINEMENT_MAX &&
        this.latitude > LATITUDE_CONFINEMENT_MIN &&
        this.latitude < LATITUDE_CONFINEMENT_MAX;
    }
    
    /**
     * Computes the Pythagorean distance (in degrees) between two points.
     * Treating all the points as if they are on the same plane, instead of a sphere.
     * @param otherPoint to which distance is calculated from this point.
     * @return distance from this point to the given point.
     */
    public double distanceTo(LongLat otherPoint) {
        return Math.sqrt(Math.pow(this.longitude - otherPoint.longitude, 2) +
        Math.pow(this.latitude - otherPoint.latitude, 2));
    }
    
    /**
     * Definition of p1 being close to p2: distance between p1 and p2 is
     * <i>strictly</i> less than the distance tolerance (of 0.00015 degrees).
     *
     * @param otherPoint the point being compared to this point.
     * @return True if they are close, false if not.
     */
    public boolean closeTo(LongLat otherPoint) {
        return this.distanceTo(otherPoint) < DISTANCE_TOLERANCE;
    }
    
    /**
     * Calculates the next position if drone moves in the given angle.
     * 0 for east, 90 for north, etc.
     * <br><br>
     * Hover: a junk value of -999 means to hover, which does not alter the next position.
     * <br><br>
     * Definition of move: every move when flying is a straight line of length 0.00015 degrees,
     * in the specified direction, which MUST be a multiple of 10, within range [0, 350].<br>
     * If the given angle is NOT a multiple of 10, the effect is the same as hovering.
     *
     * @param angle the angle in which to travel, or to hover.
     * @return a new LongLat object representing the next position.
     */
    public LongLat nextPosition(int angle) {
        if (angle == -999) {
            return new LongLat(this.longitude, this.latitude);
        }
        // case for invalid angle, behaviour is undefined, might change later
        else if (angle % 10 != 0) {
            System.out.printf("angle %d not a multiple of 10, not moving%n", angle);
            return new LongLat(this.longitude, this.latitude);
        }
        else {
            double newLongitude = this.longitude + MOVE_DISTANCE * Math.cos(Math.toRadians(angle));
            double newLatitude = this.latitude + MOVE_DISTANCE * Math.sin(Math.toRadians(angle));
            return new LongLat(newLongitude, newLatitude);
        }
    }
}
