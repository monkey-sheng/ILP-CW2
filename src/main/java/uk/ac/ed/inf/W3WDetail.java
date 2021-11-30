package uk.ac.ed.inf;


/*
 * For marshalling the json response of w3w details.json
 */
public class W3WDetail {
    String country;
    Square square;
    String nearestPlace;
    Coordinates coordinates;
    String words, language, map;
    
    static class Square {
        Southwest southwest;
        Northeast northeast;
    }
    static class Southwest {
        double lng, lat;
    }
    static class Northeast {
        double lng, lat;
    }
    static class Coordinates {
        double lng, lat;
    }
}
