package dev.limbonpc.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.limbonpc.velocity.bridge.PluginMessageListener;
import dev.limbonpc.velocity.config.VelocityConfig;
import java.nio.file.Path;
import org.slf4j.Logger;

@Plugin(id = "limbo-npc", name = "LimboNPC", version = "1.0.0", description = "Velocity bridge for LimboNPC", authors = {"LimboNPC"})
public final class LimboNpcVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private MinecraftChannelIdentifier channel;

    @Inject public LimboNpcVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy; this.logger = logger; this.dataDirectory = dataDirectory;
    }

    @Subscribe public void onInitialize(ProxyInitializeEvent event) {
        try {
            VelocityConfig config = VelocityConfig.load(dataDirectory);
            channel = MinecraftChannelIdentifier.from(config.channel());
            proxy.getChannelRegistrar().register(channel);
            proxy.getEventManager().register(this, new PluginMessageListener(proxy, logger, config, channel));
            logger.info("LimboNPC bridge enabled on {} (trusted backends: {}).", config.channel(), config.trustedLimboServers());
        } catch (Exception e) {
            logger.error("Could not initialize LimboNPC", e);
        }
    }

    @Subscribe public void onShutdown(ProxyShutdownEvent event) {
        if (channel != null) proxy.getChannelRegistrar().unregister(channel);
    }
}
