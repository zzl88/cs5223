
public class Tracker {
	public static void main(String[] args) {
		if (args.length != 3) {
			System.out.println("Usage: java Tracker [port] [N] [K]");
			return;
		}
		
		int port = Integer.parseInt(args[0]);
		int N = Integer.parseInt(args[1]);
		int K = Integer.parseInt(args[2]);
		
		TrackerImpl tracker = new TrackerImpl(N, K);
		ConnectionManager connection_manager = new ConnectionManager(port, tracker);
		
		if (!connection_manager.initialize()) return;
		
		Thread t = new Thread(connection_manager);
		t.start();
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				System.out.println("Tracker::main() !Interrupted!");
				connection_manager.stop();
				
				try {
					t.join(1000);
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		}));
	}
}
