#!/usr/bin/env python3
"""Regenerate app/src/main/assets/world.bin (the Map screen's offline
land geometry) from a Natural Earth countries GeoJSON.

Provenance: Natural Earth data is public domain. The input used for
the committed asset was countries.geo.json from johan/world.geo.json
(a Natural Earth derivative), fetched 2026-11-03.

Format (our own, little-endian): magic "WLD1", u16 scale (degrees x
100), u32 ring count; per ring: u32 point count, then (i16 lon*100,
i16 lat*100) pairs. Rings are Douglas-Peucker simplified at 0.08
degrees; country identity is deliberately NOT stored — FoxyVPN's map
pins come from the server inventory's declared locations, never from
tapping the map.

Usage: build_world_map.py <countries.geo.json> <output_bin>
"""
import hashlib
import json
import math
import pathlib
import struct
import sys


def simplify(ring, tol=0.08):
    if len(ring) <= 2:
        return ring

    def dist(p, a, b):
        ax, ay = a
        bx, by = b
        px, py = p
        dx, dy = bx - ax, by - ay
        length_sq = dx * dx + dy * dy
        if length_sq == 0:
            return math.hypot(px - ax, py - ay)
        t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length_sq))
        return math.hypot(px - (ax + t * dx), py - (ay + t * dy))

    keep = [False] * len(ring)
    keep[0] = keep[-1] = True
    stack = [(0, len(ring) - 1)]
    while stack:
        i0, i1 = stack.pop()
        dmax, imax = 0.0, -1
        for i in range(i0 + 1, i1):
            dd = dist(ring[i], ring[i0], ring[i1])
            if dd > dmax:
                dmax, imax = dd, i
        if dmax > tol and imax > 0:
            keep[imax] = True
            stack.append((i0, imax))
            stack.append((imax, i1))
    return [p for p, k in zip(ring, keep) if k]


data = json.loads(pathlib.Path(sys.argv[1]).read_text())
polys = []
for feature in data["features"]:
    geometry = feature["geometry"]
    if geometry is None:
        continue
    sets = (
        geometry["coordinates"]
        if geometry["type"] == "MultiPolygon"
        else [geometry["coordinates"]]
    )
    for polygon in sets:
        for ring in polygon:
            points = [(float(x), float(y)) for x, y in ring]
            if len(points) < 4:
                continue
            simplified = simplify(points)
            if len(simplified) >= 4:
                polys.append(simplified)

out = bytearray()
out += b"WLD1"
out += struct.pack("<H", 100)
out += struct.pack("<I", len(polys))
for ring in polys:
    out += struct.pack("<I", len(ring))
    for lon, lat in ring:
        out += struct.pack("<hh", round(lon * 100), round(lat * 100))
target = pathlib.Path(sys.argv[2])
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(bytes(out))
print(
    "rings:", len(polys), "bytes:", len(out),
    "sha256:", hashlib.sha256(bytes(out)).hexdigest(),
)
