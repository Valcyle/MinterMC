/**
 * MinterMC NetherNet sidecar.
 *
 * Opens a NetherNet/WebRTC connection to a Bedrock server using the Node
 * `nethernet` implementation, then exposes it as a local TCP byte stream.
 * The Java kernel speaks the Bedrock protocol itself; this process only
 * handles signaling, WebRTC and NetherNet message framing.
 *
 * Config file (path passed as argv[2]):
 * {
 *   "host": "192.168.0.25",
 *   "privateKeyDerBase64": "<PKCS#8 DER, base64>",
 *   "token": "<NetherNet identity JWT>",
 *   "domain": "MinterMC",
 *   "listenPort": 0
 * }
 *
 * Wire format toward Java: 4-byte big-endian length + Bedrock batch bytes.
 */

const crypto = require('node:crypto')
const fs = require('node:fs')
const net = require('node:net')
const { Client } = require('nethernet')

const config = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
const identity = {
  privateKey: crypto.createPrivateKey({
    key: Buffer.from(config.privateKeyDerBase64, 'base64'),
    format: 'der',
    type: 'pkcs8'
  }),
  token: config.token,
  domain: config.domain || 'MinterMC'
}

let tcpSocket = null
let netherClient = null
let connected = false
const pending = []

function frame(payload) {
  const header = Buffer.allocUnsafe(4)
  header.writeUInt32BE(payload.length)
  return Buffer.concat([header, payload])
}

const server = net.createServer((socket) => {
  tcpSocket = socket
  let buffered = Buffer.alloc(0)

  socket.on('data', (chunk) => {
    buffered = Buffer.concat([buffered, chunk])
    while (buffered.length >= 4) {
      const length = buffered.readUInt32BE(0)
      if (buffered.length < 4 + length) break
      const payload = buffered.subarray(4, 4 + length)
      buffered = buffered.subarray(4 + length)
      if (connected && netherClient) {
        netherClient.send(Buffer.from(payload))
      } else {
        pending.push(Buffer.from(payload))
      }
    }
  })

  socket.on('close', () => {
    console.log('JAVA_DISCONNECTED')
    shutdown()
  })
  socket.on('error', (error) => {
    console.error('TCP_ERROR', error.message)
    shutdown()
  })

  console.log('JAVA_CONNECTED')
})

server.listen(config.listenPort || 0, '127.0.0.1', () => {
  console.log('LISTENING ' + server.address().port)
})

function shutdown() {
  try { netherClient?.close() } catch (_) { /* ignore */ }
  try { server.close() } catch (_) { /* ignore */ }
  setTimeout(() => process.exit(0), 100)
}

process.on('SIGTERM', shutdown)
process.on('SIGINT', shutdown)

// The discovery client finds the server's NetworkID over UDP 7551. The actual
// connection client then uses that ID; discovery itself carries no game data.
const discovery = new Client(0n, config.host)
discovery.on('error', (error) => {
  console.error('DISCOVERY_ERROR', error.message)
  process.exit(1)
})
discovery.once('pong', (pong) => {
  discovery.close()
  console.log('SERVER_NETWORK_ID ' + pong.sender_id.toString())

  netherClient = new Client(pong.sender_id, config.host, { identity })
  netherClient.on('error', (error) => console.error('NETHERNET_ERROR', error.message))
  netherClient.on('connected', () => {
    connected = true
    console.log('NETHERNET_CONNECTED')
    for (const payload of pending.splice(0)) {
      netherClient.send(payload)
    }
  })
  netherClient.on('disconnect', (address, reason) => {
    connected = false
    console.log('NETHERNET_DISCONNECTED ' + reason)
    shutdown()
  })
  netherClient.on('encapsulated', (payload) => {
    if (tcpSocket) {
      tcpSocket.write(frame(Buffer.from(payload)))
    }
  })
  netherClient.connect()
})

setTimeout(() => {
  if (!connected) {
    console.error('CONNECT_TIMEOUT')
    process.exit(1)
  }
}, 20000)
