package com.botwithus.bot.api.entities;

import com.botwithus.bot.api.GameAPI;
import com.botwithus.bot.api.model.Entity;

/**
 * Rich wrapper for a player entity.
 *
 * <p>Obtain instances through {@link Players}:</p>
 * <pre>{@code
 * Players players = new Players(api);
 * Player nearest = players.nearest();
 * if (nearest != null) {
 *     System.out.println(nearest.name() + " combat: " + nearest.getCombatLevel());
 * }
 * }</pre>
 *
 * @see Players
 * @see EntityContext
 */
public class Player extends EntityContext {

    public Player(GameAPI api, Entity raw) {
        super(api, raw);
    }

    /**
     * The server index of the entity this player is following, or -1.
     */
    public int getFollowingIndex() {
        return getInfo().followingIndex();
    }

    /**
     * Whether this player is currently following/targeting another entity.
     */
    public boolean isFollowing() {
        return getFollowingIndex() != -1;
    }

    @Override
    public String toString() {
        return "Player{" + name()
                + " @" + tileX() + "," + tileY() + "," + plane() + "}";
    }
}
