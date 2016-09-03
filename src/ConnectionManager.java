import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

interface AcceptListenerI {
	public abstract boolean OnAccepted(Connection connection);

	public abstract void OnConnected(Connection connection);
}

class Cmd {
	public Cmd(String host, int port) {
		host_ = host;
		listening_port_ = port;
	}

	public String host_;
	public int listening_port_;
}

public class ConnectionManager implements Runnable {
	public ConnectionManager(int port, AcceptListenerI listener) {
		listening_port_ = port;
		listener_ = listener;
		running_ = true;

		cmd_queue_ = new ArrayList<Cmd>();
	}

	public boolean initialize() {
		try {
			selector_ = Selector.open();

			ServerSocketChannel socket = ServerSocketChannel.open();
			socket.configureBlocking(false);
			socket.bind(new InetSocketAddress(listening_port_));
			InetSocketAddress address = (InetSocketAddress) socket.getLocalAddress();
			local_host_ = InetAddress.getLocalHost().getHostAddress();
			listening_port_ = address.getPort();
			System.out.format(
					"ConnectionManager::initialize() local_host[%s] listening_port[%s]\n", local_host_, listening_port_);

			socket.register(selector_, SelectionKey.OP_ACCEPT);
			return true;
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return false;
	}

	public String getLocalHost() {
		return local_host_;
	}

	public int getListeningPort() {
		return listening_port_;
	}

	public void connect(String remote_host, int remote_port) {
		synchronized (cmd_queue_) {
			cmd_queue_.add(new Cmd(remote_host, remote_port));
		}
		selector_.wakeup();
	}

	public void stop() {
		running_ = false;
		selector_.wakeup();
	}

	public void close(Connection connection) {
		try {
			SelectionKey key = connection.socket().keyFor(selector_);
			key.attach(null);
			key.cancel();
			connection.socket().close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public void run() {
		System.out.println("ConnectionManager::run() started");
		while (running_) {
			try {
				selector_.select();
			} catch (IOException | ClosedSelectorException ex) {
				ex.printStackTrace();
			}

			for (SelectionKey key : selector_.selectedKeys()) {
				if (key.isValid()) {
					if (key.isAcceptable()) {
						accept(key);
					} else if (key.isReadable()) {
						read(key);
					} else {
						System.out.println("ConnectionManager::run() unexpected " + key);
					}
				}
			}
			selector_.selectedKeys().clear();

			synchronized (cmd_queue_) {
				for (Cmd cmd : cmd_queue_) {
					try {
						System.out.format("ConnectionManager::run() connecting to host[%s] port[%s]\n",
								cmd.host_, cmd.listening_port_);
						SocketChannel socket = SocketChannel
								.open(new InetSocketAddress(cmd.host_, cmd.listening_port_));
						socket.configureBlocking(false);
						System.out.format("ConnectionManager::run() connected local_address[%s] remote_address[%s]\n",
								socket.getLocalAddress(), socket.getRemoteAddress());

						SelectionKey conn_key = socket.register(selector_, SelectionKey.OP_READ);
						Connection connection = new Connection(this, socket);
						conn_key.attach(connection);
						listener_.OnConnected(connection);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				cmd_queue_.clear();
			}
		}

		try {
			selector_.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("ConnectionManager::run() stopped");
	}

	private void read(SelectionKey key) {
		Connection connection = (Connection) key.attachment();
		connection.read();
	}

	private void accept(SelectionKey key) {
		try {
			ServerSocketChannel server_socket = (ServerSocketChannel) key.channel();
			SocketChannel client_socket = server_socket.accept();
			client_socket.configureBlocking(false);
			System.out.format("ConnectionManager::accept() accepted address[%s]\n", client_socket.getRemoteAddress());

			Connection connection = new Connection(this, client_socket);
			if (listener_.OnAccepted(connection)) {
				SelectionKey client_key = connection.socket().register(selector_, SelectionKey.OP_READ);
				client_key.attach(connection);
			} else {
				close(connection);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private String local_host_;
	private int listening_port_;
	private AcceptListenerI listener_;
	private volatile boolean running_;
	private Selector selector_;

	private ArrayList<Cmd> cmd_queue_;
}
