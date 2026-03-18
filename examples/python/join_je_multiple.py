"""
MinterMC Python Example: Multiple Bots

This script demonstrates how to connect multiple bots to the MinterMC kernel
sequentially using Python.
"""

import grpc
import time
import os
import sys

# Ensure the generated stubs can be imported (should be in the same folder as join.py)
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

try:
    import bot_service_pb2
    import bot_service_pb2_grpc
except ImportError:
    print("Error: gRPC stubs not found. Run the protoc command in join.py first.")
    sys.exit(1)

# List of bot names to join
BOT_NAME = "Python_Bot"
BOT_COUNT = 500

def main():
    print(f"--- MinterMC Python Client: Connecting {BOT_COUNT} bots ---")

    with grpc.insecure_channel('localhost:50051') as channel:
        stub = bot_service_pb2_grpc.MinterBotServiceStub(channel)
        active_sessions = []

        # Connect each bot
        for i in range(1, BOT_COUNT + 1):
            name = f"{BOT_NAME}_{i}"
            print(f"[*] Connecting {name}...")
            request = bot_service_pb2.LoginRequest(
                bot_name=name,
                target_edition=0, # JAVA_EDITION
                host='localhost',
                port=25565
            )

            try:
                response = stub.Login(request)
                if response.success:
                    print(f"[+] {name} SUCCESS! Session: {response.session_id[:8]}...")
                    active_sessions.append((name, response.session_id))
                else:
                    print(f"[-] {name} FAILED: {response.error_message}")
            except grpc.RpcError as e:
                print(f"[-] {name} ERROR: {e.code()}")
            time.sleep(0.02)

        print(f"\nTotal active bots: {len(active_sessions)}")

        # Wait a moment, then check status of all
        print("\n--- Verifying status of all bots ---")
        time.sleep(2)
        
        for name, sid in active_sessions:
            try:
                status = stub.GetStatus(bot_service_pb2.StatusRequest(session_id=sid))
                print(f"{name}: Pos({status.x:.1f}, {status.y:.1f}, {status.z:.1f}) | HP: {status.health}")
            except grpc.RpcError:
                print(f"{name}: Status check failed.")

if __name__ == '__main__':
    main()
