package dev.limbonpc.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import dev.limbonpc.common.protocol.Protocol;
import dev.limbonpc.velocity.bridge.VelocityMetrics;
import dev.limbonpc.velocity.config.VelocityConfig;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;

public final class VelocityAdminCommand implements SimpleCommand {
    @FunctionalInterface public interface ReloadAction { boolean reload(); }

    private final Supplier<VelocityConfig> config;
    private final VelocityMetrics metrics;
    private final ReloadAction reload;
    private final boolean reloadOnly;

    public VelocityAdminCommand(Supplier<VelocityConfig> config, VelocityMetrics metrics, ReloadAction reload, boolean reloadOnly) {
        this.config = config; this.metrics = metrics; this.reload = reload; this.reloadOnly = reloadOnly;
    }

    @Override public void execute(Invocation invocation) {
        String action = reloadOnly ? "reload" : invocation.arguments().length == 0 ? "status" : invocation.arguments()[0].toLowerCase();
        if (action.equals("reload")) {
            boolean success = reload.reload();
            invocation.source().sendMessage(Component.text(config.get().message(success ? "reload-success" : "reload-failed",
                    success ? "LimboNPC configuration reloaded." : "LimboNPC reload failed; check the proxy log.")));
            return;
        }
        if (!action.equals("status")) {
            invocation.source().sendMessage(Component.text("Usage: /limbonpcvelocity [status|reload]")); return;
        }
        VelocityConfig value = config.get();
        invocation.source().sendMessage(Component.text("LimboNPC Velocity status\n"
                + "Protocol: " + Protocol.VERSION + "\nChannel: " + value.channel() + "\nTrusted: " + value.trustedLimboServers()
                + "\nReceived/Dispatched/Rejected/Malformed/Rate-limited/Health: "
                + metrics.receivedCount() + "/" + metrics.dispatchedCount() + "/" + metrics.rejectedCount() + "/"
                + metrics.malformedCount() + "/" + metrics.rateLimitedCount() + "/" + metrics.healthCheckCount()));
    }

    @Override public List<String> suggest(Invocation invocation) {
        if (reloadOnly) return List.of();
        String prefix = invocation.arguments().length == 0 ? "" : invocation.arguments()[0].toLowerCase();
        return List.of("status", "reload").stream().filter(value -> value.startsWith(prefix)).toList();
    }

    @Override public boolean hasPermission(Invocation invocation) { return invocation.source().hasPermission("limbo-npc.velocity.admin"); }
}
