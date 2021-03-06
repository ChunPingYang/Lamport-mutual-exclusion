package cs6378;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cs6378.Message;

public class LamportMutux {
	private NClient client;
	// if send request then set it tobe true
	private boolean send_request;
	//priority queue that always place message with smallest time stamp at head of list
	private List<Message> messageList;
	//used to record unreceived message
	private Set<Integer> pending_replies;

	public LamportMutux(NClient client) {
		this.client = client;
		this.send_request = false;

		messageList = Collections.synchronizedList(new ArrayList<Message>() {
			public synchronized boolean add(Message message) {
				boolean ret = super.add(message);
				Collections.sort(messageList);
				return ret;
			}
		});
	}
	//process a received reply message according to lamport algorithm
	public synchronized void replyMessage(Message reply) {
		if (reply.getType().equals(LamportMsg.REPLY) && send_request) {
			pending_replies.remove(new Integer(reply.getFrom()));
			System.out.println("remaining pending replies: " + pending_replies);
		} else {
			System.err.println("Error: this not reply message");
		}
	}
	
	/**
	 * this function will be called when client place one request message to it's own queue
	 * meanwhile, add neighbors into pend_list as unreceived replies
	 * @param message
	 */
	public synchronized void queueRequest(Message message) {
		messageList.add(message);
		
		pending_replies = Collections.synchronizedSet(new HashSet<>(client.getReceivers().keySet()));
		pending_replies.remove(new Integer(client.getUID()));
		//send_request = true;
	}
	
	public Set<Integer> getPending_replies() {
		return pending_replies;
	}
	
	
	/**
	 * this would be called by client receive request message from neighbors
	 * then process message according to lamport algorithm
	 * @param message
	 * @return
	 */
	public synchronized Message requestMessage(Message message) {
		if (message.getType().equals(LamportMsg.REQUEST)) {
			this.messageList.add(message);
			String fileName = message.getFileName();
			Message reply = new Message(client.getClocks().get(fileName).getClock() + 1, LamportMsg.REPLY, message.getTo(),
					message.getFrom(), fileName);
			return reply;
		} else {
			System.err.println("Error: putting wrong type message into queue");
			return null;
		}
	}
	
	
	/**
	 * client call this function to try to enter criticalSection for the given file
	 * @param fileName
	 * @return
	 */
	public synchronized boolean criticalSection(String fileName) {
		if (!send_request) {
			this.send_request = true;
			System.out.println(client.getUID() + " request for resources");
			client.broadcast(LamportMsg.REQUEST, fileName);
		}

		if (send_request && pending_replies.isEmpty() && messageList.get(0).getFrom() == client.getUID()) {
			return true;
		}
		return false;
	}

	public synchronized List<Message> getMessageList() {
		return messageList;
	}

	public synchronized void cleanUp() {
		this.messageList.clear();
	}

	/**
	 * client process a release message from neighbors according to lamport algorithm
	 * @param other release message
	 */
	public synchronized void releaseMessage(Message other) {
		int sender = other.getFrom();
		if (messageList.get(0).getFrom() == sender) {
			messageList.remove(0);
		} else {
			System.err.println("Error: message at head of list is not the message from process entering cs");
		}
	}
	
	/**
	 * client process a release message from itself according to lamport algorithm
	 * @param other release message
	 */
	public synchronized void releaseMessage() {
		if (send_request && messageList.get(0).getFrom() == client.getUID()) {
			messageList.remove(0);
			send_request = false;
		} else {
			System.err.println("Error: send release before send request");
		}
	}

	public static void main(String[] args) {
		/*Message m1 = new Message(1, LamportMsg.REQUEST, 2, 1);
		Message m2 = new Message(2, LamportMsg.REQUEST, 2, 1);
		Message m3 = new Message(3, LamportMsg.REQUEST, 2, 1);
		Message m4 = new Message(10, LamportMsg.REQUEST, 2, 1);
		Message m5 = new Message(9, LamportMsg.REQUEST, 2, 1);
		Message m6 = new Message(7, LamportMsg.REQUEST, 2, 1);
		Message m7 = new Message(6, LamportMsg.REQUEST, 2, 1);
		Message m8 = new Message(7, LamportMsg.REQUEST, 0, 1);
		Message m9 = new Message(5, LamportMsg.REQUEST, 1, 3);
		LamportMutux mutux = new LamportMutux(null);
		mutux.messageList.add(m1);
		mutux.messageList.add(m3);
		mutux.messageList.add(m5);
		mutux.messageList.add(m2);
		mutux.messageList.add(m4);
		mutux.messageList.add(m6);
		mutux.messageList.add(m7);
		mutux.messageList.add(m9);
		mutux.messageList.add(m8);
		mutux.messageList.remove(m2);
		mutux.messageList.remove(m6);
		System.out.println(mutux.messageList);*/
	}
}
