package org.ea;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NTPClock {
    private static volatile long now = 0;
    private static final JLabel timeLabel = new JLabel();
    private static final JLabel resultLabel = new JLabel("Press 'Sync' to synchronize");

    public static void main(String[] args) {
        JFrame frame = new JFrame("NTP Clock");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 150);
        frame.setLayout(new BorderLayout());

        timeLabel.setFont(new Font("Monospaced", Font.BOLD, 24));
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        resultLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JButton syncButton = new JButton("Sync");
        syncButton.addActionListener(e -> {
            new Thread(() -> {
                try {
                    String server = "pool.ntp.org";
                    InetAddress address = InetAddress.getByName(server);
                    int port = 123;
                    byte[] buffer = new byte[48];
                    buffer[0] = 0b00100011;

                    /*
                    bits
                    2 - Leap indicator (0)
                    3 - Version (currently 4)
                    3 - Mode (client 3)
                    8 - Stratum (0) (1-Primary, 2-15 secondary)
                    8 - Poll (0)
                    8 - Precision (0)
                    32 - Root delay (0)
                    32 - Root dispersion (0)
                    32 - Reference id (0)
                    64 - Reference Timestamp (0)
                    64 - Origin Timestamp (0)
                    64 - Receive Timestamp (0)
                    64 - Transmit Timestamp (0)
                    Extension field 1
                    Extension field 2
                    Key Identifier
                    dgst (128)
                    */

                    long t1 = now;
                    DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, port);
                    DatagramSocket socket = new DatagramSocket();
                    socket.setSoTimeout(2000);
                    socket.send(request);

                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);
                    long t4 = now;
                    socket.close();

                    byte[] idArr = new byte[4];
                    System.arraycopy(buffer, 12, idArr, 0, 4);

                    String id = new String(idArr);

                    long t2 = decodeTimestamp(buffer, 32);
                    long t3 = decodeTimestamp(buffer, 40);

                    /*
                    t1 – Time request was sent by client
                    t2 – Time request was received by server
                    t3 – Time reply was sent by server
                    t4 – Time reply was received by client
                    */
                    long delay = (t4 - t1) - (t3 - t2);
                    long newOffset = ((t2 - t1) + (t3 - t4)) / 2;

                    now += newOffset;
                    resultLabel.setText(String.format(id + " Offset: %d ms, Delay: %d ms", newOffset, delay));
                } catch (Exception ex) {
                    resultLabel.setText("Sync failed: " + ex.getMessage());
                }
            }).start();
        });

        frame.add(timeLabel, BorderLayout.NORTH);
        frame.add(syncButton, BorderLayout.CENTER);
        frame.add(resultLabel, BorderLayout.SOUTH);

        frame.setVisible(true);

        // Start the clock update thread
        new Thread(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            while (true) {
                long finalTime = now;
                SwingUtilities.invokeLater(() -> timeLabel.setText(sdf.format(new Date(finalTime))));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
                now += 1000;
            }
        }).start();
    }

    private static long decodeTimestamp(byte[] array, int pointer) {
        long seconds = ((array[pointer] & 0xFFL) << 24)
                | ((array[pointer + 1] & 0xFFL) << 16)
                | ((array[pointer + 2] & 0xFFL) << 8)
                | (array[pointer + 3] & 0xFFL);

        long fraction = ((array[pointer + 4] & 0xFFL) << 24)
                | ((array[pointer + 5] & 0xFFL) << 16)
                | ((array[pointer + 6] & 0xFFL) << 8)
                | (array[pointer + 7] & 0xFFL);

        return ((seconds - 2208988800L) * 1000) + ((fraction * 1000L) >> 32);
    }
}
