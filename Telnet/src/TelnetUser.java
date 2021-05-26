import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Pattern;

public class TelnetUser {

    private static final int CLIENT = 1 , SERVER = 0, PORT = 23 , MAX_TRANSFER_SIZE = 16384;
    private static final int SEND = 1, FILE_TRANSFER = 2;
    private static Scanner scan = new Scanner(System.in);
    private int mode;
    private Socket socket;
    private PrintStream clientWrites;
    private BufferedReader clientReads;
    private PrintWriter serverWrites;
    private BufferedReader serverReads;
    private ArrayList<String> cmdHistory = new ArrayList();

    public TelnetUser(int mode) throws IOException {
        this.mode = mode;
        if (mode == CLIENT)
            setUpClient();
        else
            setUpServer();
    }

    private void setUpServer() throws IOException {

        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println( "\n   [Server Stat] Listening for a connection..." );

        socket = serverSocket.accept();
        serverReads = new BufferedReader( new InputStreamReader( socket.getInputStream() ) );
        serverWrites = new PrintWriter( socket.getOutputStream() );
        if (serverReads.readLine().equals("Hello Server")) {

            serverWrites.println("Hello Client");
            System.out.println("   [Server Stat] Connection Established");
            serverWrites.flush();
        }

        //until the client closes the connection or we receive an empty line
        String line = serverReads.readLine();
        while( line != null && line.length() > 0 ) {
            int cmd = Integer.parseInt(line.substring(0,1));
            switch (cmd){
                case SEND:
                    System.out.println("    [Received Message] :"+ line.substring(1));
                    serverWrites.println("\t[Host] : Message Delivered");
                    serverWrites.flush();
                    break;
                case FILE_TRANSFER:
                    if (downloadFile()) {
                        System.out.println("    [The file is saved to Telnet/Downloads]");
                        serverWrites.println("\t[Host] : Download Successful");
                    }
                    else{
                        System.out.println("    [ERROR downloading the file]");
                        serverWrites.println("\t[Host] : ERROR Downloading the File");
                    }
                    serverWrites.flush();
                    break;
                default:
                    serverWrites.println("$$$$$$"); //represents error
             }
            line = serverReads.readLine();
        }

        // Close our connection
        serverReads.close();
        serverWrites.close();
        socket.close();

        System.out.println( "   - Connection closed" );
    }

    private boolean downloadFile() {
        String directory = "..\\Downloads\\";
        try {
            DataInputStream inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            byte[] bytes = new byte[MAX_TRANSFER_SIZE];
            long passedlen = 0;

            String file = directory + inputStream.readUTF();
            DataOutputStream fileOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

            int count;
            while ((count = inputStream.read(bytes)) > 0) {
                fileOut.write(bytes, 0, count);
            }
            fileOut.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;

    }

    private void setUpClient() throws IOException {
        String server = "127.0.0.1";
        while (true) {
            System.out.println("    - Enter ");
            System.out.println("            [0] = exit ");
            System.out.println("            [1] = connect to a host ");
            System.out.println("            [2] = find open ports of the IP list ");
            int c = Integer.parseInt(scan.nextLine()); //todo check if integer
            if ( c == 0 )
                    return;
            else if ( c == 1 ) {
                // todo may want to enter host port
                // todo check host IP
                System.out.println("    - Enter Host IP");
                server = scan.nextLine();
                break;
            }
            else if ( c == 2 ) {
                System.out.println("کارگران مشغول کارند");
                //todo get open port of the IP list
                break;
            }
            System.out.println("   - Invalid input...");
        }
        socket = new Socket( server, PORT );
        clientWrites = new PrintStream( socket.getOutputStream());
        clientReads = new BufferedReader( new InputStreamReader( socket.getInputStream()));
        clientWrites.println( "Hello Server" );
        if (clientReads.readLine().equals("Hello Client")){
            System.out.println("    - Connection Established...");
            System.out.println("    - You may now use Telnet Commands");
            telnetClient();
        }
    }

    private void telnetClient() throws IOException {
        //todo if response is $$$$$ guide client to reenter the command
        while (true){
            String commandText = scan.nextLine();
            if (Pattern.compile("telnet send (.*)", Pattern.CASE_INSENSITIVE).matcher(commandText).matches()) {
                String msg[] = commandText.split("(?i)send");
                clientWrites.println(SEND+msg[1]);
                String response = clientReads.readLine();
                if (!(response.equals("$$$$$$")))
                    System.out.println(response);
                cmdHistory.add(commandText);
                saveHistory();
            }
            else if(Pattern.compile("telnet upload (.*)", Pattern.CASE_INSENSITIVE).matcher(commandText).matches()){
                String path[] = commandText.split("(?i)upload");
                File file = new File(path[1]);
                if (file.length() > MAX_TRANSFER_SIZE){
                    System.out.println("    - The file is too big\n\n- Try again...");
                    continue;
                }
                clientWrites.println(FILE_TRANSFER+" ");
                uploadFile(path[1]);
                String response = clientReads.readLine();
                if (!(response.equals("$$$$$$")))
                    System.out.println(response);
                cmdHistory.add(commandText);
                saveHistory();
            }
            else if (commandText.equalsIgnoreCase("telnet history")) {
                loadHistory();
                for ( String s: cmdHistory ) {
                    System.out.println(s);
                }
            }
            else {
                System.out.println("    - Something seems off :/ Try again");
                //todo print all the telnet commands for user
            }
        }
    }

    private void uploadFile(String path) throws IOException {
        //todo check address with regex
        File file = new File(path);
        System.out.println(path);
        try {
            DataInputStream inputStream = new DataInputStream(new FileInputStream(path));
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

            // pass file information
            outputStream.writeUTF(file.getName());
            outputStream.flush();

            // pass file data
            byte[] bytes = new byte[MAX_TRANSFER_SIZE];
            int count;
            while ((count = inputStream.read(bytes)) > 0) {
                outputStream.write(bytes, 0, count);
            }
            outputStream.flush();

            //close the stream
            outputStream.close();
            inputStream.close();

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

    public static void main(String[] args) throws IOException {
        System.out.println("    - Hello and Welcome");
        System.out.println("    - Choose your mode : Server or Client");
        System.out.print("    - [C/S]? ");
        while (true) {

            char mode = scan.nextLine().charAt(0);
            if (mode == 'c' || mode == 'C') {
                System.out.println("    - Telnet Client is being set up...");
                new TelnetUser(CLIENT);
            } else if (mode == 's' || mode == 'S') {
                System.out.println("    - Telnet Server is being set up...");
                new TelnetUser(SERVER);
            } else{
                System.out.println("    - Invalid input!");
                System.out.print("    - [C/S]? ");
            }
        }
    }
}
