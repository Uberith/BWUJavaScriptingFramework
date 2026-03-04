package com.botwithus.bot.api;

import com.botwithus.bot.api.event.EventBus;

/**
 * Context object passed to {@link BotScript#onStart} providing access to
 * the game API and the event bus.
 *
 * @see BotScript
 * @see GameAPI
 * @see EventBus
 */
public interface ScriptContext {

    /**
     * Returns the game API for interacting with the game client.
     *
     * @return the {@link GameAPI} instance
     */
    GameAPI getGameAPI();

    /**
     * Returns the event bus for subscribing to and publishing game events.
     *
     * @return the {@link EventBus} instance
     */
    EventBus getEventBus();
}
