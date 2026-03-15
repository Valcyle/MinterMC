"""
MinterMC Python Example Client

This script demonstrates how to connect to the MinterMC gRPC server
using Python.

Note: You need to generate the gRPC stubs first using:
python -m grpc_tools.protoc -I../../mintermc-protocol/src/main/proto --python_out=. --grpc_python_out=. ../../mintermc-protocol/src/main/proto/bot_service.proto
"""

import grpc
import sys
import os

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
    print("--- MinterMC Python Client: Login Test ---")

    # Create a channel to the server
    with grpc.insecure_channel('localhost:50051') as channel:
        # Create a stub (client)
        stub = bot_service_pb2_grpc.MinterBotServiceStub(channel)

        # Build a login request
        # EditionType is an enum, 0 = JAVA_EDITION
        request = bot_service_pb2.LoginRequest(
            bot_name='Python_Bot',
            target_edition=0, 
            host='localhost',
            port=25565
        )

        try:
            # Call the Login service
            response = stub.Login(request)

            if response.success:
                print("Login SUCCESS!")
                print(f"Session ID: {response.session_id}")

                # Call GetStatus
                status_request = bot_service_pb2.StatusRequest(session_id=response.session_id)
                status = stub.GetStatus(status_request)
                print(f"Current Position: ({status.x}, {status.y}, {status.z})")
                print(f"Health: {status.health}")
            else:
                print(f"Login Failed: {response.error_message}")

        except grpc.RpcError as e:
            print(f"gRPC Error: {e.code()} - {e.details()}")

if __name__ == '__main__':
    main()
