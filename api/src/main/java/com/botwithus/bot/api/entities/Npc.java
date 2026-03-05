package com.botwithus.bot.api.entities;

import com.botwithus.bot.api.GameAPI;
import com.botwithus.bot.api.model.Entity;
import com.botwithus.bot.api.model.NpcType;

import java.util.List;

/**
 * Rich wrapper for an NPC entity with convenient access to the NPC definition
 * and interaction helpers.
 *
 * <p>Obtain instances through {@link Npcs}:</p>
 * <pre>{@code
 * Npcs npcs = new Npcs(api);
 * Npc goblin = npcs.nearest("Goblin");
 * if (goblin != null) {
 *     System.out.println(goblin.name() + " combat: " + goblin.getCombatLevel());
 *     System.out.println("Options: " + goblin.getOptions());
 * }
 * }</pre>
 *
 * @see Npcs
 * @see EntityContext
 */
public class Npc extends EntityContext {

    private NpcType cachedType;

    public Npc(GameAPI api, Entity raw) {
        super(api, raw);
    }

    /**
     * Returns the NPC cache definition, fetched lazily.
     */
    public NpcType getType() {
        if (cachedType == null) {
            cachedType = api.getNpcType(typeId());
        }
        return cachedType;
    }

    /** The NPC's right-click interaction options. */
    public List<String> getOptions() {
        return getType().options();
    }

    /** Whether this NPC has a specific right-click option (case-insensitive). */
    public boolean hasOption(String option) {
        return getOptions().stream().anyMatch(o -> o != null && o.equalsIgnoreCase(option));
    }

    /** Whether this NPC is visible according to its definition. */
    public boolean isDefinitionVisible() {
        return getType().visible();
    }

    /** Whether this NPC is clickable according to its definition. */
    public boolean isClickable() {
        return getType().clickable();
    }

    /**
     * Returns the server index of the entity this NPC is following, or -1.
     */
    public int getFollowingIndex() {
        return getInfo().followingIndex();
    }

    /**
     * Whether this NPC is currently following/targeting another entity.
     */
    public boolean isFollowing() {
        return getFollowingIndex() != -1;
    }

    @Override
    public String toString() {
        return "Npc{" + name() + " id=" + typeId()
                + " @" + tileX() + "," + tileY() + "," + plane() + "}";
    }
}
