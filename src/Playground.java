import java.nio.ByteBuffer;

class Node {
	public Node(int x0, int y0) {
		x = x0;
		y = y0;
		treasure = 0;
		player = null;
	}
	
	public final int x;
	public final int y;
	public int treasure;
	public Player player;
}

public class Playground {
	public Playground(int N, int K) {
		N_ = N;
		K_ = K;
		yard_ = new Node[N][N];
		for (int i = 0; i < N; ++i) {
			for (int j = 0; j < N; ++j) {
				yard_[i][j] = new Node(i, j);
			}
		}
		
		for (int i = 0; i < K_; ++i) {
			int j = (int)(Math.random() * N_ * N_);
			++yard_[j/N_][j%N_].treasure;
		}
	}
	
	public void setPlayer(Player player) {
		while (player.getState().x != -1) {
			int j = (int)(Math.random() * N_ * N_);
			int x = j % N_;
			int y = j / N_;
			if (yard_[y][x].player == null) {
				enter(player, yard_[y][x]);
				System.out.format("Playground::setPlayer() %s\n", player.getState());
				break;
			}
		}
	}
	
	public boolean moveLeft(Player player) {
		setPlayer(player);
		PlayerState state = player.getState(); 
		if (state.x == 0) {
			System.out.format("Playground::moveLeft() invalid move(<0) player[%s]\n", state.id);
			return false;
		}
		if (yard_[state.x - 1][state.y].player != null) {
			System.out.format("Playground::moveLeft() invalid move(O) player[%s]\n", state.id);
			return false;
		}
		enter(player, yard_[state.x - 1][state.y]);
		System.out.format("Playground::moveLeft() %s", state);
		return true;
	}
	
	public boolean moveRight(Player player) {
		setPlayer(player);
		PlayerState state = player.getState();
		if (state.x >= N_ - 1) {
			System.out.format("Playground::moveRight() invalid move(>N) player[%s]\n", state.id);
			return false;
		}
		if (yard_[state.x + 1][state.y].player != null) {
			System.out.format("Playground::moveRight() invalid move(O) player[%s]\n", state.id);
			return false;
		}
		enter(player, yard_[state.x + 1][state.y]);
		System.out.format("Playground::moveRight() %s", state);
		return true;
	}
	
	public boolean moveUp(Player player) {
		setPlayer(player);
		PlayerState state = player.getState();
		if (state.y == 0) {
			System.out.format("Playground::moveUp() invalid move(<0) player[%s]\n", state.id);
			return false;
		}
		if (yard_[state.x][state.y - 1].player != null) {
			System.out.format("Playground::moveUp() invalid move(O) player[%s]\n", state.id);
			return false;
		}
		enter(player, yard_[state.x][state.y - 1]);
		System.out.format("Playground::moveUp() %s", state);
		return true;
	}
	
	public boolean moveDown(Player player) {
		setPlayer(player);
		PlayerState state = player.getState();
		if (state.y >= N_ - 1) {
			System.out.format("Playground::moveDown() invalid move(>N) player[%s]\n", state.id);
			return false;
		}
		if (yard_[state.x][state.y + 1].player != null) {
			System.out.format("Playground::moveDown() invalid move(O) player[%s]\n", state.id);
			return false;
		}
		enter(player, yard_[state.x][state.y + 1]);
		System.out.format("Playground::moveDown() %s", state);
		return true;
	}

	public void serialize(ByteBuffer buffer) {
		for (int i = 0; i < N_; ++i) {
			for (int j = 0; j < N_; ++j) {
				buffer.putInt(yard_[i][j].treasure);
			}
		}
	}
	
	public void deserialize(ByteBuffer buffer) {
		for (int i = 0; i < N_; ++i) {
			for (int j = 0; j < N_; ++j) {
				yard_[i][j].treasure = buffer.getInt();
			}
		}
	}
	
	private void enter(Player player, Node node) {
		player.getState().x = node.x;
		player.getState().y = node.y;
		player.getState().treasure += node.treasure;
		
		for (int i = 0; i < node.treasure;) {
			int j = (int)(Math.random() * N_ * N_);
			Node node0 = yard_[j/N_][j%N_];
			if (node0.player == null && node0.x != node.x && node0.y != node.y) {
				++node0.treasure;
				++i;
			}
		}
		node.player = player;
		node.treasure = 0;
	}
	
	private int N_;
	private int K_;
	private Node[][] yard_;
}
