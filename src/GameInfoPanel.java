import javax.swing.*;

public class GameInfoPanel extends JPanel {
	private static final long serialVersionUID = 533014938563604479L;
	JLabel totalNumberOfPlayersLabel = new JLabel();

	public GameInfoPanel(int numOfPlayers) {
		totalNumberOfPlayersLabel.setText("Total number of players: " + numOfPlayers);
		add(totalNumberOfPlayersLabel);
	}
}
