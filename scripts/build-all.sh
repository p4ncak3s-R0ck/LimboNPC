#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
versions=("26" "1.21" "1.20")

mkdir -p build/artifacts
find build/artifacts -mindepth 1 -maxdepth 1 -type f -name '*.jar' -delete
rm -f limbo/build/libs/LimboNPC-Limbo-*.jar velocity/build/libs/LimboNPC-Velocity-*.jar

for version in "${versions[@]}"; do
  echo "Building compatibility range ${version}.x"
  ./gradlew --no-daemon :common:test :velocity:test :limbo:jar :velocity:jar -PminecraftVersion="$version"
  cp "limbo/build/libs/LimboNPC-Limbo-${version}.jar" build/artifacts/
  cp "velocity/build/libs/LimboNPC-Velocity-${version}.jar" build/artifacts/
done

printf '\nArtifacts:\n'
ls -1 build/artifacts/*.jar
