import javax.swing.*;

public class PlayerInfoPanel extends JPanel {
	private static final long serialVersionUID = 7279427805788120369L;

	JLabel playerNameLabel = new JLabel();
	JLabel treasureCollected = new JLabel();
	JLabel serverTypeLabel = new JLabel();
	String serverTypeString;

	public PlayerInfoPanel(String playerName, int treasure, int serverType) {
		playerNameLabel.setText(playerName);
		treasureCollected.setText(treasure + " Treasures Collected");

		switch (serverType) {
		case 1:
			serverTypeString = "Primary Server";
			break;
		case 2:
			serverTypeString = "Secondary Server";
			break;
		default:
			serverTypeString = "Normal Player";
		}

		serverTypeLabel.setText(serverTypeString);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(playerNameLabel);
		add(treasureCollected);
		add(serverTypeLabel);
	}
}
