"""
MinterMC Python Example: Bedrock Edition Login with a Microsoft Account

Signs in with the Microsoft device code flow and joins a Bedrock server. On
the first run the MinterMC server log prints a verification URL and code; open
the URL, enter the code, and wait for this script to continue. Later runs reuse
the cached refresh token.

Environment variables:

    MINTER_TARGET_HOST (default: 127.0.0.1)
    MINTER_TARGET_PORT (default: 19132)
    MINTER_BOT_NAME    (default: Python_BE_Auth)
    MINTER_ACCOUNT     (default: main) local token cache name
    MINTER_GRPC_PORT   (default: 50051)

Use only an account you own, on a server where automation is permitted.
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
    print("Regenerate them with the command in docs/testing.md.")
    sys.exit(1)


def main():
    host = os.environ.get("MINTER_TARGET_HOST", "127.0.0.1")
    port = int(os.environ.get("MINTER_TARGET_PORT", "19132"))
    name = os.environ.get("MINTER_BOT_NAME", "Python_BE_Auth")
    account = os.environ.get("MINTER_ACCOUNT", "main")
    grpc_port = os.environ.get("MINTER_GRPC_PORT", "50051")

    print(f"--- Microsoft account login to {host}:{port} as {name} ---")
    print("Watch the MinterMC server console for the device code.")

    with grpc.insecure_channel(f"localhost:{grpc_port}") as channel:
        stub = bot_service_pb2_grpc.MinterBotServiceStub(channel)

        request = bot_service_pb2.LoginRequest(
            bot_name=name,
            target_edition=1,  # BEDROCK_EDITION
            host=host,
            port=port,
            auth_mode=bot_service_pb2.AUTH_MICROSOFT,
            account=account,
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

        for attempt in range(15):
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
