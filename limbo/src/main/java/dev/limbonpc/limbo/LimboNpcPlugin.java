package dev.limbonpc.limbo;

import com.loohp.limbo.events.player.PlayerJoinEvent;
import com.loohp.limbo.player.Player;
import com.loohp.limbo.plugins.LimboPlugin;
import dev.limbonpc.limbo.bridge.LimboMetrics;
import dev.limbonpc.limbo.bridge.VelocityBridgeClient;
import dev.limbonpc.limbo.command.NpcCommand;
import dev.limbonpc.limbo.config.ConfigService;
import dev.limbonpc.limbo.config.LimboConfig;
import dev.limbonpc.limbo.npc.LimboCompatibility;
import dev.limbonpc.limbo.npc.NpcInteractionAdapter;
import dev.limbonpc.limbo.npc.NpcManager;
import dev.limbonpc.limbo.npc.LimboRuntime;
import dev.limbonpc.limbo.skin.SkinService;
import java.io.IOException;
import java.nio.file.Path;

public final class LimboNpcPlugin extends LimboPlugin {
    private ConfigService configService;
    private LimboConfig config;
    private NpcManager npcManager;
    private NpcInteractionAdapter interactions;
    private VelocityBridgeClient bridge;
    private LimboMetrics metrics;

    @Override public void onEnable() {
        try {
            LimboCompatibility.verify();
            Path data = getDataFolder().toPath();
            configService = new ConfigService(data);
            config = configService.loadConfig();
            metrics = new LimboMetrics();
            bridge = new VelocityBridgeClient(this, () -> config, metrics);
            npcManager = new NpcManager(configService);
            npcManager.loadAndSpawn();
            installListeners();
            SkinService skins = new SkinService(data.resolve("skins"));
            getServer().getPluginManager().registerCommands(this,
                    new NpcCommand(this, npcManager, skins, () -> config, bridge, metrics, this::reload));
            getServer().getConsole().sendMessage("[LimboNPC] Loaded " + npcManager.all().size()
                    + " NPCs on protocol " + LimboRuntime.protocol() + ".");
        } catch (Exception e) {
            throw new RuntimeException("Could not safely enable LimboNPC", e);
        }
    }

    private void installListeners() {
        interactions = new NpcInteractionAdapter(npcManager, bridge, metrics, config.interactionCooldownMs());
        getServer().getEventsManager().registerEvents(this, bridge);
        getServer().getEventsManager().registerEvents(this, interactions);
    }

    private void reload() throws IOException {
        for (Player player : getServer().getPlayers()) interactions.unregister(player);
        getServer().getEventsManager().unregisterAllListeners(this);
        config = configService.loadConfig();
        npcManager.replaceAll(configService.loadNpcs());
        installListeners();
        for (Player player : getServer().getPlayers()) interactions.onJoin(new PlayerJoinEvent(player));
    }

    @Override public void onDisable() {
        if (interactions != null) for (Player player : getServer().getPlayers()) interactions.unregister(player);
        if (bridge != null) bridge.clear();
        getServer().getEventsManager().unregisterAllListeners(this);
        getServer().getPluginManager().unregsiterAllCommands(this);
        getServer().getScheduler().cancelTask(this);
        if (npcManager != null) npcManager.shutdown();
    }
}
