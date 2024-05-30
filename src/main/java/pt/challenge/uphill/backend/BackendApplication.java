package pt.challenge.uphill.backend;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;


public class BackendApplication {
	private static final int PORT = 12345;
	private static final Logger logger = Logger.getLogger(BackendApplication.class);

	public static void main(String[] args){

		BasicConfigurator.configure();

		try (ServerSocket serverSocket = new ServerSocket(PORT)) {
			logger.info("Server is listening on port " + PORT);

			while (true) {
				Socket socket = serverSocket.accept();
				logger.info("New client connected");

				// Create a new thread for each client connection
				new Thread(new ServerThread(socket)).start();
			}
		} catch (IOException e) {
			logger.error("Connection error. Details: " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error: " + e.getMessage());
		}
	}
}
