import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class GameUI extends JFrame implements PlaygroundListenerI {
	private static final long serialVersionUID = -4407424276798218633L;

	GameInfoPanel infoPanel;
	final JPanel playerListPanel = new JPanel();
	ControlPanel controlPanel = new ControlPanel();
	GameBoard board;

	public GameUI(int N, String player_id) {
		super("Maze Game");
		setSize(420 + 37 * N, 220 + 37 * N);
		setResizable(false);
		
		board = new GameBoard(N, 37 * N, 37 * N);
		infoPanel = new GameInfoPanel(player_id);
		
		infoPanel.setBackground(Color.CYAN);
		playerListPanel.setBackground(Color.BLUE);
		controlPanel.setBackground(Color.green);

		infoPanel.setSize(420 + 37 * N, 100);
		playerListPanel.setSize(200, 20 + 37 * N);
		controlPanel.setSize(420 + 37 * N, 100);

		add(infoPanel);
		add(playerListPanel);
		add(board);
		add(controlPanel);

		getContentPane().setLayout(null);
		infoPanel.setLocation(0, 0);
		playerListPanel.setLocation(220 + 37 * N, 100);
		board.setLocation(110, 110);
		controlPanel.setLocation(0, 120 + 37 * N);

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
				// revalidate();
			}
		});
	}
}