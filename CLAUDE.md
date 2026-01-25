# CLAUDE.md

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
      - "ctu.core.server: [NamedListener.java, Server.java, ServerConnectionHandler.java]"
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
  - "This is a semantic, AI-friendly representation of the CTU-Core repository."
  - "It intentionally omits Git internals, IDE metadata, timestamps, file sizes, and build noise."
  - "The structure communicates project identity (Maven coordinates), source layout, and package boundaries without exposing low-level filesystem detail."
  - "source_tree focuses on public and core Java packages, not every file, enabling fast architectural understanding."
  - "build_output summarizes what Maven produces, not how it is stored byte-for-byte."
  - "Use this format to give an AI enough context to reason about APIs, dependencies, and structure without overwhelming it."

You said:
No what the project does not what thius file does./
ChatGPT said:

Got it — you want the project’s purpose, not a meta explanation.
Here’s the same content, unchanged, with a proper “what this project does” note at the bottom.

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
      - "ctu.core.server: [NamedListener.java, Server.java, ServerConnectionHandler.java]"
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