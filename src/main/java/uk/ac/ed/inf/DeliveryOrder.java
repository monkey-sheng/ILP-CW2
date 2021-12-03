package uk.ac.ed.inf;

import java.sql.Date;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates data needed for the drone to perform deliveries. Contains information from DBOrder,
 * as well as relevant data from database. This object relies on the fact that there can be at
 * most 2 shops the drone needs to visit to collect all ordered items.
 */
public class DeliveryOrder {
    public final String orderNo;
    public final Date deliveryDate;
    public final String customer;  // matriculation string
    public final String deliverTo;  // w3w string
    public final List<String> items;  // list of item names, fetched from database
    private LongLat pickup1 = null;
    private LongLat pickup2 = null;  // can be from 2 stores max
    public final LongLat deliveryLngLat;
    public final int totalCost;
    
    /**
     * @param orderNo The orderNo associated with the delivery.
     * @param deliveryDate The date of the delivery order.
     * @param customer The customer matriculation string.
     * @param deliverTo The w3w string for delivery location.
     * @param dbManager the DBManager instance to be used to fetch data from database.
     * @param menus the Menus instance to
     * @param what3WordsManager The what3Words manager responsible for translating w3w string to LongLat
     */
    public DeliveryOrder(String orderNo, Date deliveryDate, String customer, String deliverTo,
                         DBManager dbManager, Menus menus, What3WordsManager what3WordsManager) {
        this.orderNo = orderNo;
        this.deliveryDate = deliveryDate;
        this.customer = customer;
        this.deliverTo = deliverTo;
        this.deliveryLngLat = what3WordsManager.getLongLatFromWords(deliverTo);
        this.items = dbManager.getOrderItemsForNo(orderNo);
        this.totalCost = menus.getDeliveryCost(items);
        List<String> locations = menus.getItemsLocations(items);
        this.pickup1 = what3WordsManager.getLongLatFromWords(locations.get(0));
        if (locations.size() == 2) {
            this.pickup2 = what3WordsManager.getLongLatFromWords(locations.get(1));
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeliveryOrder order = (DeliveryOrder) o;
        return orderNo.equals(order.orderNo);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(orderNo, deliveryDate, customer, deliverTo, items, pickup1, pickup2, deliveryLngLat, totalCost);
    }
    
    public LongLat getPickup1() {
        return pickup1;
    }
    
    public LongLat getPickup2() {
        return pickup2;
    }
}
