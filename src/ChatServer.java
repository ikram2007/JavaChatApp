import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


public class ChatServer extends Thread {
    private final int serverPort;

    private ArrayList<WorkerThread> workerList = new ArrayList<>();

    public ChatServer(int serverPort) {
        this.serverPort = serverPort;
    }

    public List<WorkerThread> getWorkerList() {
        return workerList;
    }

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(serverPort);
            while(true) {
                System.out.println("Waiting for client connections...");
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket);
                WorkerThread worker = new WorkerThread(this, clientSocket);
                workerList.add(worker);
                worker.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removeWorker(WorkerThread workerThread) {
        workerList.remove(workerThread);
    }
}
