# -*- coding: utf-8 -*-
"""Загрузка проекта на GitHub. Запросит токен, зальёт всё с форс-пушем."""
import getpass
import os
import time
import subprocess
import sys

REPO = "test"
BRANCH = "main"

def run(cmd, **kw):
    print(">", " ".join(cmd))
    return subprocess.run(cmd, **kw)

def main():
    token = getpass.getpass("GitHub token: ").strip()
    if not token:
        sys.exit("токен не введён")
    cwd = os.path.dirname(os.path.abspath(__file__))
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0")
    url = f"https://{token}@github.com/local-github/{REPO}.git"

    for attempt in range(3):
        try:
            git = run(["git", "-C", cwd, "status"], capture_output=True)
            if git.returncode != 0:
                run(["git", "-C", cwd, "init"])
            run(["git", "-C", cwd, "config", "user.email", "terminal@yumani"])
            run(["git", "-C", cwd, "config", "user.name", "yumani-terminal"])
            run(["git", "-C", cwd, "add", "-A"])
            run(["git", "-C", cwd, "commit", "-m", "yumani-terminal build"],
                capture_output=True)
            run(["git", "-C", cwd, "branch", "-M", BRANCH])
            r = run(["git", "-C", cwd, "push", "--force", url, BRANCH])
            if r.returncode == 0:
                print("\nГотово! Сборки запустятся в Actions.")
                return
        except Exception as e:
            print("попытка", attempt + 1, "не удалась:", e)
        time.sleep(2)
    sys.exit("не удалось загрузить репозиторий")

if __name__ == "__main__":
    main()
