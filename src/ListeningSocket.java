import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

interface ServerSocketListenerI {
	public abstract void onAccepted(Connection connection);
}

public class ListeningSocket implements Runnable {
	public ListeningSocket(int listening_port, ServerSocketListenerI listener) {
		listening_port_ = listening_port;
		listener_ = listener;
		running_ = false;
	}
	
	public boolean start() {
		running_ = true;
		try {
			server_socket_ = ServerSocketChannel.open();
			server_socket_.bind(new InetSocketAddress(listening_port_));
			local_host_ = InetAddress.getLocalHost().getHostAddress();
			listening_port_ = ((InetSocketAddress)server_socket_.getLocalAddress()).getPort();
			System.out.format("ListeningSocket::run() bind [%s:%s]\n", getLocalHost(), listening_port_);
			thread_ = new Thread(this);
			thread_.start();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public void stop() {
		running_ = false;
		try {
			server_socket_.close();
			thread_.join(1000);
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}
	
	public String getLocalHost() { return local_host_; }
	public int getListeningPort() { return listening_port_; }
	
	public void run() {
		System.out.println("ListeningSocket::run() started");
		while (running_) {
			try {
				SocketChannel socket = server_socket_.accept();
				if (socket != null)
					listener_.onAccepted(new Connection(socket));
			} catch (IOException e) {
				// e.printStackTrace();
				System.out.println("ListeningSocket::run() error when accepting");
			}
		}
		System.out.println("ListeningSocket::run() stopped");
	}
	
	private String local_host_;
	private int listening_port_;
	private ServerSocketListenerI listener_;
	private volatile boolean running_;
	private ServerSocketChannel server_socket_;
	
	private Thread thread_;
}
