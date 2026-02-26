# CLAUDE.md

Always use str_replace for editing. Do not output the full file if only one function changes.

repo:
  path: "C:\\Users\\Fentus\\git\\CTU-Core"
  type: "git+maven+java"
  top_files:
    - "CLAUDE.md"
    - "pom.xml"
    - "README.md"
  folders:
    - "src/main/java"
    - "src/main/resources"
    - "src/test/java"
    - "src/test/resources"
    - "target/classes"
    - "target/test-classes"

source_tree:
  src/main/java:
    root_tools:
      - "colf.exe"
      - "compile.bat"
      - "packets.colf"
      - "packets.html"
      - "snip.txt"
    packages:
      - "ctu.core.abstracts: [Connection.java, Packet.java, User.java]"
      - "ctu.core.callbacks: [CallbackConnect.java]"
      - "ctu.core.client: [Client.java, ClientConnectionHandler.java]"
      - "ctu.core.interfaces: [Compression.java, Listener.java]"
      - "ctu.core.logger: [Log.java]"
      - "ctu.core.packets: [PacketPing.java]"
      - "ctu.core.security: [Security utilities]"
      - "ctu.core.server: [NamedListener.java, Server.java, ServerConnectionHandler.java]"
      - "ctu.core.server.bridge: [BridgeConnection.java, BridgeListener.java, ServerBridge.java]"
      - "ctu.core.server.config: [RemoteServerConfig.java, ServerArgs.java, ServerConfig.java, ServerType.java]"
      - "examples: [ClientLauncher.java, ServerLauncher.java]"
      - "examples.keys: [GenerateSSLCerts.java]"
      - "javax.annotation: [Generated.java]"

build_output:
  target/classes:
    contains_compiled:
      - "ctu.core.*"
      - "examples.*"
      - "javax.annotation.Generated"
    meta_inf_maven: "com.fentusgames:ctu-core (pom.properties, pom.xml)"

notes:
  - "CTU-Core is a lightweight Java networking framework providing a shared client/server communication layer."
  - "It defines core abstractions for connections, packets, users, and listeners that are used by both clients and servers."
  - "The project includes a TCP-based server and client implementation with pluggable connection handlers."
  - "Packets are modeled as typed Java objects (e.g., PacketPing) and can be extended to define custom protocols."
  - "The framework supports callbacks, listeners, compression hooks, and structured logging."
  - "Example launchers demonstrate how to start a server, connect a client, and generate SSL certificates for secure communication."
  - "CTU-Core is intended to be embedded as a dependency in higher-level applications (e.g., games or services) rather than used standalone."
