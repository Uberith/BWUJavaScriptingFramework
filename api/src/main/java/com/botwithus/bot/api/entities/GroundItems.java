package com.botwithus.bot.api.entities;

import com.botwithus.bot.api.GameAPI;
import com.botwithus.bot.api.model.GroundItem;
import com.botwithus.bot.api.model.GroundItemStack;
import com.botwithus.bot.api.model.ItemType;
import com.botwithus.bot.api.model.LocalPlayer;
import com.botwithus.bot.api.query.EntityFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Query facade for ground items. Provides convenient methods for finding
 * items on the ground without manually constructing filters.
 *
 * <h3>Quick usage:</h3>
 * <pre>{@code
 * GroundItems ground = new GroundItems(api);
 *
 * // Nearest stack containing a specific item
 * GroundItems.Entry bones = ground.nearest(526);
 * if (bones != null) {
 *     System.out.println("Found " + bones.name() + " x" + bones.quantity()
 *         + " at " + bones.tileX() + "," + bones.tileY());
 * }
 *
 * // All ground items within range
 * List<GroundItems.Entry> nearby = ground.query()
 *     .withinDistance(15)
 *     .all();
 * }</pre>
 *
 * @see GroundItemStack
 * @see GroundItem
 */
public class GroundItems {

    private final GameAPI api;

    public GroundItems(GameAPI api) {
        this.api = api;
    }

    /**
     * Start a fluent ground item query.
     */
    public Query query() {
        return new Query(api);
    }

    /**
     * Returns the nearest ground item entry with the given item ID, or null.
     */
    public Entry nearest(int itemId) {
        return query().withItemId(itemId).nearest();
    }

    /**
     * Returns all ground item entries matching the given item ID.
     */
    public List<Entry> all(int itemId) {
        return query().withItemId(itemId).all();
    }

    /**
     * Returns all ground item entries within the given radius of the player.
     */
    public List<Entry> nearby(int radius) {
        return query().withinDistance(radius).all();
    }

    // ========================== Entry ==========================

    /**
     * A flattened ground item entry combining the stack location with an
     * individual item from that stack, plus resolved item definition.
     */
    public static class Entry {

        private final GameAPI api;
        private final GroundItemStack stack;
        private final GroundItem item;
        private ItemType cachedType;

        Entry(GameAPI api, GroundItemStack stack, GroundItem item) {
            this.api = api;
            this.stack = stack;
            this.item = item;
        }

        /** The stack handle. */
        public int handle() { return stack.handle(); }

        /** Item definition ID. */
        public int itemId() { return item.itemId(); }

        /** Stack size / quantity. */
        public int quantity() { return item.quantity(); }

        /** Tile X coordinate. */
        public int tileX() { return stack.tileX(); }

        /** Tile Y coordinate. */
        public int tileY() { return stack.tileY(); }

        /** Plane / height level. */
        public int plane() { return stack.tileZ(); }

        /** The underlying stack record. */
        public GroundItemStack stack() { return stack; }

        /** The underlying ground item record. */
        public GroundItem item() { return item; }

        /**
         * Returns the item definition, fetched lazily.
         */
        public ItemType getType() {
            if (cachedType == null) {
                cachedType = api.getItemType(item.itemId());
            }
            return cachedType;
        }

        /** The display name of the item. */
        public String name() { return getType().name(); }

        /** Whether this is a members-only item. */
        public boolean isMembers() { return getType().members(); }

        /** Chebyshev distance to the given tile. */
        public int distanceTo(int tileX, int tileY) {
            return Math.max(Math.abs(stack.tileX() - tileX), Math.abs(stack.tileY() - tileY));
        }

        /** Distance to the local player. */
        public int distanceToPlayer() {
            LocalPlayer lp = api.getLocalPlayer();
            return distanceTo(lp.tileX(), lp.tileY());
        }

        @Override
        public String toString() {
            return "GroundItem{" + name() + " x" + quantity()
                    + " @" + tileX() + "," + tileY() + "," + plane() + "}";
        }
    }

    // ========================== Query ==========================

    /**
     * Fluent query builder for ground items.
     */
    public static class Query {

        private final GameAPI api;
        private final EntityFilter.Builder filterBuilder = EntityFilter.builder();
        private int itemIdFilter = -1;

        Query(GameAPI api) {
            this.api = api;
        }

        /** Filter to stacks near a specific tile. */
        public Query within(int tileX, int tileY, int radius) {
            filterBuilder.tileX(tileX).tileY(tileY).radius(radius);
            return this;
        }

        /** Filter to stacks within the given radius of the local player. */
        public Query withinDistance(int radius) {
            var lp = api.getLocalPlayer();
            filterBuilder.tileX(lp.tileX()).tileY(lp.tileY()).radius(radius);
            return this;
        }

        /** Filter to a specific plane. */
        public Query onPlane(int plane) {
            filterBuilder.plane(plane);
            return this;
        }

        /** Filter results to only include a specific item ID. */
        public Query withItemId(int itemId) {
            this.itemIdFilter = itemId;
            return this;
        }

        /** Sort by distance and limit results. */
        public Query limit(int max) {
            filterBuilder.maxResults(max);
            return this;
        }

        /** Returns all matching ground item entries. */
        public List<Entry> all() {
            filterBuilder.sortByDistance(true);
            List<GroundItemStack> stacks = api.queryGroundItems(filterBuilder.build());
            List<Entry> entries = new ArrayList<>();
            for (GroundItemStack stack : stacks) {
                for (GroundItem gi : stack.items()) {
                    if (itemIdFilter == -1 || gi.itemId() == itemIdFilter) {
                        entries.add(new Entry(api, stack, gi));
                    }
                }
            }
            return entries;
        }

        /** Returns the nearest matching ground item entry, or null. */
        public Entry nearest() {
            List<Entry> entries = all();
            if (entries.isEmpty()) return null;
            LocalPlayer lp = api.getLocalPlayer();
            return entries.stream()
                    .min(Comparator.comparingInt(e -> e.distanceTo(lp.tileX(), lp.tileY())))
                    .orElse(null);
        }

        /** Returns true if at least one matching ground item exists. */
        public boolean exists() {
            return nearest() != null;
        }

        /** Returns the count of matching entries. */
        public int count() {
            return all().size();
        }
    }
}
