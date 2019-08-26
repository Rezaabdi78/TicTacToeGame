import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.Executors;

public class TicTaacToeServer {
    public static void main(String[] args) throws Exception {
        try (var listener = new ServerSocket(58901)) {
            System.out.println("TicTacToe Server is Running...");
            var pool = Executors.newFixedThreadPool(200);
            while (true) {
                Game game = new Game();
                pool.execute(game.new Player(listener.accept(), 'X'));
                pool.execute(game.new Player(listener.accept(), 'O'));
            }
        }
    }
}

class Game {
    private Player[] board = new Player[9];

    Player CurrentPlayer;

    public Boolean hasWinner() {
        return (board[0] != null && board[0] == board[1] && board[0] == board[2]
                || board[3] != null && board[3] == board[4] && board[3] == board[5]
                || board[6] != null && board[6] == board[7] && board[6] == board[8]
                || board[0] != null && board[0] == board[6] && board[0] == board[3]
                || board[1] != null && board[1] == board[4] && board[1] == board[7]
                || board[2] != null && board[2] == board[5] && board[2] == board[8]
                || board[0] != null && board[0] == board[5] && board[0] == board[8]
                || board[2] != null && board[2] == board[5] && board[2] == board[7]
        );
    }

    public boolean boardFilledUp() {
        return Arrays.stream(board).allMatch(p -> p != null);
    }

    public synchronized void move(int location, Player player) {
        if (player != CurrentPlayer) {
            throw new IllegalStateException("Not your Turn!");
        } else if (player.opponent == null) {
            throw new IllegalStateException("You don't have an  opponent yet.");
        } else if (board[location] != null) {
            throw new IllegalStateException("Cell already Occupied");
        }
        board[location] = CurrentPlayer;
        CurrentPlayer = CurrentPlayer.opponent;
    }


    class Player implements Runnable {
        char mark;
        Player opponent;
        Socket socket;
        PrintWriter output;
        Scanner input;

        public Player(Socket socket, char mark) {
            this.socket = socket;
            this.mark = mark;
        }

        @Override
        public void run() {
            try {
                setup();
                processCommands();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (opponent != null && opponent.output != null) {
                    opponent.output.println("OTHER_PLAYER_LEFT");
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }

        private void setup() throws IOException {
            input = new Scanner(socket.getInputStream());
            output = new PrintWriter(socket.getOutputStream(), true);
            output.println("WELCOME " + mark);
            if (mark == 'X') {
                CurrentPlayer = this;
                output.println("MESSAGE waiting for your opponent to connect");
            } else {
                opponent = CurrentPlayer;
                opponent.opponent = this;
                opponent.output.println("MESSAGE your move");
            }
        }

        private void processCommands() {
            while (input.hasNextLine()) {
                var command = input.nextLine();
                if (command.startsWith("QUIT")) {
                    return;
                } else if (command.startsWith("MOVE")) {
                    processMoveCommand(Integer.parseInt(command.substring(5)));
                }
            }
        }

        private void processMoveCommand(int location) {
            try {
                move(location, this);
                output.println("VALID_MOVE");
                opponent.output.println("OPPONENT_MOVED " + location);
                if (hasWinner()) {
                    output.println("VICTORY");
                    opponent.output.println("DEFEAT");
                } else if (boardFilledUp()) {
                    output.println("TIE");
                    opponent.output.println("TIE");
                }
            } catch (IllegalStateException e) {
                output.println("MESSAGE" + e.getMessage());
            }
        }
    }
}
