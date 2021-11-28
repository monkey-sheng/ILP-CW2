package uk.ac.ed.inf;

import java.util.ArrayList;

/**
 * Used for deserializing entries in menus.json
 */
public class MenuEntry {
    public String name;
    public String location;
    public ArrayList<MenuItem> menu;
    static class MenuItem {
        String item;
        int pence;
    }
}
