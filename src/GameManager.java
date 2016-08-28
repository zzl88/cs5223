import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Hashtable;

public class GameManager implements AcceptListenerI, ConnectionListenerI {
	public GameManager(String tracker_host, int tracker_port, String player_id) {
		tracker_host_ = tracker_host;
		tracker_port_ = tracker_port;
		player_id_ = player_id;
		N_ = 0;
		K_ = 0;
		
		role_manager_ = new PlayerManager(this);
		player_list_ = new ArrayList<Player>();
		players_ = new Hashtable<Connection, Player>();
	}

	public void setConnectionManager(ConnectionManager connection_manager) {
		connection_manager_ = connection_manager;
	}

	public String getPlayerId() { return player_id_; }
	public String getLocalHost() { return connection_manager_.getLocalHost(); }
	public int getListeningPort() { return connection_manager_.getListeningPort(); }
	public String getTrackerHost() { return tracker_host_; }
	public int getTrackerPort() { return tracker_port_; }
	
	public void stop() {
		connection_manager_.stop();
		for (Player player : player_list_) {
			player.stop();
		}
	}
	
	@Override
	public boolean OnAccepted(Connection connection) {
		connection.set_listener(this);
		return role_manager_.OnAccepted(connection);
	}

	@Override
	public void OnConnected(Connection connection) {
		connection.set_listener(this);
		role_manager_.OnConnected(connection);
	}
	
	@Override
	public void OnDisconnected(Connection connection) {
		role_manager_.OnDisconnected(connection);
		Player player = players_.get(connection);
		if (player != null) {
			players_.remove(connection);
			player_list_.remove(player);
			player.stop();
		}
	}

	@Override
	public void OnMessage(Connection connection, ByteBuffer buffer) {
		role_manager_.OnMessage(connection, buffer);
	}
	
	public void inilialize(int N, int K) {
		if (N_ == 0 && K_ == 0) {
			N_ = N;
			K_ = K;
			System.out.println("GameManager::inilialize() N[" + N_ + "] K[" + K_ + "]");
		}
	}
	
	public Player getPlayer(Connection connection) {
		return players_.get(connection);
	}
	public Player getPlayer(String host, int listening_port) {
		for (Player player : player_list_) {
			if (player.getState().host == host && player.getState().port == listening_port) {
				return player;
			}
		}
		return null;
	}
	
	public void connect(String host, int port) {
		connection_manager_.connect(host, port);
	}
	
	public void promotePrimary(PlayerManager pm) {
		System.out.println("GameManager::promotePrimary() promote to Primary server");
		PrimaryManager new_rm = new PrimaryManager(this);
		new_rm.promote(pm);
		role_manager_ = new_rm;
	}
	
	public void promoteSecondary(PlayerManager pm) {
		System.out.println("GameManager::promoteSecondary() promote to Secondary server");
		SecondaryManager new_rm = new SecondaryManager(this);
		new_rm.promote(pm);
		role_manager_ = new_rm;
	}
	
	public boolean Handle(Connection connection, PlayerJoinMsg msg) {
		if (msg.deserialize()) {
			String host = msg.getHost();
			PeerState peer = new PeerState(msg.getId(), host, msg.getListeningPort());
			Player player = new Player(connection, peer);
			players_.put(connection, player);
			player_list_.add(player);
			player.start();
			return true;
		}
		return false;
	}
	
	private String tracker_host_;
	private int tracker_port_;
	private String player_id_;
	private int N_;
	private int K_;
	
	private ConnectionManager connection_manager_;
	
	private RoleManager role_manager_;
	private ArrayList<Player> player_list_;
	private Hashtable<Connection, Player> players_;
}
