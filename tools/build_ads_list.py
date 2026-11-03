#!/usr/bin/env python3
"""Generate app/src/main/assets/ads_rules.json from the v2fly
domain-list-community data files (MIT License, (c) V2Ray contributors)
at a pinned commit (see DLC_COMMIT in the workflows).

Resolves geosite:category-ads-all exactly like the dlc builder:
recursive include: expansion; plain entries are domain-suffix matches;
full:/regexp:/keyword: prefixes map to the same-named sing-box
headless-rule fields; attributes (@ads ...) are dropped.

Usage: build_ads_list.py <dlc_repo_root> <output_json>
The workflow verifies the output's sha256 against ADS_RULES_SHA256, so
a data change upstream fails the build loudly until the pin is
deliberately updated here and in REVIEW.md.
"""
import hashlib
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1]) / "data"
suffix, exact, regex, keyword = [], [], [], []
seen = set()


def resolve(name):
    if name in seen:
        return
    seen.add(name)
    for raw in (root / name).read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        entry = line.split()[0]
        low = entry.lower()
        if low.startswith("include:"):
            resolve(low[8:])
        elif low.startswith("full:"):
            exact.append(entry[5:])
        elif low.startswith("regexp:"):
            regex.append(entry[7:])
        elif low.startswith("keyword:"):
            keyword.append(entry[8:])
        else:
            suffix.append(entry)


resolve("category-ads-all")


def clean(xs):
    out, s = [], set()
    for x in xs:
        x = x.strip().lower()
        if x and x not in s:
            s.add(x)
            out.append(x)
    return sorted(out)


data = {
    "domain_suffix": clean(suffix),
    "domain": clean(exact),
    "domain_regex": clean(regex),
    "domain_keyword": clean(keyword),
}
blob = json.dumps(data, separators=(",", ":"), sort_keys=True)
out = pathlib.Path(sys.argv[2])
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(blob)
print({k: len(v) for k, v in data.items()}, "bytes:", len(blob))
print("sha256:", hashlib.sha256(blob.encode()).hexdigest())
