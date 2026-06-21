#!/usr/bin/env python3
"""
Тест всех торрент и музыкальных API.
Запрос: 'Паша техник'
Результаты сохраняются в scripts/api-test-results.json
"""
import json, urllib.request, urllib.parse, time
from xml.etree import ElementTree as ET

QUERY = "Паша техник"
ENC = urllib.parse.quote(QUERY)
RESULTS = {}

def get(url, timeout=15, headers=None):
    h = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120"}
    if headers: h.update(headers)
    try:
        req = urllib.request.Request(url, headers=h)
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.read().decode("utf-8", errors="replace"), r.status
    except Exception as e:
        return None, str(e)

# ── 1. TPB — apibay.org JSON API ──────────────────────────────────
def test_tpb():
    url = f"https://apibay.org/q.php?q={ENC}&cat=0"
    data, status = get(url)
    if not data:
        return {"ok": False, "error": str(status), "url": url}
    try:
        arr = json.loads(data)
        if arr and arr[0].get("name") != "No results returned.":
            sample = arr[0]
            return {
                "ok": True, "count": len(arr),
                "sample_name": sample["name"][:70],
                "sample_hash": sample["info_hash"],
                "sample_seeds": sample["seeders"],
                "magnet": f"magnet:?xt=urn:btih:{sample['info_hash'].lower()}&dn={urllib.parse.quote(sample['name'])}"
            }
        return {"ok": True, "count": 0, "note": "no results for this query"}
    except Exception as e:
        return {"ok": False, "error": str(e), "raw": data[:200]}

# ── 2. Nyaa — RSS ─────────────────────────────────────────────────
def test_nyaa():
    url = f"https://nyaa.si/?page=rss&q={ENC}&c=2_0&f=0"
    data, status = get(url)
    if not data:
        return {"ok": False, "error": str(status)}
    try:
        root = ET.fromstring(data)
        items = root.findall(".//item")
        if items:
            first = items[0]
            title = first.findtext("title", "")
            link  = first.findtext("link", "")
            ns = {"nyaa": "https://nyaa.si/xmlns/nyaa"}
            seeds = first.findtext("nyaa:seeders", "0", ns)
            return {"ok": True, "count": len(items), "sample_title": title[:70],
                    "sample_link": link[:100], "sample_seeds": seeds}
        return {"ok": True, "count": 0}
    except Exception as e:
        return {"ok": False, "error": str(e), "raw": data[:300]}

# ── 3. RuTor — HTML scraping ──────────────────────────────────────
def test_rutor():
    url = f"https://rutor.info/search/0/0/000/0/{ENC}"
    data, status = get(url)
    if not data:
        return {"ok": False, "error": str(status)}
    # Извлекаем магнеты
    import re
    magnets = re.findall(r'href="(magnet:[^"]+)"', data)
    titles = re.findall(r'href="/torrent/[^"]+">([^<]+)</a>', data)
    seeds_raw = re.findall(r'class="s">(\d+)</td>', data)
    if magnets:
        return {
            "ok": True, "count": len(magnets),
            "sample_title": titles[0][:70] if titles else "?",
            "sample_magnet": magnets[0][:100],
            "sample_seeds": seeds_raw[0] if seeds_raw else "?"
        }
    return {"ok": True, "count": 0, "has_content": len(data) > 1000}

# ── 4. 1337x — HTML scraping ──────────────────────────────────────
def test_1337x():
    url = f"https://1337x.to/search/{ENC}/1/"
    data, status = get(url)
    if not data:
        return {"ok": False, "error": str(status)}
    import re
    # Ищем ссылки на торренты
    links = re.findall(r'href="(/torrent/\d+/[^"]+)"', data)
    names = re.findall(r'class="name"><a[^>]+>[^<]*<a[^>]+>([^<]+)</a>', data)
    seeds = re.findall(r'class="seeds">(\d+)</td>', data)
    if links:
        return {
            "ok": True, "count": len(links),
            "sample_name": names[0][:70] if names else "?",
            "sample_link": "https://1337x.to" + links[0],
            "sample_seeds": seeds[0] if seeds else "?"
        }
    return {"ok": True, "count": 0, "raw_size": len(data)}

# ── 5. RuTracker без логина ───────────────────────────────────────
def test_rutracker():
    url = f"https://rutracker.org/forum/tracker.php?nm={ENC}"
    data, status = get(url)
    if not data:
        return {"ok": False, "error": str(status)}
    import re
    # Ссылки на темы
    topics = re.findall(r'href="viewtopic\.php\?t=(\d+)"[^>]*class="tLink">([^<]+)<', data)
    seeds  = re.findall(r'class="seedmed"><b>(\d+)</b>', data)
    if topics:
        return {
            "ok": True, "count": len(topics),
            "sample_title": topics[0][1][:70],
            "sample_topic_id": topics[0][0],
            "sample_seeds": seeds[0] if seeds else "?",
            "needs_login_for_magnet": True
        }
    redirected_to_login = "login.php" in data or 'class="login"' in data
    return {"ok": True, "count": 0, "redirected_to_login": redirected_to_login}

# ── 6. Jamendo — официальный API ──────────────────────────────────
def test_jamendo():
    url = f"https://api.jamendo.com/v3.0/tracks/?client_id=d1c41421&format=json&limit=5&namesearch={ENC}&audioformat=mp32"
    data, status = get(url)
    if not data:
        return {"ok": False, "error": str(status)}
    try:
        obj = json.loads(data)
        results = obj.get("results", [])
        if results:
            r = results[0]
            return {
                "ok": True, "count": len(results),
                "sample_name": r.get("name","")[:60],
                "sample_artist": r.get("artist_name","")[:40],
                "sample_audio": r.get("audiodownload","")[:100]
            }
        return {"ok": True, "count": 0, "headers": obj.get("headers",{})}
    except Exception as e:
        return {"ok": False, "error": str(e), "raw": data[:200]}

# ── 7. FMA — Free Music Archive ───────────────────────────────────
def test_fma():
    url = f"https://freemusicarchive.org/api/get/tracks.json?api_key=60BLHNQCAOUFPIBZ&title={ENC}&limit=5"
    data, status = get(url)
    if not data:
        return {"ok": False, "error": str(status)}
    try:
        obj = json.loads(data)
        dataset = obj.get("dataset", [])
        if dataset:
            r = dataset[0]
            return {
                "ok": True, "count": len(dataset),
                "sample_title": r.get("track_title","")[:60],
                "sample_artist": r.get("artist_name","")[:40],
                "sample_file": r.get("track_file","")[:100]
            }
        return {"ok": True, "count": 0}
    except Exception as e:
        return {"ok": False, "error": str(e), "raw": data[:200]}

# ── 8. Deezer — публичный API ─────────────────────────────────────
def test_deezer():
    url = f"https://api.deezer.com/search?q={ENC}&limit=5"
    data, status = get(url)
    if not data:
        return {"ok": False, "error": str(status)}
    try:
        obj = json.loads(data)
        items = obj.get("data", [])
        if items:
            r = items[0]
            return {
                "ok": True, "count": len(items),
                "sample_title": r.get("title","")[:60],
                "sample_artist": r.get("artist",{}).get("name","")[:40],
                "sample_preview": r.get("preview","")[:100],
                "duration_sec": r.get("duration",0)
            }
        return {"ok": True, "count": 0}
    except Exception as e:
        return {"ok": False, "error": str(e), "raw": data[:200]}

# ── 9. SoundCloud — автоextract clientId ──────────────────────────
def test_soundcloud():
    # Шаг 1: извлечь clientId со страницы
    html, _ = get("https://soundcloud.com")
    if not html:
        return {"ok": False, "error": "Cannot fetch soundcloud.com"}
    import re
    # Ищем последний скрипт
    scripts = re.findall(r'src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"', html)
    if not scripts:
        return {"ok": False, "error": "No script URLs found"}
    # Берём последний
    js, _ = get(scripts[-1])
    if not js:
        return {"ok": False, "error": "Cannot fetch script"}
    client_id = re.search(r'client_id:"([a-zA-Z0-9]+)"', js)
    if not client_id:
        return {"ok": False, "error": "clientId not found in script"}
    cid = client_id.group(1)
    # Шаг 2: поиск
    url = f"https://api-v2.soundcloud.com/search/tracks?q={ENC}&limit=5&client_id={cid}"
    data, status = get(url)
    if not data:
        return {"ok": False, "error": str(status), "client_id": cid}
    try:
        obj = json.loads(data)
        items = obj.get("collection", [])
        if items:
            r = items[0]
            return {
                "ok": True, "client_id": cid, "count": len(items),
                "sample_title": r.get("title","")[:60],
                "sample_user": r.get("user",{}).get("username","")[:40],
                "sample_stream_url": f"https://api.soundcloud.com/tracks/{r.get('id')}/stream?client_id={cid}"
            }
        return {"ok": True, "client_id": cid, "count": 0}
    except Exception as e:
        return {"ok": False, "error": str(e), "client_id": cid}

# ── Запускаем тесты ───────────────────────────────────────────────
tests = [
    ("TPB (apibay.org)", test_tpb),
    ("Nyaa RSS", test_nyaa),
    ("RuTor", test_rutor),
    ("1337x", test_1337x),
    ("RuTracker (no login)", test_rutracker),
    ("Jamendo", test_jamendo),
    ("FMA", test_fma),
    ("Deezer", test_deezer),
    ("SoundCloud", test_soundcloud),
]

print(f"Testing {len(tests)} APIs for query: '{QUERY}'\n")
for name, fn in tests:
    print(f"[{name}]", end=" ", flush=True)
    t0 = time.time()
    try:
        result = fn()
        ms = int((time.time() - t0) * 1000)
        result["ms"] = ms
        RESULTS[name] = result
        status = "✅" if result.get("ok") else "❌"
        print(f"{status} {ms}ms", json.dumps(result, ensure_ascii=False)[:200])
    except Exception as e:
        RESULTS[name] = {"ok": False, "error": str(e)}
        print(f"❌ EXCEPTION: {e}")
    time.sleep(0.5)

# Сохраняем результаты
with open("scripts/api-test-results.json", "w", encoding="utf-8") as f:
    json.dump(RESULTS, f, ensure_ascii=False, indent=2)
print("\n✅ Results saved to scripts/api-test-results.json")
