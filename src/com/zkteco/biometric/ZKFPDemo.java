package com.zkteco.biometric;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;

public class ZKFPDemo extends JFrame {
	
	// Constants
	private static final int TEMPLATE_SIZE = 2048;
	private static final int ENROLL_COUNT = 3;
	private static final int DEFAULT_DPI = 500;
	private static final int PARAM_SIZE = 4;
	private static final int THREAD_SLEEP_MS = 500;
	private static final int THREAD_STOP_WAIT_MS = 1000;
	private static final int FAKE_FUN_ON = 1;
	private static final int FAKE_STATUS_MASK = 31;
	
	// UI Components
	private JButton btnOpen = null;
	private JButton btnEnroll = null;
	private JButton btnVerify = null;
	private JButton btnIdentify = null;
	private JButton btnRegImg = null;
	private JButton btnIdentImg = null;
	private JButton btnClose = null;
	private JButton btnImg = null;
	private JButton btnSaveImage = null;
	private JRadioButton radioISO = null;
	private JRadioButton radioANSI = null;
	private JTextArea textArea;
	
	// Fingerprint image dimensions
	private int fpWidth = 0;
	private int fpHeight = 0;
	
	// Template data
	private byte[] lastRegTemp = new byte[TEMPLATE_SIZE];
	private int cbRegTemp = 0;
	private byte[][] regtemparray = new byte[ENROLL_COUNT][TEMPLATE_SIZE];
	
	// State flags
	private boolean bRegister = false;
	private boolean bIdentify = true;
	private int iFid = 1;
	private int nFakeFunOn = FAKE_FUN_ON;
	private int enroll_idx = 0;
	
	// Image and template buffers
	private byte[] imgbuf = null;
	private byte[] lastCapturedImage = null;
	private byte[] template = new byte[TEMPLATE_SIZE];
	private int[] templateLen = new int[1];
	
	// Device handles
	private boolean mbStop = true;
	private long mhDevice = 0;
	private long mhDB = 0;
	private WorkThread workThread = null;
	
	public void launchFrame() {
		this.setLayout(null);
		
		// Button positioning constants
		int nRsize = 20;
		int buttonX = 30;
		int buttonWidth = 100;
		int buttonHeight = 30;
		int buttonSpacing = 50;
		
		// Create and position buttons
		btnOpen = new JButton("Open");
		this.add(btnOpen);
		btnOpen.setBounds(buttonX, 10 + nRsize, buttonWidth, buttonHeight);
		
		btnEnroll = new JButton("Enroll");
		this.add(btnEnroll);
		btnEnroll.setBounds(buttonX, 60 + nRsize, buttonWidth, buttonHeight);
		
		btnVerify = new JButton("Verify");
		this.add(btnVerify);
		btnVerify.setBounds(buttonX, 110 + nRsize, buttonWidth, buttonHeight);
		
		btnIdentify = new JButton("Identify");
		this.add(btnIdentify);
		btnIdentify.setBounds(buttonX, 160 + nRsize, buttonWidth, buttonHeight);
		
		btnRegImg = new JButton("Register By Image");
		this.add(btnRegImg);
		btnRegImg.setBounds(15, 210 + nRsize, 120, buttonHeight);
		
		btnIdentImg = new JButton("Verify By Image");
		this.add(btnIdentImg);
		btnIdentImg.setBounds(15, 260 + nRsize, 120, buttonHeight);
		
		btnClose = new JButton("Close");
		this.add(btnClose);
		btnClose.setBounds(buttonX, 310 + nRsize, buttonWidth, buttonHeight);
		
		btnSaveImage = new JButton("Save Image");
		this.add(btnSaveImage);
		btnSaveImage.setBounds(buttonX, 360 + nRsize, buttonWidth, buttonHeight);
		
		// ISO/ANSI radio buttons
		radioANSI = new JRadioButton("ANSI", true);
		this.add(radioANSI);
		radioANSI.setBounds(buttonX, 410 + nRsize, 60, buttonHeight);
		
		radioISO = new JRadioButton("ISO");
		this.add(radioISO);
		radioISO.setBounds(120, 410 + nRsize, 60, buttonHeight);
		
		ButtonGroup group = new ButtonGroup();
		group.add(radioANSI);
		group.add(radioISO);
		
		// Image display button
		btnImg = new JButton();
		btnImg.setBounds(150, 5, 256, 300);
		btnImg.setDefaultCapable(false);
		this.add(btnImg);
		
		// Text area for messages
		textArea = new JTextArea();
		this.add(textArea);
		textArea.setBounds(10, 460, 480, 100);
		
		// Frame setup
		this.setSize(520, 580);
		this.setLocationRelativeTo(null);
		this.setVisible(true);
		this.setTitle("ZKFinger Demo");
		this.setResizable(false);
		
		// Action listeners
		setupActionListeners();
		
		// Window close handler
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				FreeSensor();
			}
		});
	}
	
	private void setupActionListeners() {
		btnOpen.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (0 != mhDevice) {
					textArea.setText("Please close device first!");
					return;
				}
				
				int ret = FingerprintSensorErrorCode.ZKFP_ERR_OK;
				
				// Initialize state
				cbRegTemp = 0;
				bRegister = false;
				bIdentify = false;
				iFid = 1;
				enroll_idx = 0;
				
				if (FingerprintSensorErrorCode.ZKFP_ERR_OK != FingerprintSensorEx.Init()) {
					textArea.setText("Init failed!");
					return;
				}
				
				ret = FingerprintSensorEx.GetDeviceCount();
				if (ret < 0) {
					textArea.setText("No devices connected!");
					FreeSensor();
					return;
				}
				
				if (0 == (mhDevice = FingerprintSensorEx.OpenDevice(0))) {
					textArea.setText("Open device fail, ret = " + ret + "!");
					FreeSensor();
					return;
				}
				
				if (0 == (mhDB = FingerprintSensorEx.DBInit())) {
					textArea.setText("Init DB fail, ret = " + ret + "!");
					FreeSensor();
					return;
				}
				
				// Set ISO/ANSI format
				int nFmt = radioISO.isSelected() ? 1 : 0;
				FingerprintSensorEx.DBSetParameter(mhDB, 5010, nFmt);
				
				// Get image dimensions
				byte[] paramValue = new byte[PARAM_SIZE];
				int[] size = new int[1];
				size[0] = PARAM_SIZE;
				
				FingerprintSensorEx.GetParameters(mhDevice, 1, paramValue, size);
				fpWidth = byteArrayToInt(paramValue);
				
				size[0] = PARAM_SIZE;
				FingerprintSensorEx.GetParameters(mhDevice, 2, paramValue, size);
				fpHeight = byteArrayToInt(paramValue);
				
				imgbuf = new byte[fpWidth * fpHeight];
				btnImg.resize(fpWidth, fpHeight);
				mbStop = false;
				workThread = new WorkThread();
				workThread.start();
				textArea.setText("Open succ!");
			}
		});
		
		btnClose.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				FreeSensor();
				textArea.setText("Close succ!");
			}
		});
		
		btnEnroll.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (0 == mhDevice) {
					textArea.setText("Please Open device first!");
					return;
				}
				if (!bRegister) {
					enroll_idx = 0;
					bRegister = true;
					textArea.setText("Please your finger 3 times!");
				}
			}
		});
		
		btnVerify.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (0 == mhDevice) {
					textArea.setText("Please Open device first!");
					return;
				}
				if (bRegister) {
					enroll_idx = 0;
					bRegister = false;
				}
				if (bIdentify) {
					bIdentify = false;
				}
			}
		});
		
		btnIdentify.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (0 == mhDevice) {
					textArea.setText("Please Open device first!");
					return;
				}
				if (bRegister) {
					enroll_idx = 0;
					bRegister = false;
				}
				if (!bIdentify) {
					bIdentify = true;
				}
			}
		});
		
		btnRegImg.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (0 == mhDB) {
					textArea.setText("Please open device first!");
					return;
				}
				String path = "d:\\test\\fingerprint.bmp";
				byte[] fpTemplate = new byte[TEMPLATE_SIZE];
				int[] sizeFPTemp = new int[1];
				sizeFPTemp[0] = TEMPLATE_SIZE;
				int ret = FingerprintSensorEx.ExtractFromImage(mhDB, path, DEFAULT_DPI, fpTemplate, sizeFPTemp);
				if (0 == ret) {
					ret = FingerprintSensorEx.DBAdd(mhDB, iFid, fpTemplate);
					if (0 == ret) {
						iFid++;
						cbRegTemp = sizeFPTemp[0];
						System.arraycopy(fpTemplate, 0, lastRegTemp, 0, cbRegTemp);
						textArea.setText("enroll succ");
					} else {
						textArea.setText("DBAdd fail, ret=" + ret);
					}
				} else {
					textArea.setText("ExtractFromImage fail, ret=" + ret);
				}
			}
		});
		
		btnIdentImg.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (0 == mhDB) {
					textArea.setText("Please open device first!");
					return;
				}
				String path = "d:\\test\\fingerprint.bmp";
				byte[] fpTemplate = new byte[TEMPLATE_SIZE];
				int[] sizeFPTemp = new int[1];
				sizeFPTemp[0] = TEMPLATE_SIZE;
				int ret = FingerprintSensorEx.ExtractFromImage(mhDB, path, DEFAULT_DPI, fpTemplate, sizeFPTemp);
				if (0 == ret) {
					if (bIdentify) {
						int[] fid = new int[1];
						int[] score = new int[1];
						ret = FingerprintSensorEx.DBIdentify(mhDB, fpTemplate, fid, score);
						if (ret == 0) {
							textArea.setText("Identify succ, fid=" + fid[0] + ",score=" + score[0]);
						} else {
							textArea.setText("Identify fail, errcode=" + ret);
						}
					} else {
						if (cbRegTemp <= 0) {
							textArea.setText("Please register first!");
						} else {
							ret = FingerprintSensorEx.DBMatch(mhDB, lastRegTemp, fpTemplate);
							if (ret > 0) {
								textArea.setText("Verify succ, score=" + ret);
							} else {
								textArea.setText("Verify fail, ret=" + ret);
							}
						}
					}
				} else {
					textArea.setText("ExtractFromImage fail, ret=" + ret);
				}
			}
		});
		
		btnSaveImage.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				saveCurrentImage();
			}
		});
	}
	
	private void saveCurrentImage() {
		if (lastCapturedImage == null || fpWidth == 0 || fpHeight == 0) {
			textArea.setText("No image available to save!");
			return;
		}
		
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Save Fingerprint Image");
		
		// Add file filters
		FileNameExtensionFilter bmpFilter = new FileNameExtensionFilter("BMP Images (*.bmp)", "bmp");
		FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG Images (*.png)", "png");
		FileNameExtensionFilter jpgFilter = new FileNameExtensionFilter("JPEG Images (*.jpg, *.jpeg)", "jpg", "jpeg");
		
		fileChooser.addChoosableFileFilter(bmpFilter);
		fileChooser.addChoosableFileFilter(pngFilter);
		fileChooser.addChoosableFileFilter(jpgFilter);
		fileChooser.setFileFilter(bmpFilter);
		
		int userSelection = fileChooser.showSaveDialog(this);
		
		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File fileToSave = fileChooser.getSelectedFile();
			String filePath = fileToSave.getAbsolutePath();
			String extension = getFileExtension(fileToSave);
			
			// If no extension, add default based on filter
			if (extension == null || extension.isEmpty()) {
				FileNameExtensionFilter selectedFilter = (FileNameExtensionFilter) fileChooser.getFileFilter();
				if (selectedFilter == pngFilter) {
					extension = "png";
					filePath += ".png";
				} else if (selectedFilter == jpgFilter) {
					extension = "jpg";
					filePath += ".jpg";
				} else {
					extension = "bmp";
					filePath += ".bmp";
				}
			}
			
			try {
				if ("bmp".equalsIgnoreCase(extension)) {
					writeBitmap(lastCapturedImage, fpWidth, fpHeight, filePath);
					textArea.setText("Image saved successfully: " + filePath);
				} else {
					// Convert byte array to BufferedImage and save as PNG/JPG
					BufferedImage image = createBufferedImageFromBytes(lastCapturedImage, fpWidth, fpHeight);
					if (ImageIO.write(image, extension, new File(filePath))) {
						textArea.setText("Image saved successfully: " + filePath);
					} else {
						textArea.setText("Failed to save image: Unsupported format");
					}
				}
			} catch (IOException e) {
				textArea.setText("Error saving image: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}
	
	private String getFileExtension(File file) {
		String name = file.getName();
		int lastIndexOf = name.lastIndexOf(".");
		if (lastIndexOf == -1) {
			return "";
		}
		return name.substring(lastIndexOf + 1);
	}
	
	private BufferedImage createBufferedImageFromBytes(byte[] imageData, int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int index = (height - 1 - y) * width + x;
				if (index < imageData.length) {
					int gray = imageData[index] & 0xFF;
					int rgb = (gray << 16) | (gray << 8) | gray;
					image.setRGB(x, y, rgb);
				}
			}
		}
		return image;
	}
	
	private void FreeSensor() {
		mbStop = true;
		try {
			Thread.sleep(THREAD_STOP_WAIT_MS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (0 != mhDB) {
			FingerprintSensorEx.DBFree(mhDB);
			mhDB = 0;
		}
		if (0 != mhDevice) {
			FingerprintSensorEx.CloseDevice(mhDevice);
			mhDevice = 0;
		}
		FingerprintSensorEx.Terminate();
	}
	
	public static void writeBitmap(byte[] imageBuf, int nWidth, int nHeight, String path) throws IOException {
		java.io.FileOutputStream fos = new java.io.FileOutputStream(path);
		java.io.DataOutputStream dos = new java.io.DataOutputStream(fos);

		int w = (((nWidth + 3) / 4) * 4);
		int bfType = 0x424d;
		int bfSize = 54 + 1024 + w * nHeight;
		int bfReserved1 = 0;
		int bfReserved2 = 0;
		int bfOffBits = 54 + 1024;

		dos.writeShort(bfType);
		dos.write(intToByteArray(bfSize), 0, 4);
		dos.write(intToByteArray(bfReserved1), 0, 2);
		dos.write(intToByteArray(bfReserved2), 0, 2);
		dos.write(intToByteArray(bfOffBits), 0, 4);

		int biSize = 40;
		int biWidth = nWidth;
		int biHeight = nHeight;
		int biPlanes = 1;
		int biBitcount = 8;
		int biCompression = 0;
		int biSizeImage = w * nHeight;
		int biXPelsPerMeter = 0;
		int biYPelsPerMeter = 0;
		int biClrUsed = 0;
		int biClrImportant = 0;

		dos.write(intToByteArray(biSize), 0, 4);
		dos.write(intToByteArray(biWidth), 0, 4);
		dos.write(intToByteArray(biHeight), 0, 4);
		dos.write(intToByteArray(biPlanes), 0, 2);
		dos.write(intToByteArray(biBitcount), 0, 2);
		dos.write(intToByteArray(biCompression), 0, 4);
		dos.write(intToByteArray(biSizeImage), 0, 4);
		dos.write(intToByteArray(biXPelsPerMeter), 0, 4);
		dos.write(intToByteArray(biYPelsPerMeter), 0, 4);
		dos.write(intToByteArray(biClrUsed), 0, 4);
		dos.write(intToByteArray(biClrImportant), 0, 4);

		for (int i = 0; i < 256; i++) {
			dos.writeByte(i);
			dos.writeByte(i);
			dos.writeByte(i);
			dos.writeByte(0);
		}

		byte[] filter = null;
		if (w > nWidth) {
			filter = new byte[w - nWidth];
		}

		for (int i = 0; i < nHeight; i++) {
			dos.write(imageBuf, (nHeight - 1 - i) * nWidth, nWidth);
			if (w > nWidth)
				dos.write(filter, 0, w - nWidth);
		}
		dos.flush();
		dos.close();
		fos.close();
	}

	public static byte[] intToByteArray(final int number) {
		byte[] abyte = new byte[4];
		abyte[0] = (byte) (0xff & number);
		abyte[1] = (byte) ((0xff00 & number) >> 8);
		abyte[2] = (byte) ((0xff0000 & number) >> 16);
		abyte[3] = (byte) ((0xff000000 & number) >> 24);
		return abyte;
	}

	public static int byteArrayToInt(byte[] bytes) {
		int number = bytes[0] & 0xFF;
		number |= ((bytes[1] << 8) & 0xFF00);
		number |= ((bytes[2] << 16) & 0xFF0000);
		number |= ((bytes[3] << 24) & 0xFF000000);
		return number;
	}

	private class WorkThread extends Thread {
		@Override
		public void run() {
			super.run();
			int ret = 0;
			while (!mbStop) {
				templateLen[0] = TEMPLATE_SIZE;
				if (0 == (ret = FingerprintSensorEx.AcquireFingerprint(mhDevice, imgbuf, template, templateLen))) {
					if (nFakeFunOn == FAKE_FUN_ON) {
						byte[] paramValue = new byte[PARAM_SIZE];
						int[] size = new int[1];
						size[0] = PARAM_SIZE;
						int nFakeStatus = 0;
						ret = FingerprintSensorEx.GetParameters(mhDevice, 2004, paramValue, size);
						nFakeStatus = byteArrayToInt(paramValue);
						System.out.println("ret = " + ret + ",nFakeStatus=" + nFakeStatus);
						if (0 == ret && (byte) (nFakeStatus & FAKE_STATUS_MASK) != FAKE_STATUS_MASK) {
							textArea.setText("Is a fake-finger?");
							return;
						}
					}
					OnCaptureOK(imgbuf);
					OnExtractOK(template, templateLen[0]);
				}
				try {
					Thread.sleep(THREAD_SLEEP_MS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void OnCaptureOK(byte[] imgBuf) {
		try {
			// Store the last captured image
			if (imgBuf != null && fpWidth > 0 && fpHeight > 0) {
				lastCapturedImage = new byte[imgBuf.length];
				System.arraycopy(imgBuf, 0, lastCapturedImage, 0, imgBuf.length);
			}
			
			writeBitmap(imgBuf, fpWidth, fpHeight, "fingerprint.bmp");
			btnImg.setIcon(new ImageIcon(ImageIO.read(new File("fingerprint.bmp"))));
		} catch (IOException e) {
			textArea.setText("Error displaying image: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void OnExtractOK(byte[] template, int len) {
		if (bRegister) {
			int[] fid = new int[1];
			int[] score = new int[1];
			int ret = FingerprintSensorEx.DBIdentify(mhDB, template, fid, score);
			if (ret == 0) {
				textArea.setText("the finger already enroll by " + fid[0] + ",cancel enroll");
				bRegister = false;
				enroll_idx = 0;
				return;
			}
			if (enroll_idx > 0 && FingerprintSensorEx.DBMatch(mhDB, regtemparray[enroll_idx - 1], template) <= 0) {
				textArea.setText("please press the same finger 3 times for the enrollment");
				return;
			}
			System.arraycopy(template, 0, regtemparray[enroll_idx], 0, TEMPLATE_SIZE);
			enroll_idx++;
			if (enroll_idx == ENROLL_COUNT) {
				int[] _retLen = new int[1];
				_retLen[0] = TEMPLATE_SIZE;
				byte[] regTemp = new byte[_retLen[0]];

				if (0 == (ret = FingerprintSensorEx.DBMerge(mhDB, regtemparray[0], regtemparray[1], regtemparray[2], regTemp, _retLen))
						&& 0 == (ret = FingerprintSensorEx.DBAdd(mhDB, iFid, regTemp))) {
					iFid++;
					cbRegTemp = _retLen[0];
					System.arraycopy(regTemp, 0, lastRegTemp, 0, cbRegTemp);
					textArea.setText("enroll succ");
				} else {
					textArea.setText("enroll fail, error code=" + ret);
				}
				bRegister = false;
			} else {
				textArea.setText("You need to press the " + (ENROLL_COUNT - enroll_idx) + " times fingerprint");
			}
		} else {
			if (bIdentify) {
				int[] fid = new int[1];
				int[] score = new int[1];
				int ret = FingerprintSensorEx.DBIdentify(mhDB, template, fid, score);
				if (ret == 0) {
					textArea.setText("Identify succ, fid=" + fid[0] + ",score=" + score[0]);
				} else {
					textArea.setText("Identify fail, errcode=" + ret);
				}
			} else {
				if (cbRegTemp <= 0) {
					textArea.setText("Please register first!");
				} else {
					int ret = FingerprintSensorEx.DBMatch(mhDB, lastRegTemp, template);
					if (ret > 0) {
						textArea.setText("Verify succ, score=" + ret);
					} else {
						textArea.setText("Verify fail, ret=" + ret);
					}
				}
			}
		}
	}

	public static void main(String[] args) {
		new ZKFPDemo().launchFrame();
	}
}
