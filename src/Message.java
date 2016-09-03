import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

enum MsgType {
	kInfo, kPlayerJoin, kPlayerState, kMazeState, kMove
}

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
		serializeImpl();
		buffer_.putInt(0, buffer_.position());
		buffer_.flip();
	}
	
	public boolean deserialize() {
		try {
			deserializeImpl();
			return true;
		} catch (BufferUnderflowException ex) {
			ex.printStackTrace();
			return false;
		}
	}
	
	protected abstract void serializeImpl();
	protected abstract void deserializeImpl();
	
	protected MsgType type_;
	protected ByteBuffer buffer_;
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
	public int listening_port;
	
	public void serialize(ByteBuffer buffer) {
		MessageHelper.putString(buffer, host);
		buffer.putInt(listening_port);
	}
	
	public void deserialize(ByteBuffer buffer) {
		host = MessageHelper.getString(buffer);
		listening_port = buffer.getInt();
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
			if (info.host.equals(host) && info.listening_port == port) 
				return;
		}
		
		System.out.println("TrackerPeerInfo::addPeer() host[" + host + "] listening_port[" + port + "]"); 
		TrackerPeerInfo peer = new TrackerPeerInfo();
		peer.host = host;
		peer.listening_port = port;
		peers_.add(peer);
	}
	
	public void removePeer(String host, int port) {
		for (TrackerPeerInfo info : peers_) {
			if (info.host.equals(host) && info.listening_port == port) {
				peers_.remove(info);
				System.out.println("TrackerPeerInfo::removePeer() host[" + host + "] listening_port[" + port + "]"); 
				return;
			}
		}
	}
	
	public void clearPeers() {
		peers_.clear();
	}
	
	@Override
	protected void serializeImpl() {
		buffer_.putInt(N_);
		buffer_.putInt(K_);
		
		buffer_.putInt(peers_.size());
		for (TrackerPeerInfo peer : peers_) {
			peer.serialize(buffer_);
		}
	}

	@Override
	protected void deserializeImpl() {
		N_ = buffer_.getInt();
		K_ = buffer_.getInt();
		
		int peer_count = buffer_.getInt();
		for (int i = 0; i < peer_count; ++i) {
			TrackerPeerInfo peer = new TrackerPeerInfo();
			peer.deserialize(buffer_);
			peers_.add(peer);
		}
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
	protected void serializeImpl() {
		MessageHelper.putString(buffer_, id_);
		MessageHelper.putString(buffer_, host_);
		buffer_.putInt(listening_port_);
		buffer_.putInt(seq_num_);
	}

	@Override
	protected void deserializeImpl() {
		id_ = MessageHelper.getString(buffer_);
		host_ = MessageHelper.getString(buffer_);
		listening_port_ = buffer_.getInt();
		seq_num_ = buffer_.getInt();
	}
	
	private String id_;
	private String host_;
	private int listening_port_;
	private int seq_num_;
}

class PlayerState extends Message {
	public PlayerState() {
		super(MsgType.kPlayerState);
		x = -1;
		y = -1;
		treasure = 0;
		last_seq_num = 0;
	}
	
	public PlayerState(ByteBuffer buffer) {
		this();
		buffer_ = buffer;
	}
	
	public PlayerState(String id, String host, int listening_port) {
		this();
		this.id = id;
		this.host = host;
		this.listening_port = listening_port;
	}
	
	public String id;
	public String host;
	public int listening_port;
	public int x;
	public int y;
	public int treasure;
	public int last_seq_num;
	
	@Override
	public void serializeImpl() {
		MessageHelper.putString(buffer_, id);
		MessageHelper.putString(buffer_, host);
		buffer_.putInt(listening_port);
		buffer_.putInt(x);
		buffer_.putInt(y);
		buffer_.putInt(treasure);
		buffer_.putInt(last_seq_num);
	}
	
	@Override
	public void deserializeImpl() {
		id = MessageHelper.getString(buffer_);
		host = MessageHelper.getString(buffer_);
		listening_port = buffer_.getInt();
		x = buffer_.getInt();
		y = buffer_.getInt();
		treasure = buffer_.getInt();
		last_seq_num = buffer_.getInt();
	}
	
	public String toString() {
		return String.format("id[%s] host[%s] listening_port[%s] x[%s] y[%s] treasure[%s] last_seq_num[%s]", id, host,
				listening_port, x, y, treasure, last_seq_num);
	}
}

class MazeStateMsg extends Message {
	public MazeStateMsg(ByteBuffer buffer) {
		super(MsgType.kMazeState);
		buffer_ = buffer;
	}
	
	public MazeStateMsg(Playground playground) {
		super(MsgType.kMazeState);
		playground_ = playground;
	}
	
	public void setPlayground(Playground playground) { playground_ = playground; }
	
	@Override
	protected void serializeImpl() {
		playground_.serialize(buffer_);
	}
	@Override
	protected void deserializeImpl() {
		playground_.deserialize(buffer_);
	}
	
	private Playground playground_;
}

class MoveMsg extends Message {
	public MoveMsg(ByteBuffer buffer) {
		super(MsgType.kMove);
		buffer_ = buffer;
	}
	
	public MoveMsg(char direction) {
		super(MsgType.kMove);
		direction_ = direction;
	}
	
	char getDirection() { return direction_; }

	@Override
	protected void serializeImpl() {
		buffer_.putChar(direction_);
	}

	@Override
	protected void deserializeImpl() {
		direction_ = buffer_.getChar();
	}
	
	private char direction_;
}
