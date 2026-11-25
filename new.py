# -*- coding: utf-8 -*-
"""
Загружает все файлы из текущей папки на GitHub.
Запуск:  py new.py
"""

import subprocess
import sys
import time

GIT_USER = "KirillVelikt"
REPO_NAME = "Tetsst"
TOKEN = ""
BRANCH = "main"
MAX_RETRIES = 5


def run(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


def push_with_retries(flag, label):
    for attempt in range(1, MAX_RETRIES + 1):
        print("--> %s (попытка %d/%d) ..." % (label, attempt, MAX_RETRIES))
        r = run("git push %s origin %s" % (flag, BRANCH))
        print((r.stdout + r.stderr).strip())
        if r.returncode == 0:
            return True
        if attempt < MAX_RETRIES:
            wait = 5 * attempt
            print("Сбой соединения. Повтор через %d сек..." % wait)
            time.sleep(wait)
    return False


def success():
    print("")
    print("ГОТОВО! Все файлы на GitHub.")
    print("Ссылка: https://github.com/%s/%s" % (GIT_USER, REPO_NAME))
    print("Дальше: вкладка Actions -> зелёные галочки -> Artifacts:")
    print("  - app-debug-apk             (APK для телефона)")
    print("  - TerminalControl-EXE       (пульт для ПК)")
    print("  - TerminalControl-Installer (установщик)")


def main():
    token = TOKEN or input("Вставь Personal Access Token с GitHub: ").strip()
    if not token:
        print("Токен обязателен. Выход.")
        sys.exit(1)

    remote = "https://%s:%s@github.com/%s/%s.git" % (GIT_USER, token, GIT_USER, REPO_NAME)

    def step(desc, cmd, ignore_errors=False):
        print("--> %s ..." % desc)
        r = run(cmd)
        if r.returncode != 0 and not ignore_errors:
            out = (r.stdout + r.stderr).strip()
            if ("already exists" not in out and "nothing to commit" not in out
                    and "nothing added" not in out):
                print(out)
                print("Останов.")
                sys.exit(1)

    step("Проверка git", "git --version")
    step("Инициализация репозитория", "git init")
    step("Ветка main", "git branch -M " + BRANCH, ignore_errors=True)
    step("Настройка имени", 'git config user.name "%s"' % GIT_USER)
    step("Настройка почты", 'git config user.email "%s@users.noreply.github.com"' % GIT_USER)
    step("Удаление старого origin", "git remote remove origin", ignore_errors=True)
    step("Привязка origin", "git remote add origin " + remote)
    step("Добавление файлов", "git add -A")
    step("Коммит", 'git commit -m "Yumani: полная пересборка"', ignore_errors=True)

    if push_with_retries("-u", "Пуш"):
        success()
        return

    chk = run("git push -u origin " + BRANCH)
    out_last = (chk.stderr + chk.stdout).lower()
    if "rejected" in out_last or "fetch first" in out_last:
        print("На GitHub есть коммиты, которых нет локально. Перезаписываю...")
        if push_with_retries("-f", "Принудительный пуш"):
            success()
            return

    print("")
    print("Не удалось запушить после всех попыток.")
    print("Проверь githubstatus.com или попробуй раздать интернет с телефона.")
    sys.exit(1)


if __name__ == "__main__":
    main()
