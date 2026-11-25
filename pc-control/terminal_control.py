# -*- coding: utf-8 -*-
"""
TerminalControl — панель терминалов yumani.
Находит терминалы по Wi-Fi (UDP-маячок), редактирует заказ (товары),
отправляет его на терминал и показывает результат оплаты + данные карты.
"""
import json
import socket
import threading
import time
import tkinter as tk
from tkinter import messagebox

BEACON_PORT = 45555
CMD_PORT = 45556

DARK = "#111114"
PANEL = "#1B1E24"
GREEN = "#22C55E"
GRAY = "#A1A1AA"
WHITE = "#FFFFFF"
RED = "#EF4444"


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("TerminalControl — панель терминалов")
        self.geometry("820x660")
        self.configure(bg=DARK)
        self.resizable(False, False)

        self.terminals = {}
        self.current = None
        self.items = [["Кофе латте", "150", "1"], ["Круассан", "90", "1"], ["Вода 0.5", "60", "2"]]
        self.lock = threading.Lock()

        self._build_ui()
        threading.Thread(target=self._listen_beacons, daemon=True).start()
        self._open_sockets()
        threading.Thread(target=self._listen_results, daemon=True).start()
        self.protocol("WM_DELETE_WINDOW", self._on_close)

    # ---------------- UI ----------------
    def _build_ui(self):
        header = tk.Frame(self, bg=DARK)
        header.pack(fill="x", padx=16, pady=(12, 6))
        tk.Label(header, text="панель терминалов", bg=DARK, fg=GREEN,
                 font=("Segoe UI", 18, "bold")).pack(side="left")
        self.lbl_status = tk.Label(header, text="поиск терминалов…", bg=DARK, fg=GRAY,
                                   font=("Segoe UI", 11))
        self.lbl_status.pack(side="right")

        body = tk.Frame(self, bg=DARK)
        body.pack(fill="both", expand=True, padx=16, pady=6)

        # левая колонка — терминалы
        left = tk.Frame(body, bg=PANEL)
        left.pack(side="left", fill="y", padx=(0, 10), ipadx=6, ipady=6)
        tk.Label(left, text="терминалы", bg=PANEL, fg=WHITE,
                 font=("Segoe UI", 12, "bold")).pack(anchor="w", padx=8, pady=(8, 4))
        self.listbox = tk.Listbox(left, bg=PANEL, fg=WHITE, selectbackground=GREEN,
                                  selectforeground="#0C0D10", width=26, height=10,
                                  font=("Consolas", 11), highlightthickness=0, bd=0)
        self.listbox.pack(padx=8, pady=4, fill="x")
        self.listbox.bind("<<ListboxSelect>>", self._on_select)
        tk.Label(left, text="карта:", bg=PANEL, fg=GRAY,
                 font=("Segoe UI", 11)).pack(anchor="w", padx=8, pady=(14, 0))
        self.lbl_card = tk.Label(left, text="—", bg=PANEL, fg=WHITE,
                                 font=("Segoe UI", 13, "bold"))
        self.lbl_card.pack(anchor="w", padx=8)
        self.lbl_result = tk.Label(left, text="", bg=PANEL, fg=GRAY,
                                   font=("Segoe UI", 12))
        self.lbl_result.pack(anchor="w", padx=8, pady=(6, 8))

        # правая колонка — заказ
        right = tk.Frame(body, bg=DARK)
        right.pack(side="left", fill="both", expand=True)
        tk.Label(right, text="заказ", bg=DARK, fg=WHITE,
                 font=("Segoe UI", 12, "bold")).pack(anchor="w")
        self.rows_frame = tk.Frame(right, bg=DARK)
        self.rows_frame.pack(fill="x", pady=4)
        self.row_entries = []
        for it in self.items:
            self._add_row(it[0], it[1], it[2])

        btns = tk.Frame(right, bg=DARK)
        btns.pack(anchor="w", pady=4)
        tk.Button(btns, text="+ товар", command=self._add_row,
                  bg=PANEL, fg=WHITE, activebackground=PANEL, activeforeground=GREEN,
                  relief="flat", font=("Segoe UI", 10)).pack(side="left")
        tk.Button(btns, text="− убрать последний", command=self._del_row,
                  bg=PANEL, fg=GRAY, activebackground=PANEL, activeforeground=RED,
                  relief="flat", font=("Segoe UI", 10)).pack(side="left", padx=6)

        self.lbl_total = tk.Label(right, text="итого: 0 ₽", bg=DARK, fg=GREEN,
                                  font=("Segoe UI", 20, "bold"))
        self.lbl_total.pack(anchor="w", pady=(8, 4))

        tk.Button(right, text="отправить заказ и оплатить", command=self._pay,
                  bg=GREEN, fg="#0C0D10", activebackground="#16A34A",
                  relief="flat", font=("Segoe UI", 13, "bold"), pady=8).pack(fill="x", pady=6)

        self.log = tk.Text(right, bg=PANEL, fg=GRAY, height=9, font=("Consolas", 10),
                           highlightthickness=0, bd=0, state="disabled", wrap="word")
        self.log.pack(fill="both", expand=True, pady=(8, 0))

    def _add_row(self, name="", price="0", qty="1"):
        row = tk.Frame(self.rows_frame, bg=DARK)
        row.pack(fill="x", pady=2)
        e1 = tk.Entry(row, bg=PANEL, fg=WHITE, insertbackground=WHITE, width=24,
                      relief="flat", font=("Segoe UI", 11))
        e1.insert(0, name)
        e1.pack(side="left", padx=(0, 6))
        e2 = tk.Entry(row, bg=PANEL, fg=WHITE, insertbackground=WHITE, width=8,
                      relief="flat", justify="center", font=("Segoe UI", 11))
        e2.insert(0, str(price))
        e2.pack(side="left", padx=(0, 6))
        e3 = tk.Entry(row, bg=PANEL, fg=WHITE, insertbackground=WHITE, width=4,
                      relief="flat", justify="center", font=("Segoe UI", 11))
        e3.insert(0, str(qty))
        e3.pack(side="left")
        e1.bind("<KeyRelease>", lambda e: self._update_total())
        e2.bind("<KeyRelease>", lambda e: self._update_total())
        e3.bind("<KeyRelease>", lambda e: self._update_total())
        self.row_entries.append((e1, e2, e3))
        self._update_total()

    def _del_row(self):
        if self.row_entries:
            row = self.row_entries.pop()
            for e in row:
                e.master.destroy()
            self._update_total()

    def _update_total(self):
        total = 0
        for e1, e2, e3 in self.row_entries:
            try:
                total += int(float(e2.get() or 0)) * max(1, int(e3.get() or 1))
            except ValueError:
                pass
        self.lbl_total.config(text="итого: " + str(total) + " ₽")
        self.total_value = total

    # ---------------- сеть ----------------
    def _open_sockets(self):
        self.cmd_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.cmd_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            self.cmd_sock.bind(("", 45557))
        except OSError:
            pass
        self.sock_lock = threading.Lock()

    def _listen_beacons(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.settimeout(1.0)
        try:
            s.bind(("", BEACON_PORT))
        except OSError:
            self.after(0, lambda: self._log("не удалось открыть порт маячка"))
            return
        while True:
            try:
                data, addr = s.recvfrom(8192)
                obj = json.loads(data.decode("utf-8"))
                if obj.get("type") == "beacon":
                    new = addr[0] not in self.terminals
                    self.terminals[addr[0]] = {"name": obj.get("name", ""), "addr": addr,
                                               "last": time.time()}
                    self.after(0, lambda: self._refresh(new))
            except Exception:
                pass

    def _listen_results(self):
        while True:
            try:
                data, addr = self.cmd_sock.recvfrom(8192)
                obj = json.loads(data.decode("utf-8"))
                self.after(0, lambda o=obj: self._on_result(o))
            except Exception:
                pass

    def _on_result(self, obj):
        t = obj.get("type")
        if t == "payment_result":
            if obj.get("success"):
                self.lbl_result.config(text="оплата прошла ✓", fg=GREEN)
                self._log("оплата прошла ✓")
                self._beep_ok()
            else:
                self.lbl_result.config(text="операция отклонена", fg=RED)
                self._log("операция отклонена")
                self._beep_fail()
        elif t == "card":
            card = obj.get("scheme", "?") + " " + obj.get("masked", "")
            self.lbl_card.config(text=card)
            self._log("карта: " + card)

    def _refresh(self, new_terminal):
        self.listbox.delete(0, "end")
        now = time.time()
        for ip, info in list(self.terminals.items()):
            if now - info["last"] > 10:
                del self.terminals[ip]
                continue
            self.listbox.insert("end", info["name"] + "  [" + ip + "]")
        if new_terminal and self.terminals:
            self.current = sorted(self.terminals.items(),
                                  key=lambda kv: kv[1]["last"], reverse=True)[0][1]
            self.listbox.selection_set(0)
            self.lbl_status.config(text="терминал подключён ✓", fg=GREEN)
            self._log("терминал подключён: " + self.current["name"])
        elif self.current is None and self.terminals:
            self.current = list(self.terminals.values())[0]

    def _on_select(self, event):
        sel = event.widget.curselection()
        if sel:
            self.current = list(self.terminals.values())[sel[0]]

    def _send(self, obj):
        t = self.current
        if not t:
            messagebox.showwarning("TerminalControl", "терминал не найден")
            return
        with self.sock_lock:
            try:
                self.cmd_sock.sendto(json.dumps(obj).encode("utf-8"),
                                     (t["addr"][0], CMD_PORT))
            except Exception as e:
                self._log("ошибка отправки: " + str(e))

    # ---------------- действия ----------------
    def _collect_order(self):
        items = []
        for e1, e2, e3 in self.row_entries:
            n = e1.get().strip()
            if not n:
                continue
            try:
                p = int(float(e2.get() or 0))
            except ValueError:
                p = 0
            try:
                q = max(1, int(e3.get() or 1))
            except ValueError:
                q = 1
            items.append({"n": n, "p": p, "q": q})
        return items

    def _pay(self):
        items = self._collect_order()
        if not items:
            messagebox.showwarning("TerminalControl", "добавьте хотя бы один товар")
            return
        order = {"type": "order", "items": items, "total": self.total_value}
        self._send(order)
        self._log("заказ отправлен: " + str(self.total_value) + " ₽ (" + str(len(items)) + " поз.)")
        self._send({"type": "connect"})

    def _log(self, text):
        stamp = time.strftime("%H:%M:%S")
        self.log.config(state="normal")
        self.log.insert("end", "[" + stamp + "] " + text + "\n")
        self.log.see("end")
        self.log.config(state="disabled")

    def _beep_ok(self):
        try:
            import winsound
            winsound.MessageBeep(winsound.MB_ICONASTERISK)
        except Exception:
            print("\a", end="")

    def _beep_fail(self):
        try:
            import winsound
            winsound.MessageBeep(winsound.MB_ICONHAND)
        except Exception:
            print("\a", end="")

    def _on_close(self):
        try:
            self.cmd_sock.close()
        except Exception:
            pass
        self.destroy()


if __name__ == "__main__":
    App().mainloop()
