# MinterMC

[English](README.md) | 日本語

[![Discord](https://img.shields.io/discord/872775092218761226?logo=discord&color=7389D8)](https://discord.gg/ckQGXN46pu)
[![Created at](https://img.shields.io/github/created-at/Valcyle/MinterMC)](https://github.com/Valcyle/MinterMC)
[![Last commit](https://img.shields.io/github/last-commit/Valcyle/MinterMC
)](https://github.com/Valcyle/MinterMC/commits/main/)
===========
任意のプログラミング言語で Minecraft ボットを作成できます。

> [!NOTE]
> 本プロジェクトは初期開発段階です。コントリビュートする前に [CONTRIBUTING.md](CONTRIBUTING.md) を読んでください。

**Java Edition** と **Bedrock Edition** の違いを抽象化する、汎用 Minecraft ボットカーネルです。  
単一の **gRPC API** を公開するため、任意の言語から SDK 経由でボットを操作できます。

> [!IMPORTANT]
> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**  
> MinterMC は独立したプロジェクトであり、Minecraft のゲームコードやゲームアセットを含みません。

# DEMO
[<img src="https://img.youtube.com/vi/8_sQTFVja0g/0.jpg" alt="1500 bots joining using MinterMC" width="200">](https://www.youtube.com/watch?v=8_sQTFVja0g)

現在は Java Edition と Bedrock Edition のオフラインサーバーへの接続に対応しています。
Bedrock 対応は現在も開発中です。
Python、C++、JavaScript、Java のサンプルがあります。
その他の例は [`examples`](examples/) フォルダを参照してください。

# Features

- **Protocol Abstraction** — Java Edition と Bedrock Edition のボットを単一の API で操作
- **Chat and Events** — 双方向 `EventStream` RPC でチャット送信とサーバーイベントの受信が可能
- **Block Access** — 読み込み済みワールドのブロック名と通過可否を取得
- **Movement** — サーバー権威の移動で指定座標まで歩行
- **Language Agnostic** — gRPC クライアントがあれば任意の言語から操作可能（Python、TypeScript、Go など）
- **Agent-per-Bot Model** — 各ボットは Java 25 の仮想スレッド上で動作し、完全に分離
- **Plugin System** — `plugins/` に JAR を置くだけでボットの動作を拡張（経路探索、戦闘など）
- **Scalable Design** — 数百体の同時接続を想定した設計

# Requirement

- Java 25+

Bedrock で複数のボットを接続する場合は、サーバー側で
`server-udp-ports=19132-19232` のような UDP ポート範囲を設定してください。
BDS 1.26.x の単一ポート共有モードでは、同時に接続した NetherNet 接続の一部が
切断されます。詳細は [docs/testing.md](docs/testing.md) を参照してください。

# Installation

```bash
git clone https://github.com/Valcyle/MinterMC.git
cd MinterMC
./gradlew build
```

# Usage

```bash
# gRPC サーバーを起動（既定ポート: 50051）
./gradlew :mintermc-server:run
```

任意の gRPC クライアントから、次のサービス定義を使って接続します。  
[`mintermc-protocol/src/main/proto/bot_service.proto`](mintermc-protocol/src/main/proto/bot_service.proto)

### Plugin Development

1. プロジェクトに `mintermc-plugin-api` を依存として追加
2. `MinterPlugin` インターフェースを実装
3. JAR をビルドして `plugins/` ディレクトリに配置

```java
public class MyPlugin implements MinterPlugin {
    @Override
    public void onEnable(PluginContext context) {
        context.registerCommand("ping", (bot, command, args) ->
                bot.chat("pong from " + bot.getUsername()));
    }

    @Override
    public void onDisable() { /* cleanup */ }
}
```

プラグインは `PluginContext` と `Bot` を通じてイベント、コマンド、スケジューリングを
利用できます。詳細は [`docs/plugins.md`](docs/plugins.md)、ビルド可能な例は
[`examples/plugins`](examples/plugins) を参照してください。

# Note

本プロジェクトは初期開発段階です。gRPC サーバーとプラグインシステムは開発中です。Java Edition と Bedrock Edition のアダプタは動作しています。Bedrock のブロック・アイテムレジストリは読み込み済みでブロック参照も可能です。ブロックの設置・破壊はまだ実装中です。

> [!NOTE] 
> MinterMC は Java Edition アダプタに [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) を使用しています。
> MinterMC は Bedrock Edition アダプタに [Protocol](https://github.com/CloudburstMC/Protocol) を使用しています。

# 利用上の注意

MinterMC は独立したプロジェクトであり、Minecraft のゲームコードやゲームアセットを
含みません。

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG
OR MICROSOFT.**

MinterMC は、自分が所有するサーバー、または自動化が許可されているサーバーでのみ
使用してください。オフラインモードのサーバーに独自の ID で接続するものであり、
Xbox Live 認証は行わず、オンラインモードのサーバーには接続できません。プレイヤー
のなりすまし、認証の回避、許可のないサーバーへのアクセスには使用しないでください。

ボットの利用が第三者のサーバーのルールに違反する場合があります。Bedrock
Dedicated Server は Mojang からダウンロードしてください。サーバーバイナリや
ワールドデータを再配布しないでください。

# Author

* [Valcyle](https://discord.gg/ckQGXN46pu)

# License

TBD

# Third-party notices

MinterMC はサードパーティ製のコンポーネントを利用・同梱しています。各ライセンスと
 attribution の要件は [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) に記載しています。
