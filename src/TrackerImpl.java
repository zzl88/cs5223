import java.nio.ByteBuffer;
import java.util.ArrayList;

public class TrackerImpl implements ServerSocketListenerI, ConnectionListenerI {
	public TrackerImpl(int listening_port, int N, int K) {
		info_ = new InfoMsg(N, K);
		info_.serialize();
		server_ = new ConnectionManager(listening_port, this);
		connections_ = new ArrayList<Connection>();
	}

	public boolean start() {
		return server_.start();
	}

	public void stop() {
		server_.stop();
	}

	@Override
	public void onAccepted(Connection connection) {
		connections_.add(connection);
		connection.set_listener(this);
		System.out.format("TrackerImpl::onAccepted() Client accepted count[%s]\n", connections_.size());
	}

	@Override
	public void onDisconnected(Connection connection) {
		server_.close(connection);
		connections_.remove(connection);
		System.out.format("TrackerImpl::OnDisconnected() Client disconnected count[%s]\n", connections_.size());
	}

	@Override
	public void onData(Connection connection, ByteBuffer buffer) {
		buffer.getInt(); // len
		MsgType msg_type = MsgType.values()[buffer.getInt()];
		switch (msg_type) {
		case kJoin:
			JoinMsg msg0 = new JoinMsg(buffer);
			if (msg0.deserialize()) {
				if (!info_.addPeer(msg0.getHost(), msg0.getListeningPort())) return;
				
				System.out.format("TrackerImpl::OnData() kJoin remote[%s] peer[%s:%s]\n", connection.getRemoteAddress(),
						msg0.getHost(), msg0.getListeningPort());
				for (TrackerPeerInfo peer : info_.getPeers()) {
					System.out.format("    peer host[%s] port[%s]\n", peer.host, peer.listening_port);
				}
				connection.write(info_);
				
				if (connection != connections_.get(0)) {
					connections_.get(0).write(info_);
				}
			}
			break;
		case kPeerQuit:
			PeerQuitMsg msg1 = new PeerQuitMsg(buffer);
			if (msg1.deserialize()) {
				info_.removePeer(msg1.getHost(), msg1.getListeningPort());
				System.out.format("TrackerImpl::OnData() kPeerQuit remote[%s] peer[%s:%s] count[%s]\n",
						connection.getRemoteAddress(), msg1.getHost(), msg1.getListeningPort(),
						info_.getPeers().size());
				for (TrackerPeerInfo peer : info_.getPeers()) {
					System.out.format("    peer host[%s] port[%s]\n", peer.host, peer.listening_port);
				}
				connection.write(info_);
			}
			break;
		default:
			break;
		}
	}

	InfoMsg info_;
	ConnectionManager server_;
	ArrayList<Connection> connections_;
}
