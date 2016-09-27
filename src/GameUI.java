import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class GameUI extends JFrame implements PlaygroundListenerI {
	private static final long serialVersionUID = -4407424276798218633L;

	GameInfoPanel infoPanel;
	final JPanel playerListPanel = new JPanel();
	GameBoard board;

	public GameUI(int N, String player_id) {
		super("Maze Game");
		setSize(150 + 30 * N, 50 + 30 * N);
		setResizable(false);

		board = new GameBoard(N, 30 * N, 30 * N);
		infoPanel = new GameInfoPanel(player_id);

		infoPanel.setBackground(Color.CYAN);
		playerListPanel.setBackground(Color.BLUE);

		infoPanel.setSize(150 + 30 * N, 20);
		playerListPanel.setSize(150, 30 * N);

		add(infoPanel);
		add(playerListPanel);
		add(board);

		getContentPane().setLayout(null);
		infoPanel.setLocation(0, 0);
		playerListPanel.setLocation(30 * N, 20);
		board.setLocation(0, 20);

		setVisible(true);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}

	@Override
	public void onUpdate(MazeStateMsg msg) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.out.println("GameUI::onUpdate(MazeStateMsg)");
				board.update(msg);
				board.refresh();
				revalidate();
			}
		});
	}

	@Override
	public void onUpdate(PlayersStateMsg msg) {
		final ArrayList<PlayerState> players = msg.clone();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.out.println("GameUI::onUpdate(PlayerStateMsg)");
				playerListPanel.removeAll();
				for (PlayerState ps : players) {
					PlayerInfoPanel playerInfoPanel = new PlayerInfoPanel("Player <" + ps.id + ">", ps.treasure, 0);
					playerInfoPanel.setBackground(Color.yellow);
					playerInfoPanel.setSize(150, 300);

					playerListPanel.add(playerInfoPanel);
				}
				board.update(players);
				revalidate();
			}
		});
	}
}