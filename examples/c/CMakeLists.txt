cmake_minimum_required(VERSION 3.10)
project(MinterMC_C_Example)

set(CMAKE_CXX_STANDARD 17)

# Find gRPC and Protobuf
find_package(gRPC CONFIG REQUIRED)
find_package(Protobuf CONFIG REQUIRED)

# Proto file path
set(PROTO_SRC "../../mintermc-protocol/src/main/proto/bot_service.proto")

# Generate C++ code from proto
get_filename_component(proto_path "${PROTO_SRC}" ABSOLUTE)
get_filename_component(proto_dir "${proto_path}" DIRECTORY)

set(proto_out_dir "${CMAKE_CURRENT_BINARY_DIR}")
file(MAKE_DIRECTORY "${proto_out_dir}")

set(generated_srcs "${proto_out_dir}/bot_service.pb.cc" "${proto_out_dir}/bot_service.grpc.pb.cc")
set(generated_hdrs "${proto_out_dir}/bot_service.pb.h" "${proto_out_dir}/bot_service.grpc.pb.h")

add_custom_command(
    OUTPUT ${generated_srcs} ${generated_hdrs}
    COMMAND ${Protobuf_PROTOC_EXECUTABLE}
    ARGS --cpp_out "${proto_out_dir}" 
         --grpc_out "${proto_out_dir}"
         --plugin=protoc-gen-grpc=${gRPC_CPP_PLUGIN_EXECUTABLE}
         -I "${proto_dir}"
         "${proto_path}"
    DEPENDS "${proto_path}"
)

add_executable(join_test join.cpp ${generated_srcs})
target_include_directories(join_test PRIVATE "${proto_out_dir}")
target_link_libraries(join_test gRPC::grpc++ protobuf::libprotobuf)
