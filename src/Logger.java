
public class Logger {
	public static void log(String line) {
		System.out.format("%s %s", System.currentTimeMillis(), line);
	}
}
