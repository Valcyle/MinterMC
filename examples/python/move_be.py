"""
MinterMC Python Example: Bedrock Edition Movement

Logs a Bedrock Edition bot into an offline-mode server and walks it to a
position. Movement uses server-authoritative `PlayerAuthInput` reports, so the
bot travels at walking speed and the server validates every step.

The target server and destination can be changed with environment variables:

    MINTER_TARGET_HOST (default: 127.0.0.1)
    MINTER_TARGET_PORT (default: 19132)
    MINTER_BOT_NAME    (default: Python_BE_Move)
    MINTER_MOVE_X      (default: current x + 4)
    MINTER_MOVE_Y      (default: current y)
    MINTER_MOVE_Z      (default: current z)

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
    name = os.environ.get("MINTER_BOT_NAME", "Python_BE_Move")
    grpc_port = os.environ.get("MINTER_GRPC_PORT", "50051")

    print(f"--- MinterMC Python Client: Bedrock movement on {host}:{port} as {name} ---")

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

        # Login returns before the Bedrock handshake finishes.
        time.sleep(5)

        before = stub.GetStatus(bot_service_pb2.StatusRequest(session_id=response.session_id))
        print(f"before: x={before.x:.2f} y={before.y:.2f} z={before.z:.2f}")

        target_x = float(os.environ.get("MINTER_MOVE_X", before.x + 4))
        target_y = float(os.environ.get("MINTER_MOVE_Y", before.y))
        target_z = float(os.environ.get("MINTER_MOVE_Z", before.z))

        started = time.time()
        try:
            moved = stub.MoveTo(bot_service_pb2.MoveRequest(
                session_id=response.session_id,
                target_x=target_x,
                target_y=target_y,
                target_z=target_z))
        except grpc.RpcError as e:
            print(f"MoveTo error: {e.code()} - {e.details()}")
            return

        elapsed = time.time() - started
        after = stub.GetStatus(bot_service_pb2.StatusRequest(session_id=response.session_id))
        print(f"MoveTo reached={moved.reached} in {elapsed:.2f}s")
        print(f"after:  x={after.x:.2f} y={after.y:.2f} z={after.z:.2f}")


if __name__ == "__main__":
    main()
