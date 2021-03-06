import com.google.api.client.auth.oauth2.Credential;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CSVRepresentation {
    private final CSVAccess csvAccess;
    /**
     * A csv file is represented by a LinkedList of a LinkedList (CSVRow extends a LinkedList).
     */
    private LinkedList<CSVRow> rows;
    /**
     * Stack of all the commands done on the csv file (first in first out to undo). This will reset every time the user
     * restarts the program.
     */
    private final ArrayDeque<Command> commands = new ArrayDeque<>();
    /**
     * Object to perform edits
     */
    private final HandleCommand handleCommand = new HandleCommand(this);
    /**
     * Build user keystrokes to create a command.
     */
    private final CommandBuilder commandBuilder = new CommandBuilder(handleCommand);
    private final Head head;
    /**
     * Lock to prevent the screen from reading a not up to date version of the sheets if the user is connect to google
     * api. Add fairness to we are guarantee for the screen to update and not indefinitely waiting. This shouldn't
     * happen, but just to make sure.
     */
    private final Lock accessCSV = new ReentrantLock(true);

    public CSVRepresentation(CSVAccess csvAccess, Head head){
        this.head = head;
        this.csvAccess = csvAccess;
        this.rows = csvAccess.readCSV();
    }

    /**
     * Replaces the csv file on this program to another. This is used for the Google API because the Google Sheet is
     * the most recent one to user and more reliable (anything can happen to the client, but the host is pretty reliable)
     * @param newRows The csv to replace the one stored on this machine.
     */
    public void updateCSV(LinkedList<CSVRow> newRows){
        accessCSV.lock();
        rows = newRows;
        accessCSV.unlock();
    }

    /**
     * Gets the value of the cell at the given column and row position.
     * @param column Column of the cell
     * @param row Row of the cell
     * @return Value stored in that cell. Empty string if there is nothing stored.
     */
    public String getValue(int column, int row){
        try{
            accessCSV.lock();
            return rows.get(row).get(column).getData();
        }catch(IndexOutOfBoundsException e){
            // This means the cell doesn't exists, so we can just return an empty string
            return "";
        }finally {
            accessCSV.unlock();
        }
    }

    /**
     * Saves the CSV file onto disk using the original location provided by the user.
     */
    public void save(){
        accessCSV.lock();
        csvAccess.saveCSV(rows);
        accessCSV.unlock();
    }

    /**
     * Add a command done by the user onto the stack and do those edits onto the screen. Also tells the screen to
     * update to the latest changes.
     * @param command Command to perform.
     */
    public void pushCommand(Command command){
        // Add the command to the stack for undo
        commands.push(command);
        // Make the edit happen
        update(command.getColumn(), command.getRow(), command.getNewValue());

        // Screen update
        head.updateScreen();
    }

    /**
     * Update the cell at the given row and column to the new value provided. This will create any preceding row(s) or
     * previous column(s).
     * @param col Column of the cell to update the value
     * @param row Row of the cell to update the value
     * @param value New value to put in the cell
     */
    private void update(int col, int row, String value){
        accessCSV.lock();

        while(row >= rows.size()){
            rows.add(CSVRow.createEmptyRow());
        }
        rows.get(row).set(col, CSVNode.newInstance(value));

        // Need to update the google sheet if we are connected
        if(head.isConnectedToGoogle()){
            csvAccess.updateToGoogle(col, row, value);
        }

        accessCSV.unlock();
    }

    /**
     * Removes the last command done and reverts the changes. This does not add to the stack of commands, it undo and
     * the edit is lost forever. If there isn't an edit done, nothing happens.
     */
    public void undo(){
        Command lastEdit = commands.poll();
        // If there isn't a command to undo, don't do anything
        if(lastEdit == null){
            return;
        }
        update(lastEdit.getColumn(), lastEdit.getRow(), lastEdit.getOldValue());
    }

    /**
     * Updates the view, but sets the top left cell to be a certain cell.
     * @param column Column of the cell at the top left.
     * @param row Row of the cell at the top left.
     */
    public void show(int column, int row){
        head.updateScreen(column, row);
    }

    /**
     * Sets the amount of characters that can fit in one column.
     * @param size The amount of characters that can fit on one column.
     */
    public void resizeColumn(int size){
        head.resizeColumn(size);
    }

    public CommandBuilder getCommandBuilder(){
        return this.commandBuilder;
    }
}
