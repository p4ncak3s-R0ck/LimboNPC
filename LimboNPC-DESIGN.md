# LimboNPC — Design Specification

**Status:** Draft design  
**Target:** LOOHP Limbo + Velocity  
**Primary use case:** Extremely lightweight Minecraft server-selection lobby using clickable NPCs  
**Design date:** 2026-08-22

---

## 1. Overview

LimboNPC is a two-plugin system for creating clickable NPCs inside a LOOHP Limbo server and using those NPCs as selectors for backend Minecraft servers connected through Velocity.

The intended administrator workflow is intentionally simple:

```text
1. Join the Limbo lobby.
2. Stand where an NPC should be.
3. Run:

   /limbonpc create survival survival

4. The NPC appears immediately.
5. Players click it.
6. Velocity executes:

   /server survival

   as the player who clicked the NPC.
```

The system does **not** directly connect the player with `createConnectionRequest()`.

Instead, it runs the normal Velocity `/server <server>` command as the clicking player. This preserves compatibility with:

- Velocity's built-in command pipeline
- `velocity.command.server`
- ServerPermissions
- LuckPerms
- `CommandExecuteEvent`
- `ServerPreConnectEvent`
- other Velocity plugins that observe, modify, or deny server transfers

LimboNPC should remain small, predictable, and configuration-driven.

---

# 2. Goals

## 2.1 Primary Goals

LimboNPC SHALL:

1. Run on the standalone LOOHP Limbo server.
2. Allow administrators to create NPCs while physically standing in the Limbo world.
3. Persist NPCs across restarts.
4. Spawn configured NPCs automatically when Limbo starts.
5. Detect player interaction with an NPC.
6. Forward a server-switch request to a companion Velocity plugin.
7. Have Velocity execute the standard:

   ```text
   /server <server>
   ```

   command as the clicking player.
8. Allow existing Velocity-side permission and routing plugins to make the final access decision.
9. Require no permissions for ordinary players to click NPCs.
10. Protect NPC administration with:

   ```text
   limbo-npc.npc.*
   ```

   and optional action-specific permissions.

---

## 2.2 Secondary Goals

The first stable release SHOULD support:

- player-like NPC entities
- NPC names
- player skins
- hologram text
- create / remove / move commands
- configuration reload
- immediate in-game updates
- server-name validation
- tab completion
- helpful error messages
- safe Velocity plugin messaging
- protocol versioning for Limbo ↔ Velocity messages

---

## 2.3 Non-Goals

LimboNPC is not intended to be:

- a Citizens replacement
- a general-purpose scripting engine
- a permissions plugin
- a replacement for Velocity's server routing
- a replacement for ServerPermissions
- a quest or dialogue NPC framework
- a database-backed distributed NPC platform

The initial implementation should remain focused on **server-selection NPCs**.

---

# 3. High-Level Architecture

```text
                    Minecraft Client
                          │
                          │ connect
                          ▼
                 ┌──────────────────┐
                 │     Velocity     │
                 │                  │
                 │ LimboNPC-Velocity│
                 └────────┬─────────┘
                          │
                          │ backend connection
                          ▼
                 ┌──────────────────┐
                 │   LOOHP Limbo    │
                 │                  │
                 │  LimboNPC-Limbo  │
                 │                  │
                 │   [NPC] Survival │
                 │   [NPC] Creative │
                 │   [NPC] Events   │
                 └────────┬─────────┘
                          │
                    player clicks NPC
                          │
                          ▼
                  custom plugin message
                          │
                          ▼
                 ┌──────────────────┐
                 │     Velocity     │
                 │                  │
                 │ validate request │
                 │ find player      │
                 │ validate server  │
                 │                  │
                 │ executeAsync(    │
                 │   player,        │
                 │   "server xyz"   │
                 │ )                │
                 └────────┬─────────┘
                          │
                          ▼
                Standard /server pipeline
                          │
             ┌────────────┼─────────────┐
             │            │             │
             ▼            ▼             ▼
       permissions   proxy plugins   pre-connect
             │            │             │
             └────────────┼─────────────┘
                          ▼
                     destination
                       server
```

---

# 4. Project Structure

The project should be a multi-module Java project.

Recommended layout:

```text
limbo-npc/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
├── DESIGN.md
│
├── common/
│   └── src/main/java/
│       └── .../protocol/
│
├── limbo/
│   ├── src/main/java/
│   └── src/main/resources/
│       └── plugin.yml
│
└── velocity/
    ├── src/main/java/
    └── src/main/resources/
```

Build artifacts:

```text
LimboNPC-Limbo.jar
LimboNPC-Velocity.jar
```

The `common` module should contain shared protocol models and constants but should not produce a separately installed plugin.

---

# 5. Components

## 5.1 Limbo Plugin

Artifact:

```text
LimboNPC-Limbo.jar
```

Installed into:

```text
<limbo-server>/plugins/
```

Responsibilities:

- load NPC configuration
- register `/limbonpc`
- check Limbo-side administration permissions
- create NPC entities
- despawn NPC entities
- update NPC metadata
- track NPC entity IDs
- detect NPC interactions
- send server-switch requests through the player connection
- save configuration changes
- reload configuration without a full server restart

The Limbo plugin does **not** decide whether a player may access a destination server.

---

## 5.2 Velocity Plugin

Artifact:

```text
LimboNPC-Velocity.jar
```

Installed into:

```text
<velocity>/plugins/
```

Responsibilities:

- register the LimboNPC plugin-message channel
- receive requests from the configured Limbo backend
- mark handled plugin messages as handled
- reject requests from untrusted sources
- identify the player associated with the request
- validate destination server names
- execute the normal Velocity `/server` command as the player
- log malformed or rejected requests when debug logging is enabled

---

# 6. Server Transfer Philosophy

A major design requirement is that LimboNPC MUST NOT bypass normal Velocity server permissions.

## 6.1 Incorrect Approach

Do not make NPC clicks perform:

```java
player.createConnectionRequest(server).connect();
```

as the primary transfer mechanism.

That would directly request a server connection and would make LimboNPC partially responsible for routing behavior.

---

## 6.2 Required Approach

The Velocity bridge should execute:

```java
proxy.getCommandManager().executeAsync(
    player,
    "server " + validatedServerName
);
```

The command string does not include the leading `/`.

Conceptually, this is equivalent to the player typing:

```text
/server survival
```

This means the existing proxy command and connection pipeline remains authoritative.

---

# 7. ServerPermissions Compatibility

ServerPermissions uses server-specific permission nodes in the form:

```text
serverpermissions.server.<server>
```

Examples:

```text
serverpermissions.server.limbo
serverpermissions.server.survival
serverpermissions.server.creative
serverpermissions.server.staff
```

LimboNPC does not reimplement these checks.

Example flow:

```text
Player clicks "Staff"
        │
        ▼
LimboNPC sends target = staff
        │
        ▼
Velocity validates target
        │
        ▼
Velocity executes as player:
server staff
        │
        ▼
normal server-access pipeline
        │
        ├── allowed → connect
        │
        └── denied  → existing plugin handles denial
```

This lets ServerPermissions remain the source of truth.

---

# 8. LimboNPC Permissions

## 8.1 Root Administrative Permission

```text
limbo-npc.npc.*
```

This permission grants all LimboNPC NPC-management operations.

---

## 8.2 Action Permissions

Supported action-specific nodes:

```text
limbo-npc.npc.create
limbo-npc.npc.remove
limbo-npc.npc.move
limbo-npc.npc.server
limbo-npc.npc.name
limbo-npc.npc.skin
limbo-npc.npc.hologram
limbo-npc.npc.info
limbo-npc.npc.list
limbo-npc.npc.reload
```

---

## 8.3 Wildcard Handling

LOOHP Limbo's current built-in permission manager performs exact permission-node comparisons.

Therefore LimboNPC must implement its wildcard behavior explicitly.

Permission checks should follow this logic:

```java
boolean can(CommandSender sender, String action) {
    return sender.hasPermission("limbo-npc.npc.*")
        || sender.hasPermission("limbo-npc.npc." + action);
}
```

For example:

```java
can(sender, "create");
```

accepts either:

```text
limbo-npc.npc.*
```

or:

```text
limbo-npc.npc.create
```

This gives the desired wildcard UX without relying on the underlying permission system to interpret wildcard nodes.

---

## 8.4 Normal Players

Normal players need **no LimboNPC permission** to interact with NPCs.

Access to the destination server is governed by Velocity and the installed Velocity-side permission plugins.

---

# 9. Commands

Root command:

```text
/limbonpc
```

Optional alias:

```text
/lnpc
```

---

## 9.1 Help

```text
/limbonpc
/limbonpc help
```

Permission:

```text
limbo-npc.npc.info
```

Displays available commands filtered by the sender's permissions.

---

## 9.2 Create

Primary convenience command:

```text
/limbonpc create <npc-id> <server>
```

Example:

```text
/limbonpc create survival survival
```

Behavior:

1. Require player command sender.
2. Validate NPC ID.
3. Validate that the ID does not already exist.
4. Capture the player's current location.
5. Capture yaw and pitch.
6. Create a default NPC definition.
7. Spawn it immediately.
8. Save `npcs.yml`.
9. Report success.

Permission:

```text
limbo-npc.npc.create
```

or:

```text
limbo-npc.npc.*
```

Default name:

```text
<green><bold>SURVIVAL
```

or simply the NPC ID until explicitly renamed.

---

## 9.3 Create With Same ID and Server

Optional shorthand:

```text
/limbonpc create <npc-id>
```

Example:

```text
/limbonpc create survival
```

Equivalent to:

```text
/limbonpc create survival survival
```

This is useful because the NPC ID and Velocity server name will commonly match.

---

## 9.4 Remove

```text
/limbonpc remove <npc-id>
```

Example:

```text
/limbonpc remove survival
```

Behavior:

- despawn NPC
- remove entity mappings
- remove from configuration
- save file

Permission:

```text
limbo-npc.npc.remove
```

---

## 9.5 Move

```text
/limbonpc move <npc-id>
```

Behavior:

- require player sender
- capture current player position
- capture current yaw/pitch
- move or respawn NPC
- save updated location

Permission:

```text
limbo-npc.npc.move
```

Example workflow:

```text
walk to new position
/limbonpc move survival
```

---

## 9.6 Set Destination Server

```text
/limbonpc server <npc-id> <server>
```

Example:

```text
/limbonpc server survival survival-01
```

Permission:

```text
limbo-npc.npc.server
```

The Limbo server does not need to know the full Velocity server registry at configuration time.

Velocity performs authoritative validation when the NPC is clicked.

---

## 9.7 Set Display Name

```text
/limbonpc name <npc-id> <MiniMessage...>
```

Example:

```text
/limbonpc name survival "<green><bold>SURVIVAL"
```

Permission:

```text
limbo-npc.npc.name
```

MiniMessage should be used if practical.

If MiniMessage is unavailable or unsuitable in the current Limbo API, Adventure components should be used directly with a small supported parser.

---

## 9.8 Set Skin by Username

```text
/limbonpc skin <npc-id> username <minecraft-name>
```

Example:

```text
/limbonpc skin survival username Steve
```

Permission:

```text
limbo-npc.npc.skin
```

The resolved skin data should be cached to disk so NPC startup does not depend on repeated external lookups.

---

## 9.9 Set Skin by Texture

Advanced mode:

```text
/limbonpc skin <npc-id> texture <value> <signature>
```

Permission:

```text
limbo-npc.npc.skin
```

Useful for custom or pre-resolved textures.

---

## 9.10 Clear Skin

```text
/limbonpc skin <npc-id> clear
```

---

## 9.11 Holograms

Add line:

```text
/limbonpc hologram <npc-id> add <text...>
```

Example:

```text
/limbonpc hologram survival add "<gray>Click to join!"
```

Set specific line:

```text
/limbonpc hologram <npc-id> set <line> <text...>
```

Remove line:

```text
/limbonpc hologram <npc-id> remove <line>
```

Clear:

```text
/limbonpc hologram <npc-id> clear
```

Permission:

```text
limbo-npc.npc.hologram
```

---

## 9.12 Info

```text
/limbonpc info <npc-id>
```

Example output:

```text
NPC: survival
Server: survival
World: world
Position: 10.5, 65.0, -4.5
Yaw: 90.0
Pitch: 0.0
Skin: username:Steve
Hologram Lines: 2
Enabled: true
```

Permission:

```text
limbo-npc.npc.info
```

---

## 9.13 List

```text
/limbonpc list
```

Example:

```text
NPCs (3):
- survival -> survival
- creative -> creative
- events -> events
```

Permission:

```text
limbo-npc.npc.list
```

---

## 9.14 Reload

```text
/limbonpc reload
```

Behavior:

1. read configuration
2. validate configuration
3. despawn removed NPCs
4. update changed NPCs
5. spawn newly added NPCs
6. rebuild entity lookup mappings

Permission:

```text
limbo-npc.npc.reload
```

---

# 10. Configuration

Recommended Limbo-side directory:

```text
plugins/LimboNPC/
├── config.yml
├── npcs.yml
└── skins/
```

---

## 10.1 `config.yml`

Example:

```yaml
bridge:
  channel: "limbo-npc:main"

npc:
  interaction-cooldown-ms: 500
  look-at-player: false
  default-hologram:
    - "<gray>Click to join!"

messages:
  prefix: "<dark_gray>[<green>LimboNPC<dark_gray>] "
  no-permission: "<red>You do not have permission."
  player-only: "<red>This command can only be used by a player."
  npc-not-found: "<red>NPC '<id>' does not exist."
  npc-created: "<green>Created NPC '<id>' targeting '<server>'."
  npc-removed: "<green>Removed NPC '<id>'."
  npc-moved: "<green>Moved NPC '<id>' to your current position."
```

---

## 10.2 `npcs.yml`

Example:

```yaml
version: 1

npcs:
  survival:
    enabled: true

    server: "survival"

    display-name: "<green><bold>SURVIVAL"

    location:
      world: "world"
      x: 10.5
      y: 65.0
      z: -4.5
      yaw: 90.0
      pitch: 0.0

    skin:
      type: "username"
      username: "Steve"

    hologram:
      - "<green><bold>Survival"
      - "<gray>Click to join!"

  creative:
    enabled: true

    server: "creative"

    display-name: "<aqua><bold>CREATIVE"

    location:
      world: "world"
      x: 14.5
      y: 65.0
      z: -4.5
      yaw: 90.0
      pitch: 0.0

    skin:
      type: "texture"
      value: "..."
      signature: "..."

    hologram:
      - "<aqua><bold>Creative"
      - "<gray>Click to join!"
```

---

# 11. NPC Runtime Model

Suggested internal model:

```java
public record NpcDefinition(
    String id,
    boolean enabled,
    String server,
    Component displayName,
    NpcLocation location,
    NpcSkin skin,
    List<Component> hologram
) {}
```

Location:

```java
public record NpcLocation(
    String world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {}
```

Runtime object:

```java
public final class RuntimeNpc {
    private final NpcDefinition definition;
    private final int entityId;
    private final UUID entityUuid;

    // Limbo entity references
    // hologram entity references
}
```

Mappings:

```java
Map<String, RuntimeNpc> npcById;
Map<Integer, RuntimeNpc> npcByEntityId;
```

Interaction lookup must be O(1) by entity ID.

---

# 12. Entity Interaction

LOOHP Limbo has its own entity and event systems and is not Bukkit/Paper.

The implementation must use the Limbo API and/or its underlying supported packet/event hooks.

The NPC interaction component should be isolated behind an adapter:

```java
public interface NpcInteractionAdapter {

    void register();

    void unregister();

    void onNpcSpawn(RuntimeNpc npc);

    void onNpcDespawn(RuntimeNpc npc);
}
```

When an entity-interaction packet is received:

```text
client interacts with entity ID 1402
             │
             ▼
lookup npcByEntityId[1402]
             │
      ┌──────┴──────┐
      │             │
   not NPC          NPC
      │             │
   ignore           ▼
               interaction
                 handler
```

The implementation should support both:

- right-click / interact
- left-click / attack

unless a configuration option explicitly restricts the interaction type.

For server selectors, either interaction may be treated as a click.

---

# 13. Interaction Cooldown

Players may generate duplicate interaction packets.

Maintain a small cooldown:

```java
Map<UUID, Long> lastNpcInteraction;
```

Default:

```text
500 ms
```

Logic:

```java
if (now - lastInteraction < cooldown) {
    return;
}
```

The cooldown prevents:

- command spam
- duplicate plugin messages
- accidental rapid server-switch attempts

---

# 14. Limbo → Velocity Protocol

Channel:

```text
limbo-npc:main
```

Protocol should be versioned from the beginning.

---

## 14.1 Transfer Request

Logical packet:

```text
type: CONNECT
version: 1
playerUuid: <uuid>
server: survival
npcId: survival
```

Recommended binary structure:

```text
u8      protocolVersion
u8      messageType
UUID    playerUuid
String  npcId
String  serverName
```

Message type:

```text
0x01 = CONNECT
```

The `playerUuid` is included for validation and logging, but Velocity should primarily associate the request with the actual player/backend connection when possible.

---

## 14.2 Why Include NPC ID?

Including the NPC ID is useful for:

- logging
- debugging
- metrics
- future click actions
- distinguishing multiple NPCs targeting the same server

Example log:

```text
[LimboNPC] Winterj clicked NPC 'survival' -> 'survival'
```

---

# 15. Velocity Message Handling

The Velocity plugin must register:

```text
limbo-npc:main
```

with the Velocity channel registrar.

When a message arrives:

```text
PluginMessageEvent
        │
        ▼
identifier == limbo-npc:main?
        │
       yes
        │
        ▼
mark message HANDLED
        │
        ▼
source is ServerConnection?
        │
       yes
        │
        ▼
source backend is trusted Limbo server?
        │
       yes
        │
        ▼
decode protocol
        │
        ▼
validate player
        │
        ▼
validate target
        │
        ▼
execute /server as player
```

The message must be marked handled so it is not forwarded to the client.

---

# 16. Trusted Backend Validation

The bridge must not trust every backend server by default.

Velocity config:

```yaml
trusted-limbo-servers:
  - "limbo"
```

When a plugin message is received:

```java
if (!(event.getSource() instanceof ServerConnection connection)) {
    return;
}

String backendName =
    connection.getServerInfo().getName();

if (!trustedLimboServers.contains(backendName)) {
    return;
}
```

This prevents an unrelated compromised backend from asking the proxy to run server-switch actions through LimboNPC.

---

# 17. Player Validation

Velocity should verify:

1. player UUID exists
2. player is online
3. player's current backend is the same backend that emitted the message
4. backend is a configured trusted Limbo backend

Conceptual check:

```java
Player player = proxy.getPlayer(uuid).orElse(null);

if (player == null) {
    return;
}

ServerConnection current =
    player.getCurrentServer().orElse(null);

if (current == null) {
    return;
}

if (!current.getServerInfo().getName()
        .equals(sourceBackendName)) {
    return;
}
```

This ties the request to the player's actual current connection.

---

# 18. Destination Validation

Never concatenate an arbitrary unvalidated string into a command.

First resolve it against Velocity's registered servers:

```java
Optional<RegisteredServer> target =
    proxy.getServer(serverName);
```

If absent:

```text
reject request
```

If present, use the canonical registered server name:

```java
String validatedName =
    target.get().getServerInfo().getName();
```

Then execute:

```java
proxy.getCommandManager().executeAsync(
    player,
    "server " + validatedName
);
```

This blocks command-injection attempts such as malformed target values.

---

# 19. Standard `/server` Execution

The Velocity plugin SHALL use:

```java
CommandManager.executeAsync(...)
```

and SHALL NOT use:

```java
executeImmediatelyAsync(...)
```

for the primary transfer action.

Reason:

`executeAsync` participates in the normal command event flow, while the "immediately" variant is specifically intended to bypass `CommandExecuteEvent`.

Expected code:

```java
private void connectUsingServerCommand(
        Player player,
        String serverName
) {
    proxy.getServer(serverName).ifPresent(server -> {
        String validated =
            server.getServerInfo().getName();

        proxy.getCommandManager().executeAsync(
            player,
            "server " + validated
        );
    });
}
```

---

# 20. Error Handling

## 20.1 Unknown Destination

If an NPC targets an unregistered Velocity server:

```text
NPC "events" points to unknown Velocity server "events".
```

Velocity MAY send the player:

```text
That server is currently unavailable.
```

However, avoid replacing normal `/server` errors when the server is valid and the command itself denies access.

---

## 20.2 Permission Denial

Do not generate a custom LimboNPC permission-denied message for destination access.

Allow the normal Velocity / ServerPermissions stack to provide the denial.

---

## 20.3 Velocity Bridge Missing

If the Limbo plugin sends a click request and no Velocity bridge exists, there may be no response.

Optional future enhancement:

```text
CONNECT_REQUEST
CONNECT_ACK
CONNECT_REJECT
```

For v1, one-way messaging is acceptable.

---

# 21. Skin Handling

Skin system interface:

```java
public interface SkinProvider {
    CompletableFuture<NpcSkin> resolve(String input);
}
```

Supported types:

```text
username
texture
none
```

Username skins should be cached.

Cache example:

```text
plugins/LimboNPC/skins/
└── Steve.json
```

Cached representation:

```json
{
  "username": "Steve",
  "value": "...",
  "signature": "...",
  "resolvedAt": "2026-08-22T23:00:00Z"
}
```

Do not block Limbo's main networking thread while resolving external skin data.

If resolution fails, retain the previous skin or use the default skin.

---

# 22. Holograms

Holograms should be tied to the parent NPC runtime object.

Example:

```text
       SURVIVAL
   Click to join!

       [NPC]
```

The hologram implementation may use:

- text display entities where supported
- armor stand style entities where required for compatibility
- the simplest Limbo-supported entity representation

The exact rendering implementation should be version-adaptable.

---

# 23. Optional Look-at-Player Behavior

Configuration:

```yaml
npc:
  look-at-player: false
```

If enabled, NPCs may rotate toward nearby players.

This should be disabled by default because a static server selector requires less network traffic and less update logic.

---

# 24. Live Administration Workflow

The intended experience:

```text
Admin joins lobby.

Admin walks to Survival portal.

/limbonpc create survival

NPC appears.

Admin:
  /limbonpc name survival "<green><bold>SURVIVAL"
  /limbonpc skin survival username Steve
  /limbonpc hologram survival add "<gray>Click to join!"

Admin walks to Creative portal.

/limbonpc create creative

NPC appears.

Done.
```

No manual coordinate editing should be required.

---

# 25. Persistence Strategy

All successful mutating commands should:

1. mutate in-memory state
2. apply runtime entity change
3. write configuration atomically

Safe write strategy:

```text
npcs.yml
   │
   ▼
write npcs.yml.tmp
   │
   ▼
fsync/close
   │
   ▼
replace npcs.yml
```

This reduces the chance of a truncated config after a crash.

---

# 26. Startup

On plugin enable:

```text
load config
    │
    ▼
load npc definitions
    │
    ▼
validate definitions
    │
    ▼
register commands
    │
    ▼
register interaction listener
    │
    ▼
spawn enabled NPCs
```

Invalid NPC definitions should be logged and skipped rather than crashing the entire Limbo server.

---

# 27. Shutdown

On plugin disable:

```text
stop tasks
    │
    ▼
unregister listeners where required
    │
    ▼
despawn runtime entities
    │
    ▼
flush pending config changes
```

---

# 28. Reload Semantics

`/limbonpc reload` should perform a diff.

Example:

```text
Old:
  survival
  creative

New:
  survival
  events
```

Actions:

```text
survival → update if changed
creative → despawn
events   → spawn
```

Avoid despawning and respawning every NPC if nothing changed.

---

# 29. Tab Completion

Examples:

```text
/limbonpc <TAB>
create
remove
move
server
name
skin
hologram
info
list
reload
```

NPC completion:

```text
/limbonpc move <TAB>
survival
creative
events
```

Skin modes:

```text
/limbonpc skin survival <TAB>
username
texture
clear
```

Completions should be filtered by permission where practical.

---

# 30. ID Rules

NPC IDs SHALL:

- be case-insensitive for command lookup
- be normalized to lowercase for persistence
- contain only:

```text
a-z
0-9
_
-
```

Recommended regex:

```regex
^[a-z0-9_-]{1,32}$
```

Examples:

```text
survival
creative
events-01
modded_1
```

Invalid:

```text
Survival Server
../../../foo
server;stop
```

---

# 31. Server Name Handling

Destination server names should be stored as strings but resolved by Velocity at interaction time.

Velocity server resolution is authoritative.

This permits:

- destination servers being temporarily offline
- changing backend addresses without touching LimboNPC
- Velocity-side dynamic registration in future implementations

---

# 32. Message Size Limits

LimboNPC protocol messages are intentionally tiny.

Server names and NPC IDs should be bounded.

Recommended limits:

```text
NPC ID:       32 characters
Server name:  64 characters
```

Reject oversized fields before allocating large buffers.

---

# 33. Security Requirements

The Velocity bridge SHALL:

1. accept only the LimboNPC channel
2. mark matching messages handled
3. require source to be a backend server connection
4. require backend name to be trusted
5. require player UUID to resolve to an online player
6. require player to currently be connected to the source backend
7. reject unsupported protocol versions
8. reject unsupported message types
9. enforce string length limits
10. resolve destination through Velocity's registered server list
11. execute only the fixed command:

   ```text
   server <validatedServer>
   ```

12. never execute arbitrary command text received from Limbo

Limbo should send a **server name**, not a command.

---

# 34. Threat Model

## 34.1 Malicious Client Sends Plugin Message

Velocity must not treat a client-originated message as a backend request.

Required check:

```java
event.getSource() instanceof ServerConnection
```

---

## 34.2 Compromised Non-Limbo Backend

Reject unless the backend is listed in:

```yaml
trusted-limbo-servers:
  - limbo
```

---

## 34.3 Command Injection Through Server Name

Prevent by resolving the requested server with:

```java
proxy.getServer(requestedName)
```

and using only the registered canonical name.

---

## 34.4 Fake UUID

Require:

- UUID resolves to an online player
- player is currently connected to the same backend that sent the request

---

## 34.5 NPC Spam

Use a per-player cooldown.

---

# 35. Velocity Configuration

Suggested configuration:

```yaml
channel: "limbo-npc:main"

trusted-limbo-servers:
  - "limbo"

debug: false
```

Possible future extension:

```yaml
trusted-limbo-servers:
  lobby:
    allowed-targets:
      - survival
      - creative
      - events
```

Not needed for v1 because `/server` and ServerPermissions already provide access control.

---

# 36. Logging

Normal startup:

```text
[LimboNPC] Loaded 4 NPCs.
[LimboNPC] Spawned 4 NPCs.
```

Debug interaction:

```text
[LimboNPC] winterj clicked NPC 'survival'.
[LimboNPC] Sent CONNECT request: winterj -> survival.
```

Velocity:

```text
[LimboNPC] CONNECT request from limbo: winterj -> survival
[LimboNPC] Executing as winterj: /server survival
```

Rejected:

```text
[LimboNPC] Rejected message from untrusted backend 'minigames'.
```

Avoid logging every click by default on large networks.

---

# 37. Suggested Java Packages

Common:

```text
dev.limbonpc.common
dev.limbonpc.common.protocol
```

Limbo:

```text
dev.limbonpc.limbo
dev.limbonpc.limbo.command
dev.limbonpc.limbo.config
dev.limbonpc.limbo.npc
dev.limbonpc.limbo.skin
dev.limbonpc.limbo.bridge
```

Velocity:

```text
dev.limbonpc.velocity
dev.limbonpc.velocity.bridge
dev.limbonpc.velocity.config
```

---

# 38. Suggested Core Classes

## Limbo

```text
LimboNpcPlugin
NpcManager
RuntimeNpc
NpcDefinition
NpcLocation
NpcSkin
NpcRepository
NpcCommand
NpcInteractionListener
VelocityBridgeClient
SkinService
```

## Velocity

```text
LimboNpcVelocityPlugin
PluginMessageListener
TransferRequestHandler
VelocityConfig
```

## Common

```text
Protocol
MessageType
ConnectRequest
ProtocolEncoder
ProtocolDecoder
```

---

# 39. Command Service Design

Avoid putting all command logic into one giant executor.

Suggested structure:

```java
public final class NpcCommandService {

    private final NpcManager npcManager;

    public void create(Player player, String id, String server) {}
    public void remove(CommandSender sender, String id) {}
    public void move(Player player, String id) {}
    public void setServer(CommandSender sender, String id, String server) {}
    public void setName(CommandSender sender, String id, Component name) {}
}
```

Permission helper:

```java
public final class NpcPermissions {

    public static boolean has(
            CommandSender sender,
            String action
    ) {
        return sender.hasPermission("limbo-npc.npc.*")
            || sender.hasPermission(
                "limbo-npc.npc." + action
            );
    }
}
```

---

# 40. NPC Manager

Conceptual API:

```java
public interface NpcManager {

    RuntimeNpc create(NpcDefinition definition);

    void remove(String id);

    void move(String id, NpcLocation location);

    void update(String id, NpcDefinition definition);

    Optional<RuntimeNpc> get(String id);

    Optional<RuntimeNpc> getByEntityId(int entityId);

    Collection<RuntimeNpc> all();

    void reload();
}
```

---

# 41. Bridge API

Limbo:

```java
public interface ProxyBridge {

    void requestServerTransfer(
        Player player,
        RuntimeNpc npc
    );
}
```

Velocity:

```java
public interface TransferHandler {

    void handle(
        ServerConnection source,
        ConnectRequest request
    );
}
```

---

# 42. Future Action System

Although v1 NPCs only connect to servers, internal design may represent the click action generically:

```yaml
action:
  type: "server"
  server: "survival"
```

Possible future actions:

```text
server
message
sound
```

Avoid allowing arbitrary commands by default.

If arbitrary commands are ever added, they should have a separate security model and should not reuse the trusted server-transfer protocol blindly.

---

# 43. Installation

## 43.1 Limbo

1. Install and configure LOOHP Limbo.
2. Stop Limbo.
3. Copy:

   ```text
   LimboNPC-Limbo.jar
   ```

   into:

   ```text
   plugins/
   ```

4. Start Limbo.
5. Verify the plugin loads.
6. Configure permissions.
7. Join through Velocity.
8. Create NPCs in-game.

---

## 43.2 Velocity

1. Stop Velocity.
2. Copy:

   ```text
   LimboNPC-Velocity.jar
   ```

   into:

   ```text
   plugins/
   ```

3. Start Velocity.
4. Configure the trusted Limbo backend server name.
5. Restart/reload as required by the plugin.
6. Verify the custom plugin channel registers.

---

# 44. Example Complete Setup

Velocity servers:

```toml
[servers]
limbo = "127.0.0.1:25566"
survival = "127.0.0.1:25567"
creative = "127.0.0.1:25568"
events = "127.0.0.1:25569"
```

ServerPermissions / LuckPerms conceptual grants:

```text
default:
  velocity.command.server
  serverpermissions.server.limbo
  serverpermissions.server.survival

builders:
  serverpermissions.server.creative

event:
  serverpermissions.server.events
```

Limbo administrator permission:

```text
limbo-npc.npc.*
```

Then inside Limbo:

```text
/limbonpc create survival
/limbonpc name survival "<green><bold>SURVIVAL"
/limbonpc hologram survival add "<gray>Click to join!"

/limbonpc create creative
/limbonpc name creative "<aqua><bold>CREATIVE"
/limbonpc hologram creative add "<gray>Click to join!"

/limbonpc create events
/limbonpc name events "<gold><bold>EVENTS"
/limbonpc hologram events add "<gray>Click to join!"
```

---

# 45. Example Click

Player clicks:

```text
SURVIVAL
```

Limbo runtime:

```text
entityId 1402
    ↓
npcByEntityId
    ↓
survival
    ↓
target = survival
```

Protocol:

```text
CONNECT
UUID = player UUID
NPC = survival
SERVER = survival
```

Velocity:

```text
source backend = limbo
player current backend = limbo
server "survival" exists
```

Then:

```java
commandManager.executeAsync(
    player,
    "server survival"
);
```

From there, LimboNPC is finished.

The rest of the operation belongs to Velocity and its normal plugins.

---

# 46. Version Compatibility

Initial target:

```text
LOOHP Limbo: current modern branch / 26.2 generation
Velocity API: 3.4.x-compatible API
Java: follow the minimum required by the selected Limbo and Velocity builds
```

The project should avoid unnecessarily depending on Minecraft-version-specific packet classes outside the isolated NPC interaction/rendering layer.

Version-specific handling should be contained behind adapters.

---

# 47. Current API Notes

As of the design date:

- LOOHP Limbo is a standalone server with its own plugin API.
- Its current source contains dedicated command, entity, event, permission, player, and plugin packages.
- `CommandSender` exposes `hasPermission(String)`.
- the built-in permission manager performs exact node matching.
- Velocity exposes `CommandManager.executeAsync(CommandSource, String)`.
- Velocity's plugin-message API supports receiving messages from backend servers.
- Velocity documentation specifically recommends marking handled plugin messages as handled to prevent unintended forwarding.
- ServerPermissions uses `serverpermissions.server.<server>` nodes.

The exact Limbo entity-interaction hook should be verified against the exact Limbo build selected during implementation. It should remain isolated behind the `NpcInteractionAdapter` so a protocol/API change does not affect the rest of the plugin.

---

# 48. Implementation Milestones

## Milestone 1 — Skeleton

- multi-module Gradle project
- Limbo plugin loads
- Velocity plugin loads
- common protocol module
- `/limbonpc` command registered

## Milestone 2 — Persistence

- `npcs.yml`
- create
- remove
- move
- list
- info
- reload

## Milestone 3 — NPC Rendering

- spawn player-like NPC
- display name
- skin
- rotation
- despawn
- respawn

## Milestone 4 — Interaction

- entity interaction detection
- entity ID lookup
- cooldown
- click callback

## Milestone 5 — Velocity Bridge

- custom channel
- protocol encoder/decoder
- trusted backend validation
- player validation
- registered-server validation
- `executeAsync(player, "server <name>")`

## Milestone 6 — Holograms

- hologram spawn
- add/set/remove/clear commands
- persistence

## Milestone 7 — Hardening

- malformed packet tests
- permission tests
- reload tests
- invalid config handling
- concurrency review
- atomic configuration saves

---

# 49. Acceptance Criteria

A release is considered functionally complete when this works:

```text
1. Start Velocity.
2. Start Limbo.
3. Join the network.
4. Enter the Limbo backend.
5. Grant an administrator:

   limbo-npc.npc.*

6. Administrator stands at a position.
7. Administrator runs:

   /limbonpc create survival

8. An NPC appears immediately.
9. Restart Limbo.
10. NPC reappears in the same place.
11. Normal player clicks NPC.
12. Velocity executes:

    /server survival

    as that player.

13. A player allowed by ServerPermissions connects.
14. A player denied by ServerPermissions remains denied.
15. No LimboNPC-specific destination permission system is required.
```

---

# 50. Design Principle

The core design rule is:

> **LimboNPC chooses what server the NPC represents. Velocity decides whether the player may actually go there.**

That separation keeps the Limbo plugin extremely lightweight and allows the existing Minecraft proxy ecosystem to continue doing what it already does well.

---

# References

- LOOHP Limbo: https://github.com/LOOHP/Limbo
- LOOHP Limbo on Modrinth: https://modrinth.com/mod/limbo-server
- Velocity plugin messaging: https://docs.papermc.io/velocity/dev/plugin-messaging/
- Velocity `CommandManager`: https://jd.papermc.io/velocity/3.4.0/com/velocitypowered/api/command/CommandManager.html
- ServerPermissions: https://modrinth.com/plugin/serverpermissions
