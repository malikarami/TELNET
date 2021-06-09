import java.io.*;
import java.util.Scanner;

public class Main {

    private static Scanner scan = new Scanner(System.in);
    private static final int CLIENT = 1 , SERVER = 0;

    public Main(int mode) throws IOException {
        if (mode == CLIENT)
            new Client();
        else
            new Host(false);
    }

    public static void main(String[] args) throws IOException {
        System.out.println("    - Hello and Welcome");
        System.out.println("    - Choose your mode : Host or Client");
        System.out.print("    - [C/S]? ");
        while (true) {

            char mode = scan.nextLine().charAt(0);
            if (mode == 'c' || mode == 'C') {
                System.out.println("    - Telnet Client is being set up...");
                new Main(CLIENT);
                break;
            } else if (mode == 's' || mode == 'S') {
                System.out.println("    - Telnet Server is being set up...");
                new Main(SERVER);
                break;
            } else{
                System.out.println("    - Invalid input!");
                System.out.print("    - [C/S]? ");
            }
        }
        System.out.println("good bye");
    }
}
