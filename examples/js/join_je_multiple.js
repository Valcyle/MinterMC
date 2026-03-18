/**
 * MinterMC JavaScript Example: Multiple Bots
 * 
 * This script demonstrates how to connect multiple bots to the MinterMC kernel
 * concurrently using Node.js.
 */

const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');

const PROTO_PATH = path.join(__dirname, '../../mintermc-protocol/src/main/proto/bot_service.proto');
const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
    keepCase: true, longs: String, enums: String, defaults: true, oneofs: true
});
const botProto = grpc.loadPackageDefinition(packageDefinition);

const client = new botProto.MinterBotService(
    'localhost:50051',
    grpc.credentials.createInsecure()
);

// Names of the bots to join
const BOT_NAME = 'JS_Bot';

/**
 * Promisified login helper
 */
function loginBot(name) {
    return new Promise((resolve, reject) => {
        const request = {
            bot_name: name,
            target_edition: 'JAVA_EDITION',
            host: 'localhost',
            port: 25565
        };

        client.Login(request, (error, response) => {
            if (error) return reject(error);
            if (!response.success) return reject(new Error(response.error_message));
            resolve(response.session_id);
        });
    });
}

async function main() {
    const botCount = 500;
    console.log(`--- MinterMC JS Client: Connecting ${botCount} bots ---`);

    try {
        // Connect all bots in parallel
        const activeSessions = [];


        for (let i = 0; i < botCount; i++) {
            const name = `${BOT_NAME}_${i + 1}`;
            try {
                const sessionId = await loginBot(name);
                console.log(`[+] ${name} joined! Session: ${sessionId.substring(0, 8)}...`);
                activeSessions.push(sessionId);
            } catch (err) {
                console.error(`[-] ${name} failed: ${err.message}`);
            }
            await new Promise(resolve => setTimeout(resolve, 20));
        }

        console.log(`\nSuccessfully connected ${activeSessions.length} bots.`);

        // Example: Check status of all bots after 2 seconds
        setTimeout(() => {
            console.log('\n--- Checking status of all active bots ---');
            activeSessions.forEach((sid, i) => {
                client.GetStatus({ session_id: sid }, (err, status) => {
                    if (err) return;
                    console.log(`Bot ${i + 1}: Pos(${status.x.toFixed(1)}, ${status.y.toFixed(1)}, ${status.z.toFixed(1)})`);
                });
            });
        }, 2000);

    } catch (err) {
        console.error('Fatal error:', err);
    }
}

main();
