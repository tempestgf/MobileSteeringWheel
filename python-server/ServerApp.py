import os
import sys
import tkinter as tk
from tkinter import scrolledtext, messagebox
from tkinter import ttk
import threading
import logging
from io import StringIO

# Configurar logging para redirigir a la interfaz gráfica
log_stream = StringIO()
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s', stream=log_stream)

import xbox as server_module  # Cambia esto por el nombre real del archivo del servidor

class ServerApp:
    def __init__(self, root):
        # Base path para la aplicación empaquetada o no empaquetada
        base_path = getattr(sys, '_MEIPASS', os.path.dirname(os.path.abspath(__file__)))

        self.root = root
        self.root.title("Mobile Wheel")
        self.root.geometry("600x600")  # Establecer el tamaño de la ventana
        self.root.resizable(False, False)  # Evitar redimensionamiento

        # Cargar el ícono en formato .ico utilizando la ruta correcta
        icon_path = os.path.join(base_path, "minimal.ico")
        self.root.iconbitmap(icon_path)  # Usar la ruta correcta para el ícono

        # Cargar el tema Azure
        tcl_path = os.path.join(base_path, "azure.tcl")
        self.root.tk.call("source", tcl_path)  # Asegúrate de que el archivo .tcl esté en el directorio del script
        self.root.tk.call("set_theme", "dark")  # Usar el tema oscuro de Azure

        # Aplicar esquema de colores Tokyo Night para la ventana principal
        self.root.configure(bg="#1a1b26")  # Fondo de la ventana

        style = ttk.Style()
        style.configure("TFrame", background="#1a1b26")  # Fondo de los frames
        style.configure("TLabelFrame", background="#1a1b26", foreground="#7aa2f7")  # Colores de los LabelFrames

        # Crear un estilo personalizado para los checkbuttons y labels con color de texto Tokyo Night
        style.configure("TokyoNight.TCheckbutton", foreground="#7aa2f7")
        style.configure("TokyoNight.TLabel", foreground="#7aa2f7")

        # Botones de control del servidor
        self.control_frame = ttk.Frame(root)
        self.control_frame.grid(row=0, column=0, padx=20, pady=20, sticky="ew")

        self.start_button = ttk.Button(self.control_frame, text="Start Server", command=self.start_server)
        self.start_button.grid(row=0, column=0, padx=5, pady=10, sticky="ew")

        self.stop_button = ttk.Button(self.control_frame, text="Stop Server", command=self.stop_server, state=tk.DISABLED)
        self.stop_button.grid(row=0, column=1, padx=5, pady=10, sticky="ew")

        # Frame para las barras de progreso
        self.progress_frame = ttk.LabelFrame(root, text="Axis", padding=(20, 10))
        self.progress_frame.grid(row=2, column=0, padx=20, pady=10, sticky="ew")

        # Barra de progreso para acelerar (solo color de texto Tokyo Night)
        self.accelerate_label = ttk.Label(self.progress_frame, text="Accelerate:", style="TokyoNight.TLabel")
        self.accelerate_label.grid(row=0, column=0, sticky="w")
        self.accelerate_bar = ttk.Progressbar(self.progress_frame, orient="horizontal", length=300, mode="determinate")
        self.accelerate_bar.grid(row=0, column=1, padx=10, pady=5)

        # Barra de progreso para frenar (solo color de texto Tokyo Night)
        self.brake_label = ttk.Label(self.progress_frame, text="Brake:", style="TokyoNight.TLabel")
        self.brake_label.grid(row=1, column=0, sticky="w")
        self.brake_bar = ttk.Progressbar(self.progress_frame, orient="horizontal", length=300, mode="determinate")
        self.brake_bar.grid(row=1, column=1, padx=10, pady=5)

        # Barra de progreso para steering (giro del volante) (solo color de texto Tokyo Night)
        self.steering_label = ttk.Label(self.progress_frame, text="Steering:", style="TokyoNight.TLabel")
        self.steering_label.grid(row=2, column=0, sticky="w")
        self.steering_bar = ttk.Progressbar(self.progress_frame, orient="horizontal", length=300, mode="determinate", maximum=32767)
        self.steering_bar.grid(row=2, column=1, padx=10, pady=5)

        # Frame para los checkbuttons (solo color de texto Tokyo Night)
        self.button_frame = ttk.LabelFrame(root, text="Button States", padding=(20, 10))
        self.button_frame.grid(row=3, column=0, padx=20, pady=10, sticky="ew")

        self.button_states = {
            'left_top': tk.BooleanVar(),
            'right_top': tk.BooleanVar(),
            'left_bottom': tk.BooleanVar(),
            'right_bottom': tk.BooleanVar(),
            'volume_up': tk.BooleanVar(),
            'volume_down': tk.BooleanVar(),
        }

        self.checkbuttons = {
            'left_top': ttk.Checkbutton(self.button_frame, text='Left Top', variable=self.button_states['left_top'], style="TokyoNight.TCheckbutton"),
            'right_top': ttk.Checkbutton(self.button_frame, text='Right Top', variable=self.button_states['right_top'], style="TokyoNight.TCheckbutton"),
            'left_bottom': ttk.Checkbutton(self.button_frame, text='Left Bottom', variable=self.button_states['left_bottom'], style="TokyoNight.TCheckbutton"),
            'right_bottom': ttk.Checkbutton(self.button_frame, text='Right Bottom', variable=self.button_states['right_bottom'], style="TokyoNight.TCheckbutton"),
            'volume_up': ttk.Checkbutton(self.button_frame, text='Volume Up', variable=self.button_states['volume_up'], style="TokyoNight.TCheckbutton"),
            'volume_down': ttk.Checkbutton(self.button_frame, text='Volume Down', variable=self.button_states['volume_down'], style="TokyoNight.TCheckbutton"),
        }

        # Colocar los checkbuttons
        for i, cb in enumerate(self.checkbuttons.values()):
            cb.grid(row=i//2, column=i%2, padx=10, pady=5, sticky="w")

        # Área de texto para mostrar los logs
        self.log_area = scrolledtext.ScrolledText(root, wrap=tk.WORD, width=60, height=10, font=("Courier New", 10), borderwidth=0, highlightthickness=0, bg="#24283b", fg="#a9b1d6", insertbackground="#c0caf5")
        self.log_area.grid(row=4, column=0, padx=20, pady=10, sticky="ew")
        self.log_area.insert(tk.END, "Logs will appear here...\n")
        self.log_area.configure(state='disabled')

        self.server_thread = None
        self.server_running = threading.Event()

        # Inicia la actualización de logs
        self.update_logs()

    def start_server(self):
        if not self.server_running.is_set():
            self.server_running.set()
            logging.info("Server is starting...")  # Log de prueba
            self.server_thread = threading.Thread(target=self.run_server)
            self.server_thread.daemon = True
            self.server_thread.start()
            self.start_button.configure(state=tk.DISABLED)
            self.stop_button.configure(state=tk.NORMAL)

    def stop_server(self):
        if self.server_running.is_set():
            self.server_running.clear()
            logging.info("Server is stopping...")  # Log de prueba
            server_module.shutdown_event.set()  # Señal para detener el servidor
            self.server_thread.join()
            self.start_button.configure(state=tk.NORMAL)
            self.stop_button.configure(state=tk.DISABLED)

    def run_server(self):
        try:
            logging.info("Starting server with UI callback")
            server_module.start_server(self.update_ui)
        except Exception as e:
            logging.error(f"Server encountered an error: {e}")
            messagebox.showerror("Error", f"Server encountered an error: {e}")

    def update_logs(self):
        self.log_area.configure(state='normal')
        self.log_area.insert(tk.END, log_stream.getvalue())  # Inserta el contenido actual del log
        log_stream.truncate(0)  # Limpia el contenido después de capturar
        log_stream.seek(0)  # Reinicia el cursor al inicio
        self.log_area.yview(tk.END)  # Desplázate al final para ver los últimos logs
        self.log_area.configure(state='disabled')
        self.root.after(100, self.update_logs)  # Ajuste de tiempo a 100ms para actualizar logs

    def update_ui(self, command, value):
        if command in ['accelerate', 'brake', 'steering']:
            self.root.after(0, self.update_progress_bars, command, value)
        elif command in ['left_top', 'right_top', 'left_bottom', 'right_bottom', 'volume_up', 'volume_down']:
            self.root.after(0, self.toggle_button_check, command)

    def update_progress_bars(self, command, value):
        if command == 'accelerate':
            self.accelerate_bar['value'] = value
        elif command == 'brake':
            self.brake_bar['value'] = value
        elif command == 'steering':
            self.steering_bar['value'] = value

    def toggle_button_check(self, button_name):
        if button_name in self.checkbuttons:
            logging.info(f"UI received button press for {button_name}.")
            self.button_states[button_name].set(True)  # Cambia el estado a seleccionado
            self.root.update_idletasks()  # Forza la actualización de la interfaz
            self.root.after(200, self.deselect_button, button_name)  # Deselecciona después de 200ms

    def deselect_button(self, button_name):
        if button_name in self.checkbuttons:
            self.button_states[button_name].set(False)  # Cambia el estado a no seleccionado
            self.root.update_idletasks()  # Forza la actualización de la interfaz

# Configuración de la ventana principal
if __name__ == "__main__":
    root = tk.Tk()
    app = ServerApp(root)
    root.mainloop()
