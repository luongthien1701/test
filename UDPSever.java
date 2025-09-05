package src;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.*;
import java.util.Base64;

public class UDPSever {
    public final static int PORT = 3000;
    public final static int BUFFER_SIZE = 1024;
    
    // GUI Components
    private JFrame frame;
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private JLabel statusLabel;
    
    // Packet statistics
    private JLabel packetsSentLabel;
    private JLabel packetsReceivedLabel;
    private JLabel packetsLostLabel;
    private JLabel retransmissionsLabel;
    private JLabel congestionWindowLabel;
    private JLabel connectedClientsLabel;
    
    // Statistics counters
    private int packetsSent = 0;
    private int packetsReceived = 0;
    private int packetsLost = 0;
    private int retransmissions = 0;
    private int congestionWindow = 1; // Slow start initial value
    
    // Network components
    private DatagramSocket server;
    private boolean isRunning = false;
    private Thread serverThread;
    
    // Multi-client support
    private java.util.Map<String, ClientSession> clientSessions = new java.util.concurrent.ConcurrentHashMap<>();
    private java.util.concurrent.ExecutorService clientExecutor = java.util.concurrent.Executors.newCachedThreadPool();
    
    // File transfer components (moved to ClientSession)
    // These will be per-client now

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new UDPSever().createAndShowGUI();
        });
    }
    
    private void createAndShowGUI() {
        frame = new JFrame("UDP Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 600);
        frame.setLocationRelativeTo(null);
        
        // Create components
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        startButton = new JButton("Khởi động Server");
        stopButton = new JButton("Dừng Server");
        stopButton.setEnabled(false);
        
        statusLabel = new JLabel("Trạng thái: Chưa khởi động");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        // Create statistics labels
        packetsSentLabel = new JLabel("Gửi: 0");
        packetsReceivedLabel = new JLabel("Nhận: 0");
        packetsLostLabel = new JLabel("Mất: 0");
        retransmissionsLabel = new JLabel("Retransmit: 0");
        congestionWindowLabel = new JLabel("CWND: 1");
        connectedClientsLabel = new JLabel("Clients: 0");
        
        // Style labels
        packetsSentLabel.setForeground(Color.GREEN);
        packetsReceivedLabel.setForeground(Color.BLUE);
        packetsLostLabel.setForeground(Color.RED);
        retransmissionsLabel.setForeground(Color.ORANGE);
        congestionWindowLabel.setForeground(Color.MAGENTA);
        connectedClientsLabel.setForeground(Color.DARK_GRAY);
        
        // Layout
        JPanel controlPanel = new JPanel(new BorderLayout());
        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(statusLabel);
        topPanel.add(startButton);
        topPanel.add(stopButton);
        
        JPanel statsPanel = new JPanel(new FlowLayout());
        statsPanel.setBorder(BorderFactory.createTitledBorder("Thống kê gói tin"));
        statsPanel.add(packetsSentLabel);
        statsPanel.add(packetsReceivedLabel);
        statsPanel.add(packetsLostLabel);
        statsPanel.add(retransmissionsLabel);
        statsPanel.add(congestionWindowLabel);
        statsPanel.add(connectedClientsLabel);
        
        controlPanel.add(topPanel, BorderLayout.NORTH);
        controlPanel.add(statsPanel, BorderLayout.CENTER);
        
        frame.setLayout(new BorderLayout());
        frame.add(controlPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        
        // Add event listeners
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());
        
        frame.setVisible(true);
        log("UDP Server đã sẵn sàng. Nhấn 'Khởi động Server' để bắt đầu.");
    }
    
    private void startServer() {
        if (isRunning) {
            log("Server đã đang chạy!");
            return;
        }
        
        try {
            server = new DatagramSocket(PORT);
            isRunning = true;
            
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            statusLabel.setText("Trạng thái: Đang chạy trên port " + PORT);
            
            log("Server đã khởi động và đang lắng nghe trên port " + PORT);
            
            // Start server in background thread
            serverThread = new Thread(this::runServer);
            serverThread.start();
            
        } catch (SocketException e) {
            log("Lỗi khởi động server: " + e.getMessage());
            isRunning = false;
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        }
    }
    
    private void stopServer() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        if (server != null && !server.isClosed()) {
            server.close();
        }
        
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
        }
        
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        statusLabel.setText("Trạng thái: Đã dừng");
        log("Server đã dừng.");
        
        // Reset statistics
        resetStatistics();
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
            connectedClientsLabel.setText("Clients: " + clientSessions.size());
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
    
    private void runServer() {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            log("🚀 Server đã sẵn sàng nhận nhiều client đồng thời!");

            while (isRunning) {
                try {
                    // Nhận packet từ bất kỳ client nào
                    server.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    incrementPacketsReceived();
                    
                    String clientId = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                    log("📥 Đã nhận từ " + clientId + ": " + msg);

                    // Xử lý packet trong thread riêng cho mỗi client
                    clientExecutor.submit(() -> handleClientPacket(packet.getAddress(), packet.getPort(), msg));
                    
                } catch (IOException e) {
                    if (isRunning) {
                        incrementPacketsLost();
                        log("❌ Lỗi nhận dữ liệu: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            if (isRunning) {
                log("Lỗi server: " + e.getMessage());
            }
        } finally {
            if (server != null && !server.isClosed()) {
                server.close();
            }
            clientExecutor.shutdown();
        }
    }
    
    private void handleClientPacket(InetAddress clientAddress, int clientPort, String msg) {
        try {
            String clientId = clientAddress.getHostAddress() + ":" + clientPort;
            ClientSession session = getOrCreateClientSession(clientAddress, clientPort);
            
            if (msg.equals("SYN")) {
                // Handshake Step 1: SYN received
                sendResponse("SYN-ACK", clientAddress, clientPort);
                log("🤝 SYN từ " + clientId + " - đã gửi SYN-ACK");
                
            } else if (msg.equals("ACK")) {
                // Handshake Step 3: ACK received
                log("✅ Handshake hoàn tất với " + clientId + "!");
                session.setCongestionWindow(1);
                
            } else if (msg.equals("FIN")) {
                // Client disconnect
                log("🔚 Client " + clientId + " đã ngắt kết nối");
                clientSessions.remove(clientId);
                
            } else if (msg.contains(":")) {
                // Data packet: seq:data
                String[] parts = msg.split(":", 2);
                if (parts.length == 2) {
                    int seq = Integer.parseInt(parts[0]);
                    String data = parts[1];
                    
                    log("📦 Đã nhận packet [Seq=" + seq + "] từ " + clientId + ": " + data);
                    
                    // Process data and send response
                    String response = processDataWithResponse(data, clientAddress, clientPort, session);
                    String ackMsg = response != null ? "ACK:" + seq + ":" + response : "ACK:" + seq;
                    sendResponse(ackMsg, clientAddress, clientPort);
                    
                    // Update congestion window for this client
                    if (session.getCongestionWindow() < 64) {
                        session.setCongestionWindow(session.getCongestionWindow() * 2);
                        log("🚀 Slow Start cho " + clientId + ": CWND = " + session.getCongestionWindow());
                    }
                    
                    session.incrementExpectedSeq();
                }
            }
            
        } catch (Exception e) {
            log("❌ Lỗi xử lý packet từ " + clientAddress.getHostAddress() + ":" + clientPort + ": " + e.getMessage());
        }
    }
    
    private ClientSession getOrCreateClientSession(InetAddress address, int port) {
        String clientId = address.getHostAddress() + ":" + port;
        return clientSessions.computeIfAbsent(clientId, k -> {
            log("🆕 Client mới kết nối: " + clientId);
            return new ClientSession(address, port);
        });
    }
    
    private void sendResponse(String response, InetAddress address, int port) {
        try {
            DatagramPacket responsePacket = new DatagramPacket(
                response.getBytes(),
                response.length(),
                address,
                port
            );
            server.send(responsePacket);
            incrementPacketsSent();
            log("📤 Đã gửi đến " + address.getHostAddress() + ":" + port + ": " + response);
        } catch (Exception e) {
            log("❌ Lỗi gửi response: " + e.getMessage());
        }
    }
    
    private String processDataWithResponse(String data, InetAddress clientAddress, int clientPort, ClientSession session) {
        if (data.startsWith("FILE_INFO:")) {
            handleFileInfo(data, session);
            return null;
        } else if (data.startsWith("FILE_CHUNK:")) {
            handleFileChunk(data, session);
            return null;
        } else if (data.startsWith("FILE_COMPLETE:")) {
            handleFileComplete(data, session);
            return null;
        } else if (data.equals("LIST_FILES")) {
            return handleListFilesRequest(clientAddress, clientPort);
        } else if (data.startsWith("DOWNLOAD_FILE:")) {
            return handleDownloadFileRequest(data, clientAddress, clientPort);
        } else if (data.startsWith("REQUEST_CHUNK:")) {
            return handleChunkRequest(data, clientAddress, clientPort);
        } else {
            // Regular message
            log("💬 Tin nhắn từ " + session.getClientId() + ": " + data);
            return null;
        }
    }
    
    private void processData(String data, InetAddress clientAddress, int clientPort) {
        processDataWithResponse(data, clientAddress, clientPort, null);
    }
    
    private void handleFileInfo(String data, ClientSession session) {
        String[] parts = data.split(":");
        if (parts.length >= 3) {
            String fileName = parts[1];
            long fileSize = Long.parseLong(parts[2]);
            session.setFileInfo(fileName, fileSize);
            log("📁 Bắt đầu nhận file từ " + session.getClientId() + ": " + fileName + " (" + formatFileSize(fileSize) + ")");
        }
    }
    
    private void handleFileChunk(String data, ClientSession session) {
        String[] parts = data.split(":");
        if (parts.length >= 4) {
            int chunkIndex = Integer.parseInt(parts[1]);
            int totalChunks = Integer.parseInt(parts[2]);
            String chunkData = parts[3];
            
            if (session.getCurrentFileName() == null) {
                log("❌ Lỗi: " + session.getClientId() + " chưa nhận thông tin file!");
                return;
            }
            
            if (session.getExpectedChunks() == 0) {
                session.setExpectedChunks(totalChunks);
            }
            
            try {
                byte[] chunkBytes = Base64.getDecoder().decode(chunkData);
                session.addFileChunk(chunkBytes);
                
                log("📦 Đã nhận chunk " + session.getReceivedChunks() + "/" + session.getExpectedChunks() + " từ " + session.getClientId());
            } catch (Exception e) {
                log("❌ Lỗi xử lý chunk từ " + session.getClientId() + ": " + e.getMessage());
            }
        }
    }
    
    private void handleFileComplete(String data, ClientSession session) {
        if (session.getCurrentFileName() == null) {
            log("❌ Lỗi: " + session.getClientId() + " không có file để lưu!");
            return;
        }
        
        try {
            // Create received_files directory if it doesn't exist
            Path receivedDir = Paths.get("received_files");
            if (!Files.exists(receivedDir)) {
                Files.createDirectories(receivedDir);
            }
            
            // Save file
            Path filePath = receivedDir.resolve(session.getCurrentFileName());
            byte[] fileData = session.getFileData();
            if (fileData != null) {
                Files.write(filePath, fileData);
                log("✅ Đã lưu file từ " + session.getClientId() + ": " + session.getCurrentFileName() + " (" + formatFileSize(filePath.toFile().length()) + ")");
            }
            
            // Reset file transfer state for this client
            session.resetFileTransfer();
            
        } catch (Exception e) {
            log("❌ Lỗi lưu file từ " + session.getClientId() + ": " + e.getMessage());
        }
    }
    
    private String handleListFilesRequest(InetAddress clientAddress, int clientPort) {
        try {
            Path receivedDir = Paths.get("received_files");
            if (!Files.exists(receivedDir)) {
                log("Thư mục received_files không tồn tại");
                return "NO_FILES";
            }
            
            StringBuilder fileList = new StringBuilder();
            Files.list(receivedDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    if (fileList.length() > 0) fileList.append(",");
                    fileList.append(path.getFileName().toString());
                });
            
            String response = fileList.length() > 0 ? fileList.toString() : "NO_FILES";
            log("Đã chuẩn bị danh sách file cho " + clientAddress.getHostAddress() + ": " + response);
            return response;
            
        } catch (Exception e) {
            log("Lỗi xử lý yêu cầu danh sách file: " + e.getMessage());
            return "NO_FILES";
        }
    }
    
    private String handleDownloadFileRequest(String data, InetAddress clientAddress, int clientPort) {
        try {
            String fileName = data.substring("DOWNLOAD_FILE:".length());
            Path filePath = Paths.get("received_files", fileName);
            
            if (!Files.exists(filePath)) {
                log("File không tồn tại: " + fileName);
                return "FILE_NOT_FOUND";
            }
            
            byte[] fileData = Files.readAllBytes(filePath);
            long fileSize = fileData.length;
            
            // Check if file is too large for single UDP packet (limit to ~50KB)
            if (fileSize > 50000) {
                // File too large, start chunked transfer
                startChunkedFileTransfer(fileName, fileData, clientAddress, clientPort);
                return "FILE_CHUNKED:" + fileName + ":" + fileSize;
            } else {
                // Small file, send directly
                String encodedFileData = Base64.getEncoder().encodeToString(fileData);
                String response = "FILE_DATA:" + fileName + ":" + fileSize + ":" + encodedFileData;
                log("Đã gửi file nhỏ " + fileName + " (" + formatFileSize(fileSize) + ") cho " + clientAddress.getHostAddress());
                return response;
            }
            
        } catch (Exception e) {
            log("Lỗi xử lý yêu cầu tải file: " + e.getMessage());
            return "FILE_ERROR";
        }
    }
    
    // Add method to handle chunk requests from client
    private String handleChunkRequest(String data, InetAddress clientAddress, int clientPort) {
        try {
            // Parse: REQUEST_CHUNK:filename:chunkIndex
            String[] parts = data.split(":");
            if (parts.length >= 3) {
                String fileName = parts[1];
                int chunkIndex = Integer.parseInt(parts[2]);
                
                Path filePath = Paths.get("received_files", fileName);
                if (!Files.exists(filePath)) {
                    return "CHUNK_ERROR:File not found";
                }
                
                byte[] fileData = Files.readAllBytes(filePath);
                int chunkSize = 40000;
                int totalChunks = (int) Math.ceil((double) fileData.length / chunkSize);
                
                if (chunkIndex >= totalChunks) {
                    return "CHUNK_ERROR:Invalid chunk index";
                }
                
                int start = chunkIndex * chunkSize;
                int end = Math.min(start + chunkSize, fileData.length);
                byte[] chunk = new byte[end - start];
                System.arraycopy(fileData, start, chunk, 0, chunk.length);
                
                String encodedChunk = Base64.getEncoder().encodeToString(chunk);
                String response = "CHUNK_DATA:" + chunkIndex + ":" + totalChunks + ":" + encodedChunk;
                
                log("Đã gửi chunk " + (chunkIndex + 1) + "/" + totalChunks + " cho " + clientAddress.getHostAddress());
                return response;
            }
        } catch (Exception e) {
            log("Lỗi xử lý yêu cầu chunk: " + e.getMessage());
        }
        return "CHUNK_ERROR:Invalid request";
    }
    
    private void startChunkedFileTransfer(String fileName, byte[] fileData, InetAddress clientAddress, int clientPort) {
        new Thread(() -> {
            try {
                int chunkSize = 40000; // ~40KB per chunk (safe for UDP)
                int totalChunks = (int) Math.ceil((double) fileData.length / chunkSize);
                
                log("🚀 Bắt đầu gửi file lớn " + fileName + " (" + formatFileSize(fileData.length) + ") trong " + totalChunks + " chunks");
                
                // Send file info first
                String fileInfo = "FILE_CHUNK_INFO:" + fileName + ":" + fileData.length + ":" + totalChunks;
                sendChunkedResponse(fileInfo, clientAddress, clientPort);
                Thread.sleep(200); // Wait for client to process
                
                // Send chunks with congestion control
                int currentWindow = congestionWindow;
                int chunksInFlight = 0;
                
                for (int i = 0; i < totalChunks; i++) {
                    // Check congestion window
                    while (chunksInFlight >= currentWindow) {
                        Thread.sleep(50); // Wait for ACKs
                        chunksInFlight = Math.max(0, chunksInFlight - 1);
                    }
                    
                    int start = i * chunkSize;
                    int end = Math.min(start + chunkSize, fileData.length);
                    byte[] chunk = new byte[end - start];
                    System.arraycopy(fileData, start, chunk, 0, chunk.length);
                    
                    String encodedChunk = Base64.getEncoder().encodeToString(chunk);
                    String chunkData = "FILE_CHUNK_DATA:" + i + ":" + totalChunks + ":" + encodedChunk;
                    
                    // Send chunk with retry mechanism
                    boolean chunkSent = false;
                    int retries = 0;
                    while (!chunkSent && retries < 3) {
                        sendChunkedResponse(chunkData, clientAddress, clientPort);
                        chunksInFlight++;
                        log("📤 Đã gửi chunk " + (i + 1) + "/" + totalChunks + " (CWND: " + currentWindow + ", InFlight: " + chunksInFlight + ")");
                        
                        if (retries > 0) {
                            incrementRetransmissions();
                            log("🔄 Retransmit chunk " + (i + 1) + " (lần " + (retries + 1) + ")");
                        }
                        
                        // Wait based on congestion window
                        Thread.sleep(100 * currentWindow);
                        retries++;
                    }
                    
                    if (!chunkSent) {
                        incrementPacketsLost();
                        log("❌ Lỗi: Không thể gửi chunk " + (i + 1) + " sau " + retries + " lần thử");
                        // Reduce congestion window on failure
                        currentWindow = Math.max(1, currentWindow / 2);
                        updateCongestionWindow(currentWindow);
                        return;
                    }
                }
                
                // Send complete signal
                sendChunkedResponse("FILE_CHUNK_COMPLETE:" + fileName, clientAddress, clientPort);
                log("✅ Hoàn thành gửi file " + fileName + " cho " + clientAddress.getHostAddress());
                
            } catch (Exception e) {
                log("❌ Lỗi gửi file chunked: " + e.getMessage());
            }
        }).start();
    }
    
    private void sendChunkedResponse(String response, InetAddress clientAddress, int clientPort) {
        try {
            DatagramPacket responsePacket = new DatagramPacket(
                response.getBytes(),
                response.length(),
                clientAddress,
                clientPort
            );
            server.send(responsePacket);
        } catch (Exception e) {
            log("Lỗi gửi chunked response: " + e.getMessage());
        }
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
    
    // ClientSession class to manage individual client connections
    private static class ClientSession {
        private InetAddress address;
        private int port;
        private String clientId;
        private int expectedSeq = 0;
        private int congestionWindow = 1;
        
        // File transfer components for this client
        private String currentFileName = null;
        private long currentFileSize = 0;
        private int expectedChunks = 0;
        private int receivedChunks = 0;
        private ByteArrayOutputStream fileBuffer = null;
        
        public ClientSession(InetAddress address, int port) {
            this.address = address;
            this.port = port;
            this.clientId = address.getHostAddress() + ":" + port;
        }
        
        public String getClientId() {
            return clientId;
        }
        
        public InetAddress getAddress() {
            return address;
        }
        
        public int getPort() {
            return port;
        }
        
        public int getExpectedSeq() {
            return expectedSeq;
        }
        
        public void incrementExpectedSeq() {
            expectedSeq++;
        }
        
        public int getCongestionWindow() {
            return congestionWindow;
        }
        
        public void setCongestionWindow(int value) {
            congestionWindow = value;
        }
        
        // File transfer methods
        public void setFileInfo(String fileName, long fileSize) {
            this.currentFileName = fileName;
            this.currentFileSize = fileSize;
            this.fileBuffer = new ByteArrayOutputStream();
            this.receivedChunks = 0;
            this.expectedChunks = 0;
        }
        
        public void addFileChunk(byte[] chunkData) {
            if (fileBuffer != null) {
                try {
                    fileBuffer.write(chunkData);
                    receivedChunks++;
                } catch (Exception e) {
                    // Handle error
                }
            }
        }
        
        public void setExpectedChunks(int total) {
            this.expectedChunks = total;
        }
        
        public int getExpectedChunks() {
            return expectedChunks;
        }
        
        public int getReceivedChunks() {
            return receivedChunks;
        }
        
        public boolean isFileComplete() {
            return fileBuffer != null && receivedChunks >= expectedChunks && expectedChunks > 0;
        }
        
        public byte[] getFileData() {
            return fileBuffer != null ? fileBuffer.toByteArray() : null;
        }
        
        public String getCurrentFileName() {
            return currentFileName;
        }
        
        public void resetFileTransfer() {
            currentFileName = null;
            currentFileSize = 0;
            expectedChunks = 0;
            receivedChunks = 0;
            fileBuffer = null;
        }
    }
}

