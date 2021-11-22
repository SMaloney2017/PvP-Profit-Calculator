## Death-Tracker
A "Loot Tracker"-like plugin for logging cost of deaths on RuneLite

## Goals
- ✓ Show basic world information, skull status, and item protection
- ✓ Log items lost per death and cost of retrieval
- ✓ Differentiate PvP death vs NPC death and calculate loss respectively
- Calculate average cost per death/ total overall cost

## Known Issues
- Source of death is taken to be the last target the player attacks
  regardless if that is the NPC or player who dealt the final damage.
  - eg. Dying to a Dagonnoth Prime magic attack while focusing Supreme will link your death to Supreme.
- Log is only added if the player entered combat.
  - eg. Running from PKer/ NPC without first entering combat with them won't result in an entry log.