PvPLobby imports
================

Put a custom lobby in this folder and load it with /lobby import <name>. It replaces the generated hub until you run
/lobby regenerate. Your layout.yml is backed up to layout.yml.bak first.

What you can import
-------------------
  mylobby.schem         Sponge schematic (WorldEdit 7+, FAWE)           /lobby import mylobby
  mylobby.schematic     legacy MCEdit/WorldEdit 1.8-1.12 schematic       /lobby import mylobby
  MyLobbyWorld/         a whole world folder (the one with level.dat)   /lobby import MyLobbyWorld

Step by step
------------
1. Build your lobby anywhere (a creative server, singleplayer...).
2. Place signs where things should go. Write the tag on any line, in square brackets:

     [spawn]                    where players appear; they face the way you faced when placing the sign
     [npc ranked]               an NPC from npcs.yml: ranked, unranked, ffa, kit editor, stats, leaderboards,
                                cosmetics, party, info (it faces the sign's text side)
     [hologram parkour]         a floating text: parkour (best times), rules, links, welcome, ...
     [portal ranked]            a walk-in portal 3 wide and 4 high, sign at the bottom centre of the doorway
                                (ranked, unranked, ffa, ffa nodebuff, kit editor, stats, cosmetics, ...)
     [pad]  or  [pad 3]         a launch pad pushing players the way you faced (the number is the power)
     [parkour start]  [checkpoint 1]  [checkpoint 2] ...  [parkour finish]
     [egg]  or  [egg roof]      a hidden egg (the sign becomes a dragon egg)
     [button kit editor]        makes the block below the sign clickable (anvils, lecterns...)
     [zone ranked 15]           a named area announced when entered (names are in config.yml zones)
     [particles fountain]       ambient particles (types are in config.yml ambient.emitters)
     [wall]  or  [wall 5]       the leaderboard wall; the sign marks the top-left panel and faces the viewers
     [border 200]               world border size, centred on the sign
     [void]                     players falling below this sign's height are sent back to spawn

   Tagged signs are removed when importing (start, checkpoint and finish signs become pressure plates, pads become
   heavy pressure plates, eggs become dragon eggs).
3. Schematic: select the build with WorldEdit and save it: //copy then //schem save mylobby
   World: copy the world folder itself.
4. Put the file or folder in plugins/PvPLobby/imports/ and run /lobby import mylobby in game.
5. Check the result, then fine tune positions standing where things should be:
     /lobby set spawn           /lobby set npc <id>        /lobby set hologram <id>      /lobby set wall
   or edit plugins/PvPLobby/layout.yml and run /lobby reload.

Notes
-----
- Schematics are pasted into the lobby world (world.name in config.yml) so that the [spawn] sign ends up at
  world.floor-y + 1. World folders replace the lobby world completely.
- Without a [spawn] sign the spawn goes to the highest spot near the middle of the build.
- NPCs without a position are not spawned; /lobby import lists them.
- Imports need world.mode: generated (a dedicated lobby world). In custom mode the plugin never pastes anything.
