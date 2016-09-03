import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class TrackerImpl implements AcceptListenerI, ConnectionListenerI {
	public TrackerImpl(int N, int K) {
		info_ = new InfoMsg(N, K);
		connections_ = new ArrayList<Connection>();
	}

	@Override
	public boolean OnAccepted(Connection connection) {
		connections_.add(connection);
		connection.set_listener(this);
		System.out.format("TrackerImpl::OnAccepted() Client accepted count[%s]\n", connections_.size());
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
		System.out.format("TrackerImpl::OnDisconnected() Client disconnected count[%s]\n", connections_.size());
		if (connections_.isEmpty()) {
			info_.clearPeers();
			System.out.println("TrackerImpl::OnDisconnected() clear peers"); 
		}
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
					System.out.format("TrackerImpl::OnMessage() kInfo remote[%s]\n",
							connection.socket().getRemoteAddress());
					for (TrackerPeerInfo peer : info_.getPeers()) {
						System.out.format("    peer host[%s] port[%s]\n", peer.host, peer.port);
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
