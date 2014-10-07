import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import spaceWar.*;

/**
 * @author bachmaer
 * @author whitedc2
 * Driver class for a simple networked space game. Opponents try to destroy each 
 * other by ramming. Head on collisions destroy both ships. Ships move and turn 
 * through GUI mouse clicks. All friendly and alien ships are displayed on a 2D 
 * interface.  
 */
public class SpaceGameClient implements SpaceGUIInterface
{
	// Keeps track of the game state
	Sector sector;
	
	// User interface
	SpaceGameGUI gui;
	
	// IP address and port to identify ownship and the 
	// DatagramSocket being used for game play messages.
	InetSocketAddress ownShipID;
	
	// Socket for sending and receiving
	// game play messages.
	DatagramSocket gamePlaySocket;

	// Socket used to register and to receive remove information
	// for ships and 
	Socket reliableSocket;
	
	// Set to false to stops all receiving loops
	boolean playing = true;
	
	static final boolean DEBUG = false;
	
	//Streams for TCP in out
	DataInputStream dis;
	DataOutputStream dos;
	
	/**
	 * Creates all components needed to start a space game. Creates Sector 
	 * canvas, GUI interface, a Sender object for sending update messages, a 
	 * Receiver object for receiving messages.
	 * @throws UnknownHostException 
	 */
	public SpaceGameClient()
	{
		// Create UDP Datagram Socket for sending and receiving
		// game play messages.
		try {
			
			gamePlaySocket = new DatagramSocket();
			gamePlaySocket.setSoTimeout(100);
		
			// Instantiate ownShipID using the DatagramSocket port
			// and the local IP address.
			ownShipID = new InetSocketAddress( InetAddress.getLocalHost(),
											   gamePlaySocket.getLocalPort());
			
			// Create display, ownPort is used to uniquely identify the 
			// controlled entity.
			sector = new Sector( ownShipID );
			
			//	gui will call SpaceGame methods to handle user events
			gui = new SpaceGameGUI( this, sector ); 
			
			// Establish TCP connection with the server and pass the 
			// IP address and port number of the gamePlaySocket to the 
			// server.
			try {
				reliableSocket = new Socket(Constants.SERVER_IP, Constants.SERVER_PORT);
				getStreams();
				byte[] ip = ownShipID.getAddress().getAddress();
				dos.write(ip);
				dos.writeInt(gamePlaySocket.getLocalPort());
			} catch (IOException e) {
				System.out.println("Error in Client Constructor");
			}
			// Call a method that uses TCP/IP to receive obstacles 
			// from the server. 
			receiveObstacles();
			
			// Start thread to listen on the TCP Socket and receive remove messages.
			new SpaceGameRemoveListener(this).start();
			
			// Infinite loop or separate thread to receive update 
			// messages from the server and use the messages to 
			// update the sector display
			
			byte[] data = new byte[24];
			int port,type,x,y,heading;
			byte[] ip = new byte[4];
			while (playing) {
				DatagramPacket dp = new DatagramPacket(data,24);
				try {
					gamePlaySocket.receive(dp);
					ByteArrayInputStream bais = new ByteArrayInputStream(dp.getData());
					DataInputStream dis = new DataInputStream(bais);
					dis.read(ip);
					port = dis.readInt();
					type = dis.readInt();
					x = dis.readInt();
					y = dis.readInt();
					heading = dis.readInt();
					if (type == Constants.JOIN) {
						sector.updateOrAddSpaceCraft(new AlienSpaceCraft(new InetSocketAddress(InetAddress.getByAddress(ip), port), x, y, heading));
					}
					else if (type == Constants.UPDATE_SHIP) {
						sector.updateOrAddSpaceCraft(new AlienSpaceCraft(new InetSocketAddress(InetAddress.getByAddress(ip), port), x, y, heading));
					}
					else if (type == Constants.UPDATE_TORPEDO) {
						sector.updateOrAddTorpedo(new Torpedo(new InetSocketAddress(InetAddress.getByAddress(ip), port), x, y, heading));
					}
				} catch (IOException e) {
					//Constantly throws an error
					//System.out.println("Error in Client Constructor UDP Update");
				}
			}
		
		} catch (SocketException e) {
			System.err.println("Error creating game play datagram socket.");
			System.err.println("Server is not opening.");

		} catch (UnknownHostException e) {
			System.err.println("Error creating ownship ID. Exiting.");
			System.err.println("Server is not opening.");
		}
		

	} // end SpaceGame constructor


	// TODO
	/**
	 * Causes sector.ownShip to turn and sends an update message for the heading 
	 * change.
	 */
	public void turnRight()
	{
		if (sector.ownShip != null) {
			
			if ( DEBUG ) System.out.println( " Right Turn " );
			
			// Update the display			
			sector.ownShip.rightTurn();
			
			// Send update message to server with new heading.
			sendUDPUpdate(Constants.UPDATE_SHIP);
				
		} 
		
	} // end turnRight


	/**
	 * Causes sector.ownShip to turn and sends an update message for the heading 
	 * change.
	 */
	public void turnLeft()
	{
		// See if the player has a ship in play
		if (sector.ownShip != null) {		
			
			if ( DEBUG ) System.out.println( " Left Turn " );
			
			// Update the display
			sector.ownShip.leftTurn();
			
			// Send update message to other server with new heading.
			sendUDPUpdate(Constants.UPDATE_SHIP);
		}		
		
	} // end turnLeft
	
	
	/**
	 * Causes sector.ownShip to turn and sends an update message for the heading 
	 * change.
	 */
	public void fireTorpedo()
	{
		// See if the player has a ship in play
		if (sector.ownShip != null) {		
			
			if ( DEBUG ) System.out.println( "Informing server of new torpedo" );
	
			// Send code to let server know a torpedo is being fired.
			try {
				dos.writeInt(Constants.FIRED_TORPEDO);
				dos.writeInt(sector.ownShip.getXPosition());
				dos.writeInt(sector.ownShip.getYPosition());
				dos.writeInt(sector.ownShip.getHeading());
			} catch (IOException e) {
				
			}

			// Send Position and heading
			sendUDPUpdate(Constants.UPDATE_TORPEDO);

		}		
		
	} // end turnLeft

	
	/**
	 * Causes sector.ownShip to move forward and sends an update message for the 
	 * position change. If there is an obstacle in front of
	 * the ship it will not move forward and a message is not sent. 
	 */
	public void moveFoward()
	{
		// Check if the player has and unblocked ship in the game
		if ( sector.ownShip != null && sector.clearInfront() ) {
			
			if ( DEBUG ) System.out.println( " Move Forward" );
			
			//Update the displayed position of the ship
			sector.ownShip.moveForward();
			
			// Send a message with the updated position to server
			sendUDPUpdate(Constants.UPDATE_SHIP);
		}
								
	} // end moveFoward
	
	
	/**
	 * Causes sector.ownShip to move forward and sends an update message for the 
	 * position change. If there is an obstacle in front of
	 * the ship it will not move forward and a message is not sent. 
	 */
	public void moveBackward()
	{
		// Check if the player has and unblocked ship in the game
		if ( sector.ownShip != null && sector.clearBehind() ) {
			
			if ( DEBUG ) System.out.println( " Move Backward" );
			
			//Update the displayed position of the ship
			sector.ownShip.moveBackward();
			
			// Send a message with the updated position to server
			sendUDPUpdate(Constants.UPDATE_SHIP);
		}
								
	} // end moveFoward

	//helper method to send UDP Updates
	public void sendUDPUpdate(int type) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		byte[] ip = new byte[4];
		ip = ownShipID.getAddress().getAddress();
		try {
			dos.write(ip);
			dos.writeInt(ownShipID.getPort());
			dos.writeInt(type);
			dos.writeInt(sector.ownShip.getXPosition());
			dos.writeInt(sector.ownShip.getYPosition());
			dos.writeInt(sector.ownShip.getHeading());
			DatagramPacket dp = new DatagramPacket(baos.toByteArray(), 24);
			dp.setAddress(Constants.SERVER_IP);
			dp.setPort(Constants.SERVER_PORT);
			gamePlaySocket.send(dp);
		} catch (IOException e) {
			System.err.println("Error sending UDP Update");
		}
	}
	/**
	 * Creates a new sector.ownShip if one does not exist. Sends a join message 
	 * for the new ship.
	 *
	 */
	public void join()
	{
		if (sector.ownShip == null ) {

			if ( DEBUG ) System.out.println( " Join " );
			
			// Add a new ownShip to the sector display
			sector.createOwnSpaceCraft();
			
			// Send message to server let them know you have joined the game using the 
			// send object
			sendUDPUpdate(Constants.JOIN);
		}
		
	} // end join

	
	/**
	 *  Perform clean-up for application shut down
	 */
	public void stop()
	{
		if ( DEBUG ) System.out.println("stop");
		
		// Stop all thread and close all streams and sockets
		playing = false;
		
		// Send exit code to the server
		try {
			dos.writeInt(Constants.EXIT);
		} catch (IOException e) {
			System.err.println("Error sending exit message");
		}
		
	} // end stop
	
	protected void getStreams() {
		try {
			dis = new DataInputStream(reliableSocket.getInputStream());
			dos = new DataOutputStream(reliableSocket.getOutputStream());
		} catch (IOException e) {
			System.err.println("Error getting stream to server");
		}
	}
	//Helper Method to receive Obstacles from TCP
	public void receiveObstacles() {
		try {
			int x;
			while ((x=dis.readInt()) > 0) {
				sector.addObstacle(x, dis.readInt());
			}
		} catch (IOException e) {
			System.err.println("Error receiving Obstacles");
		}
		
	}
	//Helper method to remove ships
	public void removeShip(SpaceCraft Sc) {
		sector.removeSpaceCraft(Sc);
	}
	//Helper method to remove Torpedoes
	public void removeTorpedo(Torpedo torp) {
		sector.removeTorpedo(torp);
	}
	/*
	 * Starts the space game. Driver for the application.
	 */
	public static void main(String[] args) 
	{	
		new SpaceGameClient();
				
	} // end main
	
	
} // end SpaceGame class

class SpaceGameRemoveListener extends Thread {
	protected Socket clientSock = null;
	protected boolean playing;
	protected DataInputStream dis = null;
	SpaceGameClient sgc = null;
	public SpaceGameRemoveListener(SpaceGameClient s) {
		super("RemoveListener");
		sgc = s;
		clientSock = s.reliableSocket;
		this.playing = s.playing;
	}
	
	public void run() {
		try {
			dis = new DataInputStream(clientSock.getInputStream());
		} catch (IOException e) {
			System.out.println("Error in Client Thread Run");
		}
		listen();
	}
	protected void listen() {
		int command=-1;
		byte[] ip = new byte[4];
		while (playing) {
			try {
				dis.read(ip);
				InetSocketAddress isa = new InetSocketAddress(InetAddress.getByAddress(ip), dis.readInt());
				command = dis.readInt();
				if (command == Constants.REMOVE_SHIP) {
					sgc.removeShip(new SpaceCraft(isa,0,0,0));
				}
				if (command == Constants.REMOVE_TORPEDO) {
					sgc.removeTorpedo(new Torpedo(isa,0,0,0));
				}
			} catch (IOException e) {
				System.out.println("Error in Listen");
			}
		}
	}
}