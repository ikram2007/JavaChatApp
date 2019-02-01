public class MainApp {
    private static final int PORT = 8899;
    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer(PORT);
        chatServer.start();
    }
}
