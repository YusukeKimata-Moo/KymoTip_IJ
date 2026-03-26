import ij.*;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import ij.process.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;

/**
 * KymoTip_Trajectory: Fiji plugin for extracting and smoothing
 * cell outline trajectories from binarized time-lapse images.
 *
 * Algorithm ported from python_trajectory.ipynb
 * (Original code by Dr. Zichen Kang, modified by Dr. Yusuke Kimata)
 */
public class KymoTip_Trajectory implements PlugIn {

    private static JFrame panelFrame;
    private JTextField linkageDistTf;
    private JComboBox<String> smoothMethodCb;
    private JTextField loessDegreeTf, loessFractionTf;
    private JTextField movAvgWindowTf;
    private JTextField wraparoundTf;
    private JTextField outputDirTf;
    private JButton browseDirBtn;
    private JButton runBtn;

    // ------------------------------------------------------------------ //
    //  Entry point
    // ------------------------------------------------------------------ //
    @Override
    public void run(String arg) {
        SwingUtilities.invokeLater(() -> {
            if (panelFrame != null && panelFrame.isVisible()) {
                panelFrame.toFront();
                return;
            }
            createAndShowGUI();
        });
    }

    // ------------------------------------------------------------------ //
    //  GUI construction
    // ------------------------------------------------------------------ //
    private void createAndShowGUI() {
        panelFrame = new JFrame("KymoTip_IJ: Trajectory");
        panelFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        panelFrame.setLayout(new GridBagLayout());
        panelFrame.setResizable(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;

        int row = 0;

        // Linkage distance
        gbc.gridx = 0; gbc.gridy = row;
        panelFrame.add(new JLabel("Linkage distance:"), gbc);
        linkageDistTf = new JTextField("3", 6);
        gbc.gridx = 1;
        panelFrame.add(linkageDistTf, gbc);
        row++;

        // Smoothing method
        gbc.gridx = 0; gbc.gridy = row;
        panelFrame.add(new JLabel("Smoothing method:"), gbc);
        smoothMethodCb = new JComboBox<>(new String[]{"LOESS", "Moving Average"});
        gbc.gridx = 1;
        panelFrame.add(smoothMethodCb, gbc);
        row++;

        // LOESS degree (only used for LOESS)
        gbc.gridx = 0; gbc.gridy = row;
        panelFrame.add(new JLabel("LOESS degree:"), gbc);
        loessDegreeTf = new JTextField("2", 6);
        gbc.gridx = 1;
        panelFrame.add(loessDegreeTf, gbc);
        row++;

        // LOESS fraction
        gbc.gridx = 0; gbc.gridy = row;
        panelFrame.add(new JLabel("LOESS fraction:"), gbc);
        loessFractionTf = new JTextField("0.10", 6);
        gbc.gridx = 1;
        panelFrame.add(loessFractionTf, gbc);
        row++;

        // Moving average window
        gbc.gridx = 0; gbc.gridy = row;
        panelFrame.add(new JLabel("Moving Avg window:"), gbc);
        movAvgWindowTf = new JTextField("10", 6);
        gbc.gridx = 1;
        panelFrame.add(movAvgWindowTf, gbc);
        row++;

        // Wraparound points (for LOESS)
        gbc.gridx = 0; gbc.gridy = row;
        panelFrame.add(new JLabel("Wraparound points:"), gbc);
        wraparoundTf = new JTextField("100", 6);
        gbc.gridx = 1;
        panelFrame.add(wraparoundTf, gbc);
        row++;

        // Output directory
        gbc.gridx = 0; gbc.gridy = row;
        panelFrame.add(new JLabel("Output directory:"), gbc);
        JPanel dirPanel = new JPanel(new BorderLayout(4, 0));
        outputDirTf = new JTextField("", 16);
        browseDirBtn = new JButton("...");
        browseDirBtn.addActionListener(e -> {
            DirectoryChooser dc = new DirectoryChooser("Select output directory");
            String dir = dc.getDirectory();
            if (dir != null) outputDirTf.setText(dir);
        });
        dirPanel.add(outputDirTf, BorderLayout.CENTER);
        dirPanel.add(browseDirBtn, BorderLayout.EAST);
        gbc.gridx = 1;
        panelFrame.add(dirPanel, gbc);
        row++;

        // Run button
        runBtn = new JButton("Run Trajectory");
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        panelFrame.add(runBtn, gbc);

        runBtn.addActionListener(e -> startTrajectory());

        panelFrame.pack();
        panelFrame.setLocationRelativeTo(null);
        panelFrame.setVisible(true);
    }

    // ------------------------------------------------------------------ //
    //  Parameter parsing and thread launch
    // ------------------------------------------------------------------ //
    private void startTrajectory() {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            IJ.error("KymoTip Trajectory", "No image is open in Fiji.");
            return;
        }
        if (imp.getBitDepth() != 8) {
            IJ.error("KymoTip Trajectory", "Input must be an 8-bit image.");
            return;
        }
        int nFrames = imp.getStackSize();
        if (nFrames < 1) {
            IJ.error("KymoTip Trajectory", "Image stack is empty.");
            return;
        }

        // Parse parameters
        int linkageDist;
        int loessDegree;
        double loessFraction;
        int movAvgWindow;
        int wraparoundPts;
        String outputDir;
        int smoothMethodIdx;

        try {
            linkageDist = Integer.parseInt(linkageDistTf.getText().trim());
            loessDegree = Integer.parseInt(loessDegreeTf.getText().trim());
            loessFraction = Double.parseDouble(loessFractionTf.getText().trim());
            movAvgWindow = Integer.parseInt(movAvgWindowTf.getText().trim());
            wraparoundPts = Integer.parseInt(wraparoundTf.getText().trim());
            smoothMethodIdx = smoothMethodCb.getSelectedIndex();
            outputDir = outputDirTf.getText().trim();

            if (linkageDist <= 0) throw new NumberFormatException("Linkage distance must be > 0");
            if (loessFraction <= 0 || loessFraction > 1) throw new NumberFormatException("LOESS fraction must be in (0, 1]");
            if (movAvgWindow <= 0) throw new NumberFormatException("Window size must be > 0");
            if (wraparoundPts < 0) throw new NumberFormatException("Wraparound points must be >= 0");
            if (outputDir.isEmpty()) throw new IllegalArgumentException("Output directory is required");
        } catch (Exception ex) {
            IJ.error("KymoTip Trajectory", "Invalid parameter: " + ex.getMessage());
            return;
        }

        runBtn.setEnabled(false);
        runBtn.setText("Running...");

        final int fLinkageDist = linkageDist;
        final int fLoessDegree = loessDegree;
        final double fLoessFraction = loessFraction;
        final int fMovAvgWindow = movAvgWindow;
        final int fWraparoundPts = wraparoundPts;
        final int fSmoothMethodIdx = smoothMethodIdx;
        final String fOutputDir = outputDir;

        Thread thread = new Thread(() -> {
            try {
                executeTrajectory(imp, fLinkageDist, fSmoothMethodIdx,
                        fLoessDegree, fLoessFraction, fMovAvgWindow,
                        fWraparoundPts, fOutputDir);
            } catch (Exception ex) {
                IJ.log("Error during trajectory extraction: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    runBtn.setEnabled(true);
                    runBtn.setText("Run Trajectory");
                });
            }
        });
        thread.start();
    }

    // ------------------------------------------------------------------ //
    //  Main processing
    // ------------------------------------------------------------------ //
    private void executeTrajectory(ImagePlus imp, int linkageDist,
            int smoothMethodIdx, int loessDegree, double loessFraction,
            int movAvgWindow, int wraparoundPts, String outputDir) {

        ImageStack stack = imp.getStack();
        int nFrames = stack.getSize();
        String title = imp.getTitle();
        // Remove extension from title for output naming
        if (title.contains(".")) {
            title = title.substring(0, title.lastIndexOf('.'));
        }

        String methodName = (smoothMethodIdx == 0) ? "LOESS" : "MovingAverage";
        IJ.log("=== KymoTip Trajectory ===");
        IJ.log("Input: " + imp.getTitle() + " (" + nFrames + " frames)");
        IJ.log("Method: " + methodName);
        IJ.log("Linkage distance: " + linkageDist);
        if (smoothMethodIdx == 0) {
            IJ.log("LOESS degree: " + loessDegree + ", fraction: " + loessFraction);
            IJ.log("Wraparound points: " + wraparoundPts);
        } else {
            IJ.log("Window size: " + movAvgWindow);
        }
        IJ.log("Output directory: " + outputDir);

        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        for (int t = 1; t <= nFrames; t++) {
            IJ.showProgress(t, nFrames);
            IJ.showStatus("Trajectory: frame " + t + "/" + nFrames);

            ImageProcessor ip = stack.getProcessor(t);

            // Step 1: Extract outline coordinates
            double[][] outline = extractOutline(ip);
            double[] xCoords = outline[0];
            double[] yCoords = outline[1];

            if (xCoords.length < 10) {
                IJ.log("Frame " + t + ": too few outline pixels (" + xCoords.length + "), skipping.");
                continue;
            }

            // Step 2: Resort coordinates by nearest-neighbor chain
            double[][] resorted = resort(xCoords, yCoords, linkageDist);
            double[] xr = resorted[0];
            double[] yr = resorted[1];

            if (xr.length < 10) {
                IJ.log("Frame " + t + ": too few resorted points (" + xr.length + "), skipping.");
                continue;
            }

            // Step 3: Smooth
            double[] smoothedX, smoothedY;
            if (smoothMethodIdx == 0) {
                // LOESS
                double[][] smoothed = loessSmooth(xr, yr, loessDegree, loessFraction, wraparoundPts);
                smoothedX = smoothed[0];
                smoothedY = smoothed[1];
            } else {
                // Moving Average
                smoothedX = cyclicMovingAverage(xr, movAvgWindow);
                smoothedY = cyclicMovingAverage(yr, movAvgWindow);
            }

            // Step 4: Write output
            String outName = title + "_" + String.format("%03d", t - 1) + ".txt";
            File outFile = new File(outDir, outName);
            try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
                for (int i = 0; i < smoothedX.length; i++) {
                    pw.println(smoothedX[i] + " " + smoothedY[i]);
                }
            } catch (IOException ex) {
                IJ.log("Frame " + t + ": failed to write output: " + ex.getMessage());
            }

            IJ.log("Frame " + t + ": " + xCoords.length + " outline pixels -> "
                    + xr.length + " resorted -> " + smoothedX.length + " smoothed. Saved: " + outName);
        }

        IJ.showProgress(1.0);
        IJ.showStatus("Trajectory extraction done.");
        IJ.log("=== Trajectory extraction complete ===");
    }

    // ================================================================== //
    //  Core algorithm methods
    // ================================================================== //

    /**
     * Extract non-zero pixel coordinates from an 8-bit image.
     * Equivalent to np.where(img != 0).
     */
    private double[][] extractOutline(ImageProcessor ip) {
        int w = ip.getWidth();
        int h = ip.getHeight();
        java.util.List<Double> xList = new ArrayList<>();
        java.util.List<Double> yList = new ArrayList<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (ip.get(x, y) != 0) {
                    xList.add((double) x);
                    yList.add((double) y);
                }
            }
        }

        double[] xArr = new double[xList.size()];
        double[] yArr = new double[yList.size()];
        for (int i = 0; i < xList.size(); i++) {
            xArr[i] = xList.get(i);
            yArr[i] = yList.get(i);
        }
        return new double[][] { xArr, yArr };
    }

    /**
     * Resort outline coordinates by nearest-neighbor chain.
     * Port of the Python resort() function.
     *
     * @param x  x-coordinates of outline pixels
     * @param y  y-coordinates of outline pixels
     * @param d  maximum linkage distance
     * @return   resorted coordinates {xr, yr}
     */
    private double[][] resort(double[] x, double[] y, int d) {
        int n = x.length;
        double[] xr = new double[n];
        double[] yr = new double[n];

        xr[0] = x[0];
        yr[0] = y[0];

        boolean[] used = new boolean[n];
        used[0] = true;

        int p = 1;
        for (int i = 1; i < n; i++) {
            double minDist = Double.MAX_VALUE;
            int k = -1;

            for (int j = 0; j < n; j++) {
                if (!used[j]) {
                    double dx = x[j] - xr[p - 1];
                    double dy = y[j] - yr[p - 1];
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist <= d && dist < minDist) {
                        minDist = dist;
                        k = j;
                    }
                }
            }

            if (k >= 0) {
                xr[p] = x[k];
                yr[p] = y[k];
                used[k] = true;
                p++;
            }
        }

        // Trim unused portion
        return new double[][] {
            Arrays.copyOf(xr, p),
            Arrays.copyOf(yr, p)
        };
    }

    /**
     * LOESS smoothing with cyclic data extension.
     * Extends data by appending wraparound points, applies LOESS,
     * then trims back to original length.
     *
     * Note: Apache Commons Math LoessInterpolator supports degree 1 or 2 only
     * (locally weighted linear or quadratic). The 'degree' parameter from the
     * Python notebook maps to robustnessIters in this implementation.
     */
    private double[][] loessSmooth(double[] xr, double[] yr,
            int degree, double fraction, int wraparound) {

        int origLen = xr.length;
        int wrapPts = Math.min(wraparound, origLen);

        // Build extended arrays
        double[] extX = new double[origLen + wrapPts];
        double[] extY = new double[origLen + wrapPts];
        System.arraycopy(xr, 0, extX, 0, origLen);
        System.arraycopy(xr, 0, extX, origLen, wrapPts);
        System.arraycopy(yr, 0, extY, 0, origLen);
        System.arraycopy(yr, 0, extY, origLen, wrapPts);

        // Index array for LOESS (independent variable)
        double[] idx = new double[extX.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;

        // Apply LOESS
        // bandwidth = fraction, robustnessIters = degree (as approximation)
        double bandwidth = Math.max(fraction, 3.0 / idx.length); // ensure minimum
        LoessInterpolator loess = new LoessInterpolator(bandwidth, degree);

        double[] smoothedExtX = loess.smooth(idx, extX);
        double[] smoothedExtY = loess.smooth(idx, extY);

        // Trim to original length
        return new double[][] {
            Arrays.copyOf(smoothedExtX, origLen),
            Arrays.copyOf(smoothedExtY, origLen)
        };
    }

    /**
     * Cyclic moving average.
     * Port of the Python cyclic_moving_average() function.
     */
    private double[] cyclicMovingAverage(double[] data, int windowSize) {
        int n = data.length;
        int ext = windowSize - 1;

        // Build extended data: [data[-(ext):]  data  data[:ext]]
        double[] extended = new double[n + 2 * ext];
        // tail portion
        System.arraycopy(data, n - ext, extended, 0, ext);
        // main data
        System.arraycopy(data, 0, extended, ext, n);
        // head portion
        System.arraycopy(data, 0, extended, ext + n, ext);

        // Convolve with uniform kernel
        double[] kernel = new double[windowSize];
        Arrays.fill(kernel, 1.0 / windowSize);

        // "valid" mode convolution
        int validLen = extended.length - windowSize + 1;
        double[] convResult = new double[validLen];
        for (int i = 0; i < validLen; i++) {
            double sum = 0;
            for (int j = 0; j < windowSize; j++) {
                sum += extended[i + j] * kernel[j];
            }
            convResult[i] = sum;
        }

        // Extract the portion corresponding to the original data
        int halfWin = windowSize / 2;
        double[] result = new double[n];
        System.arraycopy(convResult, halfWin, result, 0, n);
        return result;
    }
}
