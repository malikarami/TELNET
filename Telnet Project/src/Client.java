import com.sun.net.ssl.*;
import com.sun.net.ssl.internal.ssl.Provider;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static java.lang.Thread.sleep;

public class Client {
    private static final int PORT_T = 23 , MAX_TRANSFER_SIZE = 8192, PORT_E = 44300;
    private static final int SEND = 1, FILE_TRANSFER = 2, LOGOUT = 3, EXEC = 4, SEND_E = 5;
    private static Scanner scan = new Scanner(System.in);
    private boolean logout = false, first = true, noTLS = true;
    private String hostIP;
    private int hostPort;
    private PrintStream clientWrites;
    private BufferedReader clientReads;
    private DataOutputStream outputStream;
    private ArrayList<String> cmdHistory = new ArrayList();
    private String EMSG;

    public Client() throws IOException {

        while (true) {
            System.out.println("    - Enter ");
            System.out.println("            [0] = exit ");
            System.out.println("            [1] = connect to a telnet host ");
            System.out.println("            [2] = connect to any host ");
            System.out.println("            [3] = find open ports of an IP address ");
            int c = Integer.parseInt(scan.nextLine().split(" ")[0]); //todo check if integer
            if ( c == 0 )
                return;
            else if ( c == 1 ) {
                System.out.println("    - Enter Host IP");
                hostIP = scan.nextLine();
                hostPort = 23;
                first = true;
                logout = false;
                setUpTelnetClient(); // goes onto telnet and runs a loop until logout
            }
            else if ( c == 3 ) {
                System.out.println("\t- Enter IP Address");
                String st = scan.nextLine();
                int start;
                int end;
                System.out.println("\t- choose port range ");
                System.out.print("\t- start from: ");
                start = Integer.parseInt(scan.nextLine());
                System.out.print("\tto: ");
                end = Integer.parseInt(scan.nextLine());
                portScanner(st, start, end);
            }
            else if ( c == 2 ){
                System.out.println("    - Enter Host IP");
                hostIP = scan.nextLine();
                System.out.println("    - Enter Host Port");
                hostPort = Integer.parseInt(scan.nextLine());
                setUpGeneralClient();
            }
            else
                System.out.println("   - Invalid input...");
        }
    }

    private void setUpGeneralClient() throws IOException {

        Socket socket = new Socket(hostIP, hostPort);

        PrintStream outputS = new PrintStream( socket.getOutputStream());
        BufferedReader inputS = new BufferedReader( new InputStreamReader( socket.getInputStream()));

        System.out.println("\t- Enter Commands ");
        System.out.println("\t- $$TERMINATE$$ will close the connection");
        while (scan.hasNext()){
            String s = scan.nextLine();
            if (s.equals("$$TERMINATE$$")){
                System.out.println("\t- Connection Terminated");
                outputS.close();
                inputS.close();
                socket.close();
            }
            outputS.println(s);
            outputS.flush();
        }

        String line = inputS.readLine();
        while( line != null ) {
            System.out.println( line );
            line = inputS.readLine();
        }

        outputS.close();
        inputS.close();
        socket.close();
    }


    private void setUpTelnetClient() throws IOException {
        loadHistory();
        String server = "127.0.0.1";
        while (true){
            if (noTLS){

                Socket socket = new Socket( server, PORT_T );
                outputStream = new DataOutputStream(socket.getOutputStream());
                clientWrites = new PrintStream( socket.getOutputStream());
                clientReads = new BufferedReader( new InputStreamReader( socket.getInputStream()));
                clientWrites.println( "Hello Server" );

                if (hostPort == 23){
                    if (clientReads.readLine().equals("Hello Client")){
                        if (first){
                            System.out.println("    - Connection Established with" + hostIP + ":" + hostPort);
                            System.out.println("    - You may now use Telnet Commands");
                            first = false;
                        }
                        telnetClient();
                    }
                    else{
                        System.out.println("\t- ERROR! Try Again");
                        return;
                    }
                }

                clientWrites.println("3goodbye server");
                if (!clientReads.readLine().equals("goodbye client"))
                    return;

                clientReads.close();
                clientWrites.close();
                outputStream.close();
                socket.close();

            }
            if (!noTLS){
                System.out.println("\t- Secure Connection Setup");
                encrypt(EMSG);
                noTLS = true;
            }
            if (logout){
                System.out.println("\t[Telnet] : Client Logged Out");
                return;
            }
        }

    }


    private void telnetClient() throws IOException {

        //todo if response is $$$$$ guide client to reenter the command
        while (true){
            String commandText = scan.nextLine();
            if (Pattern.compile("telnet send -e (.*)", Pattern.CASE_INSENSITIVE).matcher(commandText).matches()) {
                String emsg[] = commandText.split("(?i)send -e ");
                clientWrites.println(SEND_E);
                String response = clientReads.readLine();
                if (!(response.equals("$$$$$$"))){
                    noTLS = Boolean.parseBoolean(response);
                    EMSG = emsg[1];
                }
                else
                    System.out.println("\tSomething went wrong");
                cmdHistory.add(commandText);
                saveHistory();
                return;
            }
            else if (Pattern.compile("telnet send (.*)", Pattern.CASE_INSENSITIVE).matcher(commandText).matches()) {
                String msg[] = commandText.split("(?i)send ");
                clientWrites.println(SEND+msg[1]);
                String response = clientReads.readLine();
                if (!(response.equals("$$$$$$")))
                    System.out.println(response);
                cmdHistory.add(commandText);
                saveHistory();
                return;
            }
            else if(Pattern.compile("telnet upload (.*)", Pattern.CASE_INSENSITIVE).matcher(commandText).matches()){
                String path[] = commandText.split("(?i)upload ",2);
                clientWrites.println(FILE_TRANSFER+path[1]);
                while (true){
                    if (clientReads.readLine().equals("$Ok!"))
                        break;
                }
                uploadFile(path[1]);
                String response = clientReads.readLine();
                if (!(response.equals("$$$$$$")))
                    System.out.println(response);
                cmdHistory.add(commandText);
                saveHistory();
                return;
            }
            else if(commandText.equalsIgnoreCase("telnet logout")){
                clientWrites.println(LOGOUT+" ");
                cmdHistory.add(commandText);
                saveHistory();
                logout = true;
                return;
            }
            else if(Pattern.compile("telnet exec (.*)", Pattern.CASE_INSENSITIVE).matcher(commandText).matches()){
                String command[] = commandText.split("(?i)exec ",2);
                clientWrites.println(EXEC+command[1]);
                System.out.println("\t[Host] Result :");
                while (true){
                    String line = clientReads.readLine();
                    if (line.equals("$$done$$")) {
                        break;
                    }
                    System.out.println(line);
                }
                System.out.println(clientReads.readLine());
                cmdHistory.add(commandText);
                saveHistory();
                logout = false;
                return;
            }
            else if (commandText.equalsIgnoreCase("telnet history")) {
                cmdHistory.add(commandText);
                saveHistory();
                loadHistory();
                for ( String s: cmdHistory ) {
                    System.out.println(s);
                }
            }
            else {
                System.out.println("\t[Telnet] : Something seems off with the command. Try again");
                //todo print all the telnet commands for user
            }
        }
    }

    private void encrypt(String msg) throws IOException {

        //Security.addProvider(new Sun());
        Security.addProvider(new Provider());
        System.setProperty("javax.net.ssl.keyStore", "myKeyStore.jks" );
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStore","myTrustStore.jts");
        System.setProperty("javax.net.ssl.trustStorePassword","changeit");
        //System.setProperty("javax.net.ssl.trustStore","myTrustStore.jts");
        //System.setProperty("javax.net.ssl.trustStorePassword","1234567");
        System.setProperty("javax.net.ssl.trustStoreType","JCEKS");
        //System.setProperty("javax.net.debug","all");
        try
        {
            SSLSocketFactory sslsocketfactory = (SSLSocketFactory)SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket)sslsocketfactory.createSocket(hostIP,PORT_E);
            var outputS = sslSocket.getOutputStream();
            var inputS = sslSocket.getInputStream();
            System.out.println(msg);
            outputS.write((msg + "\n").getBytes());

            String line = "";
            char c;
            while((c = (char) inputS.read()) != '\n') {
                line += c;
            }
            System.out.println(line);

            outputS.close();
            inputS.close();
            sslSocket.close();
        }
        catch(Exception ex)
        {
            System.err.println("Error Happened : "+ex.toString());
        }

    }

    private void uploadFile(String path) throws IOException {
        //todo check address with regex
        File file = new File(path);
        if(!file.isFile()){
            System.out.println("\t- This is not a file");
            clientWrites.println("!abort");
            return;
        }
        clientWrites.println(file.getName() + "!name");

        String t = clientReads.readLine();
        if (t.equals("$exists!")){
            System.out.println("\t- Host already has a file named " + file.getName());
            return;
        }
        else if(!t.equals("$Ok!")){
            System.out.println("\t- Something went wrong!!!");
            return;
        }

        clientWrites.println(file.length()+"!size");
        t = clientReads.readLine();
        if (t.equals("$size!")){
            clientWrites.println("!data");
            System.out.println("\t- Uploading: " + path);
            System.out.println("\t- It may take a while...");
        }
        else{
            System.out.println("\t- Something went wrong!!!");
            clientWrites.println("!abort");
            return;
        }
        try {
            DataInputStream fileInput = new DataInputStream(new FileInputStream(file));

            // pass file data
            byte[] chunk = new byte[MAX_TRANSFER_SIZE];
            int chunkCount = (int)Math.ceil(file.length()/(double)MAX_TRANSFER_SIZE);

            int readcount;
            for (int i = 0; i < chunkCount ; i++) {
                if ((readcount = fileInput.read(chunk)) > 0) {
                    outputStream.write(chunk, 0, readcount);
                    outputStream.flush();
                }
            }

            //close the stream
            //clientWrites.println("!done");
            fileInput.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void saveHistory() {
        //todo serve Ip + server port + command + time stamp (for now it's just commands)
        // may as well make a class named history!
        try {
            FileOutputStream fileOutputStream = new FileOutputStream("Telnet.hist");
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(cmdHistory);
            objectOutputStream.close();
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadHistory() {
        try{
            FileInputStream fileInputStream = new FileInputStream("Telnet.hist");
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            cmdHistory = (ArrayList<String>)objectInputStream.readObject();
            objectInputStream.close();
            fileInputStream.close();
        } catch (IOException | ClassNotFoundException ignored) {
        }
        // todo if you make a class for stored items, gotta iterate and refill each class. ref: xo game
    }

    public static final class Result{
        private final int portR;
        private final boolean state;

        public Result(int p, boolean state){
            portR = p;
            this.state = state;
        }

        public int getPortR() {
            return portR;
        }

        public boolean isState() {
            return state;
        }
    }

    public static Future<Result> portIsOpen(final ExecutorService es, final String ip, final int port, final int timeout) {
        return es.submit(new Callable<Result>() {
            @Override public Result call() {
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, port), timeout);
                    socket.close();
                    return new Result(port, true);
                } catch (Exception ex) {
                    return new Result(port, false);
                }
            }
        });
    }

    private void portScanner(String IP, int start, int end) {
        System.out.println("\t- it may take several minutes...");
        final ExecutorService es = Executors.newFixedThreadPool(1000);
        final String ip = IP;
        final int timeout = 2500;
        final List<Future<Result>> futures = new ArrayList<>();
        for (int port = start; port >= 0 && port <= end && port <= 65535; port++) {
            futures.add(portIsOpen(es, ip, port, timeout));
        }
        es.shutdown();
        System.out.println("  List of Listening Ports:");
        int line = 0;
        int openPorts = 0;
        for (final Future<Result> f : futures) {
            try {
                if (f.get().isState()) {
                    System.out.print(" "+f.get().getPortR()+" |");
                    line++;
                    if (line == 19){
                        System.out.println();
                        line = 0;
                    }
                    openPorts++;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        System.out.println("\nThere are " + openPorts + " open ports on host " + ip+" from "+Math.max(0,start)+" to "+Math.min(end, 65535)+"\n");
    }

}

