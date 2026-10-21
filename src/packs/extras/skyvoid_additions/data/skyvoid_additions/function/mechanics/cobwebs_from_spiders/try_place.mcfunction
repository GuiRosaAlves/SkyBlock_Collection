# Runs at the eligible spider's feet, after a single random roll.
# Webs form directly under ceilings or near the top edges of climbable walls.
# Use keep so occupied blocks, including existing cobwebs, are untouched.

# Spider directly under an overhead block.
execute unless block ~ ~1 ~ #minecraft:air run setblock ~ ~ ~ minecraft:cobweb keep

# Spider climbing near a wall's exposed upper edge. Re-check the target air
# because the first command may already have placed a cobweb.
execute if block ~ ~ ~ #minecraft:air unless block ~1 ~1 ~ #minecraft:air if block ~1 ~2 ~ #minecraft:air run setblock ~ ~ ~ minecraft:cobweb keep
execute if block ~ ~ ~ #minecraft:air unless block ~-1 ~1 ~ #minecraft:air if block ~-1 ~2 ~ #minecraft:air run setblock ~ ~ ~ minecraft:cobweb keep
execute if block ~ ~ ~ #minecraft:air unless block ~ ~1 ~1 #minecraft:air if block ~ ~2 ~1 #minecraft:air run setblock ~ ~ ~ minecraft:cobweb keep
execute if block ~ ~ ~ #minecraft:air unless block ~ ~1 ~-1 #minecraft:air if block ~ ~2 ~-1 #minecraft:air run setblock ~ ~ ~ minecraft:cobweb keep
