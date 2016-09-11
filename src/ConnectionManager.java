import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;


interface ServerSocketListenerI {
	public abstract void onAccepted(Connection connection);
}

class SelectorCmd {
	enum Type {
		kAdd, kRemove
	}
	
	public SelectorCmd(Type type, Connection connection) {
		this.type = type;
		this.connection = connection;
	}
	Connection connection;
	Type type;
}

public class ConnectionManager implements Runnable {
	public ConnectionManager(int port, ServerSocketListenerI listener) {
		listening_port_ = port;
		listener_ = listener;
		running_ = true;
		selector_cmds_ = new ArrayList<SelectorCmd>();
	}

	public String getLocalHost() { return local_host_; }

	public int getListeningPort() { return listening_port_; }

	public boolean start() {
		try {
			selector_ = Selector.open();

			ServerSocketChannel socket = ServerSocketChannel.open();
			socket.configureBlocking(false);
			socket.bind(new InetSocketAddress(listening_port_));
			InetSocketAddress address = (InetSocketAddress) socket.getLocalAddress();
			local_host_ = InetAddress.getLocalHost().getHostAddress();
			listening_port_ = address.getPort();
			System.out.format(
					"ConnectionManager::start() local_host[%s] listening_port[%s]\n", local_host_, listening_port_);

			socket.register(selector_, SelectionKey.OP_ACCEPT);
			
			thread_ = new Thread(this);
			thread_.start();
			return true;
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return false;
	}
	
	public void stop() {
		running_ = false;
		selector_.wakeup();
		
		try {
			thread_.join(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public Connection connect(String remote_host, int remote_port) {
		System.out.format("ConnectionManager::connect() connecting to remote[%s:%s]\n",
				remote_host, remote_port);
		try {
			SocketChannel socket = SocketChannel
					.open(new InetSocketAddress(remote_host, remote_port));
			socket.configureBlocking(false);
			System.out.format("ConnectionManager::connect() connected local[%s] remote[%s]\n",
					socket.getLocalAddress(), socket.getRemoteAddress());
	
			Connection connection = new Connection(socket);
			synchronized (selector_cmds_) {
				selector_cmds_.add(new SelectorCmd(SelectorCmd.Type.kAdd, connection));
			}
			selector_.wakeup();
			return connection;
		} catch (IOException e) {
			// e.printStackTrace();
			System.out.format("ConnectionManager::connect() failed to connect remote[%s:%s]\n", remote_host, remote_port);
		}
		return null;
	}
	
	public void close(Connection connection) {
		synchronized (selector_cmds_) {
			selector_cmds_.add(new SelectorCmd(SelectorCmd.Type.kRemove, connection));
		}
		selector_.wakeup();
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
			
			synchronized (selector_cmds_) {
				for (SelectorCmd cmd : selector_cmds_) {
					SocketChannel socket = cmd.connection.getSocket();
					switch (cmd.type) {
					case kAdd:
						try {
							SelectionKey conn_key = socket.register(selector_, SelectionKey.OP_READ);
							System.out.println("ConnectionManager::run() registered");
							conn_key.attach(cmd.connection);
						} catch (ClosedChannelException e) {
							e.printStackTrace();
						}
						break;
					case kRemove:
						try {
							SelectionKey key = cmd.connection.getSocket().keyFor(selector_);
							key.attach(null);
							key.cancel();
							cmd.connection.getSocket().close();
							System.out.println("ConnectionManager::run() unregistered");
						} catch (IOException e) {
							e.printStackTrace();
						}
						break;
					}
				}
				selector_cmds_.clear();
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
		if (key.attachment() != null) {
			Connection connection = (Connection) key.attachment();
			connection.read();
		}
	}

	private void accept(SelectionKey key) {
		try {
			ServerSocketChannel server_socket = (ServerSocketChannel) key.channel();
			SocketChannel client_socket = server_socket.accept();
			client_socket.configureBlocking(false);
			System.out.format("ConnectionManager::accept() accepted address[%s]\n", client_socket.getRemoteAddress());

			Connection connection = new Connection(client_socket);
			SelectionKey client_key = client_socket.register(selector_, SelectionKey.OP_READ);
			client_key.attach(connection);
			listener_.onAccepted(connection);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private String local_host_;
	private int listening_port_;
	private ServerSocketListenerI listener_;
	private Selector selector_;
	
	private ArrayList<SelectorCmd> selector_cmds_;

	private volatile boolean running_;
	private Thread thread_;
}
