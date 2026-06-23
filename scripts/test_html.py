#!/usr/bin/env python3
import urllib.request, re

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120"
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ru-RU,ru;q=0.9"})
    with urllib.request.urlopen(req, timeout=15) as r:
        return r.read().decode("utf-8", errors="replace")

html = get("https://rutor.info/search/0/0/000/0/" + urllib.request.quote("пилот"))

# Находим таблицу #index и смотрим ALL <tr> теги
idx = html.find('id="index"')
table_html = html[idx:idx+5000]

print("=== ALL TR tags in table ===")
for m in re.finditer(r'<tr[^>]*>', table_html):
    print(repr(m.group()))

print()
print("=== SECOND TR (first data row) full content ===")
trs = re.findall(r'<tr[^>]*>.*?</tr>', table_html, re.DOTALL)
if len(trs) > 1:
    print(trs[1][:800])

print()
print("=== SIZE ISSUE: what is 226B? ===")
# Смотрим что скачивается с Archive.org - это страница каталога а не файл
# Проверяем реальный Content-Type
test_url = "https://archive.org/download/GratefulDead"
req = urllib.request.Request(test_url, headers={"User-Agent": UA}, method="HEAD")
try:
    with urllib.request.urlopen(req, timeout=10) as r:
        print("Archive folder status:", r.status, r.headers.get("Content-Type"))
except Exception as e:
    print("Archive folder:", e)
