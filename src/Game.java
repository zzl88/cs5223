
public class Game {
	public static void main(String[] args) {
		if (args.length != 3) {
			System.out.println("Usage: java Game [tracker_ip] [tracker_port] [id]");
			return;
		}

		String ip = args[0];
		int port = Integer.parseInt(args[1]);
		String id = args[2];
		
		GameManager manager = new GameManager(ip, port, id);
		ConnectionManager connection_manager = new ConnectionManager(0, manager);
		manager.setConnectionManager(connection_manager);
		
		if (!connection_manager.initialize()) return;
		manager.connect(ip, port);
		
		Thread t1 = new Thread(manager);
		t1.start();
		Thread t2 = new Thread(connection_manager);
		t2.start();

				
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				System.out.println("Game::main() !Interrupted!");
				connection_manager.stop();
				manager.stop();
				
				try {
					t1.join(1000);
					t2.join(1000);
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		}));
	}
}
