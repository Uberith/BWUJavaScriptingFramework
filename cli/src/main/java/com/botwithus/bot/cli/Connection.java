package com.botwithus.bot.cli;

import com.botwithus.bot.core.pipe.PipeClient;
import com.botwithus.bot.core.rpc.RpcClient;
import com.botwithus.bot.core.runtime.ScriptRuntime;

public class Connection {

    private final String name;
    private final PipeClient pipe;
    private final RpcClient rpc;
    private final ScriptRuntime runtime;

    public Connection(String name, PipeClient pipe, RpcClient rpc, ScriptRuntime runtime) {
        this.name = name;
        this.pipe = pipe;
        this.rpc = rpc;
        this.runtime = runtime;
    }

    public String getName() { return name; }
    public PipeClient getPipe() { return pipe; }
    public RpcClient getRpc() { return rpc; }
    public ScriptRuntime getRuntime() { return runtime; }

    /** Disconnect the pipe without stopping scripts on the game client. */
    public void close() {
        try { rpc.close(); } catch (Exception ignored) {}
    }

    /** Stop all scripts AND close the connection. */
    public void closeAndStopScripts() {
        try { runtime.stopAll(); } catch (Exception ignored) {}
        close();
    }
}
