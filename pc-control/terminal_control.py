# -*- coding: utf-8 -*-
"""
Юмани Пульт — управление терминалом с ПК.
Подключение по коду через UDP, отправка суммы, получение результата.
"""
import socket
import threading
import time
import sys

try:
    import tkinter as tk
    from tkinter import ttk, messagebox
except Exception:
    tk = None

try:
    import winsound
    HAS_WINSOUND = True
except Exception:
    HAS_WINSOUND = False

PORT_TERM = 45555   # терминал слушает здесь (discovery + команды)
PORT_CTRL = 45557   # пульт слушает здесь (ответы и результат)

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
sock.bind(("", PORT_CTRL))

state = {
    "terminal_ip": None,
    "terminal_code": None,
    "waiting_result": False,
}

def beep(kind):
    if not HAS_WINSOUND:
        return
    try:
        if kind == "ok":
            winsound.Beep(880, 200); winsound.Beep(1320, 400)
        elif kind == "fail":
            winsound.Beep(392, 250); winsound.Beep(311, 400)
        else:
            winsound.Beep(1046, 120)
    except Exception:
        pass

def listen():
    buf = bytearray(4096)
    while True:
        try:
            n, addr = sock.recvfrom_into(buf)
            msg = buf[:n].decode("utf-8", "replace").strip()
            if msg.startswith("YMNI;") and not state["waiting_result"]:
                code = msg.split(";")[1]
                state["terminal_ip"] = addr[0]
                state["terminal_code"] = code
                update_status(f"подключено: терминал {code} ({addr[0]})")
                beep("tick")
            elif msg.startswith("YMNI_RESULT;"):
                res = msg.split(";")[1]
                if res == "OK":
                    update_status("ОПЛАТА ПРОШЛА ✓")
                    beep("ok")
                else:
                    update_status("оплата не прошла ✗")
                    beep("fail")
                state["waiting_result"] = False
        except Exception:
            pass

def update_status(text):
    if APP:
        APP.after(0, lambda: status_var.set(text))

def discover():
    try:
        sock.sendto(b"YMNI_DISCOVER", ("255.255.255.255", PORT_TERM))
    except Exception as e:
        update_status(f"ошибка отправки: {e}")

def pay():
    if not state["terminal_ip"]:
        update_status("терминал не найден — нажмите Подключить")
        return
    try:
        amount = int(amount_entry.get().replace(" ", ""))
    except ValueError:
        update_status("введите сумму цифрами")
        return
    msg = f"YMNI_PAY;{amount};{state['terminal_code']}".encode()
    sock.sendto(msg, (state["terminal_ip"], PORT_TERM))
    state["waiting_result"] = True
    update_status(f"отправлена оплата {amount} ₽ — ждём результат…")
    beep("tick")

def gui():
    global APP, status_var, amount_entry
    APP = tk.Tk()
    APP.title("Юмани Пульт")
    APP.geometry("420x300")
    APP.configure(bg="#0E1116")

    tk.Label(APP, text="Юмани Пульт", font=("Segoe UI", 20, "bold"),
             bg="#0E1116", fg="#FFFFFF").pack(pady=(16, 4))
    tk.Label(APP, text="управление терминалом", font=("Segoe UI", 10),
             bg="#0E1116", fg="#8A94A6").pack()

    status_var = tk.StringVar(value="нажмите Подключить")
    tk.Label(APP, textvariable=status_var, font=("Segoe UI", 11),
             bg="#0E1116", fg="#35D07F", wraplength=380).pack(pady=10)

    frame = tk.Frame(APP, bg="#0E1116")
    frame.pack(pady=8)
    tk.Label(frame, text="Сумма:", bg="#0E1116", fg="#FFFFFF",
             font=("Segoe UI", 12)).pack(side="left", padx=4)
    amount_entry = tk.Entry(frame, width=10, font=("Segoe UI", 14), justify="center")
    amount_entry.insert(0, "198")
    amount_entry.pack(side="left", padx=4)

    def mk_btn(text, cmd, bg):
        return tk.Button(APP, text=text, command=cmd, bg=bg, fg="#0E1116",
                         font=("Segoe UI", 13, "bold"), relief="flat",
                         padx=18, pady=8, cursor="hand2")

    mk_btn("Подключить", discover, "#35D07F").pack(pady=6)
    mk_btn("Оплатить", pay, "#4ade80").pack(pady=4)

    threading.Thread(target=listen, daemon=True).start()
    APP.mainloop()

def cli():
    print("Юмани Пульт (консоль)")
    threading.Thread(target=listen, daemon=True).start()
    while True:
        cmd = input("подключить/оплатить/выход > ").strip().lower()
        if cmd == "подключить":
            discover()
        elif cmd == "оплатить":
            amount = input("сумма: ").strip()
            try:
                int(amount)
            except ValueError:
                print("цифрами, пожалуйста")
                continue
            if state["terminal_ip"]:
                sock.sendto(f"YMNI_PAY;{amount};{state['terminal_code']}".encode(),
                            (state["terminal_ip"], PORT_TERM))
                state["waiting_result"] = True
                print("ждём результат…")
            else:
                print("сначала подключите терминал")
        elif cmd in ("выход", "exit", "q"):
            break

if __name__ == "__main__":
    if tk and "--cli" not in sys.argv:
        gui()
    else:
        cli()
