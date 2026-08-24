package dev.limbonpc.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.limbonpc.velocity.bridge.PluginMessageListener;
import dev.limbonpc.velocity.bridge.VelocityMetrics;
import dev.limbonpc.velocity.config.VelocityConfig;
import java.nio.file.Path;
import org.slf4j.Logger;

@Plugin(id = "limbo-npc", name = "LimboNPC", version = "1.0.0", description = "Velocity bridge for LimboNPC", authors = {"LimboNPC"})
public final class LimboNpcVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final VelocityMetrics metrics = new VelocityMetrics();
    private volatile VelocityConfig config;
    private volatile MinecraftChannelIdentifier channel;

    @Inject public LimboNpcVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy; this.logger = logger; this.dataDirectory = dataDirectory;
    }

    @Subscribe public void onInitialize(ProxyInitializeEvent event) {
        if (!reload()) return;
        proxy.getEventManager().register(this, new PluginMessageListener(proxy, logger, () -> config, () -> channel, metrics));
        CommandManager commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder("limbonpcvelocity").aliases("lnpcvelocity").plugin(this).build(),
                new VelocityAdminCommand(() -> config, metrics, this::reload, false));
        commands.register(commands.metaBuilder("limbonpcreload").plugin(this).build(),
                new VelocityAdminCommand(() -> config, metrics, this::reload, true));
        logger.info("LimboNPC bridge enabled on {} (trusted backends: {}, protocol: {}).",
                config.channel(), config.trustedLimboServers(), dev.limbonpc.common.protocol.Protocol.VERSION);
    }

    public synchronized boolean reload() {
        try {
            VelocityConfig loaded = VelocityConfig.load(dataDirectory);
            MinecraftChannelIdentifier loadedChannel = MinecraftChannelIdentifier.from(loaded.channel());
            if (channel == null || !channel.equals(loadedChannel)) {
                if (channel != null) proxy.getChannelRegistrar().unregister(channel);
                proxy.getChannelRegistrar().register(loadedChannel);
                channel = loadedChannel;
            }
            config = loaded;
            logger.info("Loaded LimboNPC config: channel={}, trusted={}, rateLimitMs={}, debug={}",
                    loaded.channel(), loaded.trustedLimboServers(), loaded.rateLimitMs(), loaded.debug());
            return true;
        } catch (Exception e) {
            logger.error("Could not reload LimboNPC; keeping the previous configuration", e);
            return false;
        }
    }

    @Subscribe public void onShutdown(ProxyShutdownEvent event) {
        if (channel != null) proxy.getChannelRegistrar().unregister(channel);
        proxy.getCommandManager().unregister("limbonpcvelocity");
        proxy.getCommandManager().unregister("limbonpcreload");
        logger.info("LimboNPC metrics: received={}, dispatched={}, rejected={}, malformed={}, rateLimited={}, healthChecks={}",
                metrics.receivedCount(), metrics.dispatchedCount(), metrics.rejectedCount(), metrics.malformedCount(),
                metrics.rateLimitedCount(), metrics.healthCheckCount());
    }
}
