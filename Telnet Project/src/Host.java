import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.security.Security;

public class Host {

    private static final int PORT = 23 , MAX_TRANSFER_SIZE = 8192, PORT_E = 44300;
    private static final int SEND = 1, FILE_TRANSFER = 2, LOGOUT = 3, EXEC = 4, SEND_E = 5;
    private boolean first = true, noTLS = true;
    private int mode;
    private PrintWriter serverWrites;
    private BufferedReader serverReads;
    private DataInputStream inputStream;

    public Host(boolean isTLS) throws IOException {
        if (!isTLS)
            setUpServer();
    }


    private void setUpServer() throws IOException {

        boolean done = true;
        System.out.println( "\n\t[Server Stat] Listening for a connection..." );

        while (true){
            if (noTLS){

                ServerSocket serverSocket = new ServerSocket(PORT);

                Socket socket = serverSocket.accept();
                serverReads = new BufferedReader( new InputStreamReader( socket.getInputStream() ) );
                serverWrites = new PrintWriter( socket.getOutputStream() );
                inputStream =  new DataInputStream(new BufferedInputStream(socket.getInputStream()));

                if (serverReads.readLine().equals("Hello Server")) {

                    serverWrites.println("Hello Client");
                    if (first){
                        System.out.println("\tConnection Established with "+ socket.getInetAddress());
                        first = false;
                    }
                    serverWrites.flush();
                    done = telnetServer();
                }

                if (serverReads.readLine().equals("3goodbye server")) {
                    serverWrites.println("goodbye client");
                    serverWrites.flush();
                }
                else  return;

                serverReads.close();
                serverWrites.close();
                inputStream.close();
                socket.close();
                serverSocket.close();
            }
            if (!noTLS){
                decrypt();
                noTLS = true;
            }

            if (done)
                break;
        }
        System.out.println( "   - Connection closed" );
    }

    private boolean telnetServer() throws IOException {

        String line = serverReads.readLine();
        if( line != null && line.length() > 0 ) {
            //System.out.println("the line: "+ line);
            int cmd = Integer.parseInt(line.substring(0,1));
            switch (cmd){
                case SEND_E:
                    noTLS = false; //the only place noTLS gets false, elsewhere it can just be changed to be true
                    serverWrites.println(false);
                    serverWrites.flush();
                    return false;
                case SEND:
                    System.out.println("\t[Received Message] : "+ line.substring(1));
                    serverWrites.println("\t[Host] : Message Delivered");
                    serverWrites.flush();
                    return false;
                case FILE_TRANSFER:
                    serverWrites.println("$Ok!");
                    serverWrites.flush();
                    boolean state = downloadFile();
                    if (state) {
                        System.out.println("\t[The file is saved to Telnet/Downloads]");
                        serverWrites.println("\t[Host] : Download Successful");
                    }
                    else{
                        System.out.println("\t[ERROR downloading the file]");
                        serverWrites.println("\t[Host] : ERROR Downloading " + line.substring(1));
                    }
                    serverWrites.flush();
                    return false;
                case EXEC:
                    boolean res = execute(line.substring(1));
                    if (res){
                        System.out.println("\t[Command Executed Successfully]");
                        serverWrites.println("\t[Host] : Execution Successful");
                    }else {
                        System.out.println("\t[ERROR Executing the Command]");
                        serverWrites.println("\t[Host] : ERROR Exec " + line.substring(1));
                    }
                    serverWrites.flush();
                    return false;
                case LOGOUT:
                    serverWrites.println("!ok!");
                    serverWrites.flush();
                    return true;
                default:
                    serverWrites.println("$$$$$$"); //represents error
            }
        }
        return false;
    }

    private void decrypt() throws IOException {

        System.out.println("\t- Secure Connection Set Up");
        Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
        System.setProperty("javax.net.ssl.keyStore","myKeyStore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword","changeit");
        System.setProperty("javax.net.ssl.trustStoreType","JCEKS");
        try
        {
            SSLServerSocketFactory sslServerSocketfactory = (SSLServerSocketFactory)SSLServerSocketFactory.getDefault();
            SSLServerSocket sslServerSocket = (SSLServerSocket)sslServerSocketfactory.createServerSocket(PORT_E);
            //System.out.println("Echo Server Started & Ready to accept Client Connection");
            SSLSocket sslSocket = (SSLSocket)sslServerSocket.accept();

            InputStream inputS = sslSocket.getInputStream();
            OutputStream outputS = sslSocket.getOutputStream();

            String msg = "";
            char c;
            while((c = (char) inputS.read()) != '\n') {
                msg += c;
            }

            System.out.println("\t[Secure Message] : " + msg);
            outputS.write("\t[Host] : Message Received\n".getBytes());
            outputS.flush();

            outputS.close();
            inputS.close();
            sslSocket.close();
            sslServerSocket.close();
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            System.err.println("Error Happened : "+ex.toString());
        }
        System.out.println();
    }

    private boolean execute(String command) throws IOException {
        //todo cd ls dir pwd

        Process process = Runtime.getRuntime().exec(command);
        process.getOutputStream();
        InputStream pInputStrean = process.getInputStream();

        BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()));
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(pInputStrean));
        String line;
        String error;
        while ((line = reader.readLine()) != null) {
            while ((error = errorReader.readLine()) != null){
                System.out.println(error);
                serverWrites.println(error);
                serverWrites.flush();
                return false;
            }
            System.out.println(line);
            serverWrites.println(line);
            serverWrites.flush();
        }
        reader.close();
        errorReader.close();

        serverWrites.println("$$done$$");
        serverWrites.flush();

        System.out.println();
        return true;
    }

    private boolean downloadFile() throws IOException {

        String name = serverReads.readLine().split("!name")[0];
        if (name.equals("!abort"))
            return false;

        String path = "Downloads" + File.separator + name;
        File file = new File(path);
        try {
            if (file.exists()){
                System.out.println("\t!!! File already exist !!!");
                serverWrites.println("$exists!");
                serverWrites.flush();
                return false;
            }
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        serverWrites.println("$Ok!");
        serverWrites.flush();

        String size = serverReads.readLine().split("!size")[0];
        if (Integer.valueOf(size) > 0){
            serverWrites.println("$size!");
            serverWrites.flush();
        }
        else{
            serverWrites.println("error");
            serverWrites.flush();
        }
        System.out.println("\tFile size : "+size);

        String t = serverReads.readLine();
        if (t.equals("!abort")) {
            return false;
        }
        else if (t.equals("!data")){
            System.out.println("\t[Server Stat] Downloading "+ name+" ...");}
        else {
            System.out.println("\t!!! something went wrong !!!");
            return false;
        }

        try {
            DataOutputStream fileOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

            byte[] bytes = new byte[MAX_TRANSFER_SIZE];
            int chunksCount = (int)Math.ceil(Integer.valueOf(size)/(double)MAX_TRANSFER_SIZE);

            int count;
            for (int i = 0; i < chunksCount; i++) {
                if ((count = inputStream.read(bytes)) > 0) {
                    fileOut.write(bytes, 0, count);
                }
            }
            fileOut.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println();
        return true;
    }
}