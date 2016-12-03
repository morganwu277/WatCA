package ca.uwaterloo.watca;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class RealtimeMain {
  static boolean javaControl;
  int portYCSB;
  String hostYCSB;
  private RealtimeAnalyzer ana;

  public RealtimeMain() {
    ana = new RealtimeAnalyzer();
  }

  // Params: $ServerLogPort $WebPort localhost 12347 javaControl
  public static void main(String[] args) throws Exception {
    if (args.length == 5 && args[4].equals("javaControl")) {
      javaControl = true;
    } else {
      javaControl = false;
    }
    RealtimeMain m = new RealtimeMain();
    m.listen(Integer.parseInt(args[0]), Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]));
  }

  public void listen(int serverLogPort, int webPort, String ycsbHost, int ycsbPort) throws IOException {
    portYCSB = ycsbPort;
    hostYCSB = ycsbHost;
    WebServer ws = new WebServer(webPort, ana);
    ws.setOldVars(portYCSB, hostYCSB, javaControl);
    ws.start();

    LogServer server = new LogServer(serverLogPort);
    server.start();
  }

  private class LogServer {
    private int port;
    private ServerSocket listener;

    public LogServer(int port) throws IOException {
      this.port = port;
      this.listener = new ServerSocket(port);
    }

    public void start() throws IOException {
      try {
        while (true) {
          new Handler(listener.accept()).start();
        }
      } catch (Exception e) {
        System.err.println("Error: " + e);
      } finally {
        this.listener.close();
      }
    }
  }

  private class Handler extends Thread {
    private Socket socket;

    public Handler(Socket socket) {
      this.socket = socket;
    }

    public void run() {
      System.out.println("Accepted connection: " + socket);
      try {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        while (true) {
          String line = in.readLine();
          if (line == null) {
            break;
          }
          ana.processOperation(new Operation(line));
        }
      } catch (IOException e) {
        System.err.println("Error: " + e);
      } finally {
        try {
          socket.close();
        } catch (IOException e) {
          System.err.println("Error: " + e);
        }
      }
      System.out.println("Done handling connection: " + socket);
    }
  }

}
