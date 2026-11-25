# -*- coding: utf-8 -*-
"""Пульт Yumani Terminal.

Находит терминалы в Wi-Fi сети (маячок по UDP 45555),
при подключении терминала автоматически открывает окно ввода суммы:
оплатить можно прямо с ПК, не трогая телефон.
Результат оплаты приходит обратно со звуковым сигналом.

Запуск:  python terminal_control.py        (графический режим)
         python terminal_control.py --cli  (консольный режим)
"""
import socket
import sys
import threading
import time

BEACON_PORT = 45555
CMD_PORT = 45557

TERMINALS = {}  # addr -> {"code":..,"name":..,"seen":..}
_pay_lock = threading.Lock()

# --- сеть ---
def beacon_listener(on_new):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("", BEACON_PORT))
    while True:
        try:
            data, addr = sock.recvfrom(4096)
        except OSError:
            continue
        text = data.decode("utf-8", "ignore")
        if not text.startswith("YUMANI_BEACON|"):
            continue
        parts = text.split("|")
        code = parts[1] if len(parts) > 1 else "?"
        name = parts[2] if len(parts) > 2 else "Терминал"
        is_new = addr not in TERMINALS
        TERMINALS[addr] = {"code": code, "name": name, "seen": time.time()}
        if on_new:
            try:
                on_new(addr, is_new)
            except Exception:
                pass

class ResultSocket:
    """Один сокет на 45558: из него шлём PAY, в него же приходит RESULT."""
    def __init__(self, on_result):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            self.sock.bind(("", 45558))
        except OSError:
            pass
        threading.Thread(target=self._loop, args=(on_result,), daemon=True).start()

    def send_pay(self, terminal_addr, amount):
        with _pay_lock:
            self.sock.sendto(("PAY|%d" % int(amount)).encode(),
                             (terminal_addr[0], CMD_PORT))

    def _loop(self, on_result):
        self.sock.settimeout(None)
        while True:
            try:
                data, _ = self.sock.recvfrom(4096)
            except OSError:
                continue
            text = data.decode("utf-8", "ignore")
            if not text.startswith("RESULT|"):
                continue
            parts = text.split("|")
            ok = parts[1] == "ok"
            amount = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 0
            try:
                on_result(ok, amount)
            except Exception:
                pass

def beep_ok():
    try:
        import winsound
        winsound.MessageBeep(winsound.MB_ICONASTERISK)
    except Exception:
        print("\a", end="", flush=True)

def beep_fail():
    try:
        import winsound
        winsound.MessageBeep(winsound.MB_ICONHAND)
    except Exception:
        print("\a", end="", flush=True)

# --- графический режим ---
def run_gui():
    import tkinter as tk
    from tkinter import ttk

    BG, CARD, FG, DIM, GREEN = "#0B0F0D", "#16181A", "#FFFFFF", "#8A9199", "#22C55E"
    root = tk.Tk()
    root.title("Пульт Yumani Terminal")
    root.geometry("580x680")
    root.configure(bg=BG)

    status_var = tk.StringVar(value="поиск терминалов…")
    list_var = tk.Variable(value=[])
    result_var = tk.StringVar(value="—")
    log = []

    pay_win = {"w": None}
    result_sock = {"s": None}

    def log_add(msg):
        log.append(msg)
        if len(log) > 200:
            del log[:-100]
        if "logw" in dir_list:
            logw.configure(state="normal")
            logw.insert("end", msg + "\n")
            logw.see("end")
            logw.configure(state="disabled")

    dir_list = []

    def refresh_list():
        items = ["%s · %s (%s)" % (TERMINALS[a]["code"], TERMINALS[a]["name"], a[0])
                 for a in sorted(TERMINALS)]
        list_var.set(items)

    def on_result(ok, amount):
        result_var.set("оплата %d ₽: УСПЕХ" % amount if ok else "оплата %d ₽: ОТКАЗ" % amount)
        log_add(("УСПЕХ" if ok else "ОТКАЗ") + ": %d ₽" % amount)
        (beep_ok if ok else beep_fail)()
        if pay_win["w"] is not None:
            try:
                pay_win["w"].show_result(ok, amount)
            except Exception:
                pass

    def open_pay_window(addr):
        t = TERMINALS.get(addr)
        if not t:
            return
        if pay_win["w"] is not None:
            try:
                pay_win["w"].lift()
                return
            except Exception:
                pay_win["w"] = None
        pay_win["w"] = PayWindow(root, t, addr)

    class PayWindow:
        def __init__(self, master, t, addr):
            self.addr = addr
            self.term = t
            self.win = tk.Toplevel(master)
            self.win.title("Оплата — %s" % t["code"])
            self.win.geometry("360x300")
            self.win.configure(bg=BG)
            self.win.protocol("WM_DELETE_WINDOW", self._close)
            self.win.attributes("-topmost", True)

            tk.Label(self.win, text="терминал подключён", bg=BG, fg=DIM,
                     font=("Segoe UI", 10)).pack(pady=(22, 4))
            tk.Label(self.win, text="%s · %s" % (t["code"], t["name"]), bg=BG,
                     fg=FG, font=("Segoe UI", 14, "bold")).pack()

            tk.Label(self.win, text="сумма, ₽", bg=BG, fg=DIM,
                     font=("Segoe UI", 10)).pack(pady=(18, 4))
            self.ent = tk.Entry(self.win, justify="center", font=("Segoe UI", 22, "bold"),
                                bg=CARD, fg=FG, insertbackground=FG, relief="flat")
            self.ent.insert(0, "198")
            self.ent.pack(ipady=8, padx=40, fill="x")
            self.ent.focus_set()

            self.btn = tk.Button(self.win, text="Оплатить с ПК", command=self.pay,
                                 bg=GREEN, fg="white", activebackground="#16A34A",
                                 activeforeground="white", relief="flat",
                                 font=("Segoe UI", 13, "bold"), cursor="hand2")
            self.btn.pack(pady=16, ipadx=20, ipady=8)

            self.res_lbl = tk.Label(self.win, text="введите сумму и нажмите кнопку",
                                    bg=BG, fg=DIM, font=("Segoe UI", 11))
            self.res_lbl.pack()

            def focus_send(_e):
                self.pay()
                return "break"
            self.ent.bind("<Return>", focus_send)

        def pay(self):
            raw = self.ent.get().strip().replace(",", ".")
            try:
                amount = int(float(raw))
            except ValueError:
                self.res_lbl.configure(text="введите число, например 198")
                return
            if amount <= 0:
                self.res_lbl.configure(text="сумма должна быть больше нуля")
                return
            if result_sock["s"] is not None:
                result_sock["s"].send_pay(self.addr, amount)
                self.res_lbl.configure(text="идёт оплата %d ₽…" % amount)
                log_add("отправлена оплата %d ₽ → %s" % (amount, self.term["code"]))

        def show_result(self, ok, amount):
            self.res_lbl.configure(text="оплата прошла ✓" if ok else "оплата не прошла")

        def _close(self):
            pay_win["w"] = None
            self.win.destroy()

    def on_new_terminal(addr, is_new):
        root.after(0, refresh_list)
        if is_new:
            def announce():
                status_var.set("терминал подключён ✓")
                log_add("подключён: %s (%s)" % (TERMINALS[addr]["code"], addr[0]))
                open_pay_window(addr)  # автоокно ввода суммы с ПК
            root.after(0, announce)

    def on_list_select(_e):
        sel = lb.curselection()
        if sel:
            addr = sorted(TERMINALS)[sel[0]]
            open_pay_window(addr)

    def manual_reconnect():
        pay_win["w"] = None
        sel = lb.curselection()
        if sel:
            open_pay_window(sorted(TERMINALS)[sel[0]])
        else:
            status_var.set("выберите терминал в списке")

    tk.Label(root, text="ПУЛЬТ YUMANI", bg=BG, fg=FG,
             font=("Segoe UI", 20, "bold")).pack(pady=(18, 2))
    tk.Label(root, textvariable=status_var, bg=BG, fg=GREEN,
             font=("Segoe UI", 11)).pack()

    fr = tk.Frame(root, bg=BG)
    fr.pack(fill="both", expand=True, padx=24, pady=12)
    lb = tk.Listbox(fr, listvariable=list_var, bg=CARD, fg=FG, selectbackground=GREEN,
                    selectforeground="white", relief="flat", font=("Consolas", 11),
                    highlightthickness=0)
    lb.pack(side="left", fill="both", expand=True)
    sb = ttk.Scrollbar(fr, command=lb.yview)
    sb.pack(side="right", fill="y")
    lb.config(yscrollcommand=sb.set)
    lb.bind("<<ListboxSelect>>", on_list_select)

    tk.Button(root, text="Оплатить выбранному…", command=manual_reconnect,
              bg=CARD, fg=FG, relief="flat", font=("Segoe UI", 11),
              activebackground="#22262A", activeforeground=FG, cursor="hand2"
              ).pack(pady=4)

    tk.Label(root, textvariable=result_var, bg=BG, fg=FG,
             font=("Segoe UI", 13, "bold")).pack(pady=(6, 2))

    logw = tk.Text(root, height=6, bg=CARD, fg="#9AA3AB", relief="flat",
                   font=("Consolas", 9), state="disabled", highlightthickness=0)
    logw.pack(fill="x", padx=24, pady=(4, 18))
    dir_list.append("logw")

    result_sock["s"] = ResultSocket(on_result)
    threading.Thread(target=beacon_listener, args=(on_new_terminal,), daemon=True).start()
    root.after(2000, refresh_list)

    def cleanup_stale():
        now = time.time()
        for a in list(TERMINALS):
            if now - TERMINALS[a]["seen"] > 8:
                del TERMINALS[a]
                log_add("потерян: %s" % (a[0],))
        refresh_list()
        root.after(4000, cleanup_stale)
    root.after(4000, cleanup_stale)

    root.mainloop()

# --- консольный режим ---
def run_cli():
    print("Пульт Yumani Terminal (CLI). Терминалы: ")
    got = threading.Event()

    def on_new(addr, is_new):
        print("  подключён: %s · %s (%s)" % (TERMINALS[addr]["code"], TERMINALS[addr]["name"], addr[0]))
        if is_new:
            got.set()

    threading.Thread(target=beacon_listener, args=(on_new,), daemon=True).start()
    sock = ResultSocket(lambda ok, amount: print(
        "  результат: %s (%d ₽)" % ("УСПЕХ" if ok else "ОТКАЗ", amount)) or beep_ok() if ok else beep_fail())
    got.wait(10)
    if not TERMINALS:
        print("  терминалы не найдены. Проверьте, что ПК и телефон в одной Wi-Fi сети.")
        return
    addr = sorted(TERMINALS)[0]
    while True:
        raw = input("Сумма (Enter = 198, q = выход): ").strip()
        if raw.lower() in ("q", "quit", "exit"):
            return
        try:
            amount = int(float(raw or "198"))
        except ValueError:
            print("  введите число")
            continue
        sock.send_pay(addr, amount)
        print("  отправлено, ждём результат…")

if __name__ == "__main__":
    if "--cli" in sys.argv:
        run_cli()
    else:
        try:
            run_gui()
        except Exception as e:
            print("GUI недоступен (%s), включаю CLI" % e)
            run_cli()
