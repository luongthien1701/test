package src;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Base64;

public class UDPClient {
    public final static int PORT = 3000;
    public final static int TIMEOUT = 2000; 
    public final static int BUFFER_SIZE = 1024;
    
    private JFrame frame;
    private JTextArea logArea;
    private JTextField messageField;
    private JTextField serverIPField;
    private JButton connectButton;
    private JButton sendButton;
    private JButton disconnectButton;
    private JButton selectFileButton;
    private JButton sendFileButton;
    private JButton downloadFileButton;
    private JButton refreshFilesButton;
    private JLabel selectedFileLabel;
    private JComboBox<String> fileListCombo;
    
    // Packet statistics
    private JLabel packetsSentLabel;
    private JLabel packetsReceivedLabel;
    private JLabel packetsLostLabel;
    private JLabel retransmissionsLabel;
    private JLabel congestionWindowLabel;
    
    // Statistics counters
    private int packetsSent = 0;
    private int packetsReceived = 0;
    private int packetsLost = 0;
    private int retransmissions = 0;
    private int congestionWindow = 1; // Slow start initial value
    
    // Network components
    private DatagramSocket client;
    private InetAddress serverAddr;
    private boolean isConnected = false;
    private int seq = 0;
    

    
    // File transfer components
    private File selectedFile = null;
    private String[] availableFiles = {};
    
    // Chunked download components
    private String downloadingFileName = null;
    private long downloadingFileSize = 0;
    private int downloadingExpectedChunks = 0;
    private int downloadingReceivedChunks = 0;
    private ByteArrayOutputStream downloadingFileBuffer = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new UDPClient().createAndShowGUI();
        });
    }
    
    private void createAndShowGUI() {
        frame = new JFrame("UDP Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setLocationRelativeTo(null);
        
        // Create components
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        messageField = new JTextField();
        messageField.setEnabled(false);
        
        serverIPField = new JTextField("127.0.0.1");
        serverIPField.setPreferredSize(new Dimension(150, 25));
        
        connectButton = new JButton("Kết nối");
        sendButton = new JButton("Gửi tin nhắn");
        sendButton.setEnabled(false);
        disconnectButton = new JButton("Ngắt kết nối");
        disconnectButton.setEnabled(false);
        
        selectFileButton = new JButton("Chọn file");
        selectFileButton.setEnabled(false);
        sendFileButton = new JButton("Gửi file");
        sendFileButton.setEnabled(false);
        selectedFileLabel = new JLabel("Chưa chọn file nào");
        selectedFileLabel.setForeground(Color.GRAY);
        
        fileListCombo = new JComboBox<>();
        fileListCombo.setPreferredSize(new Dimension(200, 25));
        fileListCombo.setEnabled(false);
        refreshFilesButton = new JButton("Làm mới");
        refreshFilesButton.setEnabled(false);
        downloadFileButton = new JButton("Tải file");
        downloadFileButton.setEnabled(false);
        
        // Create statistics labels
        packetsSentLabel = new JLabel("Gửi: 0");
        packetsReceivedLabel = new JLabel("Nhận: 0");
        packetsLostLabel = new JLabel("Mất: 0");
        retransmissionsLabel = new JLabel("Retransmit: 0");
        congestionWindowLabel = new JLabel("CWND: 1");
        
        // Style labels
        packetsSentLabel.setForeground(Color.GREEN);
        packetsReceivedLabel.setForeground(Color.BLUE);
        packetsLostLabel.setForeground(Color.RED);
        retransmissionsLabel.setForeground(Color.ORANGE);
        congestionWindowLabel.setForeground(Color.MAGENTA);
        
        // Layout
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel connectionPanel = new JPanel(new FlowLayout());
        connectionPanel.add(new JLabel("Server IP:"));
        connectionPanel.add(serverIPField);
        connectionPanel.add(connectButton);
        connectionPanel.add(disconnectButton);
        
        JPanel statsPanel = new JPanel(new FlowLayout());
        statsPanel.setBorder(BorderFactory.createTitledBorder("Thống kê gói tin"));
        statsPanel.add(packetsSentLabel);
        statsPanel.add(packetsReceivedLabel);
        statsPanel.add(packetsLostLabel);
        statsPanel.add(retransmissionsLabel);
        statsPanel.add(congestionWindowLabel);
        
        topPanel.add(connectionPanel, BorderLayout.NORTH);
        topPanel.add(statsPanel, BorderLayout.CENTER);
        
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filePanel.add(selectFileButton);
        filePanel.add(sendFileButton);
        filePanel.add(selectedFileLabel);
        
        JPanel downloadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        downloadPanel.add(new JLabel("File trên server:"));
        downloadPanel.add(fileListCombo);
        downloadPanel.add(refreshFilesButton);
        downloadPanel.add(downloadFileButton);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JLabel("Tin nhắn: "), BorderLayout.WEST);
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(filePanel, BorderLayout.NORTH);
        southPanel.add(downloadPanel, BorderLayout.CENTER);
        southPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(southPanel, BorderLayout.SOUTH);
        
        // Add event listeners
        connectButton.addActionListener(e -> connectToServer());
        disconnectButton.addActionListener(e -> disconnectFromServer());
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        selectFileButton.addActionListener(e -> selectFile());
        sendFileButton.addActionListener(e -> sendFile());
        refreshFilesButton.addActionListener(e -> refreshFileList());
        downloadFileButton.addActionListener(e -> downloadFile());
        
        frame.setVisible(true);
        log("UDP Client đã sẵn sàng. Vui lòng nhập IP server và nhấn 'Kết nối'");
    }
    
    private void connectToServer() {
        if (isConnected) {
            log("Đã kết nối rồi!");
            return;
        }
        
        try {
            String serverIP = serverIPField.getText().trim();
            if (serverIP.isEmpty()) {
                log("Vui lòng nhập IP server!");
                return;
            }
            
            serverAddr = InetAddress.getByName(serverIP);
            client = new DatagramSocket();
            client.setSoTimeout(TIMEOUT);

            log("Đang kết nối đến server " + serverIP + ":" + PORT + "...");
            
            // Start connection in background thread
            new Thread(() -> {
                try {
                    performHandshake();
                    SwingUtilities.invokeLater(() -> {
                        isConnected = true;
                        connectButton.setEnabled(false);
                        disconnectButton.setEnabled(true);
                        sendButton.setEnabled(true);
                        messageField.setEnabled(true);
                        selectFileButton.setEnabled(true);
                        refreshFilesButton.setEnabled(true);
                        fileListCombo.setEnabled(true);
                        messageField.requestFocus();
                        log("Kết nối thành công! Có thể gửi tin nhắn và file.");
                        refreshFileList(); // Auto refresh file list
                    });
                    
                    // Start listening for server responses
                    startResponseListener();
                    
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        log("Lỗi kết nối: " + e.getMessage());
                        disconnectFromServer();
                    });
                }
            }).start();
            
        } catch (Exception e) {
            log("Lỗi: " + e.getMessage());
        }
    }
    
    private void performHandshake() throws IOException {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                // Step 1: Send SYN
        sendMsg(client, "SYN", serverAddr, PORT);
        incrementPacketsSent();
        log("📤 Đã gửi: SYN");
        
        // Step 2: Receive SYN-ACK
        client.receive(packet);
        String msg = new String(packet.getData(), 0, packet.getLength());
        incrementPacketsReceived();
        log("📥 Đã nhận: " + msg);
        
        // Step 3: Send ACK
        sendMsg(client, "ACK", serverAddr, PORT);
        incrementPacketsSent();
        log("📤 Đã gửi: ACK");
        log("🤝 Handshake hoàn tất!");
        // Start slow start
        updateCongestionWindow(1);
    }
    
    private void sendMessage() {
        if (!isConnected) {
            log("Chưa kết nối đến server!");
            return;
        }
        
        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        
        messageField.setText("");
        
        // Send message in background thread
        new Thread(() -> {
            try {
                sendMessageWithRetry(message);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    log("Lỗi gửi tin nhắn: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void sendMessageWithRetry(String message) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        
        String data = seq + ":" + message;
                boolean acknowledged = false;

                        while (!acknowledged) {
            sendMsg(client, data, serverAddr, PORT);
            incrementPacketsSent();
            log("📤 Đã gửi packet [Seq=" + seq + "]");
            
            try {
                client.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                incrementPacketsReceived();
                
                if (msg.equals("ACK:" + seq)) {
                    log("📥 Đã nhận " + msg);
                    acknowledged = true;
                    seq++;
                    
                    // Simulate congestion control - slow start
                    if (congestionWindow < 64) { // Max window size
                        updateCongestionWindow(congestionWindow * 2);
                        log("🚀 Slow Start: CWND tăng lên " + congestionWindow);
                    }
                } else if (msg.startsWith("ACK:" + seq + ":")) {
                    // ACK with response data
                    String responseData = msg.substring(("ACK:" + seq + ":").length());
                    log("📥 Đã nhận ACK với response: " + responseData);
                    handleServerResponse(responseData);
                    acknowledged = true;
                    seq++;
                    
                    // Simulate congestion control - slow start
                    if (congestionWindow < 64) { // Max window size
                        updateCongestionWindow(congestionWindow * 2);
                        log("🚀 Slow Start: CWND tăng lên " + congestionWindow);
                    }
                } else {
                    // Handle other responses (like file list, download responses)
                    handleServerResponse(msg);
                }
            } catch (SocketTimeoutException e) {
                incrementPacketsLost();
                incrementRetransmissions();
                log("⏰ Timeout! Đang gửi lại... (Retransmit #" + retransmissions + ")");
                
                // Simulate packet loss - reduce congestion window
                if (congestionWindow > 1) {
                    updateCongestionWindow(congestionWindow / 2);
                    log("📉 Congestion Control: CWND giảm xuống " + congestionWindow + " do timeout");
                }
            }
        }
    }
    
    private void disconnectFromServer() {
        if (!isConnected) {
            return;
        }
        
        try {
            sendMsg(client, "FIN", serverAddr, PORT);
            log("Đã gửi FIN - Ngắt kết nối");
            client.close();
        } catch (Exception e) {
            log("Lỗi khi ngắt kết nối: " + e.getMessage());
        } finally {
            isConnected = false;
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            sendButton.setEnabled(false);
            messageField.setEnabled(false);
            selectFileButton.setEnabled(false);
            sendFileButton.setEnabled(false);
            refreshFilesButton.setEnabled(false);
            downloadFileButton.setEnabled(false);
            fileListCombo.setEnabled(false);
            selectedFile = null;
            selectedFileLabel.setText("Chưa chọn file nào");
            selectedFileLabel.setForeground(Color.GRAY);
            fileListCombo.removeAllItems();
            seq = 0;
            log("Đã ngắt kết nối");
            
            // Reset statistics
            resetStatistics();
        }
    }
    
    private void resetStatistics() {
        packetsSent = 0;
        packetsReceived = 0;
        packetsLost = 0;
        retransmissions = 0;
        congestionWindow = 1;
        updateStatisticsDisplay();
    }
    
    private void updateStatisticsDisplay() {
        SwingUtilities.invokeLater(() -> {
            packetsSentLabel.setText("Gửi: " + packetsSent);
            packetsReceivedLabel.setText("Nhận: " + packetsReceived);
            packetsLostLabel.setText("Mất: " + packetsLost);
            retransmissionsLabel.setText("Retransmit: " + retransmissions);
            congestionWindowLabel.setText("CWND: " + congestionWindow);
        });
    }
    
    private void incrementPacketsSent() {
        packetsSent++;
        updateStatisticsDisplay();
    }
    
    private void incrementPacketsReceived() {
        packetsReceived++;
        updateStatisticsDisplay();
    }
    
    private void incrementPacketsLost() {
        packetsLost++;
        updateStatisticsDisplay();
    }
    
    private void incrementRetransmissions() {
        retransmissions++;
        updateStatisticsDisplay();
    }
    
    private void updateCongestionWindow(int newValue) {
        congestionWindow = newValue;
        updateStatisticsDisplay();
    }
    
    private void selectFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn file để gửi");
        int result = fileChooser.showOpenDialog(frame);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            selectedFileLabel.setText("File: " + selectedFile.getName() + " (" + formatFileSize(selectedFile.length()) + ")");
            selectedFileLabel.setForeground(Color.BLUE);
            sendFileButton.setEnabled(true);
            log("Đã chọn file: " + selectedFile.getName());
        }
    }
    
    private void sendFile() {
        if (selectedFile == null) {
            log("Chưa chọn file nào!");
            return;
        }
        
        if (!isConnected) {
            log("Chưa kết nối đến server!");
            return;
        }
        
        // Send file in background thread
        new Thread(() -> {
            try {
                sendFileWithRetry();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    log("Lỗi gửi file: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void sendFileWithRetry() throws IOException, InterruptedException {
        byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
        String fileName = selectedFile.getName();
        long fileSize = selectedFile.length();
        
        log("🚀 Bắt đầu gửi file: " + fileName + " (" + formatFileSize(fileSize) + ")");
        
        // Send file info first
        String fileInfo = "FILE_INFO:" + fileName + ":" + fileSize;
        sendMessageWithRetry(fileInfo);
        
        // Send file data in chunks with congestion control
        int chunkSize = 512; // Smaller chunks for UDP
        int totalChunks = (int) Math.ceil((double) fileBytes.length / chunkSize);
        int currentWindow = congestionWindow;
        int chunksInFlight = 0;
        
        for (int i = 0; i < totalChunks; i++) {
            // Check congestion window
            while (chunksInFlight >= currentWindow) {
                Thread.sleep(50); // Wait for ACKs
                chunksInFlight = Math.max(0, chunksInFlight - 1);
            }
            
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, fileBytes.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(fileBytes, start, chunk, 0, chunk.length);
            
            String chunkData = "FILE_CHUNK:" + i + ":" + totalChunks + ":" + Base64.getEncoder().encodeToString(chunk);
            sendMessageWithRetry(chunkData);
            chunksInFlight++;
            
            log("📤 Đã gửi chunk " + (i + 1) + "/" + totalChunks + " (CWND: " + currentWindow + ", InFlight: " + chunksInFlight + ")");
        }
        
        // Send file complete signal
        sendMessageWithRetry("FILE_COMPLETE:" + fileName);
        log("✅ Hoàn thành gửi file: " + fileName);
    }
    
    private void refreshFileList() {
        if (!isConnected) {
            log("Chưa kết nối đến server!");
            return;
        }
        
        // Send request for file list in background thread
        new Thread(() -> {
            try {
                sendMessageWithRetry("LIST_FILES");
                log("Đã yêu cầu danh sách file từ server");
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    log("Lỗi yêu cầu danh sách file: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void downloadFile() {
        if (!isConnected) {
            log("Chưa kết nối đến server!");
            return;
        }
        
        String selectedFileName = (String) fileListCombo.getSelectedItem();
        if (selectedFileName == null || selectedFileName.isEmpty()) {
            log("Vui lòng chọn file để tải!");
            return;
        }
        
        // Send download request in background thread
        new Thread(() -> {
            try {
                sendMessageWithRetry("DOWNLOAD_FILE:" + selectedFileName);
                log("Đã yêu cầu tải file: " + selectedFileName);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    log("Lỗi yêu cầu tải file: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void handleFileListResponse(String fileList) {
        SwingUtilities.invokeLater(() -> {
            fileListCombo.removeAllItems();
            if (fileList.equals("NO_FILES")) {
                fileListCombo.addItem("Không có file nào");
                downloadFileButton.setEnabled(false);
            } else {
                String[] files = fileList.split(",");
                for (String file : files) {
                    if (!file.trim().isEmpty()) {
                        fileListCombo.addItem(file.trim());
                    }
                }
                downloadFileButton.setEnabled(true);
            }
            log("Đã cập nhật danh sách file từ server");
        });
    }
    
    private void startResponseListener() {
        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            while (isConnected) {
                try {
                    client.receive(packet);
                    String response = new String(packet.getData(), 0, packet.getLength());
                    
                    // Log để debug
                    if (response.startsWith("FILE_CHUNK_")) {
                        log("DEBUG: Nhận chunk response: " + response.substring(0, Math.min(50, response.length())) + "...");
                    }
                    
                    handleServerResponse(response);
                } catch (SocketTimeoutException e) {
                    // Timeout is normal, continue listening
                } catch (Exception e) {
                    if (isConnected) {
                        log("Lỗi nhận response: " + e.getMessage());
                    }
                }
            }
        }).start();
    }
    
    private void handleServerResponse(String response) {
        if (response.startsWith("FILE_DATA:")) {
            handleFileData(response);
        } else if (response.startsWith("FILE_CHUNKED:")) {
            handleFileChunked(response);
        } else if (response.startsWith("CHUNK_DATA:")) {
            handleChunkData(response);
        } else if (response.startsWith("CHUNK_ERROR:")) {
            handleChunkError(response);
        } else if (response.equals("NO_FILES") || response.contains(",")) {
            handleFileListResponse(response);
        } else if (response.equals("FILE_NOT_FOUND")) {
            log("File không tồn tại trên server");
        } else if (response.equals("FILE_ERROR")) {
            log("Lỗi tải file từ server");
        } else {
            log("Response từ server: " + response);
        }
    }
    
    private void handleFileData(String response) {
        try {
            // Parse: FILE_DATA:filename:size:base64data
            String[] parts = response.split(":", 4);
            if (parts.length >= 4) {
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                String encodedData = parts[3];
                
                log("Đã nhận file nhỏ: " + fileName + " (" + formatFileSize(fileSize) + ")");
                
                // Decode Base64 data
                byte[] fileData = Base64.getDecoder().decode(encodedData);
                
                // Show save dialog
                handleFileDownload(fileName, fileData);
                
            } else {
                log("Lỗi: Response file data không đúng định dạng");
            }
        } catch (Exception e) {
            log("Lỗi xử lý file data: " + e.getMessage());
        }
    }
    
    private void handleFileChunked(String response) {
        try {
            // Parse: FILE_CHUNKED:filename:size
            String[] parts = response.split(":");
            if (parts.length >= 3) {
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                log("Server đã chuẩn bị file lớn: " + fileName + " (" + formatFileSize(fileSize) + "). Bắt đầu tải chunks...");
                
                // Start active chunk downloader
                startActiveChunkDownloader(fileName, fileSize);
            }
        } catch (Exception e) {
            log("Lỗi xử lý file chunked: " + e.getMessage());
        }
    }
    
    private void startActiveChunkDownloader(String fileName, long fileSize) {
        new Thread(() -> {
            try {
                int chunkSize = 40000; // Same as server
                int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
                
                // Initialize download state
                downloadingFileName = fileName;
                downloadingFileSize = fileSize;
                downloadingExpectedChunks = totalChunks;
                downloadingReceivedChunks = 0;
                downloadingFileBuffer = new ByteArrayOutputStream();
                
                log("Bắt đầu tải file " + fileName + " (" + formatFileSize(fileSize) + ") - " + totalChunks + " chunks");
                
                // Request chunks one by one
                for (int i = 0; i < totalChunks; i++) {
                    requestChunk(fileName, i);
                    Thread.sleep(100); // Small delay between requests
                }
                
            } catch (Exception e) {
                log("Lỗi trong chunk downloader: " + e.getMessage());
            }
        }).start();
    }
    
    private void requestChunk(String fileName, int chunkIndex) {
        new Thread(() -> {
            try {
                String request = "REQUEST_CHUNK:" + fileName + ":" + chunkIndex;
                sendMessageWithRetry(request);
                log("📤 Đã yêu cầu chunk " + (chunkIndex + 1) + "/" + downloadingExpectedChunks);
            } catch (Exception e) {
                log("❌ Lỗi yêu cầu chunk " + chunkIndex + ": " + e.getMessage());
            }
        }).start();
    }
    
    private void handleFileChunkInfo(String response) {
        try {
            // Parse: FILE_CHUNK_INFO:filename:size:totalChunks
            String[] parts = response.split(":");
            if (parts.length >= 4) {
                downloadingFileName = parts[1];
                downloadingFileSize = Long.parseLong(parts[2]);
                downloadingExpectedChunks = Integer.parseInt(parts[3]);
                downloadingReceivedChunks = 0;
                downloadingFileBuffer = new ByteArrayOutputStream();
                
                log("Bắt đầu tải file lớn: " + downloadingFileName + " (" + formatFileSize(downloadingFileSize) + ") - " + downloadingExpectedChunks + " chunks");
            }
        } catch (Exception e) {
            log("Lỗi xử lý file chunk info: " + e.getMessage());
        }
    }
    
    private void handleChunkData(String response) {
        if (downloadingFileBuffer == null) {
            log("❌ Lỗi: Chưa nhận thông tin file chunk!");
            return;
        }
        
        try {
            // Parse: CHUNK_DATA:chunkIndex:totalChunks:base64data
            String[] parts = response.split(":", 4);
            if (parts.length >= 4) {
                int chunkIndex = Integer.parseInt(parts[1]);
                int totalChunks = Integer.parseInt(parts[2]);
                String chunkData = parts[3];
                
                // Decode and write chunk
                byte[] chunkBytes = Base64.getDecoder().decode(chunkData);
                downloadingFileBuffer.write(chunkBytes);
                downloadingReceivedChunks++;
                
                log("📥 Đã nhận chunk " + downloadingReceivedChunks + "/" + downloadingExpectedChunks);
                
                // Check if download is complete
                if (downloadingReceivedChunks >= downloadingExpectedChunks) {
                    log("✅ Hoàn thành tải file! Đang lưu...");
                    byte[] fileData = downloadingFileBuffer.toByteArray();
                    handleFileDownload(downloadingFileName, fileData);
                    
                    // Reset download state
                    downloadingFileName = null;
                    downloadingFileSize = 0;
                    downloadingExpectedChunks = 0;
                    downloadingReceivedChunks = 0;
                    downloadingFileBuffer = null;
                }
            }
        } catch (Exception e) {
            log("❌ Lỗi xử lý chunk data: " + e.getMessage());
        }
    }
    
    private void handleChunkError(String response) {
        String error = response.substring("CHUNK_ERROR:".length());
        log("Lỗi chunk: " + error);
    }
    
    private void handleFileDownload(String fileName, byte[] fileData) {
        SwingUtilities.invokeLater(() -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(fileName));
            fileChooser.setDialogTitle("Lưu file");
            int result = fileChooser.showSaveDialog(frame);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File saveFile = fileChooser.getSelectedFile();
                try {
                    Files.write(saveFile.toPath(), fileData);
                    log("Đã tải file thành công: " + saveFile.getName() + " (" + formatFileSize(saveFile.length()) + ")");
                } catch (Exception e) {
                    log("Lỗi lưu file: " + e.getMessage());
                }
            }
        });
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalTime.now().toString().substring(0, 8) + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private static void sendMsg(DatagramSocket socket, String msg, InetAddress addr, int port) throws IOException {
        DatagramPacket packet = new DatagramPacket(
                msg.getBytes(),
                msg.length(),
                addr,
                port
        );
        socket.send(packet);
    }
}
