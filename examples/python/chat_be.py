"""
MinterMC Python Example: Bedrock Edition Chat

Logs a Bedrock Edition bot into an offline-mode server, subscribes to the
event stream, sends one chat message and prints the events the server sends
back until the subscription window ends.

The target server can be changed with environment variables:

    MINTER_TARGET_HOST (default: 127.0.0.1)
    MINTER_TARGET_PORT (default: 19132)
    MINTER_BOT_NAME    (default: Python_BE_Chat)
    MINTER_CHAT        (default: hello from mintermc)
    MINTER_LISTEN_SECONDS (default: 10)

The MinterMC gRPC server must be running on localhost:50051.
"""

import os
import sys
import threading
import time

import grpc

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

try:
    import bot_service_pb2
    import bot_service_pb2_grpc
except ImportError:
    print("Error: gRPC stubs not found.")
    print("Generate them with:")
    print("  python -m grpc_tools.protoc -I../../mintermc-protocol/src/main/proto "
          "--python_out=. --grpc_python_out=. ../../mintermc-protocol/src/main/proto/bot_service.proto")
    sys.exit(1)


def main():
    host = os.environ.get("MINTER_TARGET_HOST", "127.0.0.1")
    port = int(os.environ.get("MINTER_TARGET_PORT", "19132"))
    name = os.environ.get("MINTER_BOT_NAME", "Python_BE_Chat")
    message = os.environ.get("MINTER_CHAT", "hello from mintermc")
    listen_seconds = int(os.environ.get("MINTER_LISTEN_SECONDS", "10"))
    grpc_port = os.environ.get("MINTER_GRPC_PORT", "50051")

    print(f"--- MinterMC Python Client: Bedrock chat on {host}:{port} as {name} ---")

    with grpc.insecure_channel(f"localhost:{grpc_port}") as channel:
        stub = bot_service_pb2_grpc.MinterBotServiceStub(channel)

        try:
            response = stub.Login(bot_service_pb2.LoginRequest(
                bot_name=name,
                target_edition=1,  # BEDROCK_EDITION
                host=host,
                port=port,
            ))
        except grpc.RpcError as e:
            print(f"gRPC Error: {e.code()} - {e.details()}")
            return

        if not response.success:
            print(f"Login FAILED: {response.error_message}")
            return

        print(f"Login request accepted. Session ID: {response.session_id}")
        session_id = response.session_id

        # Login returns before the Bedrock handshake finishes, so give the bot a
        # moment to spawn before the first chat command is sent.
        time.sleep(4)

        stop = threading.Event()

        def commands():
            yield bot_service_pb2.ClientCommand(
                session_id=session_id, chat_message=message)
            stop.wait(listen_seconds + 10)

        def listen():
            try:
                for event in stub.EventStream(commands()):
                    if event.HasField("chat_received"):
                        chat = event.chat_received
                        sender = chat.sender or "server"
                        print(f"[chat] {sender}: {chat.content}")
            except grpc.RpcError as e:
                if e.code() != grpc.StatusCode.CANCELLED:
                    print(f"Event stream error: {e.code()} - {e.details()}")

        listener = threading.Thread(target=listen, daemon=True)
        listener.start()

        deadline = time.time() + listen_seconds
        while time.time() < deadline:
            time.sleep(1)
            try:
                status = stub.GetStatus(bot_service_pb2.StatusRequest(session_id=session_id))
                print(f"position=({status.x:.2f}, {status.y:.2f}, {status.z:.2f}) "
                      f"health={status.health}")
            except grpc.RpcError as e:
                print(f"Status error: {e.code()}")
                break

        stop.set()


if __name__ == "__main__":
    main()
