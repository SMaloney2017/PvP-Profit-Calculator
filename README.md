## PvP-Profit-Calculator
A "Loot Tracker"-like plugin for logging PvP kills/ deaths in RuneLite as well as returning useful information
such as world type, skull status, protect item, K/D Ratio, and profit gained/ lost.

### Goals
- ✓ Show basic world information, skull status, item protection, and current opponent
- ✓ Log items lost per pvp death
- ✓ Log loot gained from player kill
- ✓ Calculate K/D Ratio
- ✓ Calculate Profit gained/ Lost

### Bugs to fix/ Concerns
- 📓 Not certain to function correctly in multi or when multiple others are interacting with the user at their death (e.g. following, trading, attacking).
- 🐞 Any player who interacts with you (non-combat, e.g. trading or following) that you are also interacting with will appear as your opponent until your next interaction. Bug may occur if you die (to anything non-pvp) during this time which logs and attributes your death to that player, regardless if they were the one who killed you.
