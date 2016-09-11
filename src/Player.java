import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Player implements ConnectionListenerI, Runnable {
	public Player(Connection connection, GameManager gm) {
		connection_ = connection;
		gm_ = gm;
		connection.set_listener(this);
		msg_queue_ = new LinkedBlockingQueue<ByteBuffer>();
	}

	public void start() {
		running_ = true;
		thread_ = new Thread(this);
		thread_.start();
	}

	public void stop() {
		running_ = false;
		try {
			connection_.getSocket().close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			thread_.join(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDisconnected(Connection connection) {
		gm_.onDisconnected(this);
	}

	@Override
	public void onData(Connection connection, ByteBuffer buffer) {
		System.out.println("Player::onData()");
		try {
			msg_queue_.put(buffer);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		System.out.println("Player::run() started");
		while (running_) {
			try {
				ByteBuffer buffer = msg_queue_.poll(1000, TimeUnit.MILLISECONDS);
				if (buffer != null) {
					MsgType msg_type = MsgType.values()[buffer.getInt()];
					System.out.format("Player::run() msg_type[%s]\n", msg_type);
					switch (msg_type) {
					case kInfo:
						gm_.handle(this, new InfoMsg(buffer));
						break;
					case kJoin:
						gm_.handle(this, new JoinMsg(buffer));
						break;
					case kPlayerState:
						gm_.handle(this, new PlayersStateMsg(buffer));
						break;
					case kMazeState:
						gm_.handle(this, new MazeStateMsg(buffer));
						break;
					case kMove:
						gm_.handle(this, new MoveMsg(buffer));
						break;
					default:
						System.out.format("Player::onData() unhandled msg_type[%s]\n", msg_type);
						break;
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Player::run() stopped");
	}

	public void setState(PlayerState state) {
		state_ = state;
	}

	public Connection getConnection() {
		return connection_;
	}

	public PlayerState getState() {
		return state_;
	}

	private Connection connection_;
	private GameManager gm_;
	private PlayerState state_;

	private BlockingQueue<ByteBuffer> msg_queue_;

	private volatile boolean running_;
	private Thread thread_;
}
