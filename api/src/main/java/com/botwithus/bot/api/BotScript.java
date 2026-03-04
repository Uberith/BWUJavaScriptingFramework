package com.botwithus.bot.api;

/**
 * Service Provider Interface for bot scripts.
 *
 * <p>Implementations are discovered at runtime via {@link java.util.ServiceLoader}.
 * Each script follows a lifecycle of {@link #onStart} &rarr; {@link #onLoop} &rarr; {@link #onStop}.</p>
 *
 * <p>Annotate implementations with {@link ScriptManifest} to declare metadata.</p>
 *
 * @see ScriptManifest
 * @see ScriptContext
 */
public interface BotScript {

    /**
     * Called once when the script is started.
     * Use this to initialize state and cache references from the context.
     *
     * @param ctx the script context providing access to the {@link GameAPI} and event bus
     */
    void onStart(ScriptContext ctx);

    /**
     * Called repeatedly while the script is running.
     * The returned value controls the delay before the next invocation.
     *
     * @return delay in milliseconds before the next loop iteration, or {@code -1} to stop the script
     */
    int onLoop();

    /**
     * Called once when the script is stopped, either by returning {@code -1} from
     * {@link #onLoop()} or by an external stop request. Use this to clean up resources.
     */
    void onStop();
}
