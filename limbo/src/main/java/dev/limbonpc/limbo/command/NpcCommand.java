package dev.limbonpc.limbo.command;

import com.loohp.limbo.Limbo;
import com.loohp.limbo.commands.CommandExecutor;
import com.loohp.limbo.commands.CommandSender;
import com.loohp.limbo.commands.TabCompletor;
import com.loohp.limbo.location.Location;
import com.loohp.limbo.player.Player;
import com.loohp.limbo.plugins.LimboPlugin;
import dev.limbonpc.common.protocol.Protocol;
import dev.limbonpc.limbo.bridge.LimboMetrics;
import dev.limbonpc.limbo.bridge.VelocityBridgeClient;
import dev.limbonpc.limbo.config.LimboConfig;
import dev.limbonpc.limbo.npc.NpcDefinition;
import dev.limbonpc.limbo.npc.NpcLocation;
import dev.limbonpc.limbo.npc.NpcManager;
import dev.limbonpc.limbo.npc.NpcSkin;
import dev.limbonpc.limbo.npc.RuntimeNpc;
import dev.limbonpc.limbo.skin.SkinService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class NpcCommand implements CommandExecutor, TabCompletor {
    private static final List<String> ACTIONS = List.of("create", "remove", "enable", "disable", "move", "server", "name", "skin", "hologram", "info", "list", "status", "reload");
    private final LimboPlugin plugin;
    private final NpcManager npcs;
    private final SkinService skins;
    private final Supplier<LimboConfig> config;
    private final VelocityBridgeClient bridge;
    private final LimboMetrics metrics;
    private final ReloadAction reloadAction;
    private final MiniMessage mini = MiniMessage.miniMessage();

    public NpcCommand(LimboPlugin plugin, NpcManager npcs, SkinService skins, Supplier<LimboConfig> config,
                      VelocityBridgeClient bridge, LimboMetrics metrics, ReloadAction reloadAction) {
        this.plugin = plugin; this.npcs = npcs; this.skins = skins; this.config = config;
        this.bridge = bridge; this.metrics = metrics; this.reloadAction = reloadAction;
    }

    @Override public void execute(CommandSender sender, String[] args) {
        if (args.length == 0 || (!args[0].equalsIgnoreCase("limbonpc") && !args[0].equalsIgnoreCase("lnpc"))) return;
        if (args.length == 1 || args[1].equalsIgnoreCase("help")) { help(sender); return; }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!NpcPermissions.has(sender, action)) { message(sender, config.get().message("no-permission", "<red>You do not have permission.")); return; }
        try {
            switch (action) {
                case "create" -> create(sender, args);
                case "remove" -> remove(sender, args);
                case "enable" -> enabled(sender, args, true);
                case "disable" -> enabled(sender, args, false);
                case "move" -> move(sender, args);
                case "server" -> server(sender, args);
                case "name" -> name(sender, args);
                case "skin" -> skin(sender, args);
                case "hologram" -> hologram(sender, args);
                case "info" -> info(sender, args);
                case "list" -> list(sender);
                case "status" -> status(sender);
                case "reload" -> { reloadAction.reload(); message(sender, config.get().message("reloaded", "<green>Configuration reloaded.")); }
                default -> help(sender);
            }
        } catch (IllegalArgumentException e) { message(sender, "<red>" + escape(e.getMessage())); }
          catch (Exception e) { templated(sender, "operation-failed", "<red>Operation failed: <error>", "error", escape(e.getMessage())); e.printStackTrace(); }
    }

    private void create(CommandSender sender, String[] args) throws IOException {
        Player player = player(sender); requireArgs(args, 3, "/limbonpc create <npc-id> [server]");
        String id = args[2].toLowerCase(Locale.ROOT); String server = args.length > 3 ? args[3] : id;
        if (!NpcDefinition.ID_PATTERN.matcher(id).matches()) throw new IllegalArgumentException("ID must match [a-z0-9_-]{1,32}.");
        if (npcs.get(id).isPresent()) throw new IllegalArgumentException("NPC '" + id + "' already exists.");
        Location l = player.getLocation();
        NpcLocation location = new NpcLocation(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        String display = "<green><bold>" + id.toUpperCase(Locale.ROOT);
        npcs.create(new NpcDefinition(id, true, server, display, location, NpcSkin.none(), config.get().defaultHologram()));
        templated(sender, "npc-created", "<green>Created NPC '<white><id><green>' targeting '<white><server><green>'.", "id", id, "server", escape(server));
    }

    private void remove(CommandSender sender, String[] args) throws IOException { requireArgs(args, 3, "/limbonpc remove <npc-id>"); npcs.remove(args[2]); templated(sender, "npc-removed", "<green>Removed NPC '<white><id><green>'.", "id", escape(args[2])); }
    private void enabled(CommandSender sender, String[] args, boolean enabled) throws IOException {
        RuntimeNpc npc = npc(args, 3, "/limbonpc " + (enabled ? "enable" : "disable") + " <npc-id>");
        npcs.update(npc.definition().id(), npc.definition().withEnabled(enabled));
        String key = enabled ? "npc-enabled" : "npc-disabled";
        String fallback = enabled ? "<green>Enabled NPC '<id>'." : "<green>Disabled NPC '<id>'.";
        message(sender, config.get().message(key, fallback).replace("<id>", escape(npc.definition().id())));
    }
    private void move(CommandSender sender, String[] args) throws IOException {
        Player player = player(sender); RuntimeNpc npc = npc(args, 3, "/limbonpc move <npc-id>"); Location l = player.getLocation();
        NpcLocation location = new NpcLocation(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        npcs.update(npc.definition().id(), npc.definition().withLocation(location)); templated(sender, "npc-moved", "<green>Moved NPC '<white><id><green>'.", "id", npc.definition().id());
    }
    private void server(CommandSender sender, String[] args) throws IOException { RuntimeNpc npc = npc(args, 4, "/limbonpc server <npc-id> <server>"); npcs.update(npc.definition().id(), npc.definition().withServer(args[3])); templated(sender, "server-updated", "<green>NPC '<id>' now targets '<server>'.", "id", npc.definition().id(), "server", escape(args[3])); }
    private void name(CommandSender sender, String[] args) throws IOException { RuntimeNpc npc = npc(args, 4, "/limbonpc name <npc-id> <MiniMessage...>"); String value = join(args, 3); mini.deserialize(value); npcs.update(npc.definition().id(), npc.definition().withDisplayName(value)); templated(sender, "name-updated", "<green>Display name updated for '<id>'.", "id", npc.definition().id()); }

    private void skin(CommandSender sender, String[] args) throws IOException {
        RuntimeNpc npc = npc(args, 4, "/limbonpc skin <npc-id> <username|texture|clear> ..."); String mode = args[3].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "clear" -> { npcs.update(npc.definition().id(), npc.definition().withSkin(NpcSkin.none())); templated(sender, "skin-cleared", "<green>Skin cleared for '<id>'.", "id", npc.definition().id()); }
            case "texture" -> { requireArgs(args, 6, "/limbonpc skin <npc-id> texture <value> <signature>"); npcs.update(npc.definition().id(), npc.definition().withSkin(NpcSkin.texture(args[4], args[5]))); templated(sender, "skin-updated", "<green>Skin updated for '<id>'.", "id", npc.definition().id()); }
            case "username" -> {
                requireArgs(args, 5, "/limbonpc skin <npc-id> username <minecraft-name>"); String username = args[4]; NpcSkin cached = skins.cached(username);
                if (cached != null) { npcs.update(npc.definition().id(), npc.definition().withSkin(cached)); templated(sender, "skin-cached", "<green>Skin for '<id>' updated from cache.", "id", npc.definition().id()); return; }
                templated(sender, "skin-resolving", "<gray>Resolving skin for <username>...", "username", escape(username));
                skins.resolve(username).whenComplete((resolved, error) -> Limbo.getInstance().getScheduler().runTask(plugin, () -> {
                    if (error != null) { templated(sender, "skin-lookup-failed", "<red>Skin lookup failed: <error>", "error", escape(rootMessage(error))); return; }
                    try {
                        RuntimeNpc current = npcs.get(npc.definition().id()).orElse(null);
                        if (current != null) { npcs.update(current.definition().id(), current.definition().withSkin(resolved)); templated(sender, "skin-updated", "<green>Skin updated for '<id>'.", "id", current.definition().id()); }
                    } catch (IOException e) { templated(sender, "skin-save-failed", "<red>Could not save skin: <error>", "error", escape(e.getMessage())); }
                }));
            }
            default -> throw new IllegalArgumentException("Skin mode must be username, texture, or clear.");
        }
    }

    private void hologram(CommandSender sender, String[] args) throws IOException {
        RuntimeNpc npc = npc(args, 4, "/limbonpc hologram <npc-id> <add|set|remove|clear> ...");
        String mode = args[3].toLowerCase(Locale.ROOT); List<String> lines = new ArrayList<>(npc.definition().hologram());
        switch (mode) {
            case "add" -> { requireArgs(args, 5, "/limbonpc hologram <id> add <text...>"); lines.add(join(args, 4)); }
            case "set" -> { requireArgs(args, 6, "/limbonpc hologram <id> set <line> <text...>"); int line = line(args[4], lines); lines.set(line, join(args, 5)); }
            case "remove" -> { requireArgs(args, 5, "/limbonpc hologram <id> remove <line>"); lines.remove(line(args[4], lines)); }
            case "clear" -> lines.clear();
            default -> throw new IllegalArgumentException("Hologram action must be add, set, remove, or clear.");
        }
        npcs.update(npc.definition().id(), npc.definition().withHologram(lines)); templated(sender, "hologram-updated", "<green>Hologram updated for '<id>'.", "id", npc.definition().id());
    }

    private void info(CommandSender sender, String[] args) {
        RuntimeNpc runtime = npc(args, 3, "/limbonpc info <npc-id>"); NpcDefinition n = runtime.definition(); NpcLocation l = n.location();
        message(sender, "<green>NPC: <white>" + n.id() + "\n<green>Server: <white>" + escape(n.server()) + "\n<green>World: <white>" + escape(l.world()) +
                "\n<green>Position: <white>" + format(l.x()) + ", " + format(l.y()) + ", " + format(l.z()) + "\n<green>Yaw/Pitch: <white>" + l.yaw() + "/" + l.pitch() +
                "\n<green>Skin: <white>" + n.skin().type().name().toLowerCase(Locale.ROOT) + "\n<green>Hologram lines: <white>" + n.hologram().size() + "\n<green>Enabled: <white>" + n.enabled());
    }
    private void list(CommandSender sender) { CollectionView list = new CollectionView(npcs.all().stream().map(n -> "<gray>- <white>" + n.definition().id() + " <dark_gray>-> <green>" + escape(n.definition().server())).toList()); message(sender, "<green>NPCs (" + list.lines.size() + "):\n" + String.join("\n", list.lines)); }
    private void status(CommandSender sender) {
        long last = metrics.lastResponseAt();
        String response = last == 0 ? "never" : ((System.currentTimeMillis() - last) + "ms ago");
        message(sender, "<green>LimboNPC status\n<gray>Protocol: <white>" + Protocol.VERSION
                + "\n<gray>Channel: <white>" + escape(config.get().channel())
                + "\n<gray>NPCs: <white>" + npcs.all().size()
                + "\n<gray>Pending: <white>" + bridge.pendingCount()
                + "\n<gray>Last bridge response: <white>" + response
                + "\n<gray>Clicks/ACKs/Timeouts/Errors: <white>" + metrics.clicks() + "/" + metrics.acknowledgements()
                + "/" + metrics.timeouts() + "/" + metrics.errors());
        if (sender instanceof Player player) bridge.probe(player);
    }

    private void help(CommandSender sender) {
        if (!NpcPermissions.has(sender, "info")) { message(sender, "<red>You do not have permission."); return; }
        List<String> lines = new ArrayList<>(); lines.add("<green><bold>LimboNPC commands");
        for (String action : ACTIONS) if (NpcPermissions.has(sender, action)) lines.add("<gray>/limbonpc " + action);
        sender.sendMessage(mini.deserialize(String.join("\n", lines)));
    }

    @Override public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) return NpcPermissions.has(sender, "info") ? List.of("limbonpc", "lnpc") : List.of();
        if (!args[0].equalsIgnoreCase("limbonpc") && !args[0].equalsIgnoreCase("lnpc")) return List.of();
        if (args.length == 2) return filter(ACTIONS.stream().filter(a -> NpcPermissions.has(sender, a)).toList(), args[1]);
        if (args.length == 3 && List.of("remove", "enable", "disable", "move", "server", "name", "skin", "hologram", "info").contains(args[1].toLowerCase(Locale.ROOT))) return filter(npcs.definitions().stream().map(NpcDefinition::id).toList(), args[2]);
        if (args.length == 4 && args[1].equalsIgnoreCase("skin")) return filter(List.of("username", "texture", "clear"), args[3]);
        if (args.length == 4 && args[1].equalsIgnoreCase("hologram")) return filter(List.of("add", "set", "remove", "clear"), args[3]);
        return List.of();
    }

    private RuntimeNpc npc(String[] args, int required, String usage) { requireArgs(args, required, usage); return npcs.get(args[2]).orElseThrow(() -> new IllegalArgumentException("NPC '" + args[2] + "' does not exist.")); }
    private Player player(CommandSender sender) { if (sender instanceof Player p) return p; throw new IllegalArgumentException(config.get().message("player-only", "This command can only be used by a player.").replace("<red>", "")); }
    private static void requireArgs(String[] args, int count, String usage) { if (args.length < count) throw new IllegalArgumentException("Usage: " + usage); }
    private static int line(String value, List<String> lines) { try { int index = Integer.parseInt(value) - 1; if (index < 0 || index >= lines.size()) throw new NumberFormatException(); return index; } catch (NumberFormatException e) { throw new IllegalArgumentException("Line must be between 1 and " + lines.size() + "."); } }
    private void message(CommandSender sender, String value) { sender.sendMessage(mini.deserialize(config.get().prefix() + value)); }
    private void templated(CommandSender sender, String key, String fallback, String... replacements) {
        String value = config.get().message(key, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) value = value.replace("<" + replacements[i] + ">", replacements[i + 1]);
        message(sender, value);
    }
    private static String join(String[] args, int from) { return String.join(" ", Arrays.copyOfRange(args, from, args.length)); }
    private static List<String> filter(List<String> values, String prefix) { String p = prefix.toLowerCase(Locale.ROOT); return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(p)).toList(); }
    private static String escape(String value) { return value == null ? "unknown" : value.replace("<", "\\<"); }
    private static String rootMessage(Throwable value) { Throwable current = value; while (current.getCause() != null) current = current.getCause(); return current.getMessage(); }
    private static String format(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private record CollectionView(List<String> lines) {}
    @FunctionalInterface public interface ReloadAction { void reload() throws Exception; }
}
