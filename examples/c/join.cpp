/**
 * MinterMC C++ Example Client
 * 
 * This example uses the gRPC C++ API. 
 * While possible in pure C, C++ is the standard for gRPC in the C-family.
 */

#include <iostream>
#include <memory>
#include <string>

#include <grpcpp/grpcpp.h>
#include "bot_service.grpc.pb.h"

using grpc::Channel;
using grpc::ClientContext;
using grpc::Status;
using net::mcsmash::mintermc::protocol::MinterBotService;
using net::mcsmash::mintermc::protocol::LoginRequest;
using net::mcsmash::mintermc::protocol::LoginResponse;
using net::mcsmash::mintermc::protocol::EditionType;
using net::mcsmash::mintermc::protocol::StatusRequest;
using net::mcsmash::mintermc::protocol::StatusResponse;

class MinterClient {
public:
    MinterClient(std::shared_ptr<Channel> channel)
        : stub_(MinterBotService::NewStub(channel)) {}

    void Login(const std::string& bot_name) {
        LoginRequest request;
        request.set_bot_name(bot_name);
        request.set_target_edition(EditionType::JAVA_EDITION);
        request.set_host("localhost");
        request.set_port(25565);

        LoginResponse response;
        ClientContext context;

        Status status = stub_->Login(&context, request, &response);

        if (status.ok()) {
            if (response.success()) {
                std::cout << "Login SUCCESS! Session ID: " << response.session_id() << std::endl;
                GetStatus(response.session_id());
            } else {
                std::cout << "Login Failed: " << response.error_message() << std::endl;
            }
        } else {
            std::cout << "gRPC Error: " << status.error_message() << std::endl;
        }
    }

    void GetStatus(const std::string& session_id) {
        StatusRequest request;
        request.set_session_id(session_id);

        StatusResponse response;
        ClientContext context;

        Status status = stub_->GetStatus(&context, request, &status_response);

        if (status.ok()) {
            std::cout << "Current Position: (" << response.x() << ", " << response.y() << ", " << response.z() << ")" << std::endl;
            std::cout << "Health: " << response.health() << std::endl;
        }
    }

private:
    std::unique_ptr<MinterBotService::Stub> stub_;
};

int main(int argc, char** argv) {
    std::cout << "--- MinterMC C++ Client: Login Test ---" << std::endl;
    
    MinterClient client(grpc::CreateChannel("localhost:50051", grpc::InsecureChannelCredentials()));
    client.Login("CPP_Bot");

    return 0;
}
