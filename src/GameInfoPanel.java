import javax.swing.*;

public class GameInfoPanel extends JPanel{
    JLabel totalNumberOfPlayersLabel = new JLabel();

    public GameInfoPanel(int numOfPlayers)
    {
        totalNumberOfPlayersLabel.setText("Total number of players: " + numOfPlayers);
        add(totalNumberOfPlayersLabel);
    }
}
