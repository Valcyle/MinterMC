/**
 * MinterMC JavaScript Example Client
 * 
 * This script demonstrates how to connect to the MinterMC gRPC server
 * using Node.js to log in a bot.
 */

const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');

// Path to the shared .proto file
const PROTO_PATH = path.join(__dirname, '../../mintermc-protocol/src/main/proto/bot_service.proto');

// Load the protobuf definition
const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true
});

const botProto = grpc.loadPackageDefinition(packageDefinition);

// The service name is MinterBotService
const client = new botProto.MinterBotService(
    'localhost:50051',
    grpc.credentials.createInsecure()
);

function main() {
    console.log('--- MinterMC JS Client: Login Test ---');

    const loginRequest = {
        bot_name: 'JS_Bot',
        target_edition: 'JAVA_EDITION', // Protobuf enum string or index
        host: 'localhost',
        port: 25565
    };

    client.Login(loginRequest, (error, response) => {
        if (error) {
            console.error('Error during Login:', error.message);
            return;
        }

        if (response.success) {
            console.log('Login SUCCESS!');
            console.log('Session ID:', response.session_id);

            // After login, you can call other RPCs like GetStatus
            client.GetStatus({ session_id: response.session_id }, (err, status) => {
                if (err) {
                    console.error('Error getting status:', err.message);
                    return;
                }
                console.log('Current Position:', status.x, status.y, status.z);
                console.log('Health:', status.health);
            });
        } else {
            console.error('Login Failed:', response.error_message);
        }
    });
}

main();
