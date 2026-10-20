#!/usr/bin/env python3
"""Refreshes the large website collections in app/src/main/assets/websites from the porn and
gambling extensions of the StevenBlack hosts project. Run it before a release and read the diff.

Each file starts with the sources the extension merges and their licenses (MIT or CC BY 4.0),
which is the attribution those licenses ask for.
"""
import json
import pathlib
import urllib.request

RAW = "https://raw.githubusercontent.com/StevenBlack/hosts/master"
API = "https://api.github.com/repos/StevenBlack/hosts/contents/extensions"
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/websites"
LISTS = {
    "adult.txt": ("porn", "Adult sites"),
    "gambling.txt": ("gambling", "Gambling sites"),
}


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read().decode("utf-8")


def sources(extension):
    for entry in sorted(json.loads(fetch(f"{API}/{extension}")), key=lambda e: e["name"]):
        if entry["type"] != "dir":
            continue
        meta = json.loads(fetch(f"{RAW}/extensions/{extension}/{entry['name']}/update.json"))
        yield meta["name"], meta.get("license", "license not stated"), meta.get("homeurl", "")


def main():
    for file, (extension, title) in LISTS.items():
        url = f"{RAW}/alternates/{extension}-only/hosts"
        domains = sorted({
            line.split()[1].lower().removeprefix("www.")
            for line in fetch(url).splitlines()
            if line.startswith("0.0.0.0 ") and len(line.split()) > 1
        } - {"0.0.0.0"})
        header = [
            f"# {title}: the {extension} extension of the StevenBlack hosts project",
            f"# ({url}), reduced to one domain per line, without \"www.\", sorted.",
            "# It merges these lists:",
        ]
        header += [f"#   {name}, {license}, {home}" for name, license, home in sources(extension)]
        (OUT / file).write_text("\n".join(header + domains) + "\n", encoding="utf-8")
        print(f"{file}: {len(domains)} domains")


if __name__ == "__main__":
    main()
