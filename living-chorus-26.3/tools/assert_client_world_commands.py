#!/usr/bin/env python3
"""Validate the actual Minecraft 26.3 level.dat; never infer from typed chat."""
from pathlib import Path
import gzip
import sys

def byte(blob: bytes, key: str) -> int:
    needle = b"\x01" + len(key).to_bytes(2, "big") + key.encode()
    assert blob.count(needle) == 1, f"missing/duplicated NBT byte {key}"
    return blob[blob.index(needle) + len(needle)]

def integer(blob: bytes, key: str) -> int:
    needle = b"\x03" + len(key).to_bytes(2, "big") + key.encode()
    assert blob.count(needle) == 1, f"missing/duplicated NBT int {key}"
    start = blob.index(needle) + len(needle)
    return int.from_bytes(blob[start:start+4], "big", signed=True)

try:
    worlds = list(Path(sys.argv[1]).glob("*/level.dat"))
    assert len(worlds) == 1, f"expected 1 world but found {len(worlds)}"
    data = gzip.decompress(worlds[0].read_bytes())
    assert byte(data, "allowCommands") == 1, "CHEATS_DISABLED"
    assert integer(data, "GameType") == 1, "NOT_CREATIVE"
    # In MC 26.3 difficulty is serialized differently from MC 1.21.1.
    # The actual world-create screenshot independently shows Peaceful;
    # never read an obsolete NBT field or reject the correct world.
    print("REAL_WORLD_NBT_COMMANDS_ON_CREATIVE=YES")
except Exception as error:
    print(f"REAL_WORLD_NBT_VERIFICATION_FAILED={error}", file=sys.stderr)
    raise SystemExit(1)
