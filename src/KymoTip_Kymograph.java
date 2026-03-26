import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.process.*;
import ij.measure.Calibration;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;

public class KymoTip_Kymograph implements PlugIn {

    private JFrame frame;
    private JTextField txtCenterlineDir, txtOutDir, txtW, txtScale;
    private JComboBox<String> cmbSignalImage, cmbMaskImage, cmbMode;
    private JSpinner spnChannel, spnCh1, spnCh2;
    private JLabel lblChannel, lblCh1, lblCh2;

    private int W = 40;
    private double pixelPerMicron = 4.917;

    @Override
    public void run(String arg) {
        showDialog();
    }

    // ===================== GUI =====================

    private void showDialog() {
        if (frame != null && frame.isVisible()) {
            frame.toFront();
            return;
        }

        frame = new JFrame("Kymograph");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Centerline Dir
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        frame.add(new JLabel("Centerline Dir:"), gbc);
        JPanel clPanel = new JPanel(new BorderLayout(5, 0));
        txtCenterlineDir = new JTextField(20);
        JButton btnCL = new JButton("Browse...");
        btnCL.addActionListener(e -> browseDir(txtCenterlineDir));
        clPanel.add(txtCenterlineDir, BorderLayout.CENTER);
        clPanel.add(btnCL, BorderLayout.EAST);
        gbc.gridx = 1;
        frame.add(clPanel, gbc);
        row++;

        // Output Dir
        gbc.gridx = 0; gbc.gridy = row;
        frame.add(new JLabel("Output Dir:"), gbc);
        JPanel outPanel = new JPanel(new BorderLayout(5, 0));
        txtOutDir = new JTextField(20);
        JButton btnOut = new JButton("Browse...");
        btnOut.addActionListener(e -> browseDir(txtOutDir));
        outPanel.add(txtOutDir, BorderLayout.CENTER);
        outPanel.add(btnOut, BorderLayout.EAST);
        gbc.gridx = 1;
        frame.add(outPanel, gbc);
        row++;

        // Image dropdowns
        String[] titles = WindowManager.getImageTitles();
        if (titles.length == 0) titles = new String[]{"(No images open)"};

        gbc.gridx = 0; gbc.gridy = row;
        frame.add(new JLabel("Signal Image:"), gbc);
        cmbSignalImage = new JComboBox<>(titles);
        gbc.gridx = 1;
        frame.add(cmbSignalImage, gbc);
        row++;

        gbc.gridx = 0; gbc.gridy = row;
        frame.add(new JLabel("Mask Image:"), gbc);
        cmbMaskImage = new JComboBox<>(titles);
        gbc.gridx = 1;
        frame.add(cmbMaskImage, gbc);
        row++;

        // Mode
        gbc.gridx = 0; gbc.gridy = row;
        frame.add(new JLabel("Mode:"), gbc);
        cmbMode = new JComboBox<>(new String[]{"Single Channel", "Merge"});
        cmbMode.addActionListener(e -> updateModeUI());
        gbc.gridx = 1;
        frame.add(cmbMode, gbc);
        row++;

        // Channel (Single mode)
        lblChannel = new JLabel("Channel:");
        gbc.gridx = 0; gbc.gridy = row;
        frame.add(lblChannel, gbc);
        spnChannel = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        gbc.gridx = 1;
        frame.add(spnChannel, gbc);
        row++;

        // Ch1 (Merge mode)
        lblCh1 = new JLabel("Ch1 (Green):");
        gbc.gridx = 0; gbc.gridy = row;
        frame.add(lblCh1, gbc);
        spnCh1 = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        gbc.gridx = 1;
        frame.add(spnCh1, gbc);
        row++;

        // Ch2 (Merge mode)
        lblCh2 = new JLabel("Ch2 (Magenta):");
        gbc.gridx = 0; gbc.gridy = row;
        frame.add(lblCh2, gbc);
        spnCh2 = new JSpinner(new SpinnerNumberModel(2, 1, 99, 1));
        gbc.gridx = 1;
        frame.add(spnCh2, gbc);
        row++;

        // Parameters
        addParamRow(frame, gbc, row++, "Sampling half-width W (px):", txtW = new JTextField("40", 5));
        addParamRow(frame, gbc, row++, "Scale (pixel/\u00B5m):", txtScale = new JTextField("4.917", 5));

        // Run
        JButton btnRun = new JButton("Run Processing");
        btnRun.addActionListener(e -> execute());
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        frame.add(btnRun, gbc);

        updateModeUI();
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void addParamRow(JFrame f, GridBagConstraints gbc, int row, String label, JTextField tf) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        f.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        f.add(tf, gbc);
    }

    private void updateModeUI() {
        boolean single = cmbMode.getSelectedIndex() == 0;
        lblChannel.setVisible(single);
        spnChannel.setVisible(single);
        lblCh1.setVisible(!single);
        spnCh1.setVisible(!single);
        lblCh2.setVisible(!single);
        spnCh2.setVisible(!single);
        frame.revalidate();
    }

    private void browseDir(JTextField tf) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            tf.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void execute() {
        String clDir = txtCenterlineDir.getText().trim();
        String outDir = txtOutDir.getText().trim();
        if (clDir.isEmpty() || outDir.isEmpty()) {
            IJ.error("Please specify both centerline and output directories.");
            return;
        }

        ImagePlus sigImp = WindowManager.getImage((String) cmbSignalImage.getSelectedItem());
        ImagePlus maskImp = WindowManager.getImage((String) cmbMaskImage.getSelectedItem());
        if (sigImp == null || maskImp == null) {
            IJ.error("Please open signal and mask images in Fiji.");
            return;
        }

        try {
            W = Integer.parseInt(txtW.getText().trim());
            pixelPerMicron = Double.parseDouble(txtScale.getText().trim());
        } catch (NumberFormatException e) {
            IJ.error("Invalid numeric parameter.");
            return;
        }

        boolean merge = cmbMode.getSelectedIndex() == 1;
        int ch = (Integer) spnChannel.getValue();
        int ch1 = (Integer) spnCh1.getValue();
        int ch2 = (Integer) spnCh2.getValue();

        if (merge && !sigImp.isHyperStack()) {
            IJ.error("Merge mode requires a multi-channel hyperstack as signal image.");
            return;
        }

        new Thread(() -> {
            try {
                process(new File(clDir), new File(outDir), sigImp, maskImp, merge, ch, ch1, ch2);
            } catch (Exception e) {
                IJ.log("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // ===================== Processing =====================

    private void process(File clDir, File outDir, ImagePlus sigImp, ImagePlus maskImp,
                         boolean merge, int ch, int ch1, int ch2) throws IOException {
        File[] clFiles = clDir.listFiles((d, n) -> n.toLowerCase().endsWith(".txt"));
        if (clFiles == null || clFiles.length == 0) {
            IJ.log("No centerline .txt files found.");
            return;
        }
        Arrays.sort(clFiles);

        int nChannels = sigImp.getNChannels();
        int sigFrames = sigImp.getStackSize() / nChannels;
        int maskFrames = maskImp.getStackSize();
        int numFrames = Math.min(clFiles.length, Math.min(sigFrames, maskFrames));

        IJ.log("Signal: " + sigImp.getStackSize() + " slices, " + nChannels +
               " ch -> " + sigFrames + " frames. Mask: " + maskFrames + " frames. " +
               "Centerline files: " + clFiles.length);

        IJ.log("Kymograph: processing " + numFrames + " frames (W=" + W +
               ", scale=" + pixelPerMicron + " px/um)...");

        File brightDir = new File(outDir, "mean_brightness");
        brightDir.mkdirs();

        double[][] allDistances = new double[numFrames][];
        double[][] allBrightness1 = new double[numFrames][];
        double[][] allBrightness2 = merge ? new double[numFrames][] : null;
        double maxDistance = 0;

        for (int f = 0; f < numFrames; f++) {
            IJ.showProgress(f, numFrames);
            IJ.log("  Frame " + (f + 1) + "/" + numFrames + ": " + clFiles[f].getName());

            double[][] centerline = loadCenterline(clFiles[f]);
            if (centerline.length < 3) {
                IJ.log("    Skipped (too few points).");
                allDistances[f] = new double[]{0};
                allBrightness1[f] = new double[]{0};
                if (merge) allBrightness2[f] = new double[]{0};
                continue;
            }

            double[][] spaced = equallySpacePoints(centerline);
            double[][] normals = calculateNormals(spaced);

            ImageProcessor maskIp = getProcessor(maskImp, 1, f + 1);
            ImageProcessor sigIp1 = getProcessor(sigImp, merge ? ch1 : ch, f + 1);

            double[] brightness1 = sampleBrightnessAlongNormals(sigIp1, maskIp, spaced, normals);

            // Cumulative arc-length distances for the sampled points
            double[] dists = new double[normals.length];
            for (int i = 1; i < dists.length; i++) {
                double dx = spaced[i][0] - spaced[i - 1][0];
                double dy = spaced[i][1] - spaced[i - 1][1];
                dists[i] = dists[i - 1] + Math.sqrt(dx * dx + dy * dy);
            }

            allDistances[f] = dists;
            allBrightness1[f] = brightness1;
            if (dists.length > 0 && dists[dists.length - 1] > maxDistance) {
                maxDistance = dists[dists.length - 1];
            }

            if (merge) {
                ImageProcessor sigIp2 = getProcessor(sigImp, ch2, f + 1);
                allBrightness2[f] = sampleBrightnessAlongNormals(sigIp2, maskIp, spaced, normals);
            }

            saveBrightness(spaced, brightness1, new File(brightDir, clFiles[f].getName()));
        }
        IJ.showProgress(1.0);

        // Generate kymograph
        if (merge) {
            generateMergeKymograph(allDistances, allBrightness1, allBrightness2,
                                   maxDistance, numFrames, outDir);
        } else {
            generateSingleKymograph(allDistances, allBrightness1,
                                    maxDistance, numFrames, outDir);
        }

        IJ.log("Kymograph processing completed.");
    }

    private ImageProcessor getProcessor(ImagePlus imp, int channel, int frame) {
        int nCh = imp.getNChannels();
        // Stack index: (frame-1) * nChannels + channel  (1-based)
        int idx = (frame - 1) * nCh + channel;
        return imp.getStack().getProcessor(idx);
    }

    // ===================== Core Methods =====================

    private double[][] loadCenterline(File f) throws IOException {
        java.util.List<double[]> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    list.add(new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])});
                }
            }
        }
        return list.toArray(new double[0][]);
    }

    private double[][] equallySpacePoints(double[][] data) {
        int n = data.length;
        double[] cumDist = new double[n];
        for (int i = 1; i < n; i++) {
            double dx = data[i][0] - data[i - 1][0];
            double dy = data[i][1] - data[i - 1][1];
            cumDist[i] = cumDist[i - 1] + Math.sqrt(dx * dx + dy * dy);
        }
        double totalLength = cumDist[n - 1];
        if (totalLength == 0) return data;

        double[] xVals = new double[n], yVals = new double[n];
        for (int i = 0; i < n; i++) {
            xVals[i] = data[i][0];
            yVals[i] = data[i][1];
        }

        double[][] result = new double[n][2];
        for (int i = 0; i < n; i++) {
            double target = totalLength * i / (n - 1);
            result[i][0] = interpClamped(cumDist, xVals, target);
            result[i][1] = interpClamped(cumDist, yVals, target);
        }
        return result;
    }

    private double[][] calculateNormals(double[][] points) {
        int n = points.length - 1;
        double[][] normals = new double[n][2];
        for (int i = 0; i < n; i++) {
            double dx = points[i + 1][0] - points[i][0];
            double dy = points[i + 1][1] - points[i][1];
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len > 0) {
                normals[i][0] = -dy / len;
                normals[i][1] = dx / len;
            }
        }
        return normals;
    }

    private double[] sampleBrightnessAlongNormals(ImageProcessor ip, ImageProcessor mask,
                                                   double[][] points, double[][] normals) {
        int n = normals.length;
        double[] result = new double[n];
        int imgW = ip.getWidth(), imgH = ip.getHeight();

        for (int i = 0; i < n; i++) {
            double px = points[i][0], py = points[i][1];
            double nx = normals[i][0], ny = normals[i][1];
            double sum = 0;
            int count = 0;

            for (int t = -W; t <= W; t++) {
                int sx = (int) Math.round(px + t * nx);
                int sy = (int) Math.round(py + t * ny);
                if (sx >= 0 && sx < imgW && sy >= 0 && sy < imgH) {
                    if (mask.get(sx, sy) > 127) {
                        sum += ip.getf(sx, sy);
                        count++;
                    }
                }
            }
            result[i] = count > 0 ? sum / count : 0;
        }
        return result;
    }

    // ===================== Kymograph Generation =====================

    private static final int KYMO_PADDING = 20;

    private void generateSingleKymograph(double[][] allDist, double[][] allBright,
                                          double maxDist, int numFrames, File outDir) {
        int height = Math.max(1, (int) Math.ceil(maxDist) + 1) + KYMO_PADDING;
        FloatProcessor fp = new FloatProcessor(numFrames, height);

        for (int f = 0; f < numFrames; f++) {
            for (int y = 0; y < height; y++) {
                double dist = height - 1 - y; // flip Y; padding naturally at top
                fp.setf(f, y, (float) interpZero(allDist[f], allBright[f], dist));
            }
        }

        ImagePlus kymo = new ImagePlus("Kymograph", fp);
        Calibration cal = kymo.getCalibration();
        cal.pixelHeight = 1.0 / pixelPerMicron;
        cal.setYUnit("um");
        kymo.resetDisplayRange();
        kymo.show();

        File kymoFile = new File(outDir, "kymograph.tif");
        IJ.saveAs(kymo, "Tiff", kymoFile.getAbsolutePath());
        IJ.log("Saved: " + kymoFile.getAbsolutePath());
    }

    private void generateMergeKymograph(double[][] allDist, double[][] allBright1,
                                         double[][] allBright2, double maxDist,
                                         int numFrames, File outDir) {
        int height = Math.max(1, (int) Math.ceil(maxDist) + 1) + KYMO_PADDING;

        // Find max brightness for normalization
        double max1 = 0, max2 = 0;
        for (int f = 0; f < numFrames; f++) {
            for (double v : allBright1[f]) if (v > max1) max1 = v;
            for (double v : allBright2[f]) if (v > max2) max2 = v;
        }
        if (max1 == 0) max1 = 1;
        if (max2 == 0) max2 = 1;

        ColorProcessor cp = new ColorProcessor(numFrames, height);

        for (int f = 0; f < numFrames; f++) {
            for (int y = 0; y < height; y++) {
                double dist = height - 1 - y; // flip Y; padding naturally at top
                double v1 = interpZero(allDist[f], allBright1[f], dist);
                double v2 = interpZero(allDist[f], allBright2[f], dist);

                // Ch1 = Green = (0, G, 0), Ch2 = Magenta = (R, 0, B)
                int g = clamp255(v1 / max1 * 255);
                int r = clamp255(v2 / max2 * 255);
                int b = r;

                cp.set(f, y, (r << 16) | (g << 8) | b);
            }
        }

        ImagePlus kymo = new ImagePlus("Kymograph (Merge)", cp);
        Calibration cal = kymo.getCalibration();
        cal.pixelHeight = 1.0 / pixelPerMicron;
        cal.setYUnit("um");
        kymo.show();

        File kymoFile = new File(outDir, "kymograph_merge.tif");
        IJ.saveAs(kymo, "Tiff", kymoFile.getAbsolutePath());
        IJ.log("Saved: " + kymoFile.getAbsolutePath());
    }

    // ===================== Utilities =====================

    /** Linear interpolation; clamps to endpoint values (for resampling). */
    private double interpClamped(double[] x, double[] y, double target) {
        int n = x.length;
        if (n == 0) return 0;
        if (n == 1) return y[0];
        if (target <= x[0]) return y[0];
        if (target >= x[n - 1]) return y[n - 1];
        return interpSearch(x, y, target);
    }

    /** Linear interpolation; returns 0 beyond range (for kymograph). */
    private double interpZero(double[] x, double[] y, double target) {
        int n = x.length;
        if (n == 0) return 0;
        if (n == 1) return (target <= x[0]) ? y[0] : 0;
        if (target < x[0]) return 0;
        if (target > x[n - 1]) return 0;
        return interpSearch(x, y, target);
    }

    private double interpSearch(double[] x, double[] y, double target) {
        int lo = 0, hi = x.length - 1;
        while (lo < hi - 1) {
            int mid = (lo + hi) / 2;
            if (x[mid] <= target) lo = mid;
            else hi = mid;
        }
        double denom = x[hi] - x[lo];
        if (denom == 0) return y[lo];
        double t = (target - x[lo]) / denom;
        return y[lo] + t * (y[hi] - y[lo]);
    }

    private int clamp255(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v)));
    }

    private void saveBrightness(double[][] spaced, double[] brightness, File f) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            for (int i = 0; i < brightness.length; i++) {
                pw.printf("%.6f %.6f %.6f\n", spaced[i][0], spaced[i][1], brightness[i]);
            }
        }
    }
}
