package uk.ac.ed.inf;

import org.apache.derby.client.am.ClientPreparedStatement;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBManager {
    private final String jdbcString;
    private final Connection dbConn;
    
    private final String createTableDeliveriesStmt =
        "create table deliveries(orderNo char(8), " +
            "deliveredTo varchar(19), costInPence int)";
    private final String createTableFlightpathStmt =
        "create table flightpath(orderNo char(8), " +
            "fromLongitude double, " +
            "fromLatitude double, " +
            "angle integer, " +
            "toLongitude double, " +
            "toLatitude double)";
    
    public DBManager(String dbPort) {
        this.jdbcString = String.format("jdbc:derby://localhost:%s/derbyDB", dbPort);
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(jdbcString);
        }
        catch (SQLException e) {
            System.err.println("cannot establish a connection to database");
            e.printStackTrace();
            System.exit(-1);
        }
        this.dbConn = conn;
    }
    
    private void dropAndCreateTable(String tableName, String createTableStmt) {
        DatabaseMetaData dbMetadata = null;
        ResultSet resultSet = null;
        try {
            dbMetadata = this.dbConn.getMetaData();
        }
        catch (SQLException e) {
            System.err.printf("cannot drop table%s\n", tableName);
            e.printStackTrace();
            System.exit(-1);
        }
        try {
            resultSet =
                // just in case the param isn't in upper case as needed
                dbMetadata.getTables(null, null, tableName.toUpperCase(), null);
        }
        catch (SQLException e) {
            System.err.println("cannot get metadata for tables");
            e.printStackTrace();
            System.exit(-1);
        }
        Statement statement = null;
        try {
            statement = dbConn.createStatement();
        }
        catch (SQLException e) {
            System.err.println("cannot create a statement");
            e.printStackTrace();
            System.exit(-1);
        }
        try {
            // if there is such a table, else do nothing, no need to drop
            if (resultSet.next()) {
                statement.execute(String.format("drop table %s", tableName));
            }
        }
        catch (SQLException e) {
            System.err.println("cannot execute the drop table statement");
            e.printStackTrace();
            System.exit(-1);
        }
        
        // table is now dropped (or doesn't exist in the first place), create table
        
        try {
            Statement stmt = this.dbConn.createStatement();
            stmt.execute(createTableStmt);
        }
        catch (SQLException e) {
            System.err.println("cannot execute the create table statement");
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    public void dropAndCreateTableDeliveries() {
        this.dropAndCreateTable("deliveries", this.createTableDeliveriesStmt);
    }
    
    public void dropAndCreateTableFlightpath() {
        this.dropAndCreateTable("flightpath", this.createTableFlightpathStmt);
    }
    
    public List<DBOrder> getOrdersForDay(String day, String month, String year) {
        String dateStr = year + "-" + month + "-" + day;
        Date date = Date.valueOf(dateStr);
        final String query = "select * from orders where deliveryDate=(?)";
        ArrayList<DBOrder> dbOrders = new ArrayList<>();

        try {
            PreparedStatement psQuery = this.dbConn.prepareStatement(query);
            psQuery.setDate(1, date);
            ResultSet resultSet = psQuery.executeQuery();
            
            while (resultSet.next()) {
                String orderNo = resultSet.getString("orderNo");
                Date deliveryDate = resultSet.getDate("deliveryDate");
                String customer = resultSet.getString("customer");
                String deliverTo = resultSet.getString("deliverTo");
                dbOrders.add(new DBOrder(orderNo, deliveryDate, customer, deliverTo));
            }
        }
        catch (SQLException e) {
            System.err.println("cannot query database for orders for date");
            e.printStackTrace();
            System.exit(-1);
        }
        return dbOrders;
    }
    
    public List<String> getOrderItemsForNo(String No) {
        ArrayList<String> items = new ArrayList<>();
        try {
            PreparedStatement psQuery =
                dbConn.prepareStatement("select item from orderDetails where orderNo=(?)");
            psQuery.setString(1, No);
            ResultSet resultSet = psQuery.executeQuery();
            while (resultSet.next()) {
                String item = resultSet.getString("item");
                items.add(item);
            }
        }
        catch (SQLException e) {
            System.err.printf("cannot get items for order no %s\n", No);
            e.printStackTrace();
        }
        return items;
    }
    
    public void writeFlightpath(List<Flightpath> flightpaths) {
        // drop and create table done beforehand
        System.out.printf("INSERTING %d FLIGHTPATH RECORDS\n", flightpaths.size());
        try {
            for (Flightpath flightpath : flightpaths) {
                PreparedStatement ps = this.dbConn.prepareStatement(
                    "insert into flightpath values (?, ?, ?, ?, ?, ?)");
                ps.setString(1, flightpath.orderNo);
                ps.setDouble(2, flightpath.fromLng);
                ps.setDouble(3, flightpath.fromLat);
                ps.setInt(4, flightpath.angle);
                ps.setDouble(5, flightpath.toLng);
                ps.setDouble(6, flightpath.toLat);
                ps.execute();
            }
            
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void writeDeliveries(List<DeliveryOrder> deliveredOrders) {
        // drop and create table done beforehand
        try {
            for (DeliveryOrder order : deliveredOrders) {
                PreparedStatement ps = this.dbConn.prepareStatement(
                    "insert into deliveries values (?, ?, ?)");
                ps.setString(1, order.orderNo);
                ps.setString(2, order.deliverTo);
                ps.setInt(3, order.totalCost);
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
