package com.zkteco.biometric;

import com.formdev.flatlaf.FlatLightLaf;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
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
import java.util.*;
import java.util.List;

public class ModernZKFingerDemo extends JFrame {

    // --- Enum pour les types de doigts ---
    public enum FingerType {
        POUCE_DROITE("Pouce Droit", "pouce_droite"),
        INDEX_DROITE("Index Droit", "index_droite"),
        MAJEUR_DROITE("Majeur Droit", "majeur_droite"),
        ANNULAIRE_DROITE("Annulaire Droit", "annulaire_droite"),
        AURICULAIRE_DROITE("Auriculaire Droit", "auriculaire_droite"),
        POUCE_GAUCHE("Pouce Gauche", "pouce_gauche"),
        INDEX_GAUCHE("Index Gauche", "index_gauche"),
        MAJEUR_GAUCHE("Majeur Gauche", "majeur_gauche"),
        ANNULAIRE_GAUCHE("Annulaire Gauche", "annulaire_gauche"),
        AURICULAIRE_GAUCHE("Auriculaire Gauche", "auriculaire_gauche");

        private final String displayName;
        private final String fileName;

        FingerType(String displayName, String fileName) {
            this.displayName = displayName;
            this.fileName = fileName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getFileName(String timestamp, String randomPrefix) {
            return "scanfinger-" + timestamp + "-" + randomPrefix + "-" + fileName + ".jpg";
        }
    }

    // --- Constantes SDK ---
    private static final int TEMPLATE_SIZE = 2048;
    private static final int ENROLL_COUNT = 3;
    private static final int PARAM_SIZE = 4;
    
    // --- Composants UI ---
    private JLabel lblImageFinger;
    private JTextArea txtLog;
    private JButton btnOpen, btnClose, btnEnroll, btnVerify, btnSaveImg;
    private JLabel lblStatus;
    
    // --- Composants UI Multi-Doigts ---
    private JPanel multiFingerPanel;
    private Map<FingerType, JCheckBox> fingerCheckBoxes;
    private JComboBox<FingerType> cmbSelectedFinger;
    private JButton btnCaptureFinger, btnExportAll, btnRemoveFinger;
    private JList<FingerType> listCapturedFingers;
    private DefaultListModel<FingerType> capturedFingersModel;
    
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
    
    // Multi-doigts
    private Map<FingerType, byte[]> capturedFingers = new HashMap<>();
    private Set<FingerType> selectedFingers = new HashSet<>();
    private boolean bMultiFingerCapture = false;
    private FingerType currentCaptureFinger = null;

    public ModernZKFingerDemo() {
        initUI();
    }

    private void initUI() {
        setTitle("ZKTeco Fingerprint Manager");
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();
        int width = (int) (screenSize.getWidth() * 0.9); // 90% de la largeur
        int height = (int) (screenSize.getHeight() * 0.9); // 90% de la hauteur
        setSize(width, height);
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
        controlPanel.setPreferredSize(new Dimension(220, 0));

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

        // --- 2. Panneau Central : Image ---
        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setBorder(new TitledBorder("Aperçu Empreinte"));
        imagePanel.setBackground(Color.WHITE);
        
        lblImageFinger = new JLabel("En attente...", SwingConstants.CENTER);
        lblImageFinger.setHorizontalAlignment(JLabel.CENTER);
        lblImageFinger.setVerticalAlignment(JLabel.CENTER);
        imagePanel.add(lblImageFinger, BorderLayout.CENTER);
        
        mainPanel.add(imagePanel, BorderLayout.CENTER);

        // --- 3. Panneau Droite : Multi-Doigts ---
        multiFingerPanel = createMultiFingerPanel();
        mainPanel.add(multiFingerPanel, BorderLayout.EAST);

        // --- 4. Panneau Bas : Logs et Status ---
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
    
    private JPanel createMultiFingerPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("Enregistrement Multi-Doigts"));
        panel.setPreferredSize(new Dimension(320, 0));
        
        // Panneau de sélection des doigts - Amélioré avec groupes visuels
        JPanel selectionPanel = new JPanel(new GridBagLayout());
        selectionPanel.setBorder(new TitledBorder("Sélection des doigts à scanner"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        fingerCheckBoxes = new HashMap<>();
        
        // Groupe Main Droite
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel lblMainDroite = new JLabel("Main Droite:");
        lblMainDroite.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblMainDroite.setForeground(new Color(0, 100, 200));
        selectionPanel.add(lblMainDroite, gbc);
        
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        for (FingerType finger : new FingerType[]{
            FingerType.POUCE_DROITE, FingerType.INDEX_DROITE, 
            FingerType.MAJEUR_DROITE, FingerType.ANNULAIRE_DROITE, 
            FingerType.AURICULAIRE_DROITE}) {
            JCheckBox checkBox = new JCheckBox(finger.getDisplayName().replace("Droit", ""));
            checkBox.addActionListener(e -> updateFingerComboBox());
            fingerCheckBoxes.put(finger, checkBox);
            gbc.gridx = 0;
            selectionPanel.add(checkBox, gbc);
            gbc.gridy++;
        }
        
        // Groupe Main Gauche
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel lblMainGauche = new JLabel("Main Gauche:");
        lblMainGauche.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblMainGauche.setForeground(new Color(200, 100, 0));
        selectionPanel.add(lblMainGauche, gbc);
        
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        for (FingerType finger : new FingerType[]{
            FingerType.POUCE_GAUCHE, FingerType.INDEX_GAUCHE, 
            FingerType.MAJEUR_GAUCHE, FingerType.ANNULAIRE_GAUCHE, 
            FingerType.AURICULAIRE_GAUCHE}) {
            JCheckBox checkBox = new JCheckBox(finger.getDisplayName().replace("Gauche", ""));
            checkBox.addActionListener(e -> updateFingerComboBox());
            fingerCheckBoxes.put(finger, checkBox);
            gbc.gridx = 1;
            selectionPanel.add(checkBox, gbc);
            gbc.gridy++;
        }
        
        JScrollPane scrollSelection = new JScrollPane(selectionPanel);
        scrollSelection.setPreferredSize(new Dimension(300, 180));
        
        // Panneau de capture - Amélioré
        JPanel capturePanel = new JPanel(new BorderLayout(5, 5));
        capturePanel.setBorder(new TitledBorder("Capture du doigt"));
        
        cmbSelectedFinger = new JComboBox<>();
        cmbSelectedFinger.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FingerType) {
                    setText(((FingerType) value).getDisplayName());
                }
                return this;
            }
        });
        cmbSelectedFinger.setEnabled(false);
        
        btnCaptureFinger = new JButton("📸 Capturer ce doigt");
        btnCaptureFinger.setEnabled(false);
        btnCaptureFinger.setFont(new Font("Segoe UI", Font.BOLD, 11));
        
        JPanel captureTopPanel = new JPanel(new BorderLayout(3, 3));
        captureTopPanel.add(new JLabel("Doigt sélectionné:"), BorderLayout.NORTH);
        captureTopPanel.add(cmbSelectedFinger, BorderLayout.CENTER);
        
        capturePanel.add(captureTopPanel, BorderLayout.NORTH);
        capturePanel.add(btnCaptureFinger, BorderLayout.SOUTH);
        
        // Liste des doigts capturés - Améliorée
        JPanel capturedPanel = new JPanel(new BorderLayout(5, 5));
        TitledBorder capturedBorder = new TitledBorder("Doigts capturés (0)");
        capturedPanel.setBorder(capturedBorder);
        capturedPanel.putClientProperty("TitledBorder", capturedBorder); // Pour permettre la mise à jour
        
        capturedFingersModel = new DefaultListModel<>();
        listCapturedFingers = new JList<>(capturedFingersModel);
        listCapturedFingers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listCapturedFingers.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FingerType) {
                    setText("✓ " + ((FingerType) value).getDisplayName());
                    setForeground(new Color(0, 120, 0));
                }
                return this;
            }
        });
        
        JScrollPane scrollCaptured = new JScrollPane(listCapturedFingers);
        scrollCaptured.setPreferredSize(new Dimension(300, 120));
        
        btnRemoveFinger = new JButton("🗑️ Supprimer");
        btnRemoveFinger.setEnabled(false);
        btnRemoveFinger.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        
        capturedPanel.add(scrollCaptured, BorderLayout.CENTER);
        capturedPanel.add(btnRemoveFinger, BorderLayout.SOUTH);
        
        // Bouton d'export - Amélioré
        btnExportAll = new JButton("💾 Exporter tous les doigts");
        btnExportAll.setEnabled(false);
        btnExportAll.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnExportAll.setBackground(new Color(0, 150, 0));
        btnExportAll.setForeground(Color.WHITE);
        
        // Assemblage
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(scrollSelection, BorderLayout.CENTER);
        topPanel.add(capturePanel, BorderLayout.SOUTH);
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(capturedPanel, BorderLayout.CENTER);
        panel.add(btnExportAll, BorderLayout.SOUTH);
        
        return panel;
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
            bRegister = false; 
            enroll_idx = 0;
            bRegister = true;
            log("Mode Enrôlement : Veuillez poser le doigt 3 fois.");
            bIdentify = false;
            bMultiFingerCapture = false;
        });

        btnVerify.addActionListener(e -> {
            if (bRegister) {
                bRegister = false;
                log("Mode Enrôlement annulé.");
            }
            bIdentify = !bIdentify;
            log(bIdentify ? "Mode Identification (Scan continu) activé." : "Mode Identification désactivé.");
            bMultiFingerCapture = false;
        });

        btnSaveImg.addActionListener(e -> exportImage());
        
        // Actions multi-doigts
        btnCaptureFinger.addActionListener(e -> {
            FingerType selected = (FingerType) cmbSelectedFinger.getSelectedItem();
            if (selected == null || mhDevice == 0) return;
            
            bMultiFingerCapture = true;
            currentCaptureFinger = selected;
            log("Mode capture activé pour : " + selected.getDisplayName() + ". Posez le doigt sur le scanner.");
        });
        
        btnExportAll.addActionListener(e -> exportAllFingers());
        
        btnRemoveFinger.addActionListener(e -> {
            FingerType selected = listCapturedFingers.getSelectedValue();
            if (selected != null) {
                capturedFingers.remove(selected);
                capturedFingersModel.removeElement(selected);
                updateMultiFingerUI();
                log("Doigt supprimé : " + selected.getDisplayName());
            }
        });
        
        listCapturedFingers.addListSelectionListener(e -> {
            btnRemoveFinger.setEnabled(listCapturedFingers.getSelectedValue() != null);
        });
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
                    
                    // Gestion de la capture multi-doigts
                    if (bMultiFingerCapture && currentCaptureFinger != null) {
                        SwingUtilities.invokeLater(() -> {
                            capturedFingers.put(currentCaptureFinger, currentImgCopy);
                            if (!capturedFingersModel.contains(currentCaptureFinger)) {
                                capturedFingersModel.addElement(currentCaptureFinger);
                            }
                            bMultiFingerCapture = false;
                            currentCaptureFinger = null;
                            updateMultiFingerUI();
                            log("Capture réussie pour le doigt sélectionné.");
                        });
                    }
                    
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
        
        int labelWidth = lblImageFinger.getWidth();
        int labelHeight = lblImageFinger.getHeight();
        
        if (labelWidth == 0) labelWidth = 300;
        if (labelHeight == 0) labelHeight = 400;

        Image scaled = getScaledImage(image, labelWidth, labelHeight);
        
        lblImageFinger.setIcon(new ImageIcon(scaled));
        lblImageFinger.setText(""); 
        btnSaveImg.setEnabled(true);
    }
    
    private Image getScaledImage(Image srcImg, int w, int h){
        BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resizedImg.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
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
        if (captureTemplate == null || captureTemplate.length == 0) {
            log("Erreur: Template invalide ou vide.");
            return;
        }
        
        if (bMultiFingerCapture) {
            // Ne pas traiter le template en mode multi-doigts
            return;
        }
        
        if (bRegister) {
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

            int copyLength = Math.min(captureTemplate.length, TEMPLATE_SIZE);
            System.arraycopy(captureTemplate, 0, regtemparray[enroll_idx], 0, copyLength);
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
    
    private void exportAllFingers() {
        if (capturedFingers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Aucun doigt capturé à exporter.");
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Choisir le dossier d'export");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File selectedDir = fileChooser.getSelectedFile();
        if (!selectedDir.exists() || !selectedDir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Dossier invalide.");
            return;
        }
        
        // Générer un timestamp au format yyyyMMdd-HHmm
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date());
        
        // Générer un préfixe aléatoire unique (8 caractères hexadécimaux)
        String randomPrefix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        
        // Créer le nom du dossier : scanfinger-yyyymmdd-hhmm-prefixunique
        String folderName = "scanfinger-" + timestamp + "-" + randomPrefix;
        File exportDir = new File(selectedDir, folderName);
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        
        int successCount = 0;
        int failCount = 0;
        
        for (Map.Entry<FingerType, byte[]> entry : capturedFingers.entrySet()) {
            FingerType finger = entry.getKey();
            byte[] imageData = entry.getValue();
            
            try {
                // Format : scanfinger-yyyymmdd-hhmm-prefixunique-queldoigt.jpg
                String fileName = finger.getFileName(timestamp, randomPrefix);
                File outputFile = new File(exportDir, fileName);
                
                BufferedImage image = new BufferedImage(fpWidth, fpHeight, BufferedImage.TYPE_BYTE_GRAY);
                image.getRaster().setDataElements(0, 0, fpWidth, fpHeight, imageData);
                
                if (ImageIO.write(image, "jpg", outputFile)) {
                    successCount++;
                    log("Exporté : " + fileName);
                } else {
                    failCount++;
                    log("Échec export : " + fileName);
                }
            } catch (IOException ex) {
                failCount++;
                log("Erreur export " + finger.getDisplayName() + " : " + ex.getMessage());
            }
        }
        
        String message = String.format("Export terminé !\n" +
                "Dossier : %s\n" +
                "Préfixe : %s\n" +
                "Réussis : %d\n" +
                "Échecs : %d",
                folderName, randomPrefix, successCount, failCount);
        
        JOptionPane.showMessageDialog(this, message);
        log("Export terminé. Dossier : " + folderName);
    }
    
    private void updateFingerComboBox() {
        DefaultComboBoxModel<FingerType> model = new DefaultComboBoxModel<>();
        
        // Ajouter seulement les doigts cochés
        for (Map.Entry<FingerType, JCheckBox> entry : fingerCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                model.addElement(entry.getKey());
            }
        }
        
        cmbSelectedFinger.setModel(model);
        
        // Si aucun doigt n'est sélectionné, désactiver le ComboBox et le bouton de capture
        if (model.getSize() == 0) {
            btnCaptureFinger.setEnabled(false);
            cmbSelectedFinger.setEnabled(false);
        } else {
            cmbSelectedFinger.setEnabled(true);
            btnCaptureFinger.setEnabled(mhDevice != 0);
        }
    }
    
    private void updateMultiFingerUI() {
        boolean hasCaptures = !capturedFingers.isEmpty();
        btnExportAll.setEnabled(hasCaptures && mhDevice != 0);
        
        // Mettre à jour le compteur dans le titre du panneau des doigts capturés
        if (multiFingerPanel != null) {
            Component[] components = multiFingerPanel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JPanel) {
                    JPanel panel = (JPanel) comp;
                    Border border = panel.getBorder();
                    if (border instanceof TitledBorder) {
                        TitledBorder titledBorder = (TitledBorder) border;
                        if (titledBorder.getTitle().startsWith("Doigts capturés")) {
                            titledBorder.setTitle("Doigts capturés (" + capturedFingers.size() + ")");
                            panel.repaint();
                            break;
                        }
                    }
                }
            }
        }
        
        // Mettre à jour le ComboBox si nécessaire
        updateFingerComboBox();
    }

    private void updateUIState(boolean connected) {
        btnOpen.setEnabled(!connected);
        btnClose.setEnabled(connected);
        btnEnroll.setEnabled(connected);
        btnVerify.setEnabled(connected);
        updateMultiFingerUI();
        updateFingerComboBox(); // Mettre à jour le ComboBox selon les sélections
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
