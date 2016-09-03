import java.nio.ByteBuffer;

public class Player implements ConnectionListenerI {
	public Player(Connection connection, GameManager gm) {
		connection_ = connection;
		gm_ = gm;
		connection.set_listener(this);
	}
	
	@Override
	public void onDisconnected(Connection connection) {
		connection_.stop();
		gm_.onDisconnected(this);
	}

	@Override
	public void onData(Connection connection, ByteBuffer buffer) {
		System.out.println("Player::onData()");
		MsgType msg_type = MsgType.values()[buffer.getInt()];
		switch (msg_type) {
		case kInfo:
			gm_.handle(this, new InfoMsg(buffer));
			break;
		case kPlayerJoin:
			gm_.handle(this, new PlayerJoinMsg(buffer));
			break;
		case kPlayersState:
			break;
		default:
			System.out.format("Player::onData() unhandled msg_type[%s]\n", msg_type);
			break;
		}
	}
	
	public void setState(PlayerState state) { state_ = state; }
	public Connection getConnection() { return connection_; }
	public PlayerState getState() { return state_; }
	
	private Connection connection_;
	private GameManager gm_;
	private PlayerState state_;
}
