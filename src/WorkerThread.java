import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;


public class WorkerThread extends Thread {

    private String prompt = "\nchat";

    String commandPromptHelp = "\n\n--------------------------------------------------------------\n" +
            "Commands available:\n" +
            "Create user:\t\t$$create <username>\n" +
            "To Login enter:\t\t$$login <username>\n" +
            "To Logoff enter:\t$$logoff\n" +
            "After login, chat with:\t$$chat <To username> <message text>\n" +
            "\n--------------------------------------------------------------\n" +
            prompt + ">";

    Users user = new Users();

    private final Socket clientSocket;
    private final ChatServer chatServer;
    private String username = null;
    private OutputStream outputStream;
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss a");

    //getter function
    public String getUsername() {
        return username;
    }

    //Constructor
    public WorkerThread(ChatServer chatServer, Socket clientSocket) {
        this.chatServer = chatServer;
        this.clientSocket = clientSocket;
    }

    //run method
    @Override
    public void run() {
        try {
            handleClientSocket();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //this method handles all client-server communication
    private void handleClientSocket() throws IOException {
        outputStream = clientSocket.getOutputStream();
        outputStream.write(commandPromptHelp.getBytes());

        InputStream inputStream = clientSocket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        while ( (line = reader.readLine()) != null) {
            String[] tokens = StringUtils.split(line);
            if (tokens != null && tokens.length > 0) {
                String command = tokens[0];

                if (command.equalsIgnoreCase("$$create")) {
                    String msg;
                    if (tokens.length == 2) {
                        if(createUser(outputStream, tokens)){
                            msg = "Username successfully created: '" + tokens[1] + "'";
                        }
                        else{
                            msg = "Username '" + tokens[1] + "' already exists. Please try again.";
                        }
                        outputStream.write(msg.getBytes());
                        sendPrompt();
                    }
                }
                else if (command.equalsIgnoreCase("$$login")) {
                    if (tokens.length == 2) {
                        handleLogin(outputStream, tokens);
                    }
                }
                else if (command.equalsIgnoreCase("$$logoff")) {
                    handleLogoff();
                    break;
                }
                else if (command.equalsIgnoreCase("$$chat")) {
                    boolean userExists = checkUser(username);
                    if(userExists){
                        String[] tokensMsg = StringUtils.split(line, null, 3);
                        if(tokensMsg.length == 3){
                            handleChatMessage(tokensMsg);
                        }
                        else{
                            sendError("Invalid $$chat format. Please try again.");
                        }
                    }
                    else{
                        sendError("Chat is available only after login. Please check.");
                    }


                    sendPrompt();
                }
                else {
                    sendError(command);
                    sendPrompt();
                }

            }
            else{
                sendPrompt();
            }
        }

        clientSocket.close();
    }


    //check whether user exists or not
    boolean checkUser(String username){
        if(username != null){
            Iterator iterator = user.list.iterator();
            while (iterator.hasNext())
            {
                String user = (String)iterator.next();
                if(username.equalsIgnoreCase(user)){
                    return true;
                }
            }
        }

        return false;
    }


    //this method creates new users
    private boolean createUser(OutputStream outputStream, String[] tokens) throws IOException {
        String username = tokens[1];
        boolean userExists = checkUser(username);
        if(userExists){
            return false;
        }

        //add new username to in-memory list
        user.list.add(username);
        return true;
    }


    //this method handles login and sharing logged in user info with all current users
    private void handleLogin(OutputStream outputStream, String[] tokens) throws IOException {
        String username = tokens[1];
        boolean userExists = checkUser(username);

        if (userExists) {
            String msg = "Login successful\n";
            outputStream.write(msg.getBytes());
            this.username = username;
            System.out.println("User logged in succesfully: " + username + " at " + dateTimeFormatter.format(LocalDateTime.now()));

            List<WorkerThread> workerList = chatServer.getWorkerList();

            // sendMessage current user all other online logins
            for(WorkerThread workerThread : workerList) {
                if (workerThread.getUsername() != null) {
                    if (!username.equals(workerThread.getUsername())) {
                        sendMessage(workerThread.getUsername() + " is online now\n");
                    }
                }
            }

            // sendMessage other online users current user's status
            String onlineMsg = username + " is online now\n";
            for(WorkerThread workerThread : workerList) {
                if (workerThread.username != null &&  username != workerThread.getUsername()) {
                    workerThread.sendMessage(onlineMsg);
                    workerThread.sendPrompt();
                }
            }

            sendPrompt();

        } else {
            String msg = "Invalid login credentials. Please try again.\n";
            outputStream.write(msg.getBytes());
            sendPrompt();
        }
    }

    //this method handles logoff and sharing logged off user info with all current users
    private void handleLogoff() throws IOException {
        chatServer.removeWorker(this);
        List<WorkerThread> workerList = chatServer.getWorkerList();

        // sendMessage other online users current user's status
        String onlineMsg = username + " is offline now\n";
        for (WorkerThread workerThread : workerList) {
            if (!username.equals(workerThread.getUsername())) {
                workerThread.sendMessage(onlineMsg);
                workerThread.sendPrompt();
            }
        }

        System.out.println("User logged out succesfully: " + username + " at " + dateTimeFormatter.format(LocalDateTime.now()));

        clientSocket.close();
    }


    //this method delivers message from a client to the correct receiving client
    private void handleChatMessage(String[] tokens) throws IOException {
        boolean messageSent = false;
        String sendTo = tokens[1];
        String body = tokens[2];

        List<WorkerThread> workerList = chatServer.getWorkerList();
        for(WorkerThread workerThread : workerList) {
            if (sendTo.equalsIgnoreCase(workerThread.getUsername())) {
                String outMsg = "Message received from '" + username + "': " + body + "\n";
                workerThread.sendMessage(outMsg);
                workerThread.sendPrompt();
                messageSent = true;
            }
        }

        if(messageSent == false){
            String errorMsg = "Username '" + sendTo + "' not found. Please try again later.";
            outputStream.write(errorMsg.getBytes());
        }
    }


    //send messages from server to client output stream
    private void sendMessage(String msg) throws IOException {
        if (username != null) {
            outputStream.write(msg.getBytes());
        }
    }

    //send prompt to client
    private void sendPrompt() throws IOException {
        if(this.username != null){
            outputStream.write((prompt + '@' + this.username + ">").getBytes());
        }
        else{
            outputStream.write((prompt + ">").getBytes());
        }
    }

    //send error information to client
    private void sendError(String command) throws IOException {
        String strError = "Unknown command: " + command + "\n";
        outputStream.write(strError.getBytes());
    }


}
