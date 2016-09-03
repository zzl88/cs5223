import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

interface ConnectionListenerI {
	public abstract void OnDisconnected(Connection connection);

	public abstract void OnMessage(Connection connection, ByteBuffer buffer);
}

public class Connection {
	public Connection(ConnectionManager manager, SocketChannel socket) {
		manager_ = manager;
		socket_ = socket;
		read_buffer_ = ByteBuffer.allocate(1024 * 1024);
	}

	public void set_listener(ConnectionListenerI listener) {
		listener_ = listener;
	}

	public SocketChannel socket() {
		return socket_;
	}

	public void read() {
		int num = -1;
		try {
			num = socket_.read(read_buffer_);
		} catch (IOException ex) {
			// ex.printStackTrace();
		}

		if (num == -1) {
			try {
				System.out.println("Connection::read() connection closed by peer " + socket_.getRemoteAddress());
			} catch (IOException e) {
				e.printStackTrace();
			}
			listener_.OnDisconnected(this);
			close();
		} else {
			try {
				System.out.format("Connection::read() size[%s] from[%s]\n", num, socket_.getRemoteAddress());
			} catch (IOException e) {
				e.printStackTrace();
			}

			read_buffer_.rewind();
			while (read_buffer_.position() < num) {
				int message_len = read_buffer_.getInt();
				System.out.format("Connection::read() message_len[%s]\n", message_len);
				if (message_len == 0 || message_len + read_buffer_.position() > num)
					break;

				byte[] buffer = new byte[message_len];
				read_buffer_.get(buffer, 0, message_len);

				System.out.format("Connection::read() remaining[%s]\n", (num - read_buffer_.position()));
				listener_.OnMessage(this, ByteBuffer.wrap(buffer));
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

	public void write(Message msg) {
		msg.serialize();
		try {
			System.out.format("Connection::write() size[%s]\n", msg.getBuffer().remaining());
			while (msg.getBuffer().hasRemaining()) {
				socket_.write(msg.getBuffer());
			}
		} catch (IOException e) {
			// e.printStackTrace();
			System.out.println("Connection::write() failed");
		}
	}
	
	public void close() {
		manager_.close(this);
	}

	private ConnectionManager manager_;
	private SocketChannel socket_;
	private ByteBuffer read_buffer_;

	private ConnectionListenerI listener_;
}
