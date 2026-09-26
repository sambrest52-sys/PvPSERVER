Ready-to-run Paper server for the PvP practice plugins.

1. From the project root run:  mvn clean package      (copies the plugin jars into server/plugins)
2. Start the server:           ./start.sh [memory]     (Windows: start.bat [memory], default 6G)

start.sh downloads Paper 1.21.11 from PaperMC, accepts the Minecraft EULA for you and uses Aikar's flags.
Plugin configs in plugins/PvP*/ are pre-generated copies of the defaults; edit them before or after the
first start and apply changes with /pvpadmin reload. Arena templates are generated into
plugins/PvPCore/arenas/ on first start, and the lobby hub is built into the pvp_lobby world (positions in
plugins/PvPLobby/layout.yml). Custom lobbies go into plugins/PvPLobby/imports/ (see the README there).
