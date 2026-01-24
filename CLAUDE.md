# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
mvn package

# Clean and build
mvn clean package

# Deploy to GitHub Packages (requires GITHUB_TOKEN)
mvn deploy
```

## Architecture Overview

CTU-Core is a Netty-based client-server networking library with mandatory TLS 1.3 encryption and built-in compression.

### Core Components

**Server/Client (`ctu.core.server.Server<T>`, `ctu.core.client.Client<T>`)**
- Generic type `T` represents a custom connection object for storing per-connection state
- Server uses a `Supplier<T>` to create new connection objects; Client takes a single instance
- Both require SSL certificates (`server.crt`, `server.key` for server; `server.crt` for client trust)

**Connection (`ctu.core.abstracts.Connection<T>`)**
- Extends Netty's `SimpleChannelInboundHandler<ByteBuf>`
- Handles packet serialization with compression (Deflate) and a 3-byte header (2-byte length + 1-byte packet ID)
- Packets must be registered with `register(Class<?>)` before use

**Packet (`ctu.core.abstracts.Packet`)**
- Abstract base requiring `marshal(byte[], int)` and `unmarshal(byte[], int)` implementations
- Packets are generated from Colfer schema files (`.colf`) using the Colfer compiler

**Listener (`ctu.core.interfaces.Listener<T>`)**
- Interface with callbacks: `channelActive`, `channelRead`, `channelInactive`, `channelExceptionCaught`
- Server listeners run on dedicated worker threads (one per listener)

### Packet Generation with Colfer

Packets are defined in `packets.colf` and compiled to Java:
```bash
colf -c "snip.txt" -p "ctu.core" -f -s "4096" -x "ctu/core/abstracts/Packet" java packets.colf
```
Generated packets extend `ctu.core.abstracts.Packet` and include efficient binary serialization.

### SSL Certificate Generation

Use `examples.keys.GenerateSSLCerts` or OpenSSL:
```bash
openssl genrsa -out server.key 2048
openssl req -new -x509 -key server.key -out server.crt -days 365
```

### Example Usage Pattern

See `examples/ServerLauncher.java` and `examples/ClientLauncher.java` for complete examples. Basic pattern:
1. Create `Server<T>` or `Client<T>` with port, timeout, and connection object
2. Register packet classes with `register(PacketClass.class)`
3. Add listeners to handle events
4. Call `start()`
