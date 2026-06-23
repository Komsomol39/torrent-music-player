#!/usr/bin/env python3
import urllib.request, re

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120"

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ru-RU,ru;q=0.9"})
    with urllib.request.urlopen(req, timeout=15) as r:
        return r.read().decode("utf-8", errors="replace")

html = get("https://rutor.info/search/0/0/000/0/" + urllib.request.quote("пилот"))

# Ищем таблицу с результатами поиска — id=index
idx = html.find('id="index"')
if idx == -1:
    print("TABLE NOT FOUND — printing 2000 chars from magnet context")
    m = re.search(r'href="magnet:', html)
    if m:
        print(html[max(0,m.start()-500):m.end()+500])
else:
    # Печатаем 3000 символов таблицы
    print("TABLE FOUND at", idx)
    print(html[idx:idx+3000])
