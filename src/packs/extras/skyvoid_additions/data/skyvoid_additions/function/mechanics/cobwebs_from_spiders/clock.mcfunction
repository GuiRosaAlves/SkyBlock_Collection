# Spiders occasionally spin webs when climbing near ceiling/wall edges.
# Check more frequently than the old 10s cycle: the spider may only be
# alongside an edge briefly. The 5% chance is rolled once per spider/cycle.
schedule function skyvoid_additions:mechanics/cobwebs_from_spiders/clock 2s

# Minecraft caves can contain cave_air, so match the vanilla air block tag.
# Never overwrite a block or generate webs on the floor.
execute as @e[type=minecraft:spider,tag=!smithed.entity,predicate=skyvoid_additions:cobwebs_from_spiders/cobweb_chance] at @s if block ~ ~ ~ #minecraft:air if block ~ ~-1 ~ #minecraft:air run function skyvoid_additions:mechanics/cobwebs_from_spiders/try_place
