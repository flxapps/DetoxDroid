#!/usr/bin/env python3
"""Refreshes the large website collections in app/src/main/assets/websites. Run it before a
release and read the diff.

The sources:
- the porn and gambling extensions of the StevenBlack hosts project (MIT, merging the lists named
  in each file's header)
- the dating, shopping and gambling lists of the UT1 blacklists kept by the Université Toulouse 1
  Capitole (CC BY-SA 4.0), read from their daily GitHub mirror because the university's own
  server comes and goes

Each file starts with its sources and their licenses, which is the attribution those licenses ask
for. The shopping and dating files open with a few best-known sites so the screen can name them;
everything after that is sorted, and a domain whose parent is listed too is left out, since the
app matches parents anyway. The social and video lists are written by hand and not touched.
"""
import json
import pathlib
import re
import urllib.request

SB_RAW = "https://raw.githubusercontent.com/StevenBlack/hosts/master"
SB_API = "https://api.github.com/repos/StevenBlack/hosts/contents/extensions"
UT1_RAW = "https://raw.githubusercontent.com/olbat/ut1-blacklists/master/blacklists"
UT1_CREDIT = ("UT1 blacklists, Université Toulouse 1 Capitole (Fabrice Prigent), CC BY-SA 4.0, "
              "https://dsi.ut-capitole.fr/blacklists/ (mirror: https://github.com/olbat/ut1-blacklists)")
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/websites"
DOMAIN = re.compile(r"^[a-z0-9][a-z0-9-]*(\.[a-z0-9-]+)+$")

# the first few are named on screen, so the best known come first
SHOPPING_HEAD = [
    "amazon.com", "ebay.com", "aliexpress.com", "temu.com", "shein.com", "etsy.com", "zalando.de",
    "kleinanzeigen.de", "vinted.de", "idealo.de", "wish.com", "otto.de", "lidl.de", "walmart.com",
    "target.com",
]
DATING_HEAD = [
    "tinder.com", "bumble.com", "hinge.co", "badoo.com", "okcupid.com", "match.com", "grindr.com",
    "lovoo.com", "parship.de", "elitepartner.de",
]


def fetch(url):
    with urllib.request.urlopen(url, timeout=120) as response:
        return response.read().decode("utf-8")


def clean(domain):
    domain = domain.strip().lower()
    while domain.startswith("www."):
        domain = domain[4:]
    return domain if DOMAIN.match(domain) else None


def prune(domains):
    """Drops a domain whose parent is in the set as well. The app's lookup walks up the labels of
    a host down to the last two, so a listed parent already covers it."""
    kept = set()
    for domain in domains:
        parts = domain.split(".")
        if any(".".join(parts[i:]) in domains for i in range(1, len(parts) - 1)):
            continue
        kept.add(domain)
    return kept


def steven_black(extension):
    """The domains of one extension and the credit lines for the lists it merges."""
    url = f"{SB_RAW}/alternates/{extension}-only/hosts"
    domains = set()
    for line in fetch(url).splitlines():
        parts = line.split()
        if len(parts) > 1 and parts[0] == "0.0.0.0":
            domain = clean(parts[1])
            if domain:
                domains.add(domain)
    credits = [f"StevenBlack hosts, extension {extension}, MIT, {url}, merging:"]
    for entry in sorted(json.loads(fetch(f"{SB_API}/{extension}")), key=lambda e: e["name"]):
        if entry["type"] != "dir":
            continue
        meta = json.loads(fetch(f"{SB_RAW}/extensions/{extension}/{entry['name']}/update.json"))
        credits.append(f"  {meta['name']}, {meta.get('license', 'license not stated')}, {meta.get('homeurl', '')}")
    return domains, credits


def ut1(category):
    domains = {clean(line) for line in fetch(f"{UT1_RAW}/{category}/domains").splitlines()}
    domains.discard(None)
    return domains, [f"{UT1_CREDIT}, list \"{category}\""]


def write(file, title, head, parts):
    domains, credits = set(), []
    for part_domains, part_credits in parts:
        domains |= part_domains
        credits += part_credits
    header = [f"# {title}: one domain per line, without \"www.\", the first few by hand, the rest sorted."
              if head else f"# {title}: one domain per line, without \"www.\", sorted.",
              "# Sources:"] + [f"#   {credit}" for credit in credits]
    body = head + sorted(prune(domains | set(head)) - set(head))
    (OUT / file).write_text("\n".join(header + body) + "\n", encoding="utf-8")
    print(f"{file}: {len(body)} domains")


def main():
    write("adult.txt", "Adult sites", [], [steven_black("porn")])
    write("gambling.txt", "Gambling sites", [], [steven_black("gambling"), ut1("gambling")])
    write("dating.txt", "Dating sites", DATING_HEAD, [ut1("dating")])
    write("shopping.txt", "Shops and marketplaces", SHOPPING_HEAD, [ut1("shopping")])


if __name__ == "__main__":
    main()
