#!/usr/bin/env bash
# PvP Practice server launcher.
#   - downloads the latest Paper build for MC_VERSION (PaperMC Fill v3 API, v2 fallback) and verifies its sha256
#   - accepts the Minecraft EULA (https://aka.ms/MinecraftEULA) on your behalf: running this script means you agree
#   - builds the plugins with Maven when server/plugins has no jars yet
#   - starts Paper with Aikar's G1 flags
#
# Environment overrides: MC_VERSION (default 1.21.11), MEMORY (default 6G), UPDATE_PAPER=1, JAVA=/path/to/java
set -euo pipefail
cd "$(dirname "$0")"

MC_VERSION="${MC_VERSION:-1.21.11}"
MEMORY="${1:-${MEMORY:-6G}}"
JAVA="${JAVA:-java}"
JAR="paper.jar"
UA="pvp-practice-start-script/1.0 (https://github.com/)"

log() { printf '\033[36m[start]\033[0m %s\n' "$*"; }
die() { printf '\033[31m[start] %s\033[0m\n' "$*" >&2; exit 1; }

command -v "$JAVA" >/dev/null 2>&1 || die "Java not found. Install Java 21+ (e.g. Temurin 21)."
# grep for the version line: hosts that set JAVA_TOOL_OPTIONS print "Picked up ..." first.
JAVA_VERSION_LINE=$("$JAVA" -version 2>&1 | grep -m1 -E 'version "' || true)
JAVA_MAJOR=$(printf '%s' "$JAVA_VERSION_LINE" | sed -E 's/.*version "([0-9]+).*/\1/')
[ "${JAVA_MAJOR:-0}" -ge 21 ] 2>/dev/null || die "Java 21+ required (found: ${JAVA_VERSION_LINE:-unknown})."

fetch() { # url -> stdout
  if command -v curl >/dev/null 2>&1; then curl -fsSL -A "$UA" "$1"; else wget -qO- --user-agent="$UA" "$1"; fi
}
download() { # url file
  if command -v curl >/dev/null 2>&1; then curl -fL -A "$UA" -o "$2" "$1"; else wget -q --user-agent="$UA" -O "$2" "$1"; fi
}

download_paper() {
  local url="" sha=""
  log "Looking up the latest Paper build for $MC_VERSION..."
  if json=$(fetch "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds/latest" 2>/dev/null); then
    url=$(printf '%s' "$json" | tr -d '\n ' | grep -o '"url":"[^"]*"' | head -n1 | cut -d'"' -f4)
    sha=$(printf '%s' "$json" | tr -d '\n ' | grep -o '"sha256":"[^"]*"' | head -n1 | cut -d'"' -f4)
  fi
  if [ -z "$url" ]; then
    log "Fill v3 unavailable, trying the v2 API..."
    json=$(fetch "https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds") || die "Could not reach the PaperMC API."
    build=$(printf '%s' "$json" | grep -o '"build":[0-9]*' | tail -n1 | cut -d: -f2)
    [ -n "$build" ] || die "No Paper builds found for $MC_VERSION."
    url="https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds/${build}/downloads/paper-${MC_VERSION}-${build}.jar"
    sha=$(printf '%s' "$json" | tr -d '\n ' | grep -o '"sha256":"[^"]*"' | tail -n1 | cut -d'"' -f4)
  fi
  log "Downloading $url"
  download "$url" "$JAR.tmp"
  if [ -n "$sha" ] && command -v sha256sum >/dev/null 2>&1; then
    echo "$sha  $JAR.tmp" | sha256sum -c --quiet - || { rm -f "$JAR.tmp"; die "Checksum mismatch for the Paper jar."; }
  fi
  mv "$JAR.tmp" "$JAR"
}

if [ ! -f "$JAR" ] || [ "${UPDATE_PAPER:-0}" = "1" ]; then
  download_paper
fi

if ! ls plugins/PvPCore.jar >/dev/null 2>&1; then
  if command -v mvn >/dev/null 2>&1 && [ -f ../pom.xml ]; then
    log "Plugin jars missing; building them with Maven..."
    (cd .. && mvn -q -B clean package -DskipTests)
  else
    die "plugins/PvPCore.jar missing. Run 'mvn clean package' in the project root first."
  fi
fi

if ! grep -qs '^eula=true' eula.txt; then
  log "Accepting the Minecraft EULA (https://aka.ms/MinecraftEULA)."
  printf '# Accepted by start.sh on %s\neula=true\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > eula.txt
fi

# Aikar's flags (https://docs.papermc.io/paper/aikars-flags); larger heaps use the >12G variant.
MEM_GB=$(printf '%s' "$MEMORY" | sed -E 's/[^0-9].*//')
if [ "${MEM_GB:-0}" -ge 12 ] 2>/dev/null; then
  G1="-XX:G1NewSizePercent=40 -XX:G1MaxNewSizePercent=50 -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=15 -XX:InitiatingHeapOccupancyPercent=20"
else
  G1="-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15"
fi
FLAGS="-Xms${MEMORY} -Xmx${MEMORY} -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem \
-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC ${G1} -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
-XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 \
-XX:SurvivorRatio=32 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true"

log "Starting Paper $MC_VERSION with ${MEMORY} heap"
# shellcheck disable=SC2086
exec "$JAVA" $FLAGS -jar "$JAR" --nogui
