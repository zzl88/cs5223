
public class Player implements Runnable {
	public Player(Connection connection, PeerState state) {
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
	public PeerState getState() { return state_; }
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		System.out.println("Player::run() started");
		
		System.out.println("Player::run() stopped");
	}
	
	private Connection connection_;
	private PeerState state_;
	private volatile boolean running_;
	
	private Thread thread_;
}
