import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

interface ConnectionListenerI {
	public abstract void onDisconnected(Connection connection);
	public abstract void onData(Connection connection, ByteBuffer buffer);
}

public class Connection implements Runnable {
	public Connection(SocketChannel socket) {
		socket_ = socket;
		try {
			remote_host_ = ((InetSocketAddress)socket_.getRemoteAddress()).getHostString();
			remote_port_ = ((InetSocketAddress)socket_.getRemoteAddress()).getPort();
		} catch (IOException e) {
			e.printStackTrace();
		}		
		running_ = false;
		read_buffer_ = ByteBuffer.allocate(1024 * 1024);
	}
	
	public Connection(String remote_host, int remote_port) {
		socket_ = null;
		remote_host_ = remote_host;
		remote_port_ = remote_port;
		running_ = false;
		read_buffer_ = ByteBuffer.allocate(1024 * 1024);
	}

	public void set_listener(ConnectionListenerI listener) {
		listener_ = listener;
	}
	
	public String getRemoteHost() { return remote_host_; }
	public int getRemotePort() { return remote_port_; }
	public String getRemoteAddress() { return String.format("%s:%s", getRemoteHost(), getRemotePort()); }
	
	public boolean start() {
		running_ = true;
		
		if (socket_ == null) {
			try {
				socket_ = SocketChannel.open(new InetSocketAddress(remote_host_, remote_port_));
				System.out.format("Connection::start() connected to remote[%s]\n", getRemoteAddress());
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}

		thread_ = new Thread(this);
		thread_.start();
		return true;
	}
	
	public void stop() {
		running_ = false;
		try {
			socket_.close();
			thread_.join(1000);
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}

	public void write(Message msg) {
		msg.serialize();
		try {
			System.out.format("Connection::write() size[%s] remote[%s] %s\n", 
					msg.getBuffer().remaining(), getRemoteAddress(), msg);
			while (msg.getBuffer().hasRemaining()) {
				socket_.write(msg.getBuffer());
			}
		} catch (IOException e) {
			System.out.println("Connection::write() failed");
		}
	}
	
	@Override
	public void run() {
		System.out.println("Connection::run() started");
		while (running_) {
			read();
		}
		System.out.println("Connection::run() stopped");
	}
	
	private void read() {
		int num = -1;
		try {
			num = socket_.read(read_buffer_);
		} catch (IOException ex) {
			// ex.printStackTrace();
		}

		if (!running_) return;
		
		if (num == 0) {
			return;
		} else if (num == -1) {
			System.out.format("Connection::read() connection closed by remote[%s]\n", getRemoteAddress());
			if (listener_ != null) listener_.onDisconnected(this);
			running_ = false;
		} else {
			System.out.format("Connection::read() size[%s] remote[%s]\n", num, getRemoteAddress());

			read_buffer_.rewind();
			while (read_buffer_.position() < num) {
				int message_len = read_buffer_.getInt();
				System.out.format("Connection::read() message_len[%s]\n", message_len);
				if (message_len == 0 || message_len + read_buffer_.position() - 4 > num)
					break;

				byte[] buffer = new byte[message_len - 4];
				read_buffer_.get(buffer, 0, message_len - 4);

				System.out.format("Connection::read() remaining[%s]\n", (num - read_buffer_.position()));
				if (listener_ != null) listener_.onData(this, ByteBuffer.wrap(buffer));
			}
			if (read_buffer_.position() < num) {
				int i = 0;
				for (i = 0; i < num - read_buffer_.position(); ++i) {
					read_buffer_.put(0, read_buffer_.get(read_buffer_.position() + i));
				}
				read_buffer_.position(i);
			} else {
				read_buffer_.rewind();
			}
			
			System.out.format("Connection::read() buffer_position[%s]\n", read_buffer_.position());
		}
	}
	
	private SocketChannel socket_;
	private String remote_host_;
	private int remote_port_;
	private volatile boolean running_;
	private ByteBuffer read_buffer_;

	private ConnectionListenerI listener_;
	
	private Thread thread_;
}
