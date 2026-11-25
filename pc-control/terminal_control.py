# -*- coding: utf-8 -*-
"""
Пульт Юмани — управление терминалом с ПК.
Запуск: python terminal_control.py
Подключение: введите код с экрана терминала, нажмите "Подключить".
"""
import socket
import threading
import time
import tkinter as tk
from tkinter import ttk

BEACON_PORT = 45555
RESULT_PORT = 45557

BG = "#0E1B2A"
FG = "#FFFFFF"
GREEN = "#4ADE80"
DIM = "#8AA0B8"


def beep(freq, dur):
    try:
        import winsound
        winsound.Beep(freq, dur)
    except Exception:
        print("\a", end="")


class App:
    def __init__(self):
        self.connected = False
        self.peer = None

        self.root = tk.Tk()
        self.root.title("Пульт Юмани — терминал")
        self.root.geometry("420x420")
        self.root.configure(bg=BG)
        self.root.resizable(False, False)

        tk.Label(self.root, text="ПУЛЬТ ЮМАНИ", bg=BG, fg=FG,
                 font=("Segoe UI", 20, "bold")).pack(pady=(24, 2))
        tk.Label(self.root, text="управление терминалом", bg=BG, fg=DIM,
                 font=("Segoe UI", 10)).pack()

        tk.Label(self.root, text="код терминала", bg=BG, fg=DIM,
                 font=("Segoe UI", 10)).pack(pady=(24, 2))
        self.code_var = tk.StringVar()
        e1 = tk.Entry(self.root, textvariable=self.code_var, justify="center",
                      font=("Consolas", 22), bg="#17212E", fg=GREEN,
                      insertbackground=GREEN, relief="flat")
        e1.pack(ipady=6, ipadx=10)
        e1.focus()

        self.btn_connect = tk.Button(self.root, text="ПОДКЛЮЧИТЬ",
                                     command=self.connect, bg="#17212E", fg=FG,
                                     font=("Segoe UI", 11, "bold"),
                                     relief="flat", padx=20, pady=8)
        self.btn_connect.pack(pady=(10, 4))

        self.status_var = tk.StringVar(value="введите код с экрана терминала")
        tk.Label(self.root, textvariable=self.status_var, bg=BG, fg=GREEN,
                 font=("Segoe UI", 11)).pack(pady=4)

        frame = tk.Frame(self.root, bg=BG)
        frame.pack(pady=(16, 0))
        tk.Label(frame, text="сумма, ₽", bg=BG, fg=DIM,
                 font=("Segoe UI", 10)).grid(row=0, column=0, sticky="e", padx=4)
        self.amount_var = tk.StringVar(value="198")
        e2 = tk.Entry(frame, textvariable=self.amount_var, width=8, justify="center",
                      font=("Segoe UI", 14), bg="#17212E", fg=FG,
                      insertbackground=FG, relief="flat")
        e2.grid(row=0, column=1, ipady=4)

        self.btn_pay = tk.Button(self.root, text="ЗАПУСТИТЬ ОПЛАТУ",
                                 command=self.pay, bg="#7BD44A", fg="#123018",
                                 font=("Segoe UI", 12, "bold"),
                                 relief="flat", padx=20, pady=8, state="disabled")
        self.btn_pay.pack(pady=(16, 4))

        threading.Thread(target=self.result_listener, daemon=True).start()
        self.root.mainloop()

    def connect(self):
        code = self.code_var.get().strip()
        if not code.isdigit():
            self.status_var.set("код — только цифры")
            return
        self.status_var.set("ищем терминал…")
        threading.Thread(target=self.pair_loop, args=(code,), daemon=True).start()

    def pair_loop(self, code):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        s.settimeout(0.6)
        deadline = time.time() + 90
        while time.time() < deadline and not self.connected:
            try:
                s.sendto(f"PAIR:{code}".encode(), ("255.255.255.255", BEACON_PORT))
                data, addr = s.recvfrom(256)
                if data.decode().strip() == f"ACK:{code}":
                    self.connected = True
                    self.peer = addr
                    self.root.after(0, lambda: self.status_var.set("подключено ✓"))
                    self.root.after(0, lambda: self.btn_pay.config(state="normal"))
                    beep(880, 150)
                    return
            except socket.timeout:
                pass
        if not self.connected:
            self.root.after(0, lambda: self.status_var.set("терминал не найден"))

    def pay(self):
        if not self.connected:
            return
        amount = self.amount_var.get().strip()
        if not amount.isdigit():
            self.status_var.set("сумма — только цифры")
            return
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.sendto(f"PAY:{int(amount)}".encode(), self.peer)
            self.status_var.set("оплата запущена, ждём результат…")
        except Exception as e:
            self.status_var.set(f"ошибка отправки: {e}")

    def result_listener(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.bind(("", RESULT_PORT))
        except Exception:
            return
        while True:
            try:
                data, _ = s.recvfrom(256)
                msg = data.decode().strip()
                if msg.startswith("RESULT:OK"):
                    self.root.after(0, lambda: self.status_var.set("оплата прошла ✓"))
                    beep(1200, 300)
                elif msg.startswith("RESULT:FAIL"):
                    self.root.after(0, lambda: self.status_var.set("оплата не прошла"))
                    beep(300, 500)
            except Exception:
                pass


if __name__ == "__main__":
    App()
