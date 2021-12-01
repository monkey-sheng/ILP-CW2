package uk.ac.ed.inf;

import java.sql.Date;

/**
 * Used in marshalling the database records of orders.
 */
public class DBOrder {
    public String orderNo;
    public Date deliveryDate;
    public String customer;  // matriculation string
    public String deliverTo;  // w3w string
    
    
    /**
     * @param orderNo Same as database column name.
     * @param deliveryDate Same as database column name.
     * @param customer Same as database column name.
     * @param deliverTo Same as database column name.
     */
    public DBOrder(String orderNo, Date deliveryDate, String customer, String deliverTo) {
        this.orderNo = orderNo;
        this.deliveryDate = deliveryDate;
        this.customer = customer;
        this.deliverTo = deliverTo;
    }
}
