# CTU-Core

CTU-Core is a Netty-based Java client-server application that facilitates communication between clients and servers with advanced encryption methods such as SSL, TLS, compression, and other features.

## Installation

To get started with CTU-Core, please follow these steps:

1. Clone the repository onto your local machine.<br/>
2. Open the project in your preferred Java IDE.<br/>
3. Use your IDE's build tools to build the project.<br/>

If you prefer, you may use Maven in your project by including the following [packages](https://github.com/FentusGames/CTU-Core/packages/).

## Usage

Check [Example Server](https://github.com/FentusGames/CTU-Core/blob/master/src/main/java/examples/ServerLauncher.java) / [Example Client](https://github.com/FentusGames/CTU-Core/blob/master/src/main/java/examples/ClientLauncher.java) for details on how to configure the client and/or server.

## SSL & TLS (server.key & server.crt)

1. Install [OpenSSL](https://www.openssl.org/source/) on your machine if you don't already have it. You can download OpenSSL from the official website or use your system's package manager.
2. Open a command prompt or terminal window.
3. Navigate to the directory where you want to generate the certificate and key files.
4. Generate a private key using OpenSSL: This command generates a 2048-bit RSA private key and saves it to a file named server.key.
```groovy
openssl genrsa -out server.key 2048
```

1. Create a self-signed SSL/TLS certificate using OpenSSL: This command generates a self-signed X.509 certificate and saves it to a file named server.crt. The certificate is valid for 365 days from the current date.
```groovy
openssl req -new -x509 -key server.key -out server.crt -days 365
```

1. You can now place both the server.key and server.crt files in the server's running directory. Additionally, you should place the server.crt file in the client's running directory.
