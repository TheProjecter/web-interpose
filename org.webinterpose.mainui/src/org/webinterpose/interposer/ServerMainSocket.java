package org.webinterpose.interposer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.webinterpose.mainui.InterposerView.Mapping;

public class ServerMainSocket implements Runnable {

	String domain;
	Map<String, Mapping> mapOfMappedMapping;
	
	public void setDomain(String domain) {
		this.domain = domain;
	}
	
	public interface AddChildListener {
		public void newChild(String name, Object who);
		public void removeChild(String child);
		public void notify(String message, Object who);
		public void addFileUrl(String fileUrl);
	}
	public static abstract class AddChildAdapter implements AddChildListener {
		public void newChild(String name, Object who) {};
		public void removeChild(String child) {};
		public void notify(String message, Object who) {};
		public void addFileUrl(String fileUrl) {};
	}
	AddChildListener childListener;
	Integer localPort;
	ServerSocket server = null;
	volatile boolean toBreak = false;
	Vector<ServerChildSocket> childdren;

	public ServerMainSocket(Integer port, Map<String, Mapping> mapOfMappedMapping) {
		localPort = port;
		childdren = new Vector<ServerChildSocket>();
		this.mapOfMappedMapping = mapOfMappedMapping;
	}

	public void setChildListener(AddChildListener listener) {
		childListener = listener;
		if (listener != null) {
			childListener.newChild("Server On port: "+localPort, this);
			if (childdren.size() > 0) {
				for (ServerChildSocket child : childdren) {
					childListener.newChild(child.getUnicName(), child);
				}
			}
		}
	}

	public void setPort(int port) {
		localPort = port;
	}

	public void setToBreak(boolean doBreak) {
		toBreak = doBreak;
		if (toBreak == true && server != null && !server.isClosed()) {
			try {
				server.close();
				for (ServerChildSocket child : childdren) {
					child.doBreak();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	void notify(String message, Object who) {
		if (childListener != null) childListener.notify(message, who);
		System.out.println(message);
	}

	void runChildThread(ServerChildSocket child) {
		if (childListener != null) childListener.newChild(child.getUnicName(), child);
		childdren.add(child);
		Thread t = new Thread(child);
		t.setName(child.toString());
		t.start();
	}

	@Override
	public void run() {
		try {
			server = new ServerSocket(localPort);
			server.setReuseAddress(true);
			server.setSoTimeout(20000);
			
			while(!toBreak) {
				try {
					runChildThread(new ServerChildSocket(server.accept(), this));
				} catch (SocketTimeoutException e) {
					//notify("Timeout ... Continuing? " + toBreak, this);
				} 
			}
		} catch (IOException e1) {
			//e1.printStackTrace();
		} finally {
			if (server != null && !server.isClosed()) {
				try {
					server.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
