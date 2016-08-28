import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public abstract class Message {	
	public Message(MsgType type) {
		type_ = type;
	}
	
	public ByteBuffer getBuffer() {
		return buffer_;
	}
	
	public void serialize() {
		buffer_ = ByteBuffer.allocate(1024 * 1024);
		buffer_.putInt(0);  // len
		buffer_.putInt(type_.ordinal());
		int size = 4;
		size += serializeImpl();
		buffer_.putInt(0, size);
		buffer_.flip();
	}
	
	public boolean deserialize() {
		return deserializeImpl();
	}
	
	protected abstract int serializeImpl();
	protected abstract boolean deserializeImpl();
	
	protected MsgType type_;
	protected ByteBuffer buffer_;
}

enum MsgType {
	kInfo, kPlayerJoin, kGameState
}

class MessageHelper {
	public static int putString(ByteBuffer buffer, String s) {
		buffer.putInt(s.length());
		for (char c : s.toCharArray()) {
			buffer.putChar(c);
		}
		return s.length() * 2 + 4;
	}

	public static String getString(ByteBuffer buffer) {
		int length = buffer.getInt();
		StringBuilder buf = new StringBuilder(length);
		for (int i=0; i<length; ++i) {
			buf.append(buffer.getChar());
		}
		return buf.toString();
	}
}

class TrackerPeerInfo {
	public String host;
	public int port;
	
	public int serialize(ByteBuffer buffer) {
		int size = MessageHelper.putString(buffer, host);
		buffer.putInt(port);
		size += 4;
		return size;
	}
	
	public boolean deserialize(ByteBuffer buffer) {
		try {
			host = MessageHelper.getString(buffer);
			port = buffer.getInt();
			return true;
		} catch (BufferUnderflowException ex) {
			ex.printStackTrace();
		}
		return false;
	}
}
class InfoMsg extends Message {
	public InfoMsg(int N, int K) {
		super(MsgType.kInfo);
		N_ = N;
		K_ = K;
		peers_ = new ArrayList<TrackerPeerInfo>();
	}
	
	public InfoMsg(ByteBuffer buffer) {
		super(MsgType.kInfo);
		buffer_ = buffer;
		peers_ = new ArrayList<TrackerPeerInfo>();
	}
	
	public int getN() {
		return N_;
	}
	
	public int getK() {
		return K_;
	}
	
	public ArrayList<TrackerPeerInfo> getPeers() {
		return peers_;
	}
	
	public void addPeer(String host, int port) {
		for (TrackerPeerInfo info : peers_) {
			if (info.host.equals(host) && info.port == port) 
				return;
		}
		
		System.out.println("TrackerPeerInfo::addPeer() host[" + host + "] port[" + port + "]"); 
		TrackerPeerInfo peer = new TrackerPeerInfo();
		peer.host = host;
		peer.port = port;
		peers_.add(peer);
	}
	
	public void removePeer(String host, int port) {
		for (TrackerPeerInfo info : peers_) {
			if (info.host.equals(host) && info.port == port) {
				peers_.remove(info);
				System.out.println("TrackerPeerInfo::removePeer() host[" + host + "] port[" + port + "]"); 
				return;
			}
		}
	}
	
	@Override
	protected int serializeImpl() {
		buffer_.putInt(N_);
		buffer_.putInt(K_);
		
		buffer_.putInt(peers_.size());
		int size = 3 * 4;
		for (TrackerPeerInfo peer : peers_) {
			size += peer.serialize(buffer_);
		}
		return size;
	}

	@Override
	protected boolean deserializeImpl() {
		if (buffer_.remaining() < 12) return false;
		N_ = buffer_.getInt();
		K_ = buffer_.getInt();
		
		int peer_count = buffer_.getInt();
		for (int i = 0; i < peer_count; ++i) {
			TrackerPeerInfo peer = new TrackerPeerInfo();
			if (peer.deserialize(buffer_)) {
				peers_.add(peer);
			}
		}
		return true;
	}
	
	private int N_;
	private int K_;
	private ArrayList<TrackerPeerInfo> peers_;
}

class PlayerJoinMsg extends Message {
	public PlayerJoinMsg(String id, String host, int port, int seq_num) {
		super(MsgType.kPlayerJoin);
		id_ = id;
		host_ = host;
		listening_port_ = port;
		seq_num_ = seq_num;
	}
	
	public PlayerJoinMsg(ByteBuffer buffer) {
		super(MsgType.kPlayerJoin);
		buffer_ = buffer;
	}
	
	public PlayerJoinMsg(PlayerJoinMsg msg) {
		super(MsgType.kPlayerJoin);
		id_ = msg.getId();
		listening_port_ = msg.getListeningPort();
		seq_num_ = msg.getSeqNum();
	}

	public String getId() { return id_; }
	public String getHost() { return host_; }
	public int getListeningPort() { return listening_port_; }
	public int getSeqNum() { return seq_num_; }

	@Override
	protected int serializeImpl() {
		int size = MessageHelper.putString(buffer_, id_);
		size += MessageHelper.putString(buffer_, host_);
		buffer_.putInt(listening_port_);
		buffer_.putInt(seq_num_);
		size += 2 * 4;
		return size;
	}

	@Override
	protected boolean deserializeImpl() {
		if (buffer_.remaining() < 4) return false;
		id_ = MessageHelper.getString(buffer_);
		host_ = MessageHelper.getString(buffer_);
		listening_port_ = buffer_.getInt();
		seq_num_ = buffer_.getInt();
		return true;
	}
	
	private String id_;
	private String host_;
	private int listening_port_;
	private int seq_num_;
}

class PeerState {
	public PeerState() {
		x = 0;
		y = 0;
		treasure = 0;
		last_seq_num = 0;
	}
	
	public PeerState(String id, String host, int listening_port) {
		this();
		this.id = id;
		this.host = host;
		this.port = listening_port;
	}
	
	public String id;
	public String host;
	public int port;
	public int x;
	public int y;
	public int treasure;
	public int last_seq_num;
	
	public int serialize(ByteBuffer buffer) {
		int size = MessageHelper.putString(buffer, id);
		size += MessageHelper.putString(buffer, host);
		buffer.putInt(x);
		buffer.putInt(y);
		buffer.putInt(treasure);
		buffer.putInt(last_seq_num);
		size += 4 * 4;
		return size;
	}
	
	public boolean deserialize(ByteBuffer buffer) {
		try {
			id = MessageHelper.getString(buffer);
			host = MessageHelper.getString(buffer);
			x = buffer.getInt();
			y = buffer.getInt();
			treasure = buffer.getInt();
			last_seq_num = buffer.getInt();
			return true;
		} catch (BufferUnderflowException ex) {
			ex.printStackTrace();
		}
		return false;
	}
}

class GameStateMsg extends Message {
	public GameStateMsg(ByteBuffer buffer) {
		super(MsgType.kGameState);
		buffer_ = buffer;
		peers_ = new ArrayList<PeerState>();
	}
	
	public GameStateMsg() {
		super(MsgType.kGameState);
	}
	
	public ByteBuffer getBuffer() {
		return buffer_;
	}
	
	@Override
	protected int serializeImpl() {		
		buffer_.putInt(peers_.size());
		int size = 1 * 4;
		for (PeerState peer : peers_) {
			size += peer.serialize(buffer_);
		}
		return size;
	}

	@Override
	protected boolean deserializeImpl() {
		if (buffer_.remaining() < 4) return false;	
		int peer_count = buffer_.getInt();
		for (int i = 0; i < peer_count; ++i) {
			PeerState peer = new PeerState();
			if (peer.deserialize(buffer_)) {
				peers_.add(peer);
			}
		}
		return true;
	}
	
	private ArrayList<PeerState> peers_;
}
