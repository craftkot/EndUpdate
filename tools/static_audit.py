#!/usr/bin/env python3
"""Fast dependency-free asset audit for End Update CI."""
import json
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
errors: list[str] = []

json_files = list(RES.rglob("*.json"))
parsed = {}
for path in json_files:
    try:
        parsed[path] = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"{path.relative_to(ROOT)}: invalid JSON: {exc}")

for path, data in parsed.items():
    rel = path.as_posix()
    if "/blockstates/" in rel:
        raw = json.dumps(data)
        for namespace, model in re.findall(r'"model"\s*:\s*"([^":]+):([^"#]+)', raw):
            if namespace == "endupdate":
                target = RES / "assets/endupdate/models" / f"{model}.json"
                if not target.exists():
                    errors.append(f"{path.relative_to(ROOT)}: missing model endupdate:{model}")

    if "/models/" in rel and isinstance(data, dict):
        parent = data.get("parent")
        if isinstance(parent, str) and parent.startswith("endupdate:"):
            target = RES / "assets/endupdate/models" / f"{parent.split(':', 1)[1]}.json"
            if not target.exists():
                errors.append(f"{path.relative_to(ROOT)}: missing parent {parent}")

        textures = data.get("textures", {})
        if isinstance(textures, dict):
            for texture in textures.values():
                if isinstance(texture, str) and texture.startswith("endupdate:"):
                    target = RES / "assets/endupdate/textures" / f"{texture.split(':', 1)[1]}.png"
                    if not target.exists():
                        errors.append(f"{path.relative_to(ROOT)}: missing texture {texture}")

blocks_java = ROOT / "src/main/java/com/ehtid/endupdate/registry/ModBlocks.java"
if blocks_java.exists():
    registered = re.findall(r'BLOCKS\.register\("([a-z0-9_]+)"', blocks_java.read_text(encoding="utf-8"))
    for name in registered:
        required = [
            RES / "assets/endupdate/blockstates" / f"{name}.json",
            RES / "assets/endupdate/models/item" / f"{name}.json",
            RES / "data/endupdate/loot_tables/blocks" / f"{name}.json",
        ]
        for target in required:
            if not target.exists():
                errors.append(f"block {name}: missing {target.relative_to(ROOT)}")
else:
    registered = []
    errors.append("ModBlocks.java missing")

# Minimal PNG structural check without Pillow: signature + positive IHDR dimensions.
for path in RES.rglob("*.png"):
    try:
        data = path.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
            raise ValueError("invalid PNG signature/IHDR")
        width, height = struct.unpack(">II", data[16:24])
        if width <= 0 or height <= 0:
            raise ValueError(f"invalid dimensions {width}x{height}")
    except Exception as exc:
        errors.append(f"{path.relative_to(ROOT)}: {exc}")

if errors:
    print("STATIC AUDIT FAILED")
    for error in errors:
        print(" -", error)
    sys.exit(1)

png_count = sum(1 for _ in RES.rglob("*.png"))
print(f"STATIC AUDIT OK: {len(json_files)} JSON, {png_count} PNG, {len(registered)} registered blocks")
