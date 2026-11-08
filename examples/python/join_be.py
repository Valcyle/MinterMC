"""
MinterMC Python Example: Bedrock Edition Login

Logs a Bedrock Edition bot into an offline-mode server and prints its status.
The target server can be changed with environment variables:

    MINTER_TARGET_HOST (default: 127.0.0.1)
    MINTER_TARGET_PORT (default: 19132)
    MINTER_BOT_NAME    (default: Python_BE_Bot)

The MinterMC gRPC server must be running on localhost:50051.
"""

import os
import sys
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
    name = os.environ.get("MINTER_BOT_NAME", "Python_BE_Bot")
    grpc_port = os.environ.get("MINTER_GRPC_PORT", "50051")

    print(f"--- MinterMC Python Client: Bedrock login to {host}:{port} as {name} ---")

    with grpc.insecure_channel(f"localhost:{grpc_port}") as channel:
        stub = bot_service_pb2_grpc.MinterBotServiceStub(channel)

        request = bot_service_pb2.LoginRequest(
            bot_name=name,
            target_edition=1,  # BEDROCK_EDITION
            host=host,
            port=port,
        )

        try:
            response = stub.Login(request)
        except grpc.RpcError as e:
            print(f"gRPC Error: {e.code()} - {e.details()}")
            return

        if not response.success:
            print(f"Login FAILED: {response.error_message}")
            return

        print(f"Login request accepted. Session ID: {response.session_id}")

        # Login returns as soon as the connection task starts. Poll the status
        # while the Bedrock handshake continues in the background.
        for attempt in range(10):
            time.sleep(1)
            try:
                status = stub.GetStatus(
                    bot_service_pb2.StatusRequest(session_id=response.session_id))
                print(f"[{attempt + 1}s] position=({status.x:.2f}, {status.y:.2f}, "
                      f"{status.z:.2f}) health={status.health}")
            except grpc.RpcError as e:
                print(f"[{attempt + 1}s] status error: {e.code()}")
                break


if __name__ == "__main__":
    main()
