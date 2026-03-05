package com.botwithus.bot.scripts.example;

import com.botwithus.bot.api.BotScript;
import com.botwithus.bot.api.GameAPI;
import com.botwithus.bot.api.ScriptContext;
import com.botwithus.bot.api.ScriptManifest;
import com.botwithus.bot.api.entities.*;

import java.util.List;

@ScriptManifest(
        name = "Example Script",
        version = "1.0",
        author = "BotWithUs",
        description = "A demo script showing the entity query API"
)
public class ExampleScript implements BotScript {

    private ScriptContext ctx;
    private int loopCount;

    // Scripter-friendly query facades — initialize once in onStart
    private Npcs npcs;
    private SceneObjects objects;
    private Players players;
    private GroundItems groundItems;

    @Override
    public void onStart(ScriptContext ctx) {
        this.ctx = ctx;
        this.loopCount = 0;

        GameAPI api = ctx.getGameAPI();
        this.npcs = new Npcs(api);
        this.objects = new SceneObjects(api);
        this.players = new Players(api);
        this.groundItems = new GroundItems(api);

        System.out.println("[ExampleScript] Started!");
    }

    @Override
    public int onLoop() {
        loopCount++;
        GameAPI api = ctx.getGameAPI();

        System.out.println("[ExampleScript] Loop #" + loopCount
                + " | Game cycle: " + api.getGameCycle()
                + " | Login state: " + api.getLoginState().state());

        // --- NPC queries ---
        // Nearest NPC by name
        Npc goblin = npcs.nearest("Goblin");
        if (goblin != null) {
            System.out.println("Nearest goblin: " + goblin.name()
                    + " combat=" + goblin.getCombatLevel()
                    + " distance=" + goblin.distanceToPlayer()
                    + " options=" + goblin.getOptions());
        }

        // Fluent query: visible, non-combat NPCs within 15 tiles
        List<Npc> idle = npcs.query()
                .named("Guard")
                .visible()
                .notInCombat()
                .withinDistance(15)
                .all();
        System.out.println("Idle guards nearby: " + idle.size());

        // Post-filter on definition fields
        Npc attackable = npcs.query()
                .named("Goblin")
                .filter(n -> n.hasOption("Attack"))
                .nearest();

        // --- Scene object queries ---
        SceneObject booth = objects.nearest("Bank booth");
        if (booth != null) {
            System.out.println("Bank booth: " + booth
                    + " size=" + booth.sizeX() + "x" + booth.sizeY()
                    + " options=" + booth.getOptions());
        }

        // Objects with transform resolution
        SceneObject tree = objects.query().namedExact("Tree").nearest();
        if (tree != null && tree.canTransform()) {
            System.out.println("Tree resolved: " + tree.resolveTransform().name());
        }

        // Fluent query: doors you can open
        SceneObject door = objects.query()
                .named("Door")
                .withinDistance(10)
                .filter(o -> o.hasOption("Open"))
                .nearest();

        // --- Player queries ---
        Player nearestPlayer = players.nearest();
        if (nearestPlayer != null) {
            System.out.println("Nearest player: " + nearestPlayer.name()
                    + " combat=" + nearestPlayer.getCombatLevel());
        }

        // --- Ground item queries ---
        GroundItems.Entry bones = groundItems.nearest(526);
        if (bones != null) {
            System.out.println("Bones on ground: " + bones.name()
                    + " x" + bones.quantity()
                    + " distance=" + bones.distanceToPlayer());
        }

        if (loopCount >= 10) {
            System.out.println("[ExampleScript] Completed 10 loops, stopping.");
            return -1;
        }

        return 1000;
    }

    @Override
    public void onStop() {
        System.out.println("[ExampleScript] Stopped after " + loopCount + " loops.");
    }
}
