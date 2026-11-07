"""
MinterMC Python Example Client: Java Edition login

The target server can be changed with environment variables:

    MINTER_TARGET_HOST (default: 127.0.0.1)
    MINTER_TARGET_PORT (default: 25565)
    MINTER_BOT_NAME    (default: Python_Bot)

The MinterMC gRPC server must be running on localhost:50051.

Note: You need to generate the gRPC stubs first using:
python -m grpc_tools.protoc -I../../mintermc-protocol/src/main/proto --python_out=. --grpc_python_out=. ../../mintermc-protocol/src/main/proto/bot_service.proto
"""

import os
import sys
import time

import grpc

# Ensure the generated stubs can be imported
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

try:
    import bot_service_pb2
    import bot_service_pb2_grpc
except ImportError:
    print("Error: gRPC stubs not found.")
    print("Please generate them using the command in the file header.")
    sys.exit(1)


def main():
    host = os.environ.get("MINTER_TARGET_HOST", "127.0.0.1")
    port = int(os.environ.get("MINTER_TARGET_PORT", "25565"))
    name = os.environ.get("MINTER_BOT_NAME", "Python_Bot")

    print(f"--- MinterMC Python Client: Java login to {host}:{port} as {name} ---")

    with grpc.insecure_channel("localhost:50051") as channel:
        stub = bot_service_pb2_grpc.MinterBotServiceStub(channel)

        # EditionType is an enum, 0 = JAVA_EDITION
        request = bot_service_pb2.LoginRequest(
            bot_name=name,
            target_edition=0,
            host=host,
            port=port,
        )

        try:
            response = stub.Login(request)
        except grpc.RpcError as e:
            print(f"gRPC Error: {e.code()} - {e.details()}")
            return

        if not response.success:
            print(f"Login Failed: {response.error_message}")
            return

        print(f"Login request accepted. Session ID: {response.session_id}")

        # Login returns as soon as the connection task starts. Poll the status
        # while the Java handshake continues in the background.
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
