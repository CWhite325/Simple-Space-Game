import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import spaceWar.Constants;
import spaceWar.Obstacle;
import spaceWar.SpaceCraft;
import spaceWar.Torpedo;

/**
 * @author bachmaer
 * @author whitedc2
 * Class to listen for clients sending information reliably 
 * using TCP. It takes care of the following events:
 * 1. Client coming into the game
 * 2. Client firing torpedoes
 * 3. Client leaving the game
 * 4. Sending remove messages to the client
 */
public class PersistentConnectionToClient extends Thread {

	
	Socket clientConnection = null;
	
	SpaceGameServer spaceGameServer;
	
	InetSocketAddress thisClient = null;
	
	boolean thisClientIsPlaying = true;
	
	DataInputStream dis;
	DataOutputStream dos;
	
	static final int OBSTACLES_DONE = -5;
	public PersistentConnectionToClient(Socket sock, SpaceGameServer spaceGameServer) {
		
		this.clientConnection = sock;
		this.spaceGameServer = spaceGameServer;

	} // end PersistentConnectionToClient

	
	/**
	 * Listens for join and exiting clients using TCP. Joining clients are sent
	 * the x and y coordinates of all obstacles followed by a negative number. Receives
	 * fire messages from clients and the exit code when a client is leaving the game.
	 */
	public void run(){
		
		getStreams();
		byte[] ip = new byte[4];
		try {
			dis.read(ip);
			thisClient = new InetSocketAddress(InetAddress.getByAddress(ip),dis.readInt());
			spaceGameServer.addClientDatagramSocketAddresses(thisClient);
		} catch (UnknownHostException e) {
			System.err.println("Possible not a valid IP address");
		} catch (IOException e) {
			System.err.println("Error receiving IP from client");
		}
		sendObstacles();
		int command;
			while( thisClientIsPlaying && spaceGameServer.playing ){ // loop till playing is set to false
				
				try {
					command = dis.readInt();
					if (command == Constants.FIRED_TORPEDO) {
						int x = dis.readInt();
						int y =dis.readInt();
						int heading = dis.readInt();
						spaceGameServer.sector.updateOrAddTorpedo(thisClient, x, y, heading);
					}
					if (command == Constants.EXIT) {
						thisClientIsPlaying = false;
					}
				} catch (IOException e) {
					System.err.println("Error receiving command from Client");
				}
	
			} // end while
			spaceGameServer.removePersistentConnection(this);
			spaceGameServer.removeClientDatagramSocketAddresses(thisClient);
			spaceGameServer.sendRemoves(new SpaceCraft(thisClient, 0, 0, 0));
			spaceGameServer.sector.removeSpaceCraft(new SpaceCraft(thisClient, 0, 0, 0));
		

	} // end run

	protected void sendRemoveToClient( SpaceCraft sc )
	{
		try {
			byte[] ip = sc.ID.getAddress().getAddress();
			dos.write(ip);
			dos.writeInt(sc.ID.getPort());
			if (sc instanceof Torpedo) {
				dos.writeInt(Constants.REMOVE_TORPEDO);
			}
			else {
				dos.writeInt(Constants.REMOVE_SHIP);
			}
		} catch (IOException e) {
			System.err.println("Error sending Remove");
		}
	} // end sendRemoveToClient
	
	//Helper method to get client streams
	protected void getStreams() {
		try {
			dis = new DataInputStream(clientConnection.getInputStream());
			dos = new DataOutputStream(clientConnection.getOutputStream());
		} catch (IOException e) {
			System.err.println("Error getting streams from client");
		}
	}
	//Helper method to send Obstacle to client
	protected void sendObstacles() {
		try {
			for (Obstacle o : spaceGameServer.sector.getObstacles()) {
				dos.writeInt(o.getXPosition());
				dos.writeInt(o.getYPosition());
			}
			dos.writeInt(OBSTACLES_DONE);
		} catch (IOException e) {
			System.err.println("Error sending Obstacles");
		}
	}
} // end PersistentConnectionToClient class