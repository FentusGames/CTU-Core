# CTU-Core

CTU-Core is a Netty-based Java client-server application that facilitates communication between clients and servers with advanced encryption methods such as SSL, TLS, compression, and other features.

## Installation

To get started with CTU-Core, please follow these steps:

1. Clone the repository onto your local machine.<br/>
2. Open the project in your preferred Java IDE.<br/>
3. Use your IDE's build tools to build the project.<br/>

If you prefer, you may use Gradle in your project by including the following code:

## Client & Server
```groovy
repositories {
      maven { url 'https://jitpack.io' }
}

dependencies {
      implementation 'com.github.FentusGames:CTU-Core:2.0.0'
}
```

## Usage

Check [Example Server](https://github.com/FentusGames/CTU-Core/blob/master/lib/src/main/java/examples/ServerLauncher.java) / [Example Client](https://github.com/FentusGames/CTU-Core/blob/master/lib/src/main/java/examples/ClientLauncher.java) for details on how to configure the client and/or server.
