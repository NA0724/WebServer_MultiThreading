package com.webserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;


// Java implementation for a basic webserver connection using socket programming, follows multi-threadig approach
public class MainWebServer {

    // Maximum number of concurrent connections to the server
    private static final int MAX_CONCURRENT_CONNECTIONS = 10;
    // Initial timeout value when the server is idle
    private static final int IDLE_CONNECTION_TIMEOUT = 60000; // 1 minute
    // Timeout value when the server is busy
    private static final int BUSY_CONNECTION_TIMEOUT = 15000; // 15 seconds

    
    public static void main(String[] args) {
        int port = 8000; // default port
        String documentRoot = "root/"; // Directory where downloaded files are stored

        if (args.length < 2) {
            System.err.println("Usage: java MainWebServer -document_root <documentRoot> -port <port> ");
            return;
        }
        String readPort = new String();
		String readDocRoot = new String();
		for (int i = 0; i < args.length; i+=2) {
	        switch (args[i].charAt(0)) {
	        case '-':
	        	String substr = args[i].substring(1);
	            if (substr.equals("port"))
	            	readPort = args[i+1];
	            else if (substr.equals("document_root"))
	            	readDocRoot = args[i+1];
	            else 
	            	throw new IllegalArgumentException("Arguments should follow syntax -document_root <> -port <>");
	             
	            break;
	        default:
	        	throw new IllegalArgumentException("Argument list should start with -");
	        }
	    }

        port = Integer.parseInt(readPort);
        documentRoot = readDocRoot;
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("========================================================");
            System.out.println("Running Web Server on port " + port);

            while (serverSocket.isBound() && !serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection opened on (" + new Date() + ")");
                
                // Create a new thread for each client connection
                new Thread(new ClientHandler(clientSocket, documentRoot)).start();
                // Check the number of active connections
                int activeConnections = Thread.activeCount() - 2; // Subtract main and current threads
                int timeout = activeConnections >= MAX_CONCURRENT_CONNECTIONS ? BUSY_CONNECTION_TIMEOUT : IDLE_CONNECTION_TIMEOUT;
                clientSocket.setSoTimeout(timeout);

                System.out.println("Active Connections: " + activeConnections);
                System.out.println("Connection Timeout: " + timeout + " ms");
            
            }
        } catch (IOException e) {
            System.err.println("Server Connection error : " + e.getMessage());
        }

    }
}

// ClientHandler handles connection request by a client
class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private BufferedReader reader = null;
	private PrintWriter writer = null;
    private OutputStream outputStream = null;
    private String documentRoot; 


    // parameterised constructor
    ClientHandler(Socket socket, String documentRoot) {
        this.clientSocket = socket;
        this.documentRoot = documentRoot;
    }

    /**
     * Handles incoming client connections by parsing HTTP requests and serving files.
     */
    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            outputStream = clientSocket.getOutputStream();
            String request;
            while ((request = reader.readLine()) != null) {
                StringTokenizer tokenizer = new StringTokenizer(request);
                
                if (tokenizer.countTokens() != 3) {
                    sendErrorResponse(400, outputStream);
                    break; // Close the connection
                }
                
                String method = tokenizer.nextToken().toUpperCase();
                String path = tokenizer.nextToken();
                String version = tokenizer.nextToken();
                System.out.println("Request: " + request );
                
                if (method.equalsIgnoreCase("GET")) {
                    sendFile(path, outputStream);
                    if (version.equals("HTTP/1.1")){
                    String connectionHeader = reader.readLine();
                    if (connectionHeader == null || !connectionHeader.contains("keep-alive")) {
                        break; // Close the connection
                    }}
                } else {
                    // Handle unsupported methods or versions
                    System.out.println("Error 501 occurred : Method not supported");
                    sendErrorResponse(501, outputStream);
                    break; // Close the connection
                }
            }
        } catch (IOException e) {
            System.out.println("Error in establishing client conection: "+ e.getMessage());
        } finally {
			try {
                closeConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
		}
    }

    /**
     * Sends the requested file to the client.
     *
     * @param path         The path to the requested file.
     * @param outputStream The output stream to write the response to.
     * @throws IOException If an I/O error occurs.
    */
    private void sendFile(String path, OutputStream outputStream) throws IOException {
        
        if (path.equals("/")) {
            path = "/index.html"; // Default page
        }
        File file = new File(documentRoot + path);

        if (file.exists() && file.isFile()) {
            if (file.canRead()){
                System.out.println("File " + file + " found");
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    String contentType = getContentType(file.getName());
                    sendResponseHeaders(200, "OK", file.length(), outputStream,     contentType);
                
                    readFile(fileInputStream, outputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else {
                System.out.println("Error 403 occurred : File does not have read permission");
                sendErrorResponse(403, outputStream);
            }

        } else {
            // File not found
            System.out.println("Error 404 occurred : File does not exist");
            sendErrorResponse(404, outputStream);
        }
    }

    /**
     * Sends HTTP response headers to the client.
     *
     * @param statusCode    The HTTP status code.
     * @param statusMessage The HTTP status message.
     * @param contentLength The length of the response content.
     * @param outputStream  The output stream to write the headers to.
     * @param contentType   The content type of the response.
     * @throws IOException If an I/O error occurs.
     */
    private void sendResponseHeaders(int statusCode, String statusMessage, long contentLength
    , OutputStream outputStream, String contentType) throws IOException {
        
        PrintWriter writer = new PrintWriter(outputStream);

        writer.print("HTTP/1.0 " + statusCode + " " + statusMessage + "\r\n");
        writer.print("Content-Length: " + contentLength + "\r\n");
        writer.print("Content-Type: " + contentType + "\r\n");
        writer.print("Date: " + new Date() + "\r\n");
        writer.print("\r\n");
        writer.flush();
    }


    /**
     * Sends an HTTP error response to the client.
     *
     * @param statusCode    The HTTP status code for the error response.
     * @param outputStream  The output stream to write the error response to.
     * @throws IOException If an I/O error occurs.
    */
    private void sendErrorResponse(int statusCode, OutputStream outputStream) throws IOException {
        File file = null;
        
        try {
            String statusMessage = "";
            if (statusCode == 404) {
                file = new File(documentRoot + "/" + "404.html");
                statusMessage = "Not Found";
            } else if (statusCode == 403){
                file = new File(documentRoot + "/" +"403.html");
                statusMessage = "Forbidden";
            } else if (statusCode == 400){
                file = new File(documentRoot + "/" +"400.html");
                statusMessage = "Bad Request";
            } else if (statusCode == 501){
                file = new File(documentRoot + "/" +"501.html");
                statusMessage = "Method not supported";
            }
            FileInputStream fileInputStream = new FileInputStream(file);
            long contentLength = file.length();
            String contentType = getContentType(file.getName());
            
            sendResponseHeaders(statusCode, statusMessage, contentLength, outputStream, contentType);
            readFile(fileInputStream, outputStream);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads a file and writes its contents to the output stream.
     *
     * @param fileInputStream The input stream for the file.
     * @param outputStream    The output stream to write the file contents to.
    */
    private void readFile(FileInputStream fileInputStream, OutputStream outputStream){
        try {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();
            }
        } catch (IOException e) {
            System.out.println("Error reading file "+ e.getMessage());
        }
    }


    /**
     * Determines the content type based on the file name's extension.
     * 
     * @param fileName The name of the file.
     * @return The content type for the file.
    */
    private String getContentType(String fileName) {
            if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                return "text/html";
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")|| fileName.endsWith(".JPG")) {
                return "image/jpeg";
            } else if (fileName.endsWith(".png")) {
                return "image/png";
            } else if (fileName.endsWith(".gif")) {
                return "image/gif";
            } else {
                return "application/octet-stream";
            }
        }

    
    /**
     * Closes the connection and associated resources.
     *
     * @throws IOException If an I/O error occurs while closing the connection.
    */
    private void closeConnection() throws IOException {
		if (reader != null) {
            reader.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (outputStream != null) {
            outputStream.close();
        }
        if (clientSocket != null && !clientSocket.isClosed()) {
            clientSocket.close();
        }

		System.err.println("Connection closed.\n");
	}

}


