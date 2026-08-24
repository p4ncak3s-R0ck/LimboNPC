#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
./gradlew --no-daemon :limbo:jar :velocity:jar -PminecraftVersion=26

work="$(mktemp -d)"
cleanup() {
  [[ -z "${limbo_pid:-}" ]] || kill "$limbo_pid" 2>/dev/null || true
  [[ -z "${velocity_pid:-}" ]] || kill "$velocity_pid" 2>/dev/null || true
  if command -v taskkill >/dev/null 2>&1; then
    for port in 30100 25579; do
      pid=$(netstat -ano 2>/dev/null | awk -v p=":$port" '$2 ~ p && /LISTENING/{print $5; exit}')
      [[ -z "$pid" ]] || taskkill //PID "$pid" //F >/dev/null 2>&1 || true
    done
  fi
  rm -rf "$work" 2>/dev/null || true
}
trap cleanup EXIT
mkdir -p "$work/limbo/plugins/LimboNPC" "$work/velocity/plugins"

curl -fsSL 'https://repo.loohpjames.com/repository/com/loohp/Limbo/2026.0.2-ALPHA/Limbo-2026.0.2-ALPHA.jar' -o "$work/limbo/Limbo.jar"
curl -fsSL 'https://fill-data.papermc.io/v1/objects/b4e3164df5377346854dc6cb9e6a78022b1946ff69e89676313f5f6f1c6f0fb3/velocity-3.5.1-615.jar' -o "$work/velocity/velocity.jar"
cp limbo/build/libs/LimboNPC-Limbo-26.jar "$work/limbo/plugins/"
cp velocity/build/libs/LimboNPC-Velocity-26.jar "$work/velocity/plugins/"
cat > "$work/limbo/plugins/LimboNPC/npcs.yml" <<'YAML'
version: 1
npcs:
  smoke:
    enabled: true
    server: survival
    display-name: "<green>SMOKE"
    location: {world: world, x: 4.0, y: 0.0, z: 6.0, yaw: 0.0, pitch: 0.0}
    skin: {type: none}
    hologram: ["<gray>Smoke test"]
YAML

unzip -p "$work/limbo/Limbo.jar" server.properties > "$work/limbo/server.properties"
sed -i 's/server-port=.*/server-port=30100/' "$work/limbo/server.properties"
(cd "$work/velocity" && timeout 8s java -jar velocity.jar > bootstrap.log 2>&1 || true)
python_bin=python3
if ! python3 -c 'import sys' >/dev/null 2>&1; then python_bin=python; fi
"$python_bin" - "$work/velocity/velocity.toml" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]); s=p.read_text()
s=s.replace('bind = "0.0.0.0:25565"', 'bind = "127.0.0.1:25579"')
start=s.index('[servers]'); forced=s.index('[forced-hosts]'); advanced=s.index('[advanced]')
s=s[:start]+'[servers]\nlimbo = "127.0.0.1:30100"\ntry = ["limbo"]\n\n[forced-hosts]\n\n'+s[advanced:]
p.write_text(s)
PY

(cd "$work/limbo" && java -jar Limbo.jar --nogui > runtime.log 2>&1) & limbo_pid=$!
for _ in {1..30}; do grep -q 'Limbo server listening' "$work/limbo/runtime.log" 2>/dev/null && break; sleep 1; done
(cd "$work/velocity" && java -jar velocity.jar > runtime.log 2>&1) & velocity_pid=$!
for _ in {1..30}; do grep -q 'Done (' "$work/velocity/runtime.log" 2>/dev/null && break; sleep 1; done

if ! grep -q '\[LimboNPC\] Loaded 1 NPCs on protocol 776' "$work/limbo/runtime.log"; then cat "$work/limbo/runtime.log"; exit 1; fi
if ! grep -q 'LimboNPC bridge enabled' "$work/velocity/runtime.log"; then cat "$work/velocity/runtime.log"; exit 1; fi
if grep -Eiq 'error while enabling|could not safely enable|could not initialize' "$work/limbo/runtime.log" "$work/velocity/runtime.log"; then
  cat "$work/limbo/runtime.log" "$work/velocity/runtime.log"; exit 1
fi
echo 'LimboNPC 26.x runtime integration smoke test passed.'
