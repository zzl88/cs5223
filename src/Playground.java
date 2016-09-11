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
	public PlayerState player;
}

public class Playground {
	public Playground(int N, int K) {
		N_ = N;
		K_ = K;
		yard_ = new Node[N][N];
		for (int i = 0; i < N; ++i) {
			for (int j = 0; j < N; ++j) {
				yard_[i][j] = new Node(j, i);
			}
		}

		for (int i = 0; i < K_; ++i) {
			int j = (int) (Math.random() * N_ * N_);
			++yard_[j / N_][j % N_].treasure;
		}
	}

	public Node[][] getYard() {
		return yard_;
	}

	public void initPlayer(PlayerState player) {
		while (player.x == -1) {
			int j = (int) (Math.random() * N_ * N_);
			int x = j % N_;
			int y = j / N_;
			if (yard_[y][x].player == null) {
				enter(player, yard_[y][x]);
				System.out.format("Playground::initPlayer() %s\n", player);
				break;
			}
		}
	}

	public void setPlayer(int x, int y, PlayerState player) {
		yard_[y][x].player = player;
		System.out.format("Playground::setPlayer() x[%s] y[%s] %s\n", x, y, player);
	}

	public boolean moveWest(PlayerState player) {
		initPlayer(player);
		if (player.x == 0) {
			System.out.format("Playground::moveWest() invalid move(<0) player[%s]\n", player.id);
			return false;
		}
		if (yard_[player.y][player.x - 1].player != null) {
			System.out.format("Playground::moveWest() invalid move(O) player[%s]\n", player.id);
			return false;
		}
		enter(player, yard_[player.y][player.x - 1]);
		System.out.format("Playground::moveWest() %s\n", player);
		return true;
	}

	public boolean moveEast(PlayerState player) {
		initPlayer(player);
		if (player.x >= N_ - 1) {
			System.out.format("Playground::moveEast() invalid move(>N) player[%s]\n", player.id);
			return false;
		}
		if (yard_[player.y][player.x + 1].player != null) {
			System.out.format("Playground::moveEast() invalid move(O) player[%s]\n", player.id);
			return false;
		}
		enter(player, yard_[player.y][player.x + 1]);
		System.out.format("Playground::moveEast() %s\n", player);
		return true;
	}

	public boolean moveNorth(PlayerState player) {
		initPlayer(player);
		if (player.y == 0) {
			System.out.format("Playground::moveNorth() invalid move(<0) player[%s]\n", player.id);
			return false;
		}
		if (yard_[player.y - 1][player.x].player != null) {
			System.out.format("Playground::moveNorth() invalid move(O) player[%s]\n", player.id);
			return false;
		}
		enter(player, yard_[player.y - 1][player.x]);
		System.out.format("Playground::moveNorth() %s\n", player);
		return true;
	}

	public boolean moveSouth(PlayerState player) {
		initPlayer(player);
		if (player.y >= N_ - 1) {
			System.out.format("Playground::moveSouth() invalid move(>N) player[%s]\n", player.id);
			return false;
		}
		if (yard_[player.y + 1][player.x].player != null) {
			System.out.format("Playground::moveSouth() invalid move(O) player[%s]\n", player.id);
			return false;
		}
		enter(player, yard_[player.y + 1][player.x]);
		System.out.format("Playground::moveSouth() %s\n", player);
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
				// System.out.print(yard_[i][j].treasure + " ");
			}
			// System.out.println();
		}
	}

	private void enter(PlayerState player, Node node) {
		if (player.x != -1 && player.y != -1)
			yard_[player.y][player.x].player = null;

		player.x = node.x;
		player.y = node.y;
		player.treasure += node.treasure;

		for (int i = 0; i < node.treasure;) {
			int j = (int) (Math.random() * N_ * N_);
			Node node0 = yard_[j / N_][j % N_];
			if (node0.player == null && node0.x != node.x && node0.y != node.y) {
				++node0.treasure;
				++i;
			}
		}
		node.player = player;
		node.treasure = 0;

		/*
		 * for (int i = 0; i < N_; ++i) { for (int j = 0; j < N_; ++j) {
		 * System.out.print(yard_[i][j].treasure + " "); } System.out.println();
		 * }
		 */
	}

	private int N_;
	private int K_;
	private Node[][] yard_;
}
