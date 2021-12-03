package uk.ac.ed.inf;


/**
 * The class for entry point of program.
 */
public class App
{
    /**
     * @param args The entry point
     */
    public static void main( String[] args )
    {
        String day = args[0];
        String month = args[1];
        String year = args[2];
        String serverPort = args[3];
        String dbPort = args[4];
        System.out.printf("running server at %s, running database at %s, for " +
            "%s-%s-%s\n", serverPort, dbPort, day, month, year);
        
        Drone drone = new Drone("localhost", serverPort, dbPort, day, month, year);
        drone.performDeliveries();
    }
}
