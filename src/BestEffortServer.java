import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

import spaceWar.Constants;
import spaceWar.SpaceCraft;


/**
 *  Class to receive and forward UDP packets containing
 *  updates from clients. In addition, it checks for 
 *  collisions caused by client movements and sends
 *  appropriate removal information.
 *  
 * @author bachmaer
 * @author whitedc2
 */
class BestEffortServer extends Thread {
	
	// Socket through which all client UDP messages
	// are received
	protected DatagramSocket gamePlaySocket = null;

	// Reference to the server which holds the sector to be updated
	SpaceGameServer spaceGameServer;
	
	/**
	 * Creates DatagramSocket through which all client update messages
	 * will be received and forwarded.
	 */
	public BestEffortServer(SpaceGameServer spaceGameServer) {
		
		// Save reference to the server
		this.spaceGameServer = spaceGameServer;
		
		try {

			gamePlaySocket = new DatagramSocket( Constants.SERVER_PORT );
			
		} catch (IOException e) {

			System.err.println("Error creating socket to receive and forward UDP messages.");
			spaceGameServer.playing = false;
		}
		
	} // end gamePlayServer
	
	
	/**
	 * run method that continuously receives update messages, updates the display, 
	 * and then forwards update messages.
	 */
	public void run() {

		// Receive and forward messages. Update the sector display
		while (spaceGameServer.playing) {
			byte[] data = new byte[24];
			DatagramPacket dp = new DatagramPacket(data,24);
			try {
				gamePlaySocket.receive(dp);
				spaceGameServer.selectiveForward(dp, new InetSocketAddress(dp.getAddress(), dp.getPort()), gamePlaySocket);
				ByteArrayInputStream bais = new ByteArrayInputStream(dp.getData());
				DataInputStream dis = new DataInputStream(bais);
				byte[] ip = new byte[4];
				dis.read(ip);
				int port = dis.readInt();
				int type = dis.readInt();
				int x = dis.readInt();
				int y = dis.readInt();
				int heading = dis.readInt();
				if (type == Constants.JOIN || type == Constants.UPDATE_SHIP) {
					SpaceCraft sc = new SpaceCraft(new InetSocketAddress(InetAddress.getByAddress(ip), port), x, y, heading);
					spaceGameServer.sector.updateOrAddSpaceCraft(sc);
					ArrayList<SpaceCraft> destroyed = spaceGameServer.sector.collisionCheck(sc);
					if (destroyed != null) {
						for (SpaceCraft ship: destroyed) {
							spaceGameServer.sendRemoves(ship);
						}
					}
				}
				else if (type == Constants.UPDATE_TORPEDO) {
					spaceGameServer.sector.updateOrAddTorpedo(new InetSocketAddress(InetAddress.getByAddress(ip), port), x, y, heading);
				}
			} catch (IOException e) {
				System.err.println("Error forwarding packet");
			}
		}
		
		gamePlaySocket.close();

	} // end run
	
	
	//TODO
	
	
} // end BestEffortServer class
