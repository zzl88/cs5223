import javax.swing.*;
import java.awt.*;
import java.util.Random;

public class GameUI extends JFrame {
	private static final long serialVersionUID = -4407424276798218633L;

	int[] treasure = { 1, 42, 70, 30, 22, 64 };

	GameInfoPanel infoPanel = new GameInfoPanel(3);
	JPanel playerListPanel = new JPanel();
	ControlPanel controlPanel = new ControlPanel();
	GameBoard board = new GameBoard(9, 380, 380, treasure);

	public static void main(String[] args) {
		new GameUI();
	}

	public GameUI() {
		super("Maze Game");
		setSize(800, 600);
		setResizable(false);

		infoPanel.setBackground(Color.CYAN);
		playerListPanel.setBackground(Color.BLUE);
		controlPanel.setBackground(Color.green);

		infoPanel.setSize(800, 100);
		playerListPanel.setSize(200, 400);
		controlPanel.setSize(800, 100);

		for (int i = 0; i < 3; i++) {
			Random rand = new Random();
			int n = rand.nextInt(50);

			PlayerInfoPanel playerInfoPanel = new PlayerInfoPanel("Player " + i, n, i);
			playerInfoPanel.setBackground(Color.yellow);
			playerInfoPanel.setSize(150, 300);

			playerListPanel.add(playerInfoPanel);
		}

		add(infoPanel);
		add(playerListPanel);
		add(board);
		add(controlPanel);

		getContentPane().setLayout(null);
		infoPanel.setLocation(0, 0);
		playerListPanel.setLocation(600, 100);
		board.setLocation(110, 110);
		controlPanel.setLocation(0, 500);

		setVisible(true);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}
}