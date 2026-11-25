# -*- coding: utf-8 -*-
"""Пульт Юмани-терминала: поиск по Wi-Fi, оплата с ПК, звук результата."""
import json
import socket
import threading
import time
import sys

BEACON_PORT = 45555
COMMAND_PORT = 45557
RESULT_PORT = 45558

terminals = {}  # code -> {"ip":..., "name":..., "last":time}
lock = threading.Lock()


def listener_thread(on_new_terminal):
    """Слушает маячки терминалов."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    try:
        s.bind(("", BEACON_PORT))
    except OSError as e:
        print("Не удалось открыть порт маячка:", e)
        return
    while True:
        try:
            data, addr = s.recvfrom(4096)
            js = json.loads(data.decode("utf-8"))
            code = js.get("code")
            if not code:
                continue
            new = False
            with lock:
                if code not in terminals:
                    new = True
                terminals[code] = {
                    "ip": addr[0],
                    "name": js.get("name", "терминал"),
                    "last": time.time(),
                }
            if new:
                try:
                    on_new_terminal(code)
                except Exception:
                    pass
        except Exception:
            time.sleep(0.2)


def send_pay(ip, amount):
    """Отправляет команду оплаты терминалу."""
    msg = json.dumps({"cmd": "pay", "amount": int(amount)}).encode("utf-8")
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.sendto(msg, (ip, COMMAND_PORT))
    s.close()


def result_listener_thread(on_result):
    """Ждёт результат оплаты от терминала."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        s.bind(("", RESULT_PORT))
    except OSError as e:
        print("Не удалось открыть порт результата:", e)
        return
    while True:
        try:
            data, _ = s.recvfrom(4096)
            js = json.loads(data.decode("utf-8"))
            on_result(js.get("result") == "ok", js.get("code"))
        except Exception:
            time.sleep(0.1)


def beep(freq, dur_ms):
    try:
        import winsound
        winsound.Beep(freq, dur_ms)
    except Exception:
        try:
            sys.stdout.write("\a")
            sys.stdout.flush()
        except Exception:
            pass


# ---------- GUI ----------
def run_gui():
    import tkinter as tk
    from tkinter import ttk

    BG = "#0C0D10"
    CARD = "#1A1B20"
    GREEN = "#22C55E"
    FG = "#FFFFFF"
    DIM = "#8A8F98"

    root = tk.Tk()
    root.title("Юмани пульт")
    root.configure(bg=BG)
    root.geometry("760x560")

    header = tk.Frame(root, bg=BG)
    header.pack(fill="x", padx=24, pady=(20, 8))
    tk.Label(header, text="Юмани пульт", font=("Segoe UI", 22, "bold"),
             bg=BG, fg=FG).pack(side="left")
    status_dot = tk.Label(header, text="● поиск…", font=("Segoe UI", 12),
                          bg=BG, fg=DIM)
    status_dot.pack(side="right")

    # список терминалов
    list_card = tk.Frame(root, bg=CARD)
    list_card.pack(fill="x", padx=24, pady=10)
    tk.Label(list_card, text="ТЕРМИНАЛЫ В СЕТИ", font=("Segoe UI", 10, "bold"),
             bg=CARD, fg=GREEN).pack(anchor="w", padx=16, pady=(10, 4))
    listbox = tk.Listbox(list_card, bg=CARD, fg=FG, relief="flat",
                         selectbackground=GREEN, selectforeground="#0C2B12",
                         font=("Consolas", 13), height=4, highlightthickness=0)
    listbox.pack(fill="x", padx=16, pady=(0, 12))

    # панель оплаты
    pay_card = tk.Frame(root, bg=CARD)
    pay_card.pack(fill="x", padx=24, pady=6)
    tk.Label(pay_card, text="ОПЛАТА", font=("Segoe UI", 10, "bold"),
             bg=CARD, fg=GREEN).pack(anchor="w", padx=16, pady=(12, 2))

    amount_row = tk.Frame(pay_card, bg=CARD)
    amount_row.pack(fill="x", padx=16, pady=(2, 6))
    tk.Label(amount_row, text="Сумма, ₽", bg=CARD, fg=DIM,
             font=("Segoe UI", 12)).pack(side="left")
    amount_var = tk.StringVar(value="198")
    entry = tk.Entry(amount_row, textvariable=amount_var, bg="#26272E", fg=FG,
                     insertbackground=FG, relief="flat", width=12,
                     font=("Segoe UI", 16, "bold"), justify="center")
    entry.pack(side="left", padx=12, pady=6)
    pay_status = tk.Label(pay_card, text="ожидание терминала…", bg=CARD, fg=DIM,
                          font=("Segoe UI", 11))
    pay_status.pack(side="left", padx=10)

    def do_pay():
        sel = listbox.curselection()
        code = None
        if sel:
            code = listbox.get(sel[0]).split(" ")[0]
        if not code and terminals:
            code = list(terminals.keys())[0]
        if not code:
            pay_status.config(text="нет терминалов", fg="#F87171")
            return
        try:
            amt = int(amount_var.get())
        except ValueError:
            pay_status.config(text="сумма — число", fg="#F87171")
            return
        ip = terminals.get(code, {}).get("ip")
        if not ip:
            pay_status.config(text="терминал отвалился", fg="#F87171")
            return
        try:
            send_pay(ip, amt)
            pay_status.config(text="оплата запущена…", fg=GREEN)
        except Exception as e:
            pay_status.config(text="ошибка отправки", fg="#F87171")

    pay_btn = tk.Button(pay_card, text="Оплатить с ПК", command=do_pay,
                        bg=GREEN, fg="#0C2B12", activebackground="#4ADE80",
                        activeforeground="#0C2B12", relief="flat", padx=22, pady=8,
                        font=("Segoe UI", 12, "bold"), cursor="hand2")
    pay_btn.pack(side="left", padx=16, pady=(4, 14))

    # лог
    log_card = tk.Frame(root, bg=CARD)
    log_card.pack(fill="both", expand=True, padx=24, pady=(6, 20))
    tk.Label(log_card, text="СОБЫТИЯ", font=("Segoe UI", 10, "bold"),
             bg=CARD, fg=GREEN).pack(anchor="w", padx=16, pady=(10, 2))
    log = tk.Text(log_card, bg=CARD, fg=DIM, relief="flat", height=8,
                  font=("Consolas", 10), highlightthickness=0)
    log.pack(fill="both", expand=True, padx=16, pady=(0, 12))

    def log_line(text):
        log.config(state="normal")
        log.insert("end", time.strftime("[%H:%M:%S] ") + text + "\n")
        log.see("end")
        log.config(state="disabled")

    def on_new_terminal(code):
        info = terminals.get(code, {})
        def ui():
            log_line("подключён терминал " + code + " — " + info.get("name", ""))
            refresh_list()
            beep(1200, 80)
        root.after(0, ui)

    def on_result(ok, code):
        def ui():
            if ok:
                pay_status.config(text="оплата прошла ✓", fg=GREEN)
                log_line("оплата прошла (" + str(code) + ")")
                beep(880, 120)
            else:
                pay_status.config(text="оплата не прошла", fg="#F87171")
                log_line("оплата отклонена (" + str(code) + ")")
                beep(300, 200)
        root.after(0, ui)

    def refresh_list():
        with lock:
            items = sorted(terminals.items(), key=lambda kv: -kv[1]["last"])
        listbox.delete(0, "end")
        for code, info in items:
            listbox.insert("end", code + "  " + info["name"] + "  (" + info["ip"] + ")")

    def poll():
        refresh_list()
        if terminals:
            status_dot.config(text="● подключено", fg=GREEN)
        else:
            status_dot.config(text="● поиск…", fg=DIM)
        root.after(1500, poll)

    entry.bind("<Return>", lambda e: do_pay())

    threading.Thread(target=listener_thread, args=(on_new_terminal,), daemon=True).start()
    threading.Thread(target=result_listener_thread, args=(on_result,), daemon=True).start()
    poll()
    root.mainloop()


# ---------- CLI ----------
def run_cli():
    def on_new(code):
        print("подключён терминал:", code)
        beep(1200, 80)

    def on_result(ok, code):
        print("результат:", "оплачено" if ok else "отклонено")
        beep(880 if ok else 300, 150 if ok else 250)

    threading.Thread(target=listener_thread, args=(on_new,), daemon=True).start()
    threading.Thread(target=result_listener_thread, args=(on_result,), daemon=True).start()
    print("Пульт запущен. Ждём терминалы в сети…")
    while True:
        line = input("сумма (или Enter для списка): ").strip()
        if line:
            with lock:
                if terminals:
                    code = list(terminals.keys())[0]
                    ip = terminals[code]["ip"]
                    try:
                        send_pay(ip, int(line))
                        print("отправлено", code)
                    except Exception as e:
                        print("ошибка:", e)
                else:
                    print("нет терминалов")
        else:
            with lock:
                for c, i in terminals.items():
                    print(c, i["name"], i["ip"])


if __name__ == "__main__":
    if "--cli" in sys.argv:
        run_cli()
    else:
        try:
            run_gui()
        except Exception as e:
            print("GUI недоступен, CLI-режим:", e)
            run_cli()
