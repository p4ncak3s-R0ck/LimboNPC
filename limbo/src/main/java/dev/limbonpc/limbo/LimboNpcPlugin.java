package dev.limbonpc.limbo;

import com.loohp.limbo.events.player.PlayerJoinEvent;
import com.loohp.limbo.player.Player;
import com.loohp.limbo.plugins.LimboPlugin;
import dev.limbonpc.limbo.bridge.VelocityBridgeClient;
import dev.limbonpc.limbo.command.NpcCommand;
import dev.limbonpc.limbo.config.ConfigService;
import dev.limbonpc.limbo.config.LimboConfig;
import dev.limbonpc.limbo.npc.NpcInteractionAdapter;
import dev.limbonpc.limbo.npc.NpcManager;
import dev.limbonpc.limbo.skin.SkinService;
import java.io.IOException;
import java.nio.file.Path;

public final class LimboNpcPlugin extends LimboPlugin {
    private ConfigService configService;
    private LimboConfig config;
    private NpcManager npcManager;
    private NpcInteractionAdapter interactions;

    @Override public void onEnable() {
        try {
            Path data = getDataFolder().toPath();
            configService = new ConfigService(data);
            config = configService.loadConfig();
            npcManager = new NpcManager(configService);
            npcManager.loadAndSpawn();
            installInteractionAdapter();
            SkinService skins = new SkinService(data.resolve("skins"));
            getServer().getPluginManager().registerCommands(this, new NpcCommand(this, npcManager, skins, () -> config, this::reload));
            getServer().getConsole().sendMessage("[LimboNPC] Loaded " + npcManager.all().size() + " NPCs.");
        } catch (Exception e) {
            throw new RuntimeException("Could not enable LimboNPC", e);
        }
    }

    private void installInteractionAdapter() {
        interactions = new NpcInteractionAdapter(npcManager, new VelocityBridgeClient(config.channel()), config.interactionCooldownMs());
        getServer().getEventsManager().registerEvents(this, interactions);
    }

    private void reload() throws IOException {
        for (Player player : getServer().getPlayers()) interactions.unregister(player);
        getServer().getEventsManager().unregisterAllListeners(this);
        config = configService.loadConfig();
        npcManager.replaceAll(configService.loadNpcs());
        installInteractionAdapter();
        for (Player player : getServer().getPlayers()) interactions.onJoin(new PlayerJoinEvent(player));
    }

    @Override public void onDisable() {
        if (interactions != null) for (Player player : getServer().getPlayers()) interactions.unregister(player);
        getServer().getEventsManager().unregisterAllListeners(this);
        getServer().getPluginManager().unregsiterAllCommands(this);
        getServer().getScheduler().cancelTask(this);
        if (npcManager != null) npcManager.shutdown();
    }
}
