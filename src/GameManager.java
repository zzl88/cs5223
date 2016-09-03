import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Scanner;

public class GameManager implements ServerSocketListenerI, ConnectionListenerI, Runnable {
	public GameManager(String tracker_host, int tracker_port, String player_id) {
		tracker_host_ = tracker_host;
		tracker_port_ = tracker_port;
		player_id_ = player_id;
		N_ = 0;
		K_ = 0;
		
		server_ = new ListeningSocket(0, this);
		
		role_manager_ = new PlayerManager(this);
		player_list_ = new ArrayList<Player>();
	}

	public String getPlayerId() { return player_id_; }
	public String getLocalHost() { return server_.getLocalHost(); }
	public int getListeningPort() { return server_.getListeningPort(); }
	
	public boolean start() {
		if (!server_.start()) 
			return false;
		server_.stop();
		connectTracker();
		
		thread_ = new Thread(this);
		thread_.start();
		return true;
	}
	
	public void stop() {
		try {
			System.in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		synchronized (this) {
			if (tracker_ != null)
				tracker_.stop();
			tracker_ = null;
			for (Player player : player_list_) {
				player.getConnection().set_listener(null);
				player.getConnection().stop();
			}
			server_.stop();
		}
	}
	
	public void run() {
		System.out.println("GameManager::run() started");
		Scanner sc = new Scanner(System.in);
		while (sc.hasNext()) {
			System.out.println(sc.nextLine());
		}
		sc.close();
		System.out.println("GameManager::run() stopped");
	}
	

	@Override
	public void onAccepted(Connection connection) {
		Player player = new Player(connection, this);
		if (connection.start()) {
			synchronized (this) {
				player_list_.add(player);
				role_manager_.onAccepted(player);
			}
		}
	}
	
	@Override
	public void onDisconnected(Connection connection) {
		synchronized (this) {
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
	
	public void inilialize(int N, int K) {
		if (N_ == 0 && K_ == 0) {
			N_ = N;
			K_ = K;
			System.out.format("GameManager::inilialize() N[%s] K[%s]\n", N_, K_);
		}
	}
	
	public Player getPlayer(String host, int listening_port) {
		synchronized (this) {
			for (Player player : player_list_) {
				if (player.getState().host == host && player.getState().port == listening_port) {
					return player;
				}
			}
		}
		return null;
	}
	
	public Connection getTracker() { return tracker_; }
	
	public void connectTracker() {
		if (tracker_ == null) {
			tracker_ = new Connection(tracker_host_, tracker_port_);
			tracker_.set_listener(this);
			if (!tracker_.start())
				stop();
		}
	}
	
	public void disconnectTracker() {
		if (tracker_ != null) {
			tracker_.stop();
			tracker_ = null;
			System.out.println("PlayerManager::OnMessage() disconnected from Tracker");
		}
	}
	
	public Player connect(String host, int port) {
		Connection connection = new Connection(host, port);		
		Player player = new Player(connection, this);
		player.setState(new PlayerState());
		player.getState().host = host;
		player.getState().port = port;
		connection.set_listener(player);
		int i;
		for (i = 0; i < 5 && !connection.start(); ++i) {}
		if (i == 5) return null;
		return player;
	}
	
	public void promotePrimary(PlayerManager pm) {
		System.out.println("GameManager::promotePrimary() promote to Primary server");
		PrimaryManager new_rm = new PrimaryManager(this);
		new_rm.promote(pm);
		role_manager_ = new_rm;
		server_.start();
	}
	
	public void promoteSecondary(PlayerManager pm) {
		System.out.println("GameManager::promoteSecondary() promote to Secondary server");
		SecondaryManager new_rm = new SecondaryManager(this);
		new_rm.promote(pm);
		role_manager_ = new_rm;
	}
	
	public void onDisconnected(Player player) {
		synchronized (this) {
			role_manager_.onDisconnected(player);
		}
	}
	public void handle(Player player, InfoMsg info) {
		if (info.deserialize()) {
			synchronized (this) {
				role_manager_.handle(info);
			}
		}
	}
	public boolean handle(Player player, PlayerJoinMsg msg) {
		synchronized (this) {
			if (msg.deserialize()) {
				PlayerState state = new PlayerState(msg.getId(), msg.getHost(), msg.getListeningPort());
				player.setState(state);
				role_manager_.onJoined(player);
				return true;
			} else {
				player.getConnection().stop();
				player_list_.remove(player);
				return false;
			}
		}
	}
	
	private String tracker_host_;
	private int tracker_port_;
	private String player_id_;
	private int N_;
	private int K_;
	
	private ListeningSocket server_;
	private Connection tracker_;
	
	private Thread thread_;
	
	private RoleManager role_manager_;
	private ArrayList<Player> player_list_;
}
