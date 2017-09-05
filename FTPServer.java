
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * FTP Server Using Stop And Wait and Sliding Window Protocol
 * @author Sohan Dutta
 */
public class FTPServer {

    private byte[] RDT = {0x52, 0x44, 0x54};
    private byte[] SEQN = {0};
    private byte[] END = {0x45, 0x4e, 0x44};
    private byte[] CRLF = {0x0a, 0x0d};
    private byte[] REQUEST = {0x52, 0x45, 0x51, 0x55, 0x45, 0x53, 0x54};
    private int CONSIGNMENT = 512;

    private byte CONSIGNMENT_0;
    private byte CONSIGNMENT_1;
    private byte CONSIGNMENT_2;
    private byte CONSIGNMENT_3;

    private int portNumber;

    /**
     * Initialization of class FTPServer
     *
     * @param portNumber contains the port number
     * @param CON_0 contains Consignment 1
     * @param CON_1 contains Consignment 2
     * @param CON_2 contains Consignment 3
     * @param CON_3 contains Consignment 4
     */
    public FTPServer(int portNumber, byte CON_0, byte CON_1, byte CON_2, byte CON_3) {
        this.portNumber = portNumber;

        CONSIGNMENT_0 = CON_0;
        CONSIGNMENT_1 = CON_1;
        CONSIGNMENT_2 = CON_2;
        CONSIGNMENT_3 = CON_3;
    }

    public static void main(String[] args) throws IOException  {

        FTPServer ftpServer = new FTPServer(
                Integer.parseInt(args[0]),
                Byte.parseByte(args[1]),
                Byte.parseByte(args[2]),
                Byte.parseByte(args[3]),
                Byte.parseByte(args[4])
        );
        ftpServer.run();
    }

    /**
     * Executes the FTP using UDP
     */
    public void run() {
        DatagramSocket ss = null;
        FileInputStream fis = null;

        try {
            ss = new DatagramSocket(portNumber);

            byte[] fileNamePacket = new byte[50];
            DatagramPacket fp = new DatagramPacket(fileNamePacket, fileNamePacket.length);
            ss.receive(fp);

            int msgLength = calMessageLength(fileNamePacket);
            String fileName = new String(fileNamePacket, REQUEST.length, msgLength - REQUEST.length - CRLF.length);
            InetAddress ip = fp.getAddress();
            int port = fp.getPort();
            System.out.println("Received request for " + fileName + " from " + ip.toString() + " port " + port);

            fis = new FileInputStream(fileName);

            byte[] mData = new byte[CONSIGNMENT];
            byte[] mLastData;
            byte[] mMsg = null;

            int bytesRead = 0;
            DatagramPacket sp = null;
            boolean timeOut = false;

            ss.setSoTimeout(30);

            while (bytesRead != -1 || timeOut) {
                if (timeOut) {
                    System.out.println("Sending Again CONSIGNMENT " + mMsg[RDT.length]);
                    ss.send(sp);
                    timeOut = false;
                } else {
                    bytesRead = fis.read(mData);
                    if (bytesRead > -1) {
                        if (bytesRead < CONSIGNMENT) {
                            mLastData = new byte[bytesRead];
                            for (int i = 0; i < bytesRead; i++) {
                                mLastData[i] = mData[i];
                            }
                            mMsg = getByteStream(RDT, SEQN, mLastData, END, CRLF);
                            bytesRead = -1;
                        } else {
                            mMsg = getByteStream(RDT, SEQN, mData, CRLF);
                        }

                        sp = new DatagramPacket(mMsg, mMsg.length, ip, port);

                        if (SEQN[0] == CONSIGNMENT_0) {
                            CONSIGNMENT_0 = -1;
                            System.out.println("Forgot CONSIGNMENT " + SEQN[0]);
                        } else if (SEQN[0] == CONSIGNMENT_1) {
                            CONSIGNMENT_1 = -1;
                            System.out.println("Forgot CONSIGNMENT " + SEQN[0]);
                        } else if (SEQN[0] == CONSIGNMENT_2) {
                            CONSIGNMENT_2 = -1;
                            System.out.println("Forgot CONSIGNMENT " + SEQN[0]);
                        } else if (SEQN[0] == CONSIGNMENT_3) {
                            CONSIGNMENT_3 = -1;
                            System.out.println("Forgot CONSIGNMENT " + SEQN[0]);
                        } else {
                            System.out.println("Sent CONSIGNMENT " + SEQN[0]);
                            ss.send(sp);
                        }
                    }
                }
                try {
                    byte[] rd = new byte[7];
                    DatagramPacket rp = new DatagramPacket(rd, rd.length);
                    if (bytesRead != -1) {
                        ss.receive(rp);
                        if (rd[3] == SEQN[0] + 1) {
                            SEQN[0] += 1;
                        }
                        System.out.println("Received ACK " + rd[3]);
                    }
                } catch (Exception e) {
                    timeOut = true;
                }
            }
            System.out.println("END");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            ss.close();
            try {
                fis.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Return the ByteArray Packet data to be sent
     * @param rdt RDT
     * @param seq Sequence Number
     * @param payload Payload
     * @param crlf CRLF
     * @return
     */
    public byte[] getByteStream(byte[] rdt, byte[] seq, byte[] payload, byte[] crlf) {
        byte[] result = new byte[rdt.length + seq.length + payload.length + crlf.length];

        System.arraycopy(rdt, 0, result, 0, rdt.length);
        System.arraycopy(seq, 0, result, rdt.length, seq.length);
        System.arraycopy(payload, 0, result, rdt.length + seq.length, payload.length);
        System.arraycopy(crlf, 0, result, rdt.length + seq.length + payload.length, crlf.length);

        return result;
    }

    /**
     * Return the last ByteArray Packet data to be sent
	 * @param rdt RDT
     * @param seq Sequence Number
     * @param payload Payload
	 * @param end end
     * @param crlf CRLF
     * @return
     */
    public byte[] getByteStream(byte[] rdt, byte[] seq, byte[] payload, byte[] end, byte[] crlf) {
        byte[] result = new byte[rdt.length + seq.length + payload.length + end.length + crlf.length];

        System.arraycopy(rdt, 0, result, 0, rdt.length);
        System.arraycopy(seq, 0, result, rdt.length, seq.length);
        System.arraycopy(payload, 0, result, rdt.length + seq.length, payload.length);
        System.arraycopy(end, 0, result, rdt.length + seq.length + payload.length, end.length);
        System.arraycopy(crlf, 0, result, rdt.length + seq.length + payload.length + end.length, crlf.length);

        return result;
    }

    /**
     * Calculates the message length for File Request
     * @param msg message
     * @return message length
     */
    public int calMessageLength(byte[] m) {
        int count = 0;
        for (int i = m.length - 1; i >= 0; i--) {
            if (m[i] == 0) {
                count++;
            } else {
                break;
            }
        }

        return m.length - count;
    }
}
