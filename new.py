# -*- coding: utf-8 -*-
"""Загрузка проекта Yumani Terminal на GitHub.

Запуск:  py new.py
Спросит токен (на GitHub: Settings -> Developer settings -> Personal access tokens)
и URL репозитория, затем запушит всё в main с повторами.
"""
import os
import subprocess
import sys
import time

def sh(cmd):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, encoding="utf-8", errors="ignore")
    return r.returncode, (r.stdout or "") + (r.stderr or "")

def main():
    print("=== Загрузка Yumani Terminal ===")
    token = os.environ.get("GH_TOKEN") or input("GitHub токен: ").strip()
    url = input("URL репозитория (https://github.com/USER/REPO): ").strip().rstrip("/")
    if not token or "github.com" not in url:
        print("нужны токен и URL вида https://github.com/USER/REPO")
        sys.exit(1)

    if sh("git init")[0] != 0:
        print("не удалось инициализировать git")
        sys.exit(1)
    sh('git config user.email "yumani@local"')
    sh('git config user.name "Yumani Uploader"')
    sh("git add -A")
    sh('git commit -m "Yumani Terminal: полная сборка" --allow-empty')

    auth = url.replace("https://", "https://%s@" % token)
    for attempt in range(1, 6):
        print("пуш, попытка %d…" % attempt)
        code, out = sh("git push --force %s HEAD:main" % auth)
        if code == 0:
            print("Готово! Открой Actions в репозитории и жди зелёные галочки.")
            return
        print(out.strip()[:400])
        time.sleep(3)
    print("Не удалось запушить за 5 попыток.")

if __name__ == "__main__":
    main()
