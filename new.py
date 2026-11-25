# -*- coding: utf-8 -*-
"""
Загружает все файлы из текущей папки на GitHub (с повторами при сбоях сети).
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


def push(flag):
    for attempt in range(1, MAX_RETRIES + 1):
        print(f"--> Пуш (попытка {attempt}/{MAX_RETRIES}) ...")
        r = run(f"git push {flag}origin {BRANCH}")
        out = (r.stdout + r.stderr).strip()
        print(out)
        if r.returncode == 0:
            return True
        if attempt < MAX_RETRIES:
            wait = 5 * attempt
            print(f"Сбой соединения. Повтор через {wait} сек...")
            time.sleep(wait)
    return False


def main():
    token = TOKEN or input("Вставь Personal Access Token: ").strip()
    if not token:
        print("Токен обязателен. Выход.")
        sys.exit(1)

    remote = f"https://{GIT_USER}:{token}@github.com/{GIT_USER}/{REPO_NAME}.git"

    def step(desc, cmd, ignore_errors=False):
        print(f"--> {desc} ...")
        r = run(cmd)
        if r.returncode != 0 and not ignore_errors:
            out = (r.stdout + r.stderr).strip()
            if ("already exists" not in out and "nothing to commit" not in out
                    and "nothing added" not in out):
                print(out)
                sys.exit(1)

    step("Проверка git", "git --version")
    step("Инициализация", "git init")
    step("Ветка main", f"git branch -M {BRANCH}", ignore_errors=True)
    step("Имя", f'git config user.name "{GIT_USER}"')
    step("Почта", f'git config user.email "{GIT_USER}@users.noreply.github.com"')
    step("Удаление старого origin", "git remote remove origin", ignore_errors=True)
    step("Привязка origin", f"git remote add origin {remote}")
    step("Добавление файлов", "git add -A")
    step("Коммит", 'git commit -m "Сборка: терминал + пульт + установщик"', ignore_errors=True)

    if push("-u "):
        success()
        return
    if push("-f "):
        print("(история перезаписана)")
        success()
        return
    print("Не удалось. Проверь githubstatus.com или раздай интернет с телефона.")
    sys.exit(1)


def success():
    print(f"ГОТОВО! Ссылка: https://github.com/{GIT_USER}/{REPO_NAME}")
    print("Дальше: Actions -> две зелёные галочки -> Artifacts:")
    print("  app-debug-apk, TerminalControl-EXE, TerminalControl-Installer")


if __name__ == "__main__":
    main()
