import javax.swing.*;
import java.awt.*;

public class GameBoard extends JPanel {
	private static final long serialVersionUID = -4806535720184807495L;

	// set initial board size, width and height
	public GameBoard(int boardSize, int width, int height, int[] treasureList) {
		setSize(width, height);
		setBackground(Color.MAGENTA);
		setLayout(new GridLayout(boardSize, boardSize));

		JLabel grid[] = new JLabel[boardSize * boardSize];
		for (int i = 0; i < (boardSize * boardSize); i++) {
			grid[i] = new JLabel();
			grid[i].setBorder(BorderFactory.createLineBorder(Color.BLACK));

			for (int x : treasureList) {
				if (x == i) {
					grid[i].setText("X");
					grid[i].setHorizontalAlignment(JLabel.CENTER);
				}
			}

			add(grid[i]);
		}
	}
}