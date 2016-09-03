import java.util.concurrent.BlockingQueue;

public class Player implements Runnable {
	public Player(Connection connection, PlayerState state) {
		connection_ = connection;
		state_ = state;
		running_ = true;
	}
	
	public void start() {
		thread_ = new Thread(this);
		thread_.start();
	}
	
	public void stop() {
		running_ = false;
		try {
			thread_.join(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public Connection getConnection() { return connection_; }
	public PlayerState getState() { return state_; }
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		System.out.println("Player::run() started");
		while (running_) {
			try {
				MoveMsg msg = msg_queue_.take();
				if (msg.deserialize()) {
					System.out.format("    id[%s] move[%s]", state_.id, msg.getDirection());
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Player::run() stopped");
	}
	
	private Connection connection_;
	private PlayerState state_;
	private volatile boolean running_;
	private BlockingQueue<MoveMsg> msg_queue_;
	
	private Thread thread_;
}
