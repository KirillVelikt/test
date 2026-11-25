# -*- coding: utf-8 -*-
"""Загружает проект на GitHub с повторами. Запуск: py new.py"""
import subprocess, sys, time

GIT_USER = "KirillVelikt"
REPO_NAME = "Tetsst"
TOKEN = ""
BRANCH = "main"
MAX_RETRIES = 5

def run(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")

def main():
    token = TOKEN or input("Вставь Personal Access Token: ").strip()
    if not token:
        sys.exit("Токен обязателен.")
    remote = f"https://{GIT_USER}:{token}@github.com/{GIT_USER}/{REPO_NAME}.git"

    def step(desc, cmd, ignore=False):
        print(f"--> {desc} ...")
        r = run(cmd)
        if r.returncode != 0 and not ignore:
            out = (r.stdout + r.stderr).strip()
            if ("already exists" not in out and "nothing to commit" not in out
                    and "nothing added" not in out):
                print(out); sys.exit(1)

    step("git", "git --version")
    step("init", "git init")
    step("branch", f"git branch -M {BRANCH}", ignore=True)
    step("user.name", f'git config user.name "{GIT_USER}"')
    step("user.email", f'git config user.email "{GIT_USER}@users.noreply.github.com"')
    step("remove origin", "git remote remove origin", ignore=True)
    step("add origin", f"git remote add origin {remote}")
    step("add files", "git add -A")
    step("commit", 'git commit -m "Полная пересборка: APK, EXE, установщик, звуки"', ignore=True)

    def push(force=False):
        flag = "-f " if force else "-u "
        label = "Принудительный пуш" if force else "Пуш"
        for attempt in range(1, MAX_RETRIES + 1):
            print(f"--> {label} (попытка {attempt}/{MAX_RETRIES}) ...")
            r = run(f"git push {flag}origin {BRANCH}")
            print((r.stdout + r.stderr).strip())
            if r.returncode == 0:
                return True
            if attempt < MAX_RETRIES:
                wait = 5 * attempt
                print(f"Сбой соединения. Повтор через {wait} сек...")
                time.sleep(wait)
        return False

    # сначала пробуем обычный пуш; если отвергнут — перезаписываем с повторами
    r = run(f"git push -u origin {BRANCH}")
    out = (r.stdout + r.stderr).strip()
    print(out)
    if r.returncode == 0:
        success(); return
    if "rejected" in out.lower() or "fetch first" in out.lower():
        print("На GitHub есть коммиты — перезаписываю...")
        if push(force=True):
            success(); return
    elif push():
        success(); return

    print("\nНе удалось. Проверь githubstatus.com или другой интернет.")
    sys.exit(1)

def success():
    print("\nГОТОВО! Ссылка: https://github.com/" + GIT_USER + "/" + REPO_NAME)
    print("Actions -> две зелёные галочки -> Artifacts: app-debug-apk,")
    print("TerminalControl-EXE, TerminalControl-Installer.")

if __name__ == "__main__":
    main()
