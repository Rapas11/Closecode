#!/usr/bin/env python3
"""Build the hosted icon packs from the npm tarballs.

The app does not bundle these packs: Browse downloads them from GitHub Pages. This script
extracts only the icon SVG files, flattens every primitive into one path, and writes a compact
JSON pack plus a packs.json index with relative URLs. Run from the repository root:

    env -u LD_LIBRARY_PATH python3 tools/build_icon_packs.py [output-dir]
"""

import io
import json
import math
import os
import re
import sys
import tarfile
import urllib.request

MATERIAL_URL = "https://registry.npmjs.org/@material-symbols/svg-400/-/svg-400-0.48.3.tgz"
LUCIDE_URL = "https://registry.npmjs.org/lucide-static/-/lucide-static-1.70.0.tgz"

ATTR = re.compile(r'([\w:-]+)="([^"]*)"')
TAG = re.compile(r"<(path|circle|line|rect|polyline|polygon|ellipse)\b([^>]*)/?>", re.I)


def number(value, default=0.0):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def num(value):
    if abs(value - round(value)) < 0.0001:
        return str(int(round(value)))
    return ("%.3f" % value).rstrip("0").rstrip(".")


def attrs(text):
    return dict(ATTR.findall(text))


def svg_path(text):
    """Every drawable primitive in an SVG, flattened to one path string."""
    parts = []
    for match in TAG.finditer(text):
        kind = match.group(1).lower()
        a = attrs(match.group(2))
        if kind == "path":
            d = a.get("d", "").strip()
            if d:
                parts.append(d)
        elif kind == "circle":
            cx, cy, r = number(a.get("cx")), number(a.get("cy")), number(a.get("r"))
            parts.append(
                "M %s %s a %s %s 0 1 0 %s 0 a %s %s 0 1 0 -%s 0 Z"
                % (num(cx - r), num(cy), num(r), num(r), num(2 * r), num(r), num(r), num(2 * r))
            )
        elif kind == "ellipse":
            cx, cy = number(a.get("cx")), number(a.get("cy"))
            rx, ry = number(a.get("rx")), number(a.get("ry"))
            parts.append(
                "M %s %s a %s %s 0 1 0 %s 0 a %s %s 0 1 0 -%s 0 Z"
                % (num(cx - rx), num(cy), num(rx), num(ry), num(2 * rx), num(rx), num(ry), num(2 * rx))
            )
        elif kind == "line":
            parts.append(
                "M %s %s L %s %s"
                % (num(number(a.get("x1"))), num(number(a.get("y1"))),
                   num(number(a.get("x2"))), num(number(a.get("y2"))))
            )
        elif kind == "rect":
            x, y = number(a.get("x")), number(a.get("y"))
            w, h = number(a.get("width")), number(a.get("height"))
            rx = number(a.get("rx"), number(a.get("ry")))
            ry = number(a.get("ry"), rx)
            if rx <= 0 or ry <= 0:
                parts.append(
                    "M %s %s H %s V %s H %s Z"
                    % (num(x), num(y), num(x + w), num(y + h), num(x))
                )
            else:
                parts.append(
                    "M %s %s H %s A %s %s 0 0 1 %s %s V %s A %s %s 0 0 1 %s %s "
                    "H %s A %s %s 0 0 1 %s %s V %s A %s %s 0 0 1 %s %s Z"
                    % (
                        num(x + rx), num(y), num(x + w - rx),
                        num(rx), num(ry), num(x + w), num(y + ry),
                        num(y + h - ry),
                        num(rx), num(ry), num(x + w - rx), num(y + h),
                        num(x + rx),
                        num(rx), num(ry), num(x), num(y + h - ry),
                        num(y + ry),
                        num(rx), num(ry), num(x + rx), num(y),
                    )
                )
        elif kind in ("polyline", "polygon"):
            points = a.get("points", "").replace(",", " ").split()
            if len(points) >= 4:
                pairs = list(zip(points[0::2], points[1::2]))
                d = "M " + " L ".join("%s %s" % (num(number(px)), num(number(py))) for px, py in pairs)
                if kind == "polygon":
                    d += " Z"
                parts.append(d)
    return " ".join(parts)


def view_box(text, fallback):
    match = re.search(r'viewBox="([^"]+)"', text, re.I)
    return match.group(1).strip() if match else fallback


def read_tar(url):
    data = urllib.request.urlopen(url, timeout=120).read()
    return tarfile.open(fileobj=io.BytesIO(data), mode="r:gz")


def material_icons():
    icons = []
    tar = read_tar(MATERIAL_URL)
    for member in tar.getmembers():
        name = member.name
        if not member.isfile() or not name.startswith("package/outlined/") or not name.endswith(".svg"):
            continue
        stem = os.path.basename(name)[:-4]
        if stem.endswith("-fill"):
            continue
        text = tar.extractfile(member).read().decode("utf-8", "replace")
        path = svg_path(text)
        if path:
            icons.append({
                "name": stem,
                "pathData": path,
                "style": "fill",
                "viewBox": view_box(text, "0 -960 960 960"),
            })
    icons.sort(key=lambda icon: icon["name"])
    return icons


def lucide_icons():
    icons = []
    tar = read_tar(LUCIDE_URL)
    for member in tar.getmembers():
        name = member.name
        if not member.isfile() or not name.startswith("package/icons/") or not name.endswith(".svg"):
            continue
        stem = os.path.basename(name)[:-4]
        text = tar.extractfile(member).read().decode("utf-8", "replace")
        path = svg_path(text)
        if path:
            icons.append({
                "name": stem,
                "pathData": path,
                "style": "stroke",
                "viewBox": view_box(text, "0 0 24 24"),
            })
    icons.sort(key=lambda icon: icon["name"])
    return icons


def write_pack(out_dir, pack_id, name, style, licence, icons):
    pack = {"name": name, "style": style, "licence": licence, "icons": icons}
    target = os.path.join(out_dir, pack_id + ".json")
    with io.open(target, "w", encoding="utf-8") as handle:
        json.dump(pack, handle, ensure_ascii=False, separators=(",", ":"))
    return {
        "id": pack_id,
        "name": name,
        "count": len(icons),
        "bytes": os.path.getsize(target),
        "url": pack_id + ".json",
        "style": style,
        "licence": licence,
    }


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join("dist", "iconpacks")
    if not os.path.isdir(out_dir):
        os.makedirs(out_dir)
    print("Material Symbols outlined...")
    material = material_icons()
    print("Lucide...")
    lucide = lucide_icons()
    packs = [
        write_pack(out_dir, "material-symbols", "Material Symbols", "fill", "Apache-2.0", material),
        write_pack(out_dir, "lucide", "Lucide", "stroke", "ISC", lucide),
    ]
    index = {"packs": packs}
    with io.open(os.path.join(out_dir, "packs.json"), "w", encoding="utf-8") as handle:
        json.dump(index, handle, ensure_ascii=False, separators=(",", ":"))
    for pack in packs:
        print("%-20s %5d icons %8.1f KB" % (pack["id"], pack["count"], pack["bytes"] / 1024.0))
    print("index:", os.path.join(out_dir, "packs.json"))


if __name__ == "__main__":
    main()
