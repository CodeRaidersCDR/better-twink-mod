## Better Twink Mod - Configuration Examples

### Example 1: Simple Minecraft Items Organization

**Scenario**: You want to organize basic Minecraft items by type.

```
Chests configured:

1. Stones Chest
   - Allowed Items: stone, cobblestone, diorite, andesite, granite
   - Position: X:100, Y:64, Z:100
   
2. Ores Chest
   - Allowed Items: coal_ore, iron_ore, gold_ore, diamond_ore, emerald_ore
   - Position: X:100, Y:64, Z:110
   
3. Metals Chest
   - Allowed Items: iron_ingot, gold_ingot, copper_ingot, diamond, emerald
   - Crafting: copper_ingot -> copper_block (when storage needed)
   - Position: X:100, Y:64, Z:120
   
4. Quick Drop Chest
   - Type: Quick Drop
   - Position: X:100, Y:64, Z:130
```

### Example 2: Modded Server Organization

**Scenario**: Server with Applied Energistics 2, Thermal Expansion, and other mods.

```
Chests configured:

1. AE2 Storage
   - Allowed Mods: [appliedenergistics2]
   - Position: X:200, Y:64, Z:200
   
2. Thermal Storage
   - Allowed Mods: [thermal]
   - Position: X:200, Y:64, Z:210
   
3. Create Mod Storage
   - Allowed Mods: [create]
   - Position: X:200, Y:64, Z:220
   
4. Vanilla Items (Fallback)
   - Allowed Mods: [minecraft]
   - Position: X:200, Y:64, Z:230
   
5. Quick Drop Zone
   - Type: Quick Drop
   - Position: X:200, Y:64, Z:240
```

### Example 3: Material-Based Organization with Crafting

**Scenario**: Large mining operation with automatic conversion.

```
Chests configured:

1. Iron Storage
   - Allowed Items: [iron_ore, iron_ingot, iron_block, raw_iron]
   - Crafting Rules:
     * iron_ingot -> iron_block (ratio 1:1)
     * raw_iron -> iron_ingot (smelting)
   - Position: X:300, Y:64, Z:300
   
2. Copper Storage
   - Allowed Items: [copper_ore, copper_ingot, copper_block, raw_copper]
   - Crafting Rules:
     * copper_ingot -> copper_block (ratio 1:1)
   - Position: X:300, Y:64, Z:310
   
3. Gold Storage
   - Allowed Items: [gold_ore, gold_ingot, gold_block, raw_gold]
   - Position: X:300, Y:64, Z:320
   
4. Diamond/Emerald Storage
   - Allowed Items: [diamond, emerald, diamond_block, emerald_block]
   - Position: X:300, Y:64, Z:330
   
5. Drops Chest
   - Type: Quick Drop
   - Position: X:300, Y:64, Z:340
```

### Example 4: Compact Setup (Multi-Mod)

**Scenario**: Efficient single-location storage with mod filters.

```
Configuration saved to: config/bettertwink/configurations.nbt
Server: example.server.com

Chests (all in one area for efficiency):

1. Tech Mods (X:100, Y:65, Z:100)
   - Allowed Mods: [appliedenergistics2, thermal, mekanism, industrialcraft]
   
2. Magic Mods (X:100, Y:65, Z:101)
   - Allowed Mods: [botania, thaumcraft, bloodmagic, mysticalculture]
   
3. Building (X:100, Y:65, Z:102)
   - Allowed Mods: [minecraft, create, architectspalette]
   
4. Quick Drop (X:100, Y:65, Z:103)
   - Type: Quick Drop
```

### Example 5: Raid/Farm Loot Management

**Scenario**: Quickly organize farming/raid loot.

```
Chests configured:

1. Valuable Items
   - Allowed Items: [diamond, emerald, netherite_ingot, enchanted_golden_apple]
   - Position: Vault area
   
2. Books & Enchantments
   - Allowed Items: [enchanted_book, experience_bottle]
   
3. Mob Drops
   - Allowed Mods: [minecraft] (only certain items)
   - Allowed Items: [ender_pearl, blaze_rod, dragon_egg, nether_star]
   
4. Sellables
   - Allowed Items: [rotten_flesh, bone, string, spider_eye, gunpowder]
   - (Connects to villager trading)
   
5. Incinerate (Quick Drop)
   - Type: Quick Drop that feeds to grinder
   - Items like dirt, gravel go here
```

### Transfer Speeds Reference

For different server restrictions:

```
Strict Anti-Bot Server:
  itemTransferDelay: 500ms  (slow and humanlike)
  
Normal Server:
  itemTransferDelay: 200ms  (balanced)
  
Friendly Server:
  itemTransferDelay: 100ms  (fast)
```

### NBT Storage Example

Configurations are stored as NBT in:
```
%appdata%\.minecraft\config\bettertwink\configurations.nbt
```

Each server's configuration includes:
- Server name and address
- Chest list with positions
- Item/Mod filters per chest
- Crafting rules
- Quick drop assignments
- Last modification timestamp

### Config File Locations

```
Windows:
  %appdata%\.minecraft\config\bettertwink\configurations.nbt
  
Linux:
  ~/.minecraft/config/bettertwink/configurations.nbt
  
macOS:
  ~/Library/Application Support/minecraft/config/bettertwink/configurations.nbt
```

### Best Practices

1. **Group by function, not by mod**
   - ✅ Good: "Materials" chest
   - ❌ Bad: One chest per mod

2. **Use Quick Drop effectively**
   - Place it near where you drop items
   - Use 1-2 quick drops per sorting area

3. **Balance distance vs functionality**
   - Don't spread chests too far apart
   - Closer = faster sorting

4. **Name chests descriptively**
   - "Iron Storage" instead of "Chest1"
   - Makes management easier

5. **Test on local world first**
   - Verify configuration works
   - Then export and import on server
