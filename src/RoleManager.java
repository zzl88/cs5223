import java.util.HashMap;

public abstract class RoleManager {
	public RoleManager(GameManager gm) {
		gm_ = gm;
		cur_seq_num_ = 0;
		history_ = new HashMap<Integer, Message>();
	}

	public void promote(RoleManager rm) {
		info_ = rm.getInfo();
		cur_seq_num_ = rm.getCurSeqNum();
		history_ = rm.getHistory();
	}

	public InfoMsg getInfo() { return info_; }

	public int getCurSeqNum() { return cur_seq_num_; }

	public HashMap<Integer, Message> getHistory() { return history_; }

	public abstract void onTrackerDown();
	public abstract void handle(InfoMsg info);
	
	public abstract void onAccepted(Player player);
	public abstract void onDisconnected(Player player);
	public abstract void onJoined(Player player);

	protected GameManager gm_;
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
		gm_.connectTracker();
		gm_.getTracker().write(info_);
	}

	@Override
	public void onTrackerDown() {
		gm_.connectTracker();
	}
	
	@Override
	public void handle(InfoMsg info) {
		System.out.println("PrimaryManager::handle() ignore kInfo");
	}
	
	@Override
	public void onAccepted(Player player) {
	}

	@Override
	public void onDisconnected(Player player) {
		if (player == secondary_) {
			secondary_ = null;
			System.out.println("PrimaryManager::onDisconnected() secondary server down");
			if (info_.getPeers().size() >= 3) {
				TrackerPeerInfo secondary = info_.getPeers().get(2);
				Player player0 = gm_.getPlayer(secondary.host, secondary.port);
				if (player0 != null) {
					secondary_ = player0;
					System.out.format("PrimaryManager::onDisconnected() nominate new secondary server host[%s] port[%s]\n",
							secondary.host, secondary.port);
					secondary_.getConnection().write(info_);
				}
			}
		}
		
		PlayerState state = player.getState();
		if (state != null) {
			info_.removePeer(state.host, state.port);
			gm_.getTracker().write(info_);
			if (secondary_ != null)
				secondary_.getConnection().write(info_);
		}
	}
	
	@Override
	public void onJoined(Player player) {
		PlayerState state = player.getState();
		info_.addPeer(state.host, state.port);

		if (info_.getPeers().size() >= 2) {
			TrackerPeerInfo secondary = info_.getPeers().get(1);
			if (secondary.host.equals(state.host) && secondary.port == state.port) {
				secondary_ = player;
				System.out.println("PrimaryManager::onData() secondary server joined");
			} else {
				System.out.format("PrimaryManager::onData() player joined id[%s]\n", state.id);
			}
		} else {
			System.out.println("PrimaryManager::onData() ERR: wrong peer count");
		}
		gm_.getTracker().write(info_);
		if (secondary_ != null)
			secondary_.getConnection().write(info_);
	}

	private Player secondary_;
}

class PlayerManager extends RoleManager {
	public PlayerManager(GameManager gm) {
		super(gm);
	}
	
	@Override
	public void onTrackerDown() {
	}
	
	@Override
	public void handle(InfoMsg info) {
		System.out.println("PlayerManager::handle() kInfo");
		
		info_ = info;
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
			else {
				gm_.promoteSecondary(this);
			}
		} else {
			gm_.disconnectTracker();
			
			TrackerPeerInfo secondary = info_.getPeers().get(1);
			if (gm_.getLocalHost().equals(secondary.host) && gm_.getListeningPort() == secondary.port) {
				gm_.promoteSecondary(this);
			} else {
				join(info_.getPeers().get(0).host, info_.getPeers().get(0).port);
			}
		}
	}
	
	@Override
	public void onAccepted(Player player) {
		System.out.format("PlayerManager::onAccepted() unexpected connection[%s]\n", 
				player.getConnection().getRemoteAddress());
	}

	@Override
	public void onDisconnected(Player player) {
		if (player == primary_) {
			primary_ = null;
			
			TrackerPeerInfo primary = info_.getPeers().get(1);
			join(primary.host, primary.port);
		}
	}
	
	@Override
	public void onJoined(Player player) {
		System.out.println("PlayerManager::onJoined() WRN: unexpected");
	}
	
	public void join(String host, int port) {
		System.out.println("PlayerManager::join()");
		if (primary_ == null) {
			primary_ = gm_.connect(host, port);
			System.out.println("PlayerManager::join() " + primary_);
			if (primary_ == null) {
				gm_.stop();
			} else {
				PlayerJoinMsg msg = new PlayerJoinMsg(gm_.getPlayerId(), gm_.getLocalHost(), gm_.getListeningPort(),
						cur_seq_num_);
				history_.put(cur_seq_num_, new PlayerJoinMsg(msg));
				primary_.getConnection().write(msg);
			}
		}
		System.out.println("PlayerManager::join() ed");
	}

	protected Player primary_;
}

class SecondaryManager extends PlayerManager {
	public SecondaryManager(GameManager gm) {
		super(gm);
	}

	public void promote(PlayerManager pm) {
		super.promote(pm);
		primary_ = pm.primary_;
		
		gm_.disconnectTracker();
		join(info_.getPeers().get(0).host, info_.getPeers().get(0).port);
	}

	public void handle(InfoMsg info) {
		System.out.println("SecondaryManager::handle() kInfo");
		
		info_ = info;
		int N = info_.getN();
		int K = info_.getK();
		gm_.inilialize(N, K);
	}
	
	public void onDisconnected(Player player) {
		System.out.println("SecondaryManager::onDisconnected() " + player);
		System.out.println("SecondaryManager::onDisconnected() " + primary_);
		if (player == primary_) {
			TrackerPeerInfo primary = info_.getPeers().get(0);
			info_.removePeer(primary.host, primary.port);
			gm_.promotePrimary(this);
			primary_ = null;
		}
	}
}
