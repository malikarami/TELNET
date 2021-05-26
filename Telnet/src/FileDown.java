

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileDown extends Thread {
    //File save path
    private String fileDir;
    // socket server port number
    private int port;
    // Whether to stop
    private boolean stop;

    public String getFileDir() {
        return fileDir;
    }

    public void setFileDir(String fileDir) {
        this.fileDir = fileDir;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public static void main(String[] args) {
        FileDown fd = new FileDown();
        fd.setFileDir("e:\\");
        fd.setPort(9005);
        fd.start();
    }

    /**
     * file download
     */
    @Override
    public void run() {
        Socket socket = null;
        try {
            ServerSocket ss = new ServerSocket(port);
            do {
                socket = ss.accept();

                // public Socket accept() throws
                // IOException listens for and accepts connections to this socket. This method blocks until it is connected.
                System.out.println("Create a socket link");
                DataInputStream inputStream = new DataInputStream(
                        new BufferedInputStream(socket.getInputStream()));

                // Local save path, the file name will automatically inherit from the server side.
                int bufferSize = 8192;
                byte[] buf = new byte[bufferSize];
                long passedlen = 0;
                long len = 0;

                //Get the file name
                String file = fileDir + inputStream.readUTF();
                DataOutputStream fileOut = new DataOutputStream(
                        new BufferedOutputStream(new FileOutputStream(file)));
                len = inputStream.readLong();

                System.out.println("The length of the file is:" + len + "\n");
                System.out.println("Start receiving files!" + "\n");

                while (true) {
                    int read = 0;
                    if (inputStream != null) {
                        read = inputStream.read(buf);
                    }
                    passedlen += read;
                    if (read == -1) {
                        break;
                    }
                    // The following progress bar is made for the prograssBar of the graphical interface. If you are typing a file, you may repeat the same percentage.
                    System.out.println("File Received" + (passedlen * 100 / len)
                            + "%\n");
                    fileOut.write(buf, 0, read);
                }
                System.out.println("Receive completed, file saved as" + file + "\n");

                fileOut.close();
            } while (!stop);
        } catch (Exception e) {
            System.out.println("Receive Message Error" + "\n");
            e.printStackTrace();
            return;
        }
    }
}