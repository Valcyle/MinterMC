"""
MinterMC Python Example: Bedrock Edition Block Query

Logs a Bedrock Edition bot into an offline-mode server and prints the blocks
at the requested coordinates. Bedrock sends block data per 16x16x16 section,
so the first query of a section takes one round trip to the server.

The target server and coordinates can be changed with environment variables:

    MINTER_TARGET_HOST (default: 127.0.0.1)
    MINTER_TARGET_PORT (default: 19132)
    MINTER_BOT_NAME    (default: Python_BE_Blocks)
    MINTER_BLOCK_X     (default: 0)
    MINTER_BLOCK_Z     (default: 0)
    MINTER_BLOCK_YS    (default: -64,0,32,64,70,100)

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
    name = os.environ.get("MINTER_BOT_NAME", "Python_BE_Blocks")
    grpc_port = os.environ.get("MINTER_GRPC_PORT", "50051")
    block_x = int(os.environ.get("MINTER_BLOCK_X", "0"))
    block_z = int(os.environ.get("MINTER_BLOCK_Z", "0"))
    levels = [int(value) for value in
              os.environ.get("MINTER_BLOCK_YS", "-64,0,32,64,70,100").split(",")]

    print(f"--- MinterMC Python Client: Bedrock blocks on {host}:{port} as {name} ---")

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

        for y in levels:
            try:
                block = stub.GetBlock(bot_service_pb2.BlockRequest(
                    session_id=response.session_id, x=block_x, y=y, z=block_z))
            except grpc.RpcError as e:
                print(f"({block_x}, {y}, {block_z}) error: {e.code()} - {e.details()}")
                continue

            if not block.found:
                print(f"({block_x}, {y}, {block_z}) not loaded")
                continue
            print(f"({block_x}, {y}, {block_z}) {block.material} "
                  f"passable={block.passable}")


if __name__ == "__main__":
    main()
