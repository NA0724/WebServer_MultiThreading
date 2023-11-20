# MainWebServer.java

A Java program that implements a basic web server can serve static web content. It listens for incoming HTTP requests, processes them, and responds with the appropriate web page or an error page if necessary. 
This server supports HTTP 1.10 and HTTP 1.1 and handles common status codes like 200 (OK), 400 (Bad Request), 403 (Forbidden), 404 (Not Found) and 501 (Method Not Supported). 
This program follows multi-threading approach, creates a new thread for each client connection. It supports:
1.	Forever loop for listening for connections, accept new connection from incoming client.
2.	Parse HTTP request
3.	Ensure well-formed request (return error otherwise)
4.	Determine if target file exists and if permissions are set properly 
5.	Returns errors if exception occurs
6.	Transmit contents of file to connect (by performing reads on the file and writes
on the socket)
7.	Close the connection

### Overall Flow

- The main server accepts incoming connections and creates a new ClientHandler thread for each connection.
- The ClientHandler thread handles the client's request, serving files or responding with appropriate error messages.
- The server uses a simple timeout strategy based on the number of active connections to manage the connection timeout duration.

### To Run the Program

*Go to the directory of the file

*Enter the following command:
	`cd src/main/java/com/webserver`

*Compile the java class
	`javac MainWebServer.java`

*Run the class, specifying the arguments:
	`java MainWebServer.java -document_root ../../../../../root -port 9000` 
