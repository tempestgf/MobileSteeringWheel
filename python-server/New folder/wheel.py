import socket
import ctypes
import time
import struct
import os
import logging
from collections import deque

# Configurar el registro
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# Determinar si estamos en una instalación de Python de 32 bits o 64 bits
is_64bits = struct.calcsize("P") * 8 == 64

# Definir las rutas de las DLL para ambas arquitecturas
dll_path_64 = 'C:/Program Files/vJoy/x64/vJoyInterface.dll'
dll_path_32 = 'C:/Program Files (x86)/vJoy/x86/vJoyInterface.dll'
dll_path = dll_path_64 if is_64bits else dll_path_32

# Verificar si el archivo DLL existe
if not os.path.isfile(dll_path):
    raise FileNotFoundError(f"Could not find module '{dll_path}'")

# Cargar la DLL
vjoy = ctypes.WinDLL(dll_path)

# Definir constantes según la documentación de vJoy
VJD_STAT_OWN = 0
VJD_STAT_FREE = 1
VJD_STAT_BUSY = 2
VJD_STAT_MISS = 3
VJD_STAT_UNKN = 4

# Definir funciones de la DLL
vjoy.AcquireVJD.argtypes = [ctypes.c_uint]
vjoy.AcquireVJD.restype = ctypes.c_bool

vjoy.RelinquishVJD.argtypes = [ctypes.c_uint]
vjoy.RelinquishVJD.restype = ctypes.c_bool

vjoy.SetBtn.argtypes = [ctypes.c_bool, ctypes.c_uint, ctypes.c_uint]
vjoy.SetBtn.restype = ctypes.c_bool

vjoy.SetAxis.argtypes = [ctypes.c_long, ctypes.c_uint, ctypes.c_uint]
vjoy.SetAxis.restype = ctypes.c_bool

vjoy.GetVJDStatus.argtypes = [ctypes.c_uint]
vjoy.GetVJDStatus.restype = ctypes.c_int

# Adquirir el dispositivo
def acquire_vjd(device_id):
    status = vjoy.GetVJDStatus(device_id)
    if status in [VJD_STAT_FREE, VJD_STAT_OWN]:
        if vjoy.AcquireVJD(device_id):
            logging.info(f"Device {device_id} acquired successfully.")
        else:
            logging.error(f"Failed to acquire device {device_id}.")
    else:
        logging.warning(f"Device {device_id} is not free. Status: {status}")

# Liberar el dispositivo
def relinquish_vjd(device_id):
    if vjoy.RelinquishVJD(device_id):
        logging.info(f"Device {device_id} relinquished successfully.")
    else:
        logging.error(f"Failed to relinquish device {device_id}.")

# Configurar un botón
def set_button(device_id, button_id, state):
    if vjoy.SetBtn(state, device_id, button_id):
        logging.debug(f"Button {button_id} on device {device_id} set to {'pressed' if state else 'released'}.")
    else:
        logging.error(f"Failed to set button {button_id} on device {device_id}.")

# Configurar un eje
def set_axis(device_id, axis_id, value):
    if vjoy.SetAxis(value, device_id, axis_id):
        logging.debug(f"Axis {axis_id} on device {device_id} set to {value}.")
    else:
        logging.error(f"Failed to set axis {axis_id} on device {device_id}.")

# Configurar el servidor
TCP_IP = "0.0.0.0"
TCP_PORT = 12345

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.bind((TCP_IP, TCP_PORT))
sock.listen(1)

logging.info(f"Listening for connections on {TCP_IP}:{TCP_PORT}")

# Adquirir el dispositivo vJoy
device_id = 1
acquire_vjd(device_id)

def map_value(value, in_min, in_max, out_min, out_max):
    return int((value - in_min) * (out_max - out_min) / (in_max - in_min) + out_min)

def low_pass_filter(input, output, alpha=0.5):
    return output + alpha * (input - output)

last_steering_value = 16384  # Valor inicial centrado

# Mapping for command identifiers
command_map = {
    'A': 'ACCELEROMETER',
    'B': 'ACCELERATE',
    'C': 'BRAKE',
    'D': 'LEFT_TOP',
    'E': 'LEFT_BOTTOM',
    'F': 'RIGHT_TOP',
    'G': 'RIGHT_BOTTOM',
    'VOLUME_UP': 'VOLUME_UP',
    'VOLUME_DOWN': 'VOLUME_DOWN'
}

# Cola para almacenar los mensajes entrantes
critical_message_queue = deque()  # Cola para mensajes críticos (botones)
non_critical_message_queue = deque()  # Cola para mensajes no críticos (acelerómetro, acelerador/freno)

# Función para procesar mensajes críticos (botones)
def process_critical_message(message):
    global last_steering_value  # Declarar la variable como global
    logging.debug(f"Processing critical message: {message}")

    if not message:
        logging.error("Error: Empty message received")
        return

    parts = message.split(":")
    if len(parts) == 1:
        command = parts[0]
        value = None
    elif len(parts) == 2:
        command, value = parts
    else:
        logging.error(f"Error: Incorrect message format. Parts: {parts}")
        return

    if command not in command_map:
        logging.error(f"Error: Unknown command identifier: {command}")
        return

    try:
        if command in ['D', 'E', 'F', 'G', 'VOLUME_UP', 'VOLUME_DOWN'] and value is None:
            button_id = {
                'D': 1,
                'E': 2,
                'F': 3,
                'G': 4,
                'VOLUME_UP': 5,
                'VOLUME_DOWN': 6
            }[command]
            set_button(device_id, button_id, True)
            logging.info(f"Button {button_id} pressed ({command_map[command]})")
            time.sleep(0.05)
            set_button(device_id, button_id, False)
        else:
            logging.error(f"Error: Unknown or improperly formatted command: {command}")
    except ValueError as ve:
        logging.error(f"Error converting value: {ve}")

# Función para procesar mensajes no críticos (acelerómetro, acelerador/freno)
def process_non_critical_message(message):
    global last_steering_value  # Declarar la variable como global
    logging.debug(f"Processing non-critical message: {message}")

    if not message:
        logging.error("Error: Empty message received")
        return

    parts = message.split(":")
    if len(parts) == 1:
        command = parts[0]
        value = None
    elif len(parts) == 2:
        command, value = parts
    else:
        logging.error(f"Error: Incorrect message format. Parts: {parts}")
        return

    if command not in command_map:
        logging.error(f"Error: Unknown command identifier: {command}")
        return

    try:
        if command == 'A' and value is not None:
            y = float(value)
            y = max(min(y, 10.0), -10.0)
            steering_value = map_value(y, -10.0, 10.0, 1, 32767)
            steering_value = low_pass_filter(steering_value, last_steering_value)
            last_steering_value = steering_value
            set_axis(device_id, 0x30, int(steering_value))
            logging.info(f"Steering value mapped: {int(steering_value)}")
        elif command in ['B', 'C'] and value is not None:
            int_value = int(value)
            axis_value = map_value(int_value, 0, 100, 1, 32767)
            axis_id = 0x32 if command == 'B' else 0x35
            set_axis(device_id, axis_id, axis_value)
            logging.info(f"{command_map[command]} value mapped: {axis_value}")
        else:
            logging.error(f"Error: Unknown or improperly formatted command: {command}")
    except ValueError as ve:
        logging.error(f"Error converting value: {ve}")

# Bucle principal del servidor
while True:
    conn, addr = sock.accept()
    logging.info(f"Connection accepted from {addr}")
    try:
        with conn:
            buffer = ""
            while True:
                data = conn.recv(1024).decode('utf-8')
                if not data:
                    break
                buffer += data
                while '\n' in buffer:
                    line, buffer = buffer.split('\n', 1)
                    message = line.strip()
                    if message:
                        parts = message.split(":")
                        command = parts[0] if len(parts) > 0 else None
                        if command in ['D', 'E', 'F', 'G', 'VOLUME_UP', 'VOLUME_DOWN']:
                            critical_message_queue.append(message)
                        else:
                            non_critical_message_queue.append(message)

                # Procesar todos los mensajes críticos en la cola
                while critical_message_queue:
                    msg = critical_message_queue.popleft()
                    process_critical_message(msg)

                # Procesar todos los mensajes no críticos en la cola
                while non_critical_message_queue:
                    msg = non_critical_message_queue.popleft()
                    process_non_critical_message(msg)
    except Exception as e:
        logging.error(f"Error processing data: {e}")
    finally:
        logging.info(f"Connection with {addr} closed")
