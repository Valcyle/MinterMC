# 接続テスト手順

MinterMC に実際のサーバーへ接続させる手順です。2026-11-07 時点で確認できた結果も併記します。

---

## 1. MinterMC サーバーを起動する

```powershell
cd C:\Users\yamad\Desktop\projects\MinterMC
.\gradlew :mintermc-server:run
```

`MinterMC gRPC Server started, listening on 50051` と出れば起動成功です。

### バージョン・transport の上書き

環境変数で切り替えられます（再ビルド不要）。

| 環境変数 | 既定値 | 用途 |
|---|---|---|
| `MINTER_JAVA_VERSION` | `1.21.4` | Java Edition のプロトコルバージョン |
| `MINTER_BEDROCK_VERSION` | `1.26.60` | Bedrock のプロトコルバージョン（2225） |
| `MINTER_BEDROCK_TRANSPORT` | `nethernet` | `nethernet` / `nethernet-lan` / `raknet` |

例:

```powershell
$env:MINTER_BEDROCK_TRANSPORT = "raknet"
.\gradlew :mintermc-server:run
```

---

## 2. サンプルクライアントを実行する

別のターミナルで。

### Bedrock Edition

```powershell
$env:MINTER_TARGET_HOST = "127.0.0.1"
$env:MINTER_TARGET_PORT = "19228"
$env:MINTER_BOT_NAME    = "MinterProbe"
.\.venv\Scripts\python.exe examples\python\join_be.py
```

### Java Edition

```powershell
$env:MINTER_TARGET_HOST = "127.0.0.1"
$env:MINTER_TARGET_PORT = "25565"
$env:MINTER_BOT_NAME    = "MinterProbe"
.\.venv\Scripts\python.exe examples\python\join_je.py
```

Python の依存関係が未導入なら:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install grpcio grpcio-tools
```

gRPC スタブは `examples/python/` に生成済みです。再生成する場合:

```powershell
cd examples\python
..\..\.venv\Scripts\python.exe -m grpc_tools.protoc `
  -I..\..\mintermc-protocol\src\main\proto `
  --python_out=. --grpc_python_out=. `
  ..\..\mintermc-protocol\src\main\proto\bot_service.proto
```

---

## 3. 成功の見え方

### MinterMC サーバーログ

Bedrock の場合は次の順に出ます。

```
Bedrock RakNet connection established to 127.0.0.1:19228, waiting for network settings
Sent Bedrock offline login packet for MinterProbe
Bedrock login accepted for MinterProbe
Bedrock bot MinterProbe joined the world at ...
Bedrock bot MinterProbe spawned
```

Java の場合は `Login SUCCESS!` と座標が返ります。

### Python クライアント

`position` が `(0.00, 0.00, 0.00)` のままなら未参加、ワールド座標（例: `(0.50, 65.00, 0.50)`）に変われば参加成功です。

---

## 4. サーバー別の確認結果（2026-11-07）

| サーバー | transport | 結果 |
|---|---|---|
| Nukkit-MOT 1.26.60（RakNet、`127.0.0.1:19228`） | `raknet` | **参加・spawn 成功** |
| 公式 BDS 1.26.61.1（NetherNet） | `nethernet` | 接続は成立、Login 後に `readNoHeader failed! packetId: 1` で切断 |
| 公式 BDS 1.26.61.1（RakNet 互換） | `raknet` | `PacketViolationWarningPacket` で切断 |
| Java Edition サーバー | - | 未検証（`join_je.py` で確認可能） |

### Nukkit-MOT で試す場合

`mcsmash-dev` の Nukkit は RakNet で待ち受けています。

```powershell
$env:MINTER_BEDROCK_TRANSPORT = "raknet"
.\gradlew :mintermc-server:run
```

```powershell
$env:MINTER_TARGET_HOST = "127.0.0.1"
$env:MINTER_TARGET_PORT = "19228"   # skyblock
$env:MINTER_BOT_NAME    = "MinterProbe"
.\.venv\Scripts\python.exe examples\python\join_be.py
```

`nukkit`（サバイバル）は `19226`、`skyblock` は `19228` です。`xbox-auth=off` のサーバーを使ってください。

### 公式 BDS で試す場合

公式 BDS は `online-mode=false` と `transport=nethernet` が必要です。テスト用 BDS を使う場合は、設定を変更したあと必ず元に戻してください。

```properties
online-mode=false
allow-list=false
server-port=20050
transport=nethernet
```

起動後、MinterMC 側は既定の `nethernet` で接続します。

> [!WARNING]
> 現在の公式 BDS は Login パケットのバッチフレーミング（`readNoHeader`）で拒否します。Nukkit では通るため、BDS 側の NetherNet/バッチ処理との互換性が残課題です。

---

## 5. 非致命的なデコードエラー

Nukkit 接続時、`CraftingDataPacket` / `ItemComponentPacket` / `CreativeContentPacket` のデコードで Cloudburst が例外を出すことがあります。これはアイテム定義レジストリが未設定なためで、**接続・spawn 自体は継続**します。ログに `joined the world` と `spawned` が出ていれば成功です。

---

## 6. 後片付け

- `gradlew :mintermc-server:run` は `Ctrl+C` で停止
- テスト用 BDS を起動した場合はプロセスを停止し、`server.properties` を元に戻す
- ユーザーのロビー BDS や Waterdog には触れない
