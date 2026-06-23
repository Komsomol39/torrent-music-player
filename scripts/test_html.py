#!/usr/bin/env python3
import urllib.request, re, json

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120"

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ru-RU,ru;q=0.9"})
    with urllib.request.urlopen(req, timeout=15) as r:
        return r.read().decode("utf-8", errors="replace")

query = "пилот"
enc = urllib.request.quote(query)

print("=== RUTOR HTML SAMPLE ===")
html = get(f"https://rutor.info/search/0/0/000/0/{enc}")
# Печатаем первые строки таблицы
lines = html.split("\n")
in_table = False
printed = 0
for line in lines:
    if "table" in line.lower() and "id=" in line.lower():
        in_table = True
    if in_table:
        print(line[:200])
        printed += 1
        if printed > 60:
            break

print()
print("=== MAGNET EXAMPLES ===")
magnets = re.findall(r'href="(magnet:[^"]{10,})"', html)[:3]
for m in magnets:
    print("MAGNET:", m[:100])

print()
print("=== LINKS NEAR MAGNETS ===")
# Смотрим 200 символов вокруг каждого magnet
for m in re.finditer(r'href="magnet:[^"]{10,}"', html):
    start = max(0, m.start()-300)
    end = min(len(html), m.end()+100)
    snippet = html[start:end].replace("\n","\\n")
    print("CONTEXT:", snippet[:400])
    print("---")
    break  # только первый

print()
print("=== TITLE REGEX TESTS ===")
# Пробуем разные паттерны
patterns = [
    r'class="downgit"[^>]*>([^<]+)<',
    r'href="/torrent/\d+/[^"]+">([^<]+)</a>',
    r'<td[^>]*>\s*<a[^>]*href="/torrent/[^"]*">([^<]+)</a>',
    r'title="([^"]+)"',
]
for p in patterns:
    found = re.findall(p, html)[:3]
    if found:
        print(f"Pattern {p[:40]}: {found}")
    else:
        print(f"Pattern {p[:40]}: NOTHING")
