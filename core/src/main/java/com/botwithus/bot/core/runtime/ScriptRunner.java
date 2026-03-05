package com.botwithus.bot.core.runtime;

import com.botwithus.bot.api.BotScript;
import com.botwithus.bot.api.ScriptContext;
import com.botwithus.bot.api.ScriptManifest;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a single BotScript on its own virtual thread.
 * Lifecycle: onStart -> loop(onLoop + sleep) -> onStop
 */
public class ScriptRunner implements Runnable {

    private final BotScript script;
    private final ScriptContext context;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    public ScriptRunner(BotScript script, ScriptContext context) {
        this.script = script;
        this.context = context;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            String name = getScriptName();
            this.thread = Thread.ofVirtual().name("script-" + name).start(this);
        }
    }

    public void stop() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public BotScript getScript() {
        return script;
    }

    public ScriptManifest getManifest() {
        return script.getClass().getAnnotation(ScriptManifest.class);
    }

    public String getScriptName() {
        ScriptManifest manifest = getManifest();
        return manifest != null ? manifest.name() : script.getClass().getSimpleName();
    }

    @Override
    public void run() {
        try {
            script.onStart(context);
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                int delay = script.onLoop();
                if (delay < 0) break;
                if (delay > 0) {
                    Thread.sleep(delay);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[ScriptRunner] Script error in " + getScriptName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            running.set(false);
            try {
                script.onStop();
            } catch (Exception e) {
                System.err.println("[ScriptRunner] Error in onStop for " + getScriptName() + ": " + e.getMessage());
            }
        }
    }
}
