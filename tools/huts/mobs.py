from nbt import Byte, Int

JUNGLE = "minecraft:jungle"

def villager(profession, age=None):
    nbt = {
        "id": "minecraft:villager",
        "VillagerData": {"type": JUNGLE, "profession": profession, "level": Int(1)},
        "PersistenceRequired": Byte(1),
    }
    if age is not None:
        nbt["Age"] = Int(age)
    return nbt

def cat(variant):
    return {"id": "minecraft:cat", "variant": variant, "PersistenceRequired": Byte(1)}

MOBS = [
    ("villager_unemployed", "villagers", 10, 3, villager("minecraft:none")),
    ("villager_nitwit", "villagers", 1, 3, villager("minecraft:nitwit")),
    ("villager_baby", "villagers", 2, 2, villager("minecraft:none", age=-24000)),
    ("cat_black", "cats", 1, 2, cat("minecraft:black")),
    ("cat_red", "cats", 1, 2, cat("minecraft:red")),
    ("cat_tabby", "cats", 1, 2, cat("minecraft:tabby")),
]

CATS_EMPTY_WEIGHT = 0
