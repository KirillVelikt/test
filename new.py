# -*- coding: utf-8 -*-
"""Загрузка проекта на GitHub с повторами и форс-пушем."""
import getpass
import os
import subprocess
import sys
import time

REPO = "asaD"


def run(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


def main():
    token = os.environ.get("GH_TOKEN")
    if not token:
        token = getpass.getpass("GitHub токен: ").strip()
    if not token:
        print("токен не введён")
        sys.exit(1)

    for attempt in range(1, 6):
        r = run(["git", "init", "-b", "main"])
        run(["git", "config", "user.email", "bot@local"])
        run(["git", "config", "user.name", "bot"])
        run(["git", "add", "-A"])
        run(["git", "commit", "-m", "build %d" % int(time.time())])
        run(["git", "remote", "remove", "origin"])
        run(["git", "remote", "add", "origin",
             "https://%s@github.com/KirillVelikt/%s.git" % (token, REPO)])
        push = run(["git", "push", "-f", "origin", "main"])
        if push.returncode == 0:
            print("успешно запушено с попытки", attempt)
            return
        print("попытка", attempt, "не прошла:", push.stderr.strip()[:300])
        time.sleep(3)
    print("не удалось запушить за 5 попыток")
    sys.exit(1)


if __name__ == "__main__":
    main()
