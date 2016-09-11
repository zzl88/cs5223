import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Scanner;

public class GameManager implements ServerSocketListenerI, ConnectionListenerI, Runnable {
	public GameManager(String tracker_host, int tracker_port, String player_id) {
		tracker_host_ = tracker_host;
		tracker_port_ = tracker_port;
		player_id_ = player_id;
		
		server_ = new ConnectionManager(0, this);
		
		role_manager_ = new PlayerManager(this);
		player_list_ = new ArrayList<Player>();
	}

	public String getPlayerId() { return player_id_; }
	public String getLocalHost() { return server_.getLocalHost(); }
	public int getListeningPort() { return server_.getListeningPort(); }
	
	public boolean start() {
		if (!server_.start()) 
			return false;

		if (connectTracker()) {
			System.out.println("GameManager::start() connected tracker");
			thread_ = new Thread(this);
			thread_.start();
			return true;
		}
		return false;
	}
	
	public void stop() {
		try {
			System.in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		synchronized (this) {
			if (tracker_ != null)
				server_.close(tracker_);
			tracker_ = null;
			for (Player player : player_list_) {
				player.stop();
				player.getConnection().set_listener(null);
			}
			server_.stop();
		}
	}
	
	public void run() {
		System.out.println("GameManager::run() started");
		Scanner sc = new Scanner(System.in);
		while (sc.hasNext()) {
			synchronized (this) {
				role_manager_.move(sc.nextLine().charAt(0));
			}
		}
		sc.close();
		System.out.println("GameManager::run() stopped");
	}
	

	@Override
	public void onAccepted(Connection connection) {
		Player player = new Player(connection, this);
		player.start();
		synchronized (this) {
			player_list_.add(player);
			role_manager_.onAccepted(player);
		}
	}
	
	@Override
	public void onDisconnected(Connection connection) {
		synchronized (this) {
			server_.close(connection);
			tracker_ = null;
			role_manager_.onTrackerDown();
		}
	}

	@Override
	public void onData(Connection connection, ByteBuffer buffer) {
		System.out.println("GameManager::onData()");
		MsgType msg_type = MsgType.values()[buffer.getInt()];
		switch (msg_type) {
		case kInfo:
			InfoMsg msg = new InfoMsg(buffer);
			if (msg.deserialize()) {
				synchronized (this) {
					role_manager_.handle(msg);
				}
			}
			break;
		default:
			System.out.format("GameManager::onData() unexpected msg_type[%s]\n", msg_type);
			break;
		}
	}
	
	public Player getPlayer(String host, int listening_port) {
		synchronized (this) {
			for (Player player : player_list_) {
				if (player.getState().host == host && player.getState().listening_port == listening_port) {
					return player;
				}
			}
		}
		return null;
	}
	
	public Connection getTracker() { return tracker_; }
	
	public boolean connectTracker() {
		if (tracker_ == null) {
			tracker_ = server_.connect(tracker_host_, tracker_port_);
			if (tracker_ == null) {
				stop();
				return false;
			} else {
				tracker_.set_listener(this);
			}
		}
		return true;
	}
	
	public void disconnectTracker() {
		if (tracker_ != null) {
			server_.close(tracker_);
			tracker_ = null;
			System.out.println("PlayerManager::OnMessage() disconnected from Tracker");
		}
	}
	
	public Player connect(String host, int port) {
		Connection connection = server_.connect(host, port);
		if (connection != null) {
			Player player = new Player(connection, this);
			player.setState(new PlayerState());
			player.getState().host = host;
			player.getState().listening_port = port;
			connection.set_listener(player);
			player.start();
			return player;
		} else {
			return null;
		}
	}
	
	public void promotePrimary(PlayerManager pm) {
		System.out.println("GameManager::promotePrimary() promote to Primary server 1");
		PrimaryManager new_rm = new PrimaryManager(this);
		new_rm.promote(pm);
		role_manager_ = new_rm;
	}
	
	public void promotePrimary(SecondaryManager sm) {
		System.out.println("GameManager::promotePrimary() promote to Primary sever 2");
		PrimaryManager new_rm = new PrimaryManager(this);
		new_rm.promote(sm);
		role_manager_ = new_rm;
	}
	
	public void promoteSecondary(PlayerManager pm) {
		System.out.println("GameManager::promoteSecondary() promote to Secondary server");
		SecondaryManager new_rm = new SecondaryManager(this);
		new_rm.promote(pm);
		role_manager_ = new_rm;
	}
	
	public void onDisconnected(Player player) {
		synchronized (this) {
			kickPlayer(player);
		}
	}
	public void handle(Player player, InfoMsg info) {
		if (info.deserialize()) {
			synchronized (this) {
				role_manager_.handle(info);
			}
		}
	}
	public void handle(Player player, JoinMsg msg) {
		synchronized (this) {
			if (msg.deserialize()) {
				PlayerState state = new PlayerState(msg.getId(), msg.getHost(), msg.getListeningPort());
				player.setState(state);
				role_manager_.onJoined(player);
			} else {
				kickPlayer(player);
			}
		}
	}
	
	public void handle(Player player, PlayersStateMsg msg) {
		synchronized (this) {
			if (msg.deserialize()) {
				role_manager_.handle(player, msg);
			} else {
				kickPlayer(player);
			}
		}
	}
	
	public void handle(Player player, MazeStateMsg msg) {
		synchronized (this) {
			role_manager_.handle(msg);
		}
	}
	
	public void handle(Player player, MoveMsg msg) {
		synchronized (this) {
			if (msg.deserialize()) {
				role_manager_.handle(player, msg);
			} else {
				kickPlayer(player);
			}
		}
	}
	
	public void broadcast(InfoMsg info) {
		for (Player player : player_list_) {
			player.getConnection().write(info);
		}
	}
	
	private void kickPlayer(Player player) {
		player.stop();
		server_.close(player.getConnection());
		player_list_.remove(player);
		role_manager_.onDisconnected(player);
	}
	
	private String tracker_host_;
	private int tracker_port_;
	private String player_id_;
	
	private ConnectionManager server_;
	private Connection tracker_;
	
	private Thread thread_;
	
	private RoleManager role_manager_;
	private ArrayList<Player> player_list_;
}
