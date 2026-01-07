package com.zkteco.biometric;

import com.formdev.flatlaf.FlatLightLaf;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ModernZKFingerDemo extends JFrame {

    // --- Constantes SDK ---
    private static final int TEMPLATE_SIZE = 2048;
    private static final int ENROLL_COUNT = 3;
    private static final int PARAM_SIZE = 4;
    
    // --- Composants UI ---
    private JLabel lblImageFinger;
    private JTextArea txtLog;
    private JButton btnOpen, btnClose, btnEnroll, btnVerify, btnSaveImg;
    private JLabel lblStatus;
    
    // --- Variables d'état ---
    private boolean mbStop = true;
    private long mhDevice = 0;
    private long mhDB = 0;
    private WorkThread workThread = null;
    
    // Données biométriques
    private int fpWidth = 0;
    private int fpHeight = 0;
    private byte[] imgbuf = null;
    private byte[] lastCapturedImage = null;
    private byte[] template = new byte[TEMPLATE_SIZE];
    private int[] templateLen = new int[1];
    
    // Enrôlement
    private boolean bRegister = false;
    private boolean bIdentify = false;
    private int enroll_idx = 0;
    private int iFid = 1;
    private byte[][] regtemparray = new byte[ENROLL_COUNT][TEMPLATE_SIZE];
    private byte[] lastRegTemp = new byte[TEMPLATE_SIZE];
    private int cbRegTemp = 0;

    public ModernZKFingerDemo() {
        initUI();
    }

    private void initUI() {
        setTitle("ZKTeco Fingerprint Manager");
        setSize(1024, 768); // Fenêtre un peu plus grande par défaut
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Layout principal
        setLayout(new BorderLayout(10, 10));
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        add(mainPanel);

        // --- 1. Panneau Gauche : Contrôles ---
        JPanel controlPanel = new JPanel(new GridLayout(0, 1, 5, 10));
        controlPanel.setBorder(new TitledBorder("Actions"));
        controlPanel.setPreferredSize(new Dimension(220, 0)); // Légèrement plus large

        btnOpen = new JButton("Connecter Appareil");
        btnClose = new JButton("Déconnecter");
        btnEnroll = new JButton("Nouvel Enrôlement");
        btnVerify = new JButton("Vérifier (1:1)");
        btnSaveImg = new JButton("Exporter en JPEG/PNG");
        
        styleButton(btnOpen);
        styleButton(btnClose);
        
        btnClose.setEnabled(false);
        btnEnroll.setEnabled(false);
        btnVerify.setEnabled(false);
        btnSaveImg.setEnabled(false);

        controlPanel.add(btnOpen);
        controlPanel.add(btnEnroll);
        controlPanel.add(btnVerify);
        controlPanel.add(new JSeparator());
        controlPanel.add(btnSaveImg);
        controlPanel.add(new JSeparator());
        controlPanel.add(btnClose);

        mainPanel.add(controlPanel, BorderLayout.WEST);

        // --- 2. Panneau Central : Image (CORRECTION TAILLE) ---
        // On utilise BorderLayout ici pour que l'image prenne TOUT l'espace disponible
        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setBorder(new TitledBorder("Aperçu Empreinte"));
        imagePanel.setBackground(Color.WHITE);
        
        lblImageFinger = new JLabel("En attente...", SwingConstants.CENTER);
        lblImageFinger.setHorizontalAlignment(JLabel.CENTER);
        lblImageFinger.setVerticalAlignment(JLabel.CENTER);
        // On retire la taille préférée fixe pour laisser le layout gérer
        imagePanel.add(lblImageFinger, BorderLayout.CENTER);
        
        mainPanel.add(imagePanel, BorderLayout.CENTER);

        // --- 3. Panneau Bas : Logs et Status ---
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        txtLog = new JTextArea(6, 50);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollLog = new JScrollPane(txtLog);
        scrollLog.setBorder(new TitledBorder("Journaux d'événements"));
        
        lblStatus = new JLabel(" Déconnecté");
        
        bottomPanel.add(scrollLog, BorderLayout.CENTER);
        bottomPanel.add(lblStatus, BorderLayout.SOUTH);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setupActions();
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                freeSensor();
            }
        });
    }
    
    private void styleButton(JButton btn) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
    }

    private void setupActions() {
        btnOpen.addActionListener(e -> onOpenDevice());
        btnClose.addActionListener(e -> onCloseDevice());
        
        btnEnroll.addActionListener(e -> {
            if (mhDevice == 0) return;
            // Réinitialisation stricte
            bRegister = false; 
            enroll_idx = 0;
            // Activation
            bRegister = true;
            log("Mode Enrôlement : Veuillez poser le doigt 3 fois.");
            bIdentify = false;
        });

        btnVerify.addActionListener(e -> {
            if (bRegister) {
                bRegister = false;
                log("Mode Enrôlement annulé.");
            }
            bIdentify = !bIdentify;
            log(bIdentify ? "Mode Identification (Scan continu) activé." : "Mode Identification désactivé.");
        });

        btnSaveImg.addActionListener(e -> exportImage());
    }

    private void onOpenDevice() {
        if (mhDevice != 0) return;

        try {
            log("Initialisation du driver...");
            int ret = FingerprintSensorEx.Init();
            if (ret != 0) {
                logError("Echec Init", ret);
                return;
            }

            if (FingerprintSensorEx.GetDeviceCount() < 0) {
                log("Aucun appareil détecté.");
                return;
            }

            mhDevice = FingerprintSensorEx.OpenDevice(0);
            if (mhDevice == 0) {
                log("Impossible d'ouvrir l'appareil.");
                return;
            }

            mhDB = FingerprintSensorEx.DBInit();
            if (mhDB == 0) {
                log("Erreur initialisation Base de données interne.");
                FingerprintSensorEx.CloseDevice(mhDevice);
                mhDevice = 0;
                return;
            }

            byte[] paramValue = new byte[PARAM_SIZE];
            int[] size = new int[1];
            
            size[0] = PARAM_SIZE;
            FingerprintSensorEx.GetParameters(mhDevice, 1, paramValue, size);
            fpWidth = byteArrayToInt(paramValue);

            size[0] = PARAM_SIZE;
            FingerprintSensorEx.GetParameters(mhDevice, 2, paramValue, size);
            fpHeight = byteArrayToInt(paramValue);

            imgbuf = new byte[fpWidth * fpHeight];
            
            mbStop = false;
            workThread = new WorkThread();
            workThread.start();
            
            updateUIState(true);
            log("Appareil connecté avec succès (" + fpWidth + "x" + fpHeight + ")");

        } catch (Exception ex) {
            log("Erreur critique: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void onCloseDevice() {
        freeSensor();
        updateUIState(false);
        log("Appareil déconnecté.");
    }

    private void freeSensor() {
        mbStop = true;
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) { e.printStackTrace(); }
        
        if (mhDB != 0) {
            FingerprintSensorEx.DBFree(mhDB);
            mhDB = 0;
        }
        if (mhDevice != 0) {
            FingerprintSensorEx.CloseDevice(mhDevice);
            mhDevice = 0;
        }
        FingerprintSensorEx.Terminate();
    }
    
    private class WorkThread extends Thread {
        @Override
        public void run() {
            super.run();
            int ret = 0;
            while (!mbStop) {
                templateLen[0] = TEMPLATE_SIZE;
                ret = FingerprintSensorEx.AcquireFingerprint(mhDevice, imgbuf, template, templateLen);
                
                if (ret == 0) {
                    final byte[] currentImgCopy = new byte[imgbuf.length];
                    System.arraycopy(imgbuf, 0, currentImgCopy, 0, imgbuf.length);
                    lastCapturedImage = currentImgCopy;

                    SwingUtilities.invokeLater(() -> displayFingerprintImage(currentImgCopy, fpWidth, fpHeight));
                    
                    final byte[] currentTemplateCopy = new byte[templateLen[0]];
                    System.arraycopy(template, 0, currentTemplateCopy, 0, templateLen[0]);
                    
                    SwingUtilities.invokeLater(() -> processFingerprintLogic(currentTemplateCopy));
                }
                
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) { e.printStackTrace(); }
            }
        }
    }

    private void displayFingerprintImage(byte[] rawData, int width, int height) {
        if (rawData == null) return;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = image.getRaster();
        raster.setDataElements(0, 0, width, height, rawData);
        
        // CORRECTION TAILLE: On calcule la taille disponible réelle
        int labelWidth = lblImageFinger.getWidth();
        int labelHeight = lblImageFinger.getHeight();
        
        // Si le label n'est pas encore affiché (0), on met une taille par défaut
        if (labelWidth == 0) labelWidth = 300;
        if (labelHeight == 0) labelHeight = 400;

        // On redimensionne l'image pour qu'elle remplisse la zone tout en gardant les proportions
        Image scaled = getScaledImage(image, labelWidth, labelHeight);
        
        lblImageFinger.setIcon(new ImageIcon(scaled));
        lblImageFinger.setText(""); 
        btnSaveImg.setEnabled(true);
    }
    
    // Fonction utilitaire pour garder les proportions
    private Image getScaledImage(Image srcImg, int w, int h){
        BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resizedImg.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        // Calcul du ratio pour "Fit Center"
        double xScale = (double) w / srcImg.getWidth(null);
        double yScale = (double) h / srcImg.getHeight(null);
        double scale = Math.min(xScale, yScale);
        
        int width = (int) (scale * srcImg.getWidth(null));
        int height = (int) (scale * srcImg.getHeight(null));
        
        int x = (w - width) / 2;
        int y = (h - height) / 2;
        
        g2.drawImage(srcImg, x, y, width, height, null);
        g2.dispose();
        
        return resizedImg;
    }

    private void processFingerprintLogic(byte[] captureTemplate) {
        // Validation de sécurité
        if (captureTemplate == null || captureTemplate.length == 0) {
            log("Erreur: Template invalide ou vide.");
            return;
        }
        
        if (bRegister) {
            // CORRECTION CRASH: Vérification de l'index avant toute manipulation
            if (enroll_idx >= ENROLL_COUNT) {
                bRegister = false;
                enroll_idx = 0;
                log("État incohérent : reset forcé de l'enrôlement.");
                return;
            }

            int[] fid = new int[1];
            int[] score = new int[1];
            int ret = FingerprintSensorEx.DBIdentify(mhDB, captureTemplate, fid, score);
            if (ret == 0) {
                log("Attention: Ce doigt est déjà enregistré sous l'ID " + fid[0]);
                bRegister = false;
                enroll_idx = 0;
                return;
            }

            if (enroll_idx > 0 && FingerprintSensorEx.DBMatch(mhDB, regtemparray[enroll_idx - 1], captureTemplate) <= 0) {
                log("Erreur: Doigt différent ou mal placé. Réessayez.");
                return;
            }

            // Copie sécurisée - utiliser la taille réelle du template
            int copyLength = Math.min(captureTemplate.length, TEMPLATE_SIZE);
            System.arraycopy(captureTemplate, 0, regtemparray[enroll_idx], 0, copyLength);
            // Si le template est plus petit, le reste reste à zéro (déjà initialisé)
            enroll_idx++;
            log("Capture " + enroll_idx + "/" + ENROLL_COUNT + " réussie.");

            if (enroll_idx == ENROLL_COUNT) {
                int[] _retLen = new int[1];
                _retLen[0] = TEMPLATE_SIZE;
                byte[] regTemp = new byte[_retLen[0]];

                if (0 == (ret = FingerprintSensorEx.DBMerge(mhDB, regtemparray[0], regtemparray[1], regtemparray[2], regTemp, _retLen))
                        && 0 == (ret = FingerprintSensorEx.DBAdd(mhDB, iFid, regTemp))) {
                    iFid++;
                    cbRegTemp = _retLen[0];
                    // Copie sécurisée avec vérification de la taille
                    int safeCopyLen = Math.min(cbRegTemp, Math.min(regTemp.length, lastRegTemp.length));
                    System.arraycopy(regTemp, 0, lastRegTemp, 0, safeCopyLen);
                    log("SUCCÈS : Enrôlement terminé. ID attribué = " + (iFid - 1));
                } else {
                    log("ECHEC : Impossible de fusionner les empreintes (Code " + ret + ")");
                }
                bRegister = false;
                enroll_idx = 0;
            }
        } else if (bIdentify) {
            int[] fid = new int[1];
            int[] score = new int[1];
            int ret = FingerprintSensorEx.DBIdentify(mhDB, captureTemplate, fid, score);
            if (ret == 0) {
                log("IDENTIFIÉ ! ID: " + fid[0] + " (Score: " + score[0] + "%)");
            } else {
                log("Non identifié.");
            }
        }
    }

    private void exportImage() {
        if (lastCapturedImage == null || fpWidth == 0) {
            JOptionPane.showMessageDialog(this, "Aucune image à sauvegarder.");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Sauvegarder l'empreinte");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Image JPEG", "jpg"));
        
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        fileChooser.setSelectedFile(new File("Scan_" + timeStamp + ".jpg"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".jpg")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".jpg");
            }

            try {
                BufferedImage image = new BufferedImage(fpWidth, fpHeight, BufferedImage.TYPE_BYTE_GRAY);
                image.getRaster().setDataElements(0, 0, fpWidth, fpHeight, lastCapturedImage);
                ImageIO.write(image, "jpg", fileToSave);
                log("Image sauvegardée : " + fileToSave.getAbsolutePath());
                JOptionPane.showMessageDialog(this, "Sauvegarde réussie !");
            } catch (IOException ex) {
                log("Erreur sauvegarde : " + ex.getMessage());
            }
        }
    }

    private void updateUIState(boolean connected) {
        btnOpen.setEnabled(!connected);
        btnClose.setEnabled(connected);
        btnEnroll.setEnabled(connected);
        btnVerify.setEnabled(connected);
        lblStatus.setText(connected ? " Appareil Connecté" : " Déconnecté");
        lblStatus.setForeground(connected ? new Color(0, 100, 0) : Color.RED);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(new SimpleDateFormat("HH:mm:ss").format(new Date()) + " > " + msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void logError(String action, int code) {
        log("ERREUR [" + action + "] Code SDK: " + code);
    }

    public static int byteArrayToInt(byte[] bytes) {
        return (bytes[0] & 0xFF) | 
               ((bytes[1] & 0xFF) << 8) | 
               ((bytes[2] & 0xFF) << 16) | 
               ((bytes[3] & 0xFF) << 24);
    }

    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
        } catch (Exception ex) {
            System.err.println("Theme FlatLaf non trouvé.");
        }
        SwingUtilities.invokeLater(() -> new ModernZKFingerDemo().setVisible(true));
    }
}