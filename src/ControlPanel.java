import javax.swing.*;

public class ControlPanel extends JPanel{
    String[] directionText = {"West", "North", "East", "South"};
    JComboBox directionList = new JComboBox(directionText);
    JLabel directionLabel = new JLabel("Direction:");
    JButton move = new JButton("Move");

    public ControlPanel(){
        directionLabel.setBounds(10, 30, 60, 40);
        directionList.setBounds(80, 30, 60, 40);
        add(directionLabel);
        add(directionList);
        add(move);
    }

}
