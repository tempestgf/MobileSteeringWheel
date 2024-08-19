import socket
import ctypes
import time
import struct
import os
import logging
from collections import deque
import threading
import netifaces
import signal
import tkinter as tk
# Importa el log_stream desde ServerApp si es posible

# Configuración del registro
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

is_64bits = struct.calcsize("P") * 8 == 64
dll_path_64 = './vJoy/x64/vJoyInterface.dll'
dll_path_32 = './vJoy/x86/vJoyInterface.dll'
dll_path = dll_path_64 if is_64bits else dll_path_32

if not os.path.isfile(dll_path):
    raise FileNotFoundError(f"Could not find module '{dll_path}'")

vjoy = ctypes.WinDLL(dll_path)

VJD_STAT_OWN = 0
VJD_STAT_FREE = 1
VJD_STAT_BUSY = 2
VJD_STAT_MISS = 3
VJD_STAT_UNKN = 4

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

available_devices = [1, 2]
device_lock = threading.Lock()
device_states = {}

command_map = {
    'D': 'left_top',
    'E': 'left_bottom',
    'F': 'right_top',
    'G': 'right_bottom',
    'VOLUME_UP': 'volume_up',
    'VOLUME_DOWN': 'volume_down',
    'A': 'steering',
    'B': 'accelerate',
    'C': 'brake'
}

def acquire_vjd(device_id):
    attempts = 5
    while attempts > 0:
        status = vjoy.GetVJDStatus(device_id)
        if status == VJD_STAT_FREE or status == VJD_STAT_OWN:
            if vjoy.AcquireVJD(device_id):
                logging.info(f"Device {device_id} acquired successfully.")
                set_axis(device_id, 0x31, 16384)
                return True
            else:
                logging.error(f"Failed to acquire device {device_id}.")
                return False
        elif status == VJD_STAT_BUSY or status == VJD_STAT_MISS:
            logging.warning(f"Device {device_id} is in an unexpected state (Status: {status}). Forcing release...")
            relinquish_vjd(device_id)
            time.sleep(0.5)
            attempts -= 1
        else:
            logging.warning(f"Device {device_id} is not free. Status: {status}. Retrying in 0.5s...")
            time.sleep(0.5)
            attempts -= 1
    logging.error(f"Failed to acquire device {device_id} after multiple attempts.")
    return False

def relinquish_vjd(device_id):
    try:
        for _ in range(3):
            if vjoy.RelinquishVJD(device_id):
                logging.info(f"Device {device_id} relinquished successfully.")
                time.sleep(0.1)
                break
            else:
                logging.warning(f"Attempt to relinquish device {device_id} failed. Retrying...")
                time.sleep(0.1)
    except Exception as e:
        logging.error(f"Failed to relinquish device {device_id}: {e}")

def set_button(device_id, button_id, state):
    if vjoy.SetBtn(state, device_id, button_id):
        logging.debug(f"Button {button_id} on device {device_id} set to {'pressed' if state else 'released'}.")
    else:
        logging.error(f"Failed to set button {button_id} on device {device_id}.")

def set_axis(device_id, axis_id, value):
    if vjoy.SetAxis(value, device_id, axis_id):
        logging.debug(f"Axis {axis_id} on device {device_id} set to {value}.")
    else:
        logging.error(f"Failed to set axis {axis_id} on device {device_id}.")

def map_value(value, in_min, in_max, out_min, out_max):
    # Asegura que el valor esté dentro del rango de entrada
    value = max(min(value, in_max), in_min)

    # Realiza el mapeo de valor
    return int((value - in_min) * (out_max - out_min) / (in_max - in_min) + out_min)

def process_critical_message(device_id, message, update_ui_callback=None):
    logging.info(f"Received critical message: {message}")

    if not message:
        logging.error("Error: Empty message received")
        return

    command = message.strip()
    logging.info(f"Processing command: {command}")

    if command in command_map:
        # Obtén el nombre del botón desde el mapa
        button_name = command_map[command].lower()
        logging.info(f"Mapped command: {command} to button {button_name}")

        # Si es necesario, maneja la conversión para vJoy
        button_id = {
            'D': 1,  # Left Top
            'E': 2,  # Left Bottom
            'F': 3,  # Right Top
            'G': 4,  # Right Bottom
            'VOLUME_UP': 5,  # Volume Up
            'VOLUME_DOWN': 6  # Volume Down
        }.get(command)

        if button_id is not None:
            logging.info(f"Button {button_id} will be pressed on device {device_id}")
            set_button(device_id, button_id, True)
            threading.Timer(0.05, set_button, args=(device_id, button_id, False)).start()
        
        # Actualiza la interfaz gráfica
        if update_ui_callback:
            logging.info(f"Calling UI callback for button: {button_name}")
            root = tk._default_root  # Asegúrate de que este sea el root de Tkinter
            root.after(0, update_ui_callback, button_name, True)
        else:
            logging.error("update_ui_callback is None!")
    else:
        logging.error(f"Error: Command {command} did not map to a valid button ID.")



def process_non_critical_message(device_id, message, update_ui_callback=None):
    state = device_states[device_id]
    logging.debug(f"Processing non-critical message: {message}")

    if not message:
        logging.error("Error: Empty message received")
        return

    parts = message.split(":")
    if len(parts) != 2:
        logging.error(f"Error: Incorrect message format. Parts: {parts}")
        return

    command, value = parts
    if command not in command_map:
        logging.error(f"Error: Unknown command identifier: {command}")
        return

    try:
        if command == 'A' and value is not None:  # Steering
            y = float(value)
            steering_value = map_value(y, -10.0, 10.0, 1, 32767)
            state['last_steering_value'] = steering_value
            set_axis(device_id, 0x30, int(steering_value))
            if update_ui_callback:
                update_ui_callback('steering', int(steering_value))
            logging.info(f"Steering value set: {int(steering_value)}")
        
        elif command in ['B', 'C'] and value.isdigit():  # Accelerate or Brake
            int_value = int(value)
            axis_value = map_value(int_value, 0, 100, 16384, 32767)
            if command == 'B':  # Acelerar
                set_axis(device_id, 0x31, axis_value)
                if update_ui_callback:
                    update_ui_callback('accelerate', int_value)
            elif command == 'C':  # Frenar
                set_axis(device_id, 0x32, axis_value)
                if update_ui_callback:
                    update_ui_callback('brake', int_value)
            logging.info(f"{command_map[command]} value mapped: {axis_value}")

    except ValueError as ve:
        logging.error(f"Error converting value: {ve}")


def handle_client(conn, addr, update_ui_callback=None):
    logging.info(f"handle_client called with update_ui_callback: {update_ui_callback is not None}")
    acquired_device_id = None

    if acquire_vjd(1):
        acquired_device_id = 1
    elif acquire_vjd(2):
        acquired_device_id = 2
    else:
        logging.error(f"No available devices for {addr}.")
        conn.close()
        return

    if acquired_device_id is not None:
        device_states[acquired_device_id] = {
            'critical_message_queue': deque(),
            'non_critical_message_queue': deque(),
            'last_steering_value': 16384,
            'threads': []
        }

        critical_thread = threading.Thread(target=handle_critical_messages, args=(acquired_device_id, update_ui_callback))
        non_critical_thread = threading.Thread(target=handle_non_critical_messages, args=(acquired_device_id, update_ui_callback))


        
        critical_thread.start()
        non_critical_thread.start()

        device_states[acquired_device_id]['threads'].extend([critical_thread, non_critical_thread])
    else:
        logging.error("No device_id available, cannot create threads.")
        conn.close()
        return

    try:
        with conn:
            buffer = ""
            while True:
                data = conn.recv(4096).decode('utf-8')
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
                            device_states[acquired_device_id]['critical_message_queue'].append(message)
                        else:
                            device_states[acquired_device_id]['non_critical_message_queue'].append(message)

    except Exception as e:
        logging.error(f"Error processing data: {e}")
    
    finally:
        logging.info(f"Connection with {addr} closed")
        try:
            for thread in device_states[acquired_device_id]['threads']:
                if thread.is_alive():
                    logging.info(f"Waiting for thread {thread.name} to finish...")
                    thread.join(timeout=1)

            relinquish_vjd(acquired_device_id)
        except Exception as e:
            logging.error(f"Error during cleanup: {e}")
        
        finally:
            with device_lock:
                if acquired_device_id in device_states:
                    del device_states[acquired_device_id]
                if acquired_device_id not in available_devices:
                    available_devices.append(acquired_device_id)
            
            try:
                conn.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            finally:
                conn.close()


def handle_critical_messages(device_id, update_ui_callback=None):
    logging.info(f"handle_critical_messages received update_ui_callback: {update_ui_callback is not None}")
    thread_name = threading.current_thread().name
    while not shutdown_event.is_set():
        if device_id not in device_states:
            logging.info(f"Exiting {thread_name} because the device state no longer exists.")
            break
        if device_states[device_id]['critical_message_queue']:
            msg = device_states[device_id]['critical_message_queue'].popleft()
            process_critical_message(device_id, msg, update_ui_callback)  # Asegúrate de pasar el callback
        time.sleep(0.01)


def handle_non_critical_messages(device_id, update_ui_callback=None):
    thread_name = threading.current_thread().name
    while not shutdown_event.is_set():
        if device_id not in device_states:
            logging.info(f"Exiting {thread_name} because the device state no longer exists.")
            break
        processed_count = 0
        while device_states[device_id]['non_critical_message_queue'] and processed_count < 10:
            msg = device_states[device_id]['non_critical_message_queue'].popleft()
            process_non_critical_message(device_id, msg, update_ui_callback)
            processed_count += 1

def get_local_ip_for_client(client_ip):
    client_ip_prefix = '.'.join(client_ip.split('.')[:3]) + '.'
    for interface in netifaces.interfaces():
        addrs = netifaces.ifaddresses(interface)
        if netifaces.AF_INET in addrs:
            for addr in addrs[netifaces.AF_INET]:
                ip = addr['addr']
                if ip.startswith(client_ip_prefix):
                    return ip
    return None

shutdown_event = threading.Event()

def signal_handler(sig, frame):
    logging.info("Interrupt received, shutting down...")
    shutdown_event.set()

signal.signal(signal.SIGINT, signal_handler)

def start_server(update_ui_callback=None):
    logging.info(f"start_server called with update_ui_callback: {update_ui_callback is not None}")

    # El resto del código sigue igual

    TCP_IP = "0.0.0.0"
    TCP_PORT = 12345
    UDP_PORT = 12345

    sock_tcp = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock_tcp.bind((TCP_IP, TCP_PORT))
    sock_tcp.listen(5)
    logging.info(f"Listening for TCP connections on {TCP_IP}:{TCP_PORT}")

    sock_udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock_udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock_udp.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock_udp.bind((TCP_IP, UDP_PORT))
    logging.info(f"Listening for UDP broadcasts on {TCP_IP}:{UDP_PORT}")

    discovery_thread = threading.Thread(target=handle_discovery, args=(sock_udp,))
    discovery_thread.daemon = True
    discovery_thread.start()

    try:
        while not shutdown_event.is_set():
            sock_tcp.settimeout(1.0)
            try:
                conn, addr = sock_tcp.accept()
                client_thread = threading.Thread(target=handle_client, args=(conn, addr, update_ui_callback))
                client_thread.start()
            except socket.timeout:
                continue
    except KeyboardInterrupt:
        logging.info("Server is shutting down...")
    finally:
        shutdown_event.set()
        sock_tcp.close()
        sock_udp.close()
        logging.info("Sockets closed. Server shut down.")

def handle_discovery(sock_udp):
    while not shutdown_event.is_set():
        try:
            sock_udp.settimeout(1.0)
            message, address = sock_udp.recvfrom(1024)
            if message.decode() == "DISCOVER_SERVER":
                server_ip = get_local_ip_for_client(address[0])
                if server_ip is None:
                    logging.error("No valid local IP found.")
                    continue
                response_message = f"{server_ip}:12345"
                sock_udp.sendto(response_message.encode(), address)
                logging.info(f"Responded to discovery from {address} with IP {server_ip}")
        except socket.timeout:
            continue
        except OSError:
            break
