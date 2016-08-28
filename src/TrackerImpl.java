import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class TrackerImpl implements AcceptListenerI, ConnectionListenerI {
	public TrackerImpl(int N, int K) {
		info_ = new InfoMsg(N, K);
		info_.serialize();
		connections_ = new ArrayList<Connection>();
	}

	@Override
	public boolean OnAccepted(Connection connection) {
		connections_.add(connection);
		connection.set_listener(this);
		System.out.println("TrackerImpl::OnAccepted() Client accepted count: " + connections_.size());
		connection.write(info_);
		return true;
	}

	@Override
	public void OnConnected(Connection connection) {
		System.out.println("TrackerImpl::OnConnected() WRN: Unexpected");
	}

	@Override
	public void OnDisconnected(Connection connection) {
		connections_.remove(connection);
		System.out.println("TrackerImpl::OnDisconnected() Client disconnected count: " + connections_.size());
	}

	@Override
	public void OnMessage(Connection connection, ByteBuffer buffer) {
		MsgType msg_type = MsgType.values()[buffer.getInt()];
		switch (msg_type) {
		case kInfo:
			InfoMsg msg = new InfoMsg(buffer);
			if (msg.deserialize()) {
				info_ = msg;
				try {
					System.out.println("TrackerImpl::OnMessage() Updated game info from "
							+ connection.socket().getRemoteAddress());
					for (TrackerPeerInfo peer : info_.getPeers()) {
						System.out.println(
								"TrackerImpl::OnMessage() peer host[" + peer.host + "] port[" + peer.port + "]");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			break;
		default:
			break;
		}
	}

	InfoMsg info_;
	ArrayList<Connection> connections_;
}
