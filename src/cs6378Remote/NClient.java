package cs6378;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import cs6378.Message;

public class NClient {
	private ServerSocket myServer;
	private Map<Integer, ObjectInputStream> receivers;
	private Map<Integer, ObjectOutputStream> senders;
	private Map<Integer, Socket> outcomingNeighbors;
	private Map<Integer, Socket> incomingNeighbors;
	private Map<ObjectOutputStream, Integer> neighbors;
	private static int numOfNeighbors;
	private LamportClock clock;
	private PrintWriter pw;
	private File file;
	private List<String> fileList;
	private String[] remoteAddr;
	private int[] remotePort;
	public LamportClock getClock() {
		return clock;
	}

	private LamportMutux mutux;

	private final int UID = 1;

	public NClient() {
		try {
			file = new File(UID + "_log.txt");
			pw = new PrintWriter(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public ServerSocket getMyServer() {
		return myServer;
	}

	public Map<Integer, ObjectInputStream> getReceivers() {
		return receivers;
	}

	public Map<Integer, ObjectOutputStream> getSenders() {
		return senders;
	}

	public Map<Integer, Socket> getOutcomingNeighbors() {
		return outcomingNeighbors;
	}

	public Map<Integer, Socket> getIncomingNeighbors() {
		return incomingNeighbors;
	}

	public synchronized int getUID() {
		return UID;
	}

	public static void main(String[] args) {
		NClient client = new NClient();
		// int[] nids = new int[] { 1, 2, 3, 4, 5 };
		// int[] ports = new int[] { 30000, 30001, 30002, 30003, 30004 };
		// String[] ips = new String[] { "127.0.0.1", "127.0.0.1", "127.0.0.1",
		// "127.0.0.1", "127.0.0.1" };
		int[] nids = new int[] { 1, 2, 3 };
		int[] ports = new int[] { 30000, 30001, 30002 };
		String[] ips = new String[] { "127.0.0.1", "127.0.0.1", "127.0.0.1" };

		client.init(nids, ports, ips);
		// client.test();
		client.readServerConfig(ports, ips);
		client.generate();
		// client.logInfo();
	}
	
	private void readServerConfig(int[] ports, String[] ips) {
		this.remotePort = new int [ports.length];
		for(int i = 0; i < ports.length; i++) {
			remotePort[i] = ports[i];
		}
		
		for(int i = 0; i < ips.length; i++) {
			remoteAddr[i] = ips[i];
		}
	}
	
	private void init(int[] ids, int[] ports, String[] ips) {

		clock = new LamportClock(1);
		mutux = new LamportMutux(this);

		outcomingNeighbors = Collections.synchronizedMap(new HashMap<Integer, Socket>());
		incomingNeighbors = Collections.synchronizedMap(new HashMap<Integer, Socket>());

		neighbors = Collections.synchronizedMap(new HashMap<ObjectOutputStream, Integer>());
		receivers = Collections.synchronizedMap(new HashMap<Integer, ObjectInputStream>());
		senders = Collections.synchronizedMap(new HashMap<Integer, ObjectOutputStream>());

		numOfNeighbors = ids.length - 1;
		int myPort = 0;
		for (int i = 0; i < ids.length; i++) {
			if (UID == ids[i]) {
				myPort = ports[i];
			} else {

				final int j = i;
				new Thread(new Runnable() {
					@Override
					public void run() {
						boolean done = false;
						while (!done) {
							try {
								Socket socket = new Socket(ips[j], ports[j]);
								ObjectOutputStream ps = new ObjectOutputStream(socket.getOutputStream());
								senders.put(ids[j], ps);
								neighbors.put(ps, ids[j]);
								outcomingNeighbors.put(ids[j], socket);
								done = true;
							} catch (IOException e) {
								try {
									Thread.sleep(50);
								} catch (InterruptedException e1) {
									e1.printStackTrace();
								}
								System.out.println("Waitting For Other Clients To Be Started");
								done = false;
							}
						}
					}
				}).start();
			}
		}

		try {
			myServer = new ServerSocket(myPort);
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("finish Setting up outcoming connections");
		for (int i = 0; i < ids.length; i++) {
			try {
				if (ids[i] != UID) {
					Socket socket = myServer.accept();
					incomingNeighbors.put(ids[i], socket);
					receivers.put(ids[i], new ObjectInputStream(socket.getInputStream()));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		System.out.println("Network setup is finished");
		listenToNeighbors();
		while (outcomingNeighbors.size() < numOfNeighbors || incomingNeighbors.size() < numOfNeighbors) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Program starts to works");
	}

	private void listenToNeighbors() {
		for (Map.Entry<Integer, ObjectInputStream> entry : receivers.entrySet()) {
			ObjectInputStream br = entry.getValue();
			new Thread(new ClientListener(br, this)).start();
		}
	}

	private synchronized void test() {
		mutux.cleanUp();
		broadcast(LamportMsg.REQUEST);
	}

	public void startWork() {
		mutux.cleanUp();

	}

	public Map<ObjectOutputStream, Integer> getNeighbors() {
		return neighbors;
	}

	public synchronized void broadcast(String type) {
		if (type.equals(LamportMsg.REPLY)) {
			System.err.println("error: cannnot broadcast reply message");
			return;
		}
		this.clock.local_Event();
		if (type.equals(LamportMsg.REQUEST)) {
			Message message = new Message(this.clock.getClock(), type, this.UID, this.UID);
			mutux.queueRequest(message);
		}
		// System.out.println("senders" + senders);
		for (Map.Entry<Integer, ObjectOutputStream> entry : senders.entrySet()) {
			try {
				ObjectOutputStream ps = entry.getValue();
				Message message = new Message(this.clock.getClock(), type, this.UID, neighbors.get(ps));
				System.out.println("sent: " + message);
				ps.writeObject(message);
				ps.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public synchronized void processMessage(Message message) {
		System.out.println("start processing messages");
		this.clock.msg_event(message);
		if (message.getType().equals(LamportMsg.RELEASE)) {
			mutux.releaseMessage(message);
		} else if (message.getType().equals(LamportMsg.REPLY)) {
			mutux.replyMessage(message);
		} else if (message.getType().equals(LamportMsg.REQUEST)) {
			Message reply = mutux.requestMessage(message);
			privateMessage(reply);
		} else {
			System.err.println("error: You receive wrong type of message");
		}
	}
	
	public synchronized void processServerMessage(Message message) {
		
	}
	
	public synchronized void privateMessage(Message message) {
		if (!message.getType().equals(LamportMsg.REPLY)) {
			System.err.println("error: only reply message could be sent privatly");
			return;
		}
		this.clock.local_Event();
		try {
			ObjectOutputStream oos = senders.get(message.getTo());
			oos.writeObject(message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void logInfo() {
		pw.println(UID + " enters critical section");
		pw.println("clock: " + clock.getClock());
		pw.println("reply list:" + mutux.getPending_replies().toString());
		pw.println(UID + " exits critical section");
		pw.flush();
	}
	
	// no synchronized??
	private void executeCriticalSection() {
		Random random = new Random();
		if(fileList == null) {
			int index = random.nextInt(remotePort.length);
			try (Socket socket = new Socket(remoteAddr[index], remotePort[index]);) {
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				Message m = new Message(clock.getClock(), ServerMsg.ENQUIRY, UID, -1);
				oos.writeObject(m);
				oos.flush();
				ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
				Message enquiry = (Message) ois.readObject();
				String[] fileNames = enquiry.getContent().split(ServerMsg.SEPARATOR);
				fileList = new ArrayList<String>(Arrays.asList(fileNames));
				
				oos.close();
				ois.close();
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		int n = random.nextInt(11);
		if(n >= 4) {
			int index = random.nextInt(remotePort.length);
			try (Socket socket = new Socket(remoteAddr[index], remotePort[index]);) {
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				Message m = new Message(clock.getClock(), ServerMsg.READ, UID, -1);
				int fileIndex = random.nextInt(fileList.size());
				m.setContent(fileList.get(fileIndex));
				oos.writeObject(m);
				oos.flush();
				ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
				Message read = (Message) ois.readObject();
				System.out.println("Client Read Last Line from Server: " + read.getContent());
				
				oos.close();
				ois.close();
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
			
		}else {
			int fileIndex = random.nextInt(fileList.size());
			for(int i = 0; i < this.remotePort.length; i++) {
				try (Socket socket = new Socket(remoteAddr[i], remotePort[i]);) {
					ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
					Message m = new Message(clock.getClock(), ServerMsg.WRITE, UID, -1);
					m.setContent(fileList.get(fileIndex) + ServerMsg.SEPARATOR + clock.getClock() + " " + UID);
					
					oos.writeObject(m);
					oos.flush();
					ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
					Message write = (Message) ois.readObject();
					String type = write.getType();
					
					oos.close();
					ois.close();
					if(type.equals(ServerMsg.SUCCESS)) {
						System.out.println("Client WRITE Last Line to Server Successfully");
					}else {
						System.err.println("Client WRITE Error");
					}
				} catch (IOException | ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private void generate() {
		int critical_section_times = 0;
		Random rand = new Random();
		while (true) {
			int n = rand.nextInt(21);
			try {
				Thread.sleep(n * 50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (critical_section_times > 0) {
				System.out.println("I am done");
			} else {
				// what if use if below
				while (!mutux.criticalSection()) {
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				
				System.out.println(UID + " enters critical section");
				executeCriticalSection();
				logInfo();
				System.out.println("clock: " + clock.getClock());
				System.out.println("reply list:" + mutux.getPending_replies().toString());
				critical_section_times++;
				System.out.println(UID + " exits critical section");

				mutux.releaseMessage();
				broadcast(LamportMsg.RELEASE);
			}
		}
	}
}
