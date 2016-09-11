import java.util.HashMap;

public abstract class RoleManager {
	public RoleManager(GameManager gm) {
		gm_ = gm;
		cur_seq_num_ = 0;
		player_states_ = new PlayersStateMsg();
		history_ = new HashMap<Integer, Message>();
	}

	public void promote(RoleManager rm) {
		info_ = rm.getInfo();
		playground_ = rm.getPlayground();
		player_states_ = rm.getPlayerStates();
		cur_seq_num_ = rm.getCurSeqNum();
		history_ = rm.getHistory();
	}

	public InfoMsg getInfo() {
		return info_;
	}

	public int getCurSeqNum() {
		return cur_seq_num_;
	}

	public Playground getPlayground() {
		return playground_;
	}

	public PlayersStateMsg getPlayerStates() {
		return player_states_;
	}

	public HashMap<Integer, Message> getHistory() {
		return history_;
	}

	public abstract void handle(InfoMsg info);

	public abstract void handle(Player player, JoinMsg msg);

	public abstract void handle(Player player, PlayersStateMsg state);

	public abstract void handle(MazeStateMsg msg);

	public abstract void handle(Player player, MoveMsg msg);

	public abstract void onTrackerDown();

	public abstract void onDisconnected(Player player);

	public abstract void move(char direction);

	protected GameManager gm_;
	protected InfoMsg info_;

	protected Playground playground_;
	protected PlayersStateMsg player_states_;

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

		player_states_.addPlayer(self_);
		info_.addPeer(gm_.getLocalHost(), gm_.getListeningPort());
		if (gm_.connectTracker())
			gm_.getTracker().write(info_);
	}

	public void promote(SecondaryManager sm) {
		super.promote(sm);

		for (PlayerState state : player_states_.getPlayersState()) {
			playground_.setPlayer(state.x, state.y, state);
			if (state.host.equals(gm_.getLocalHost()) && state.listening_port == gm_.getListeningPort())
				self_ = state;
		}

		if (gm_.connectTracker())
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
	public void handle(Player player, PlayersStateMsg state) {
		System.out.println("PrimaryManager::handle() unexpected PlayersState");
	}

	@Override
	public void handle(MazeStateMsg msg) {
		System.out.println("PrimaryManager::handle() unexpected MazeStateMsg");
	}

	@Override
	public void handle(Player player, MoveMsg msg) {
		System.out.format("PrimaryManager::handle() MoveMsg player[%s] direction[%s]\n", player.getState().id,
				msg.getDirection());
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
		case '0':
			break;
		case '9':
			gm_.kickPlayer(player);
			return;
		default:
			break;
		}
		player.getState().last_seq_num = msg.getSeqNum();
		if (secondary_ != null && secondary_ != player) {
			secondary_.getConnection().write(player_states_);
			secondary_.getConnection().write(new MazeStateMsg(playground_));
		}
		player.getConnection().write(player_states_);
		player.getConnection().write(new MazeStateMsg(playground_));
	}

	@Override
	public void onDisconnected(Player player) {
		player_states_.removePlayer(player.getState());

		if (player == secondary_) {
			secondary_ = null;
			System.out.println("PrimaryManager::onDisconnected() secondary server down");
			if (info_.getPeers().size() >= 3) {
				TrackerPeerInfo secondary = info_.getPeers().get(2);
				Player player0 = gm_.getPlayer(secondary.host, secondary.listening_port);
				if (player0 != null) {
					secondary_ = player0;
					System.out.format(
							"PrimaryManager::onDisconnected() nominate new secondary server host[%s] port[%s]\n",
							secondary.host, secondary.listening_port);
				}
			}
		}

		playground_.setPlayer(player.getState().x, player.getState().y, null);

		PlayerState state = player.getState();
		if (state != null) {
			info_.removePeer(state.host, state.listening_port);
			gm_.getTracker().write(info_);
		}
		gm_.broadcast(info_);

		if (secondary_ != null) {
			secondary_.getConnection().write(player_states_);
			secondary_.getConnection().write(new MazeStateMsg(playground_));
		}
	}

	@Override
	public void handle(Player player, JoinMsg msg) {
		PlayerState state = new PlayerState(msg.getId(), msg.getHost(), msg.getListeningPort());
		player.setState(state);
		for (PlayerState p : player_states_.getPlayersState()) {
			if (p.host.equals(state.host) && p.listening_port == state.listening_port) {
				player.setState(p);
				System.out.format("PrimaryManager::onJoined() update %s", p);
				break;
			}
		}

		state = player.getState();
		if (state.x == -1) {
			playground_.initPlayer(state);
			player_states_.addPlayer(state);
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
		gm_.broadcast(info_);

		if (secondary_ != null) {
			secondary_.getConnection().write(player_states_);
			secondary_.getConnection().write(new MazeStateMsg(playground_));
		}

		player.getConnection().write(player_states_);
		player.getConnection().write(new MazeStateMsg(playground_));
	}

	@Override
	public void move(char direction) {
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
			gm_.stop();
			break;
		default:
			break;
		}
		++self_.last_seq_num;
		++cur_seq_num_;
		if (secondary_ != null) {
			secondary_.getConnection().write(player_states_);
			secondary_.getConnection().write(new MazeStateMsg(playground_));
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
			if (gm_.getLocalHost().equals(info_.getPeers().get(0).host)
					&& gm_.getListeningPort() == info_.getPeers().get(0).listening_port)
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
	public void handle(Player player, PlayersStateMsg msg) {
		System.out.println("PlayerManager::handle() kPlayerState");
		player_states_ = msg;
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
	public void onDisconnected(Player player) {
		player.stop();
		if (player == primary_) {
			primary_ = null;

			TrackerPeerInfo primary = info_.getPeers().get(1);
			join(primary.host, primary.listening_port);
		}
	}

	@Override
	public void handle(Player player, JoinMsg msg) {
		System.out.println("PlayerManager::handle() JoinMsg WRN: unexpected");
	}

	@Override
	public void move(char direction) {
		if (primary_ != null) {
			MoveMsg msg = new MoveMsg(direction, ++cur_seq_num_);
			primary_.getConnection().write(msg);
			history_.put(cur_seq_num_, msg);
		}
	}

	public void join(String host, int port) {
		System.out.println("PlayerManager::join()");
		if (primary_ == null) {
			primary_ = gm_.connect(host, port);
			if (primary_ == null) {
				gm_.stop();
			} else {
				JoinMsg msg = new JoinMsg(gm_.getPlayerId(), gm_.getLocalHost(), gm_.getListeningPort(), cur_seq_num_);
				history_.put(cur_seq_num_, new JoinMsg(msg));
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
		player.stop();
		if (player == primary_) {
			TrackerPeerInfo primary = info_.getPeers().get(0);
			info_.removePeer(primary.host, primary.listening_port);
			player_states_.removePlayer(player.getState());
			gm_.promotePrimary(this);
			primary_ = null;
		}
	}
}
