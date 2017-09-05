
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * FTP Client Using Stop And Wait and Sliding Window Protocol
 * @author Sohan Dutta
 */

public class FTPClient {

    private final byte[] RDT = {0x52, 0x44, 0x54};
    private final byte[] SEQN = {0};
    private final byte[] CRLF = {0x0a, 0x0d};
    private final byte[] END = {0x44, 0x4e, 0x45};
    private final byte[] REQUEST = {0x52, 0x45, 0x51, 0x55, 0x45, 0x53, 0x54};
    private final byte[] ACK = {0x41, 0x43, 0x4b};
    private boolean END_FLAG = false;

    private byte ACK_TO_FORGET_0 = -1;
    private byte ACK_TO_FORGET_1 = -2;
    private byte ACK_TO_FORGET_2 = -3;
    private byte ACK_TO_FORGET_3 = -4;

    private InetAddress ip;
    private int serverPort;
    private String fileName;

    /**
     * Initializing class FTPClient
     * @param ip contains server IP Address
     * @param serverPort contains server port number
     * @param fileName contains file name to be sent
     * @param ACK_0 contains ACKNOWLEGMENT 0
     * @param ACK_1 contains ACKNOWLEGMENT 1
     * @param ACK_2 contains ACKNOWLEGMENT 2
     * @param ACK_3 contains ACKNOWLEGMENT 3
     */
    public FTPClient(InetAddress ip, int serverPort, String fileName, byte ACK_0, byte ACK_1, byte ACK_2, byte ACK_3) {
        this.ip = ip;
        this.serverPort = serverPort;
        this.fileName = fileName;

        ACK_TO_FORGET_0 = ACK_0;
        ACK_TO_FORGET_1 = ACK_1;
        ACK_TO_FORGET_2 = ACK_2;
        ACK_TO_FORGET_3 = ACK_3;
    }

    public static void main(String[] args) throws IOException {

        FTPClient ftpClient = new FTPClient(
                InetAddress.getByName(args[0]),
                Integer.parseInt(args[1]),
                args[2],
                Byte.parseByte(args[3]),
                Byte.parseByte(args[4]),
                Byte.parseByte(args[5]),
                Byte.parseByte(args[6])
        );
        ftpClient.run();
    }

    /**
     * Executes the FTP Client
     */
    public void run() {
        DatagramSocket ss = null;
        FileOutputStream fos = null;
        try {
            ss = new DatagramSocket();
            byte[] filePacket = getPacketByteStream(REQUEST, fileName.getBytes(), CRLF);
            DatagramPacket sf = new DatagramPacket(filePacket, filePacket.length, ip, serverPort);
            System.out.println("Requesting " + fileName + " from " + ip.toString() + " port " + serverPort);
            ss.send(sf);

            fos = new FileOutputStream("output." + fileName.split("\\.")[1]);

            while (!END_FLAG) {
                byte[] rd = new byte[520];
                DatagramPacket rp = new DatagramPacket(rd, rd.length);

                ss.receive(rp);
                if (rd[RDT.length] == SEQN[0]) {
                    System.out.println("Received CONSIGNMENT " + SEQN[0]);
                    int msgLength = calMessageLength(rd);

                    if (checkLength(rd, msgLength)) {
                        fos.write(rd, RDT.length + SEQN.length, msgLength - (CRLF.length + END.length + RDT.length + SEQN.length));
                    } else {
                        fos.write(rd, RDT.length + SEQN.length, msgLength - (CRLF.length + RDT.length + SEQN.length));
                    }

                    SEQN[0] += 1;
                } else {
                    System.out.println("Received CONSIGNMENT " + rd[RDT.length] + " duplicate - discarding");
                }
				// Checking for ACK_TO_FORGET
                if (SEQN[0] == ACK_TO_FORGET_0) {
                    ACK_TO_FORGET_0 = -1;
                    System.out.println("Forgot ACK " + SEQN[0]);
                } else if (SEQN[0] == ACK_TO_FORGET_1) {
                    ACK_TO_FORGET_1 = -2;
                    System.out.println("Forgot ACK " + SEQN[0]);
                } else if (SEQN[0] == ACK_TO_FORGET_2) {
                    ACK_TO_FORGET_2 = -3;
                    System.out.println("Forgot ACK " + SEQN[0]);
                } else if (SEQN[0] == ACK_TO_FORGET_3) {
                    ACK_TO_FORGET_3 = -4;
                    System.out.println("Forgot ACK " + SEQN[0]);
                } else if (!END_FLAG) {
                    byte[] sd = getPacketByteStream(ACK, SEQN, CRLF);
                    DatagramPacket sp = new DatagramPacket(sd, sd.length, ip, serverPort);
                    System.out.println("Sent ACK " + SEQN[0]);
                    ss.send(sp);
                }
            }
            System.out.println("END");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
            ss.close();
        }
    }

    /**
     * Returns the Byte Array data to be snr
     * @param rdt RDT
     * @param payload Data
     * @param crlf CRLF
     * @return
     */
    public byte[] getPacketByteStream(byte[] rdt, byte[] payload, byte[] crlf) {
        byte[] result = new byte[rdt.length + payload.length + crlf.length];
        System.arraycopy(rdt, 0, result, 0, rdt.length);
        System.arraycopy(payload, 0, result, rdt.length, payload.length);
        System.arraycopy(crlf, 0, result, rdt.length + payload.length, crlf.length);
        return result;
    }

    /**
     * Calculates the message length for File Request
     * @param msg message
     * @return message length
     */
    public int calMessageLength(byte[] msg) {
        int count = 0;
        for (int i = msg.length - 1; i >= 0; i--) {
            if (msg[i] == 0) {
                count++;
            } else {
                break;
            }
        }

        return msg.length - count;
    }

    /**
     * Checks if Byte Stream has END tag or not
     * @param msg message
     * @param msgLength message length
     * @return
     */
    public boolean checkLength(byte[] msg, int msgLength) {

        int j = msgLength - CRLF.length - 1;
        for (int i = 0; i < END.length; i++) {
            if (END[i] != msg[j - i]) {
                return false;
            }
        }
        END_FLAG = true;
        return true;
    }

}
