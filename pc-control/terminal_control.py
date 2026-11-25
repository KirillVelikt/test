# -*- coding: utf-8 -*-
"""
Yumani Пульт — управление терминалом по коду.

GUI:   python terminal_control.py
CLI:   python terminal_control.py --cli

Протокол (UDP):
  терминал каждые 2 сек шлёт "YUMANI|КОД" на 255.255.255.255:45555
  пульт, увидев свой код, шлёт "PAY СУММА" на ip_терминала:45557
  терминал после оплаты шлёт "RESULT OK" или "RESULT FAIL" обратно
"""

import socket
import sys
import threading
import time

LISTEN_PORT = 45555
CMD_PORT = 45557

discovered = {}

try:
    import winsound

    def beep(ok):
        if ok:
            winsound.Beep(880, 250)
        else:
            winsound.Beep(220, 400)
except ImportError:
    def beep(ok):
        print("\a", end="")


def listener():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(("", LISTEN_PORT))
        while True:
            data, addr = s.recvfrom(256)
            msg = data.decode("utf-8", "replace").strip()
            if msg.startswith("YUMANI|"):
                code = msg.split("|")[1]
                discovered[code] = (addr[0], time.time())
    except Exception as e:
        print("Ошибка слушателя:", e)


def pay(ip, amount):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("", CMD_PORT))
    s.settimeout(20)
    try:
        s.sendto(("PAY " + str(amount)).encode(), (ip, CMD_PORT))
        data, _ = s.recvfrom(256)
        return data.decode("utf-8", "replace").strip()
    except socket.timeout:
        return "нет ответа от терминала"
    finally:
        s.close()


def run_gui():
    import tkinter as tk
    from tkinter import ttk, messagebox

    root = tk.Tk()
    root.title("Yumani Пульт")
    root.geometry("440x460")
    root.configure(bg="#1A1A1D")

    status_var = tk.StringVar(value="поиск терминалов…")

    tk.Label(root, text="Yumani Пульт", bg="#1A1A1D", fg="#FFFFFF",
             font=("Arial", 20, "bold")).pack(pady=(20, 10))

    frm = tk.Frame(root, bg="#1A1A1D")
    frm.pack()

    tk.Label(frm, text="Код терминала:", bg="#1A1A1D", fg="#A1A1AA",
             font=("Arial", 12)).grid(row=0, column=0, sticky="e", padx=6, pady=6)
    code_entry = ttk.Entry(frm, width=12, font=("Arial", 14))
    code_entry.grid(row=0, column=1, padx=6)

    tk.Label(frm, text="Сумма, руб:", bg="#1A1A1D", fg="#A1A1AA",
             font=("Arial", 12)).grid(row=1, column=0, sticky="e", padx=6, pady=6)
    amount_entry = ttk.Entry(frm, width=12, font=("Arial", 14))
    amount_entry.insert(0, "198")
    amount_entry.grid(row=1, column=1, padx=6)

    found_var = tk.StringVar(value="")

    def refresh():
        code = code_entry.get().strip()
        if code in discovered:
            found_var.set("терминал найден: " + discovered[code][0])
        else:
            found_var.set("терминал не найден — проверьте код и Wi-Fi")

    def on_pay():
        code = code_entry.get().strip()
        if code not in discovered:
            messagebox.showerror(
                "Ошибка",
                "Терминал с таким кодом не найден.\nПроверьте код и Wi-Fi сеть."
            )
            return
        try:
            amount = int(amount_entry.get())
        except ValueError:
            messagebox.showerror("Ошибка", "Сумма должна быть числом.")
            return
        status_var.set("идёт оплата…")
        ip = discovered[code][0]
        result = pay(ip, amount)
        ok = result == "RESULT OK"
        beep(ok)
        status_var.set("оплата прошла" if ok else "оплата не прошла: " + result)

    ttk.Button(root, text="Оплатить", command=on_pay).pack(pady=16)
    tk.Label(root, textvariable=found_var, bg="#1A1A1D", fg="#4ADE80",
             font=("Arial", 11)).pack()
    tk.Label(root, textvariable=status_var, bg="#1A1A1D", fg="#FFFFFF",
             font=("Arial", 12)).pack(pady=10)

    threading.Thread(target=listener, daemon=True).start()

    def loop():
        refresh()
        root.after(2000, loop)
    root.after(2000, loop)

    root.mainloop()


def run_cli():
    threading.Thread(target=listener, daemon=True).start()
    code = input("Код терминала: ").strip()
    print("Жду терминал с кодом", code, "...")
    while code not in discovered:
        time.sleep(1)
    ip = discovered[code][0]
    print("Найден:", ip)
    amount = int(input("Сумма, руб: ").strip())
    print("Отправляю оплату...")
    result = pay(ip, amount)
    ok = result == "RESULT OK"
    beep(ok)
    print("Результат:", result)


if __name__ == "__main__":
    if "--cli" in sys.argv:
        run_cli()
    else:
        run_gui()
