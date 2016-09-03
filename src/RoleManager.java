import java.nio.ByteBuffer;
import java.util.HashMap;

public abstract class RoleManager {
	public RoleManager(GameManager gm) {
		gm_ = gm;
		cur_seq_num_ = 0;
		history_ = new HashMap<Integer, Message>();
	}

	public void promote(RoleManager rm) {
		tracker_ = rm.getTracker();
		info_ = rm.getInfo();
		cur_seq_num_ = rm.getCurSeqNum();
		history_ = rm.getHistory();
	}

	public GameManager getGameManager() {
		return gm_;
	}

	public Connection getTracker() {
		return tracker_;
	}

	public InfoMsg getInfo() {
		return info_;
	}

	public int getCurSeqNum() {
		return cur_seq_num_;
	}

	public HashMap<Integer, Message> getHistory() {
		return history_;
	}

	public abstract boolean OnAccepted(Connection connection);

	public abstract void OnConnected(Connection connection);

	public abstract void OnDisconnected(Connection connection);

	public abstract void OnMessage(Connection connection, ByteBuffer buffer);

	protected GameManager gm_;
	protected Connection tracker_;
	protected InfoMsg info_;

	protected int cur_seq_num_;
	protected HashMap<Integer, Message> history_;
}

class PrimaryManager extends RoleManager {
	public PrimaryManager(GameManager gm) {
		super(gm);
	}

	public void promote(PlayerManager pm) {
		super.promote(pm);
		info_.addPeer(gm_.getLocalHost(), gm_.getListeningPort());
		if (tracker_ == null) {
			gm_.connect(gm_.getTrackerHost(), gm_.getTrackerPort());
		} else {
			tracker_.write(info_);
		}
	}

	@Override
	public boolean OnAccepted(Connection connection) {
		return true;
	}

	@Override
	public void OnConnected(Connection connection) {
		if (tracker_ == null) {
			tracker_ = connection;
			tracker_.write(info_);
			return;
		}
		System.out.println("PrimaryManager::OnConnected() unexpected");
	}

	@Override
	public void OnDisconnected(Connection connection) {
		if (tracker_ == connection) {
			System.out.println("PrimaryManager::OnDisconnected() ERR: Tracker down");
			tracker_ = null;
			gm_.stop();
			return;
		}
		
		if (connection == secondary_) {
			secondary_ = null;
			System.out.println("PrimaryManager::OnDisconnected() secondary server down");
			if (info_.getPeers().size() >= 3) {
				TrackerPeerInfo secondary = info_.getPeers().get(2);
				Player player0 = gm_.getPlayer(secondary.host, secondary.port);
				if (player0 != null) {
					secondary_ = player0.getConnection();
					System.out.format("PrimaryManager::OnDisconnected() nominate new secondary server host[%s] port[%s]\n",
							secondary.host, secondary.port);
					secondary_.write(info_);
				}
			}
		}
		
		Player player = gm_.getPlayer(connection);
		if (player != null) {
			PlayerState state = player.getState();
			info_.removePeer(state.host, state.port);
			tracker_.write(info_);
			if (secondary_ != null)
				secondary_.write(info_);
		}
	}

	@Override
	public void OnMessage(Connection connection, ByteBuffer buffer) {
		System.out.println("PrimaryManager::OnMessage()");
		MsgType msg_type = MsgType.values()[buffer.getInt()];
		switch (msg_type) {
		case kInfo:
			System.out.println("PrimaryManager::OnMessage() ignore kInfo");
			break;
		case kPlayerJoin:
			PlayerJoinMsg msg = new PlayerJoinMsg(buffer);
			if (gm_.Handle(connection, msg)) {
				PlayerState state = gm_.getPlayer(connection).getState();
				info_.addPeer(state.host, state.port);

				if (info_.getPeers().size() >= 2) {
					TrackerPeerInfo secondary = info_.getPeers().get(1);
					if (secondary.host.equals(state.host) && secondary.port == state.port) {
						secondary_ = connection;
						System.out.println("PrimaryManager::OnMessage() secondary server joined");
					} else {
						System.out.format("PrimaryManager::OnMessage() player joined id[%s]\n", msg.getId());
					}
				} else {
					System.out.println("PrimaryManager::OnMessage() ERR: wrong peer count");
				}
				tracker_.write(info_);
				if (secondary_ != null)
					secondary_.write(info_);
			}
			break;
		case kPlayersState:
			break;
		}
	}

	private Connection secondary_;
}

class PlayerManager extends RoleManager {
	public PlayerManager(GameManager gm) {
		super(gm);
		state_ = 0;
	}

	@Override
	public boolean OnAccepted(Connection connection) {
		return true;
	}

	@Override
	public void OnConnected(Connection connection) {
		switch (state_) {
		case 0:
			if (tracker_ == null)
				tracker_ = connection;
			break;
		case 1:
			System.out.println("PlayerManager::OnConnected() connected to Primary");

			primary_ = connection;
			PlayerJoinMsg msg = new PlayerJoinMsg(gm_.getPlayerId(), gm_.getLocalHost(), gm_.getListeningPort(),
					cur_seq_num_);
			history_.put(cur_seq_num_, new PlayerJoinMsg(msg));
			primary_.write(msg);
			break;
		default:
			System.out.println("PlayerManager::OnConnected() WRN: unexpected");
			return;
		}
		state_++;
	}

	@Override
	public void OnDisconnected(Connection connection) {
		if (connection == primary_) {
			primary_ = null;
			state_ = 1;
			
			TrackerPeerInfo primary = info_.getPeers().get(1);
			gm_.connect(primary.host, primary.port);
		}
	}

	@Override
	public void OnMessage(Connection connection, ByteBuffer buffer) {
		MsgType msg_type = MsgType.values()[buffer.getInt()];
		switch (msg_type) {
		case kInfo:
			System.out.println("PlayerManager::OnMessage() kInfo");
			
			info_ = new InfoMsg(buffer);
			if (info_.deserialize()) {
				for (TrackerPeerInfo peer : info_.getPeers()) {
					System.out.format("    peer host[%s] port[%s]\n", peer.host, peer.port);
				}
				int N = info_.getN();
				int K = info_.getK();
				gm_.inilialize(N, K);
				
				if (info_.getPeers().isEmpty()) {
					gm_.promotePrimary(this);
				} else if (info_.getPeers().size() == 1) {
					if (gm_.getLocalHost().equals(info_.getPeers().get(0).host) && 
							gm_.getListeningPort() == info_.getPeers().get(0).port)
						gm_.promotePrimary(this);
					else
						gm_.promoteSecondary(this);
				} else {
					if (tracker_ != null) {
						tracker_.close();
						System.out.println("PlayerManager::OnMessage() disconnected from Tracker");
						tracker_ = null;
					}
					if (primary_ == null)
						gm_.connect(info_.getPeers().get(0).host, info_.getPeers().get(0).port);

					TrackerPeerInfo secondary = info_.getPeers().get(1);
					if (gm_.getLocalHost().equals(secondary.host) && gm_.getListeningPort() == secondary.port) {
						gm_.promoteSecondary(this);
					}
				}
			}
			break;
		case kPlayerJoin:
			System.out.println("GameManager::OnMessage() WRN: unexpected kPlayerJoin");

			PlayerJoinMsg msg = new PlayerJoinMsg(buffer);
			gm_.Handle(connection, msg);
			break;
		case kPlayersState:
			break;
		}
	}

	protected Connection primary_;

	// 0: waiting for tracker
	// 1: waiting for primary
	// 2: established
	protected int state_;
}

class SecondaryManager extends PlayerManager {
	public SecondaryManager(GameManager gm) {
		super(gm);
	}

	public void promote(PlayerManager pm) {
		super.promote(pm);
		primary_ = pm.primary_;
		
		if (tracker_ != null) {
			tracker_.close();
			tracker_ = null;
			System.out.println("SecondaryManager::promote() disconnected from Tracker");
		}
		state_ = 1;
		if (primary_ == null)
			gm_.connect(info_.getPeers().get(0).host, info_.getPeers().get(0).port);
	}

	public void OnDisconnected(Connection connection) {
		if (connection == primary_) {
			gm_.promotePrimary(this);
			primary_ = null;
			TrackerPeerInfo primary = info_.getPeers().get(0);
			info_.removePeer(primary.host, primary.port);
		}
	}
	
	public void OnMessage(Connection connection, ByteBuffer buffer) {
		MsgType msg_type = MsgType.values()[buffer.getInt()];
		switch (msg_type) {
		case kInfo:
			System.out.println("SecondaryManager::OnMessage() kInfo");
			
			info_ = new InfoMsg(buffer);
			if (info_.deserialize()) {
				int N = info_.getN();
				int K = info_.getK();
				gm_.inilialize(N, K);
			}
			break;
		case kPlayerJoin:
			System.out.println("SecondaryManager::OnMessage() WRN: unexpected kPlayerJoin");

			PlayerJoinMsg msg = new PlayerJoinMsg(buffer);
			gm_.Handle(connection, msg);
			break;
		case kPlayersState:
			break;
		}
	}
}
