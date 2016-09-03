import java.util.ArrayList;
import java.util.HashMap;

public abstract class RoleManager {
	public RoleManager(GameManager gm) {
		gm_ = gm;
		cur_seq_num_ = 0;
		player_states_ = new ArrayList<PlayerState>();
		history_ = new HashMap<Integer, Message>();
	}

	public void promote(RoleManager rm) {
		info_ = rm.getInfo();
		playground_ = rm.getPlayground();
		player_states_ = rm.getPlayerStates();
		cur_seq_num_ = rm.getCurSeqNum();
		history_ = rm.getHistory();
	}

	public InfoMsg getInfo() { return info_; }

	public int getCurSeqNum() { return cur_seq_num_; }
	public Playground getPlayground() { return playground_; }
	public ArrayList<PlayerState> getPlayerStates() { return player_states_; }
	public HashMap<Integer, Message> getHistory() { return history_; }

	public abstract void handle(InfoMsg info);
	public abstract void handle(Player player, PlayerState state);
	public abstract void handle(MazeStateMsg msg);
	public abstract void handle(Player player, MoveMsg msg);
	
	public abstract void onTrackerDown();
	public abstract void onAccepted(Player player);
	public abstract void onDisconnected(Player player);
	public abstract void onJoined(Player player);
	
	public abstract void move(char direction);

	protected GameManager gm_;
	protected InfoMsg info_;
	
	protected Playground playground_;
	protected ArrayList<PlayerState> player_states_;
	
	protected int cur_seq_num_;
	protected HashMap<Integer, Message> history_;
}

class PrimaryManager extends RoleManager {
	public PrimaryManager(GameManager gm) {
		super(gm);
	}

	public void promote(PlayerManager pm) {
		super.promote(pm);
		self_ = new PlayerState();
		self_.host = gm_.getLocalHost();
		self_.listening_port = gm_.getListeningPort();
		self_.id = gm_.getPlayerId();
		
		playground_.initPlayer(self_);
		
		info_.addPeer(gm_.getLocalHost(), gm_.getListeningPort());
		gm_.connectTracker();
		gm_.getTracker().write(info_);
	}
	
	public void promote(SecondaryManager sm) {
		super.promote(sm);
		
		for (PlayerState state : player_states_) {
			playground_.setPlayer(state.x, state.y, state);
			if (state.host.equals(gm_.getLocalHost()) && state.listening_port == gm_.getListeningPort())
				self_ = state;
		}
		
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
	public void handle(Player player, PlayerState state) {
		System.out.println("PrimaryManager::handle() unexpected PlayerState");
	}
	
	@Override
	public void handle(MazeStateMsg msg) {
		System.out.println("PrimaryManager::handle() unexpected MazeStateMsg");
	}
	
	@Override
	public void handle(Player player, MoveMsg msg) {
		System.out.println("PrimaryManager::handle() MoveMsg");
		synchronized (playground_) {
			switch (msg.getDirection()) {
			case '1':
				playground_.moveWest(player.getState());
				break;
			case '2':
				playground_.moveSouth(player.getState());
				break;
			case '3':
				playground_.moveEast(player.getState());
				break;
			case '4':
				playground_.moveNorth(player.getState());
				break;
			case '5':
				break;
			case '9':
				break;
			default:
				break;
			}
			if (secondary_ != null) {
				secondary_.getConnection().write(player.getState());
				secondary_.getConnection().write(new MazeStateMsg(playground_));
			}
			player.getConnection().write(player.getState());
			player.getConnection().write(new MazeStateMsg(playground_));
		}
	}
	
	@Override
	public void onAccepted(Player player) {
	}

	@Override
	public void onDisconnected(Player player) {
		player_states_.remove(player.getState());
		
		if (player == secondary_) {
			secondary_ = null;
			System.out.println("PrimaryManager::onDisconnected() secondary server down");
			if (info_.getPeers().size() >= 3) {
				TrackerPeerInfo secondary = info_.getPeers().get(2);
				Player player0 = gm_.getPlayer(secondary.host, secondary.listening_port);
				if (player0 != null) {
					secondary_ = player0;
					System.out.format("PrimaryManager::onDisconnected() nominate new secondary server host[%s] port[%s]\n",
							secondary.host, secondary.listening_port);
					for (PlayerState p : player_states_) {
						secondary_.getConnection().write(p);
					}
				}
			}
		}
		
		playground_.setPlayer(player.getState().x, player.getState().y, null);
		
		PlayerState state = player.getState();
		if (state != null) {
			info_.removePeer(state.host, state.listening_port);
			gm_.getTracker().write(info_);
			if (secondary_ != null) {
				secondary_.getConnection().write(info_);
				secondary_.getConnection().write(new MazeStateMsg(playground_));
			}
		}
	}
	
	@Override
	public void onJoined(Player player) {
		PlayerState state = player.getState();
		synchronized (playground_) {
			for (PlayerState p : player_states_) {
				if (p.host.equals(state.host) && p.listening_port == state.listening_port) {
					player.setState(p);
					System.out.format("PrimaryManager::onJoined() update %s", p);
					break;
				}
			}
			
			state = player.getState();
			if (state.x == -1) {
				playground_.initPlayer(state);
				player_states_.add(state);
			}
			
			info_.addPeer(state.host, state.listening_port);

			if (info_.getPeers().size() >= 2) {
				TrackerPeerInfo secondary = info_.getPeers().get(1);
				if (secondary.host.equals(state.host) && secondary.listening_port == state.listening_port) {
					secondary_ = player;
					System.out.println("PrimaryManager::onJoined() secondary server joined");
				} else {
					System.out.format("PrimaryManager::onJoined() player joined id[%s]\n", state.id);
				}
			} else {
				System.out.println("PrimaryManager::onJoined() ERR: wrong peer count");
			}
			gm_.getTracker().write(info_);
			if (secondary_ != null) {
				secondary_.getConnection().write(info_);
				secondary_.getConnection().write(state);
			}
			
			for (PlayerState p : player_states_) {
				player.getConnection().write(p);
			}
			player.getConnection().write(new MazeStateMsg(playground_));
		}
	}
	
	@Override
	public void move(char direction) {
		synchronized (playground_) {
			switch (direction) {
			case '1':
				playground_.moveWest(self_);
				break;
			case '2':
				playground_.moveSouth(self_);
				break;
			case '3':
				playground_.moveEast(self_);
				break;
			case '4':
				playground_.moveNorth(self_);
				break;
			case '9':
				break;
			default:
				break;
			}
			if (secondary_ != null) {
				secondary_.getConnection().write(self_);
				secondary_.getConnection().write(new MazeStateMsg(playground_));
			}
		}
	}

	private PlayerState self_;
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
		System.out.format("  N[%s] K[%s]\n", info_.getN(), info_.getK());
		for (TrackerPeerInfo peer : info_.getPeers()) {
			System.out.format("    peer host[%s] port[%s]\n", peer.host, peer.listening_port);
		}
		
		if (playground_ == null) {
			playground_ = new Playground(info_.getN(), info_.getK());
		}
		
		if (info_.getPeers().isEmpty()) {
			gm_.promotePrimary(this);
		} else if (info_.getPeers().size() == 1) {
			if (gm_.getLocalHost().equals(info_.getPeers().get(0).host) && 
					gm_.getListeningPort() == info_.getPeers().get(0).listening_port)
				gm_.promotePrimary(this);
			else {
				gm_.promoteSecondary(this);
			}
		} else {
			gm_.disconnectTracker();
			
			TrackerPeerInfo secondary = info_.getPeers().get(1);
			if (gm_.getLocalHost().equals(secondary.host) && gm_.getListeningPort() == secondary.listening_port) {
				gm_.promoteSecondary(this);
			} else {
				join(info_.getPeers().get(0).host, info_.getPeers().get(0).listening_port);
			}
		}
	}
	
	@Override
	public void handle(Player player, PlayerState state) {
		System.out.println("PlayerManager::handle() kPlayerState");
		for (PlayerState p : player_states_) {
			if (p.host.equals(state.host) && p.listening_port == state.listening_port) {
				p.last_seq_num = state.last_seq_num;
				p.treasure = state.treasure;
				p.x = state.x;
				p.y = state.y;
				System.out.format(" -> %s\n", p);
				return;
			}
		}
		System.out.format("  => %s\n", state);
		player_states_.add(state);
	}
	
	@Override
	public void handle(MazeStateMsg msg) {
		System.out.println("PlayerManager::handle() kMazeState");
		msg.setPlayground(playground_);
		msg.deserialize();
	}
	
	@Override
	public void handle(Player player, MoveMsg msg) {
		System.out.println("PlayerManager::handle() unexpected kMove");
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
			join(primary.host, primary.listening_port);
		}
	}
	
	@Override
	public void onJoined(Player player) {
		System.out.println("PlayerManager::onJoined() WRN: unexpected");
	}
	
	@Override
	public void move(char direction) {
		if (primary_ != null) {
			primary_.getConnection().write(new MoveMsg(direction));
		}
	}
	
	public void join(String host, int port) {
		System.out.println("PlayerManager::join()");
		if (primary_ == null) {
			primary_ = gm_.connect(host, port);
			if (primary_ == null) {
				gm_.stop();
			} else {
				PlayerJoinMsg msg = new PlayerJoinMsg(gm_.getPlayerId(), gm_.getLocalHost(), gm_.getListeningPort(),
						cur_seq_num_);
				history_.put(cur_seq_num_, new PlayerJoinMsg(msg));
				primary_.getConnection().write(msg);
			}
		}
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
		join(info_.getPeers().get(0).host, info_.getPeers().get(0).listening_port);
	}
	

	public void handle(InfoMsg info) {
		System.out.println("SecondaryManager::handle() kInfo");
		
		info_ = info;
		System.out.format("  N[%s] K[%s]\n", info_.getN(), info_.getK());
		for (TrackerPeerInfo peer : info_.getPeers()) {
			System.out.format("    peer host[%s] port[%s]\n", peer.host, peer.listening_port);
		}
	}
	
	public void onDisconnected(Player player) {
		if (player == primary_) {
			TrackerPeerInfo primary = info_.getPeers().get(0);
			info_.removePeer(primary.host, primary.listening_port);
			player_states_.remove(player.getState());
			gm_.promotePrimary(this);
			primary_ = null;
		}
	}
}
