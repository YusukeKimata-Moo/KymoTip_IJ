import ij.*;
import ij.plugin.PlugIn;
import ij.process.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * CCN_Registration: Fiji plugin for time-series image registration.
 *
 * Performs rotation + translation correction on each frame of a hyperstack
 * using normalized cross-correlation (NCC). Unlocked GUI version.
 */
public class CCN_Registration implements PlugIn {

    private static JFrame panelFrame;
    private JComboBox<String> regChannelCb;
    private JTextField angMinTf, angMaxTf, angStepTf;
    private JComboBox<String> refModeCb;
    private JTextField windowDTf;
    private JComboBox<String> fillModeCb;
    private JTextField bitDepthTf;
    private JButton runBtn;

    // ------------------------------------------------------------------ //
    // Entry point
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

    private void createAndShowGUI() {
        ImagePlus imp = WindowManager.getCurrentImage();
        int defaultBitDepth = (imp != null) ? imp.getBitDepth() : 16;
        int maxChannels = (imp != null) ? imp.getNChannels() : 5;

        panelFrame = new JFrame("KymoTip_IJ: CCN Registration");
        panelFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        panelFrame.setLayout(new GridBagLayout());
        panelFrame.setResizable(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Channel
        gbc.gridx = 0;
        gbc.gridy = row;
        panelFrame.add(new JLabel("Registration channel:"), gbc);
        String[] chs = new String[Math.max(1, maxChannels)];
        for (int i = 0; i < chs.length; i++)
            chs[i] = "Channel " + (i + 1);
        regChannelCb = new JComboBox<>(chs);
        gbc.gridx = 1;
        panelFrame.add(regChannelCb, gbc);
        row++;

        // Angle Min
        gbc.gridx = 0;
        gbc.gridy = row;
        panelFrame.add(new JLabel("Angle min (deg):"), gbc);
        angMinTf = new JTextField("-5", 5);
        gbc.gridx = 1;
        panelFrame.add(angMinTf, gbc);
        row++;

        // Angle Max
        gbc.gridx = 0;
        gbc.gridy = row;
        panelFrame.add(new JLabel("Angle max (deg):"), gbc);
        angMaxTf = new JTextField("5", 5);
        gbc.gridx = 1;
        panelFrame.add(angMaxTf, gbc);
        row++;

        // Angle Step
        gbc.gridx = 0;
        gbc.gridy = row;
        panelFrame.add(new JLabel("Angle step (deg):"), gbc);
        angStepTf = new JTextField("1", 5);
        gbc.gridx = 1;
        panelFrame.add(angStepTf, gbc);
        row++;

        // Reference Mode
        gbc.gridx = 0;
        gbc.gridy = row;
        panelFrame.add(new JLabel("Reference mode:"), gbc);
        refModeCb = new JComboBox<>(new String[] { "First frame", "Sliding window" });
        gbc.gridx = 1;
        panelFrame.add(refModeCb, gbc);
        row++;

        // Sliding Window D
        gbc.gridx = 0;
        gbc.gridy = row;
        panelFrame.add(new JLabel("Sliding window D:"), gbc);
        windowDTf = new JTextField("10", 5);
        gbc.gridx = 1;
        panelFrame.add(windowDTf, gbc);
        row++;

        // Fill mode
        gbc.gridx = 0;
        gbc.gridy = row;
        panelFrame.add(new JLabel("Border fill:"), gbc);
        fillModeCb = new JComboBox<>(new String[] { "Zero", "Random noise" });
        gbc.gridx = 1;
        panelFrame.add(fillModeCb, gbc);
        row++;

        // Display Bit Depth
        gbc.gridx = 0;
        gbc.gridy = row;
        panelFrame.add(new JLabel("Display Bit Depth:"), gbc);
        bitDepthTf = new JTextField(String.valueOf(defaultBitDepth), 5);
        gbc.gridx = 1;
        panelFrame.add(bitDepthTf, gbc);
        row++;

        // Run button
        runBtn = new JButton("Run Registration");
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        panelFrame.add(runBtn, gbc);

        runBtn.addActionListener(e -> startRegistration());

        panelFrame.pack();
        panelFrame.setLocationRelativeTo(null);
        panelFrame.setVisible(true);
    }

    private void startRegistration() {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            IJ.error("CCN Registration", "No image is open in Fiji.");
            return;
        }

        // Parse parameters safely
        int regChannel, refMode, windowD, fillMode, displayBitDepth;
        double angMin, angMax, angStep;
        try {
            // Update channel list if the image has more channels than what's in the combo
            // box
            int nChannels = imp.getNChannels();
            if (regChannelCb.getItemCount() < nChannels) {
                int oldSel = regChannelCb.getSelectedIndex();
                regChannelCb.removeAllItems();
                for (int i = 0; i < nChannels; i++)
                    regChannelCb.addItem("Channel " + (i + 1));
                regChannelCb.setSelectedIndex(Math.min(oldSel, nChannels - 1));
            }

            regChannel = regChannelCb.getSelectedIndex();
            angMin = Double.parseDouble(angMinTf.getText());
            angMax = Double.parseDouble(angMaxTf.getText());
            angStep = Double.parseDouble(angStepTf.getText());
            refMode = refModeCb.getSelectedIndex();
            windowD = Integer.parseInt(windowDTf.getText());
            fillMode = fillModeCb.getSelectedIndex();
            displayBitDepth = Integer.parseInt(bitDepthTf.getText());

            if (angStep <= 0)
                throw new NumberFormatException("Angle step must be > 0");
        } catch (Exception ex) {
            IJ.error("CCN Registration", "Invalid parameter: " + ex.getMessage());
            return;
        }

        runBtn.setEnabled(false);
        runBtn.setText("Running...");

        Thread thread = new Thread(() -> {
            try {
                executeRegistration(imp, regChannel, angMin, angMax, angStep, refMode, windowD, fillMode,
                        displayBitDepth);
            } catch (Exception ex) {
                IJ.log("Error during registration: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    runBtn.setEnabled(true);
                    runBtn.setText("Run Registration");
                });
            }
        });
        thread.start();
    }

    private void executeRegistration(ImagePlus imp, int regChannel, double angMin, double angMax, double angStep,
            int refMode, int windowD, int fillMode, int displayBitDepth) {
        int nChannels = imp.getNChannels();
        int nFrames = imp.getNFrames();
        int nSlices = imp.getNSlices();

        boolean swappedAxes = false;
        if (nFrames == 1 && nSlices > 1) {
            nFrames = nSlices;
            nSlices = 1;
            swappedAxes = true;
            IJ.log("Z-stack detected (nSlices=" + nFrames + ", nFrames=1). Treating slices as time frames.");
        }
        if (nFrames < 2) {
            IJ.error("CCN Registration", "Image must have at least 2 time frames.");
            return;
        }

        // --- Build angle list ---
        int nAngles = (int) Math.round((angMax - angMin) / angStep) + 1;
        double[] thetas = new double[nAngles];
        for (int k = 0; k < nAngles; k++) {
            thetas[k] = Math.toRadians(angMin + k * angStep);
        }

        // --- Prepare output hyperstack ---
        int width = imp.getWidth();
        int height = imp.getHeight();
        int bitDepth = imp.getBitDepth();

        ImageStack inStack = imp.getStack();
        ImageStack resultStack = new ImageStack(width, height);

        IJ.log("Creating output hyperstack: " + nChannels + "C x " + nSlices + "Z x " + nFrames + "T");

        for (int z = 1; z <= nSlices; z++) {
            for (int c = 1; c <= nChannels; c++) {
                int srcIdx = getStackIdx(imp, c, z, 1, swappedAxes);
                ImageProcessor ipRef = inStack.getProcessor(srcIdx).duplicate();
                resultStack.addSlice(null, ipRef);
            }
        }

        // --- Get reference image ---
        float[][] refImg = getFrame(inStack, imp, regChannel + 1, 1, nSlices, swappedAxes);
        float[] refPixels = refImg[0];

        float[][] correctedRef = new float[nFrames][];
        correctedRef[0] = refPixels.clone();

        IJ.showStatus("CCN Registration...");

        final int finalNSlices = nSlices;
        final boolean finalSwapped = swappedAxes;

        for (int t = 2; t <= nFrames; t++) {
            IJ.showProgress(t - 1, nFrames - 1);

            float[] curRef;
            if (refMode == 0) {
                curRef = correctedRef[0];
            } else {
                int refT = Math.max(0, t - 1 - windowD);
                curRef = correctedRef[refT];
            }

            float[][] curFrameAll = getFrame(inStack, imp, regChannel + 1, t, finalNSlices, finalSwapped);
            float[] curPixels = curFrameAll[0];

            int sqSize = Math.max(width, height);
            float[] exRef = extendToSquare(curRef, width, height, sqSize, fillMode);
            float[] exCur = extendToSquare(curPixels, width, height, sqSize, fillMode);

            double bestTheta = 0;
            int bestRow = 0, bestCol = 0;
            double bestCorr = -Double.MAX_VALUE;

            for (int a = 0; a < nAngles; a++) {
                float[] rotated = rotate(exCur, sqSize, sqSize, thetas[a], fillMode);
                double[] nccResult = normxcorr2(exRef, sqSize, sqSize, rotated, sqSize, sqSize);
                int nccW = 2 * sqSize - 1;
                int maxIdx = 0;
                double maxVal = nccResult[0];
                for (int i = 1; i < nccResult.length; i++) {
                    if (nccResult[i] > maxVal) {
                        maxVal = nccResult[i];
                        maxIdx = i;
                    }
                }
                int mRow = maxIdx / nccW + 1;
                int mCol = maxIdx % nccW + 1;

                if (maxVal > bestCorr) {
                    bestCorr = maxVal;
                    bestTheta = thetas[a];
                    bestRow = mRow;
                    bestCol = mCol;
                }
            }

            int my = bestRow - sqSize;
            int mx = bestCol - sqSize;

            IJ.log("Frame " + t + ": angle=" + String.format("%.1f", Math.toDegrees(bestTheta))
                    + " dx=" + mx + " dy=" + my
                    + " corr=" + String.format("%.4f", bestCorr));

            for (int z = 1; z <= finalNSlices; z++) {
                for (int c = 1; c <= nChannels; c++) {
                    int srcIdx = getStackIdx(imp, c, z, t, finalSwapped);
                    float[] pixels = toFloat(inStack.getProcessor(srcIdx));

                    float[] ex = extendToSquare(pixels, width, height, sqSize, fillMode);
                    float[] rot = rotate(ex, sqSize, sqSize, bestTheta, fillMode);
                    float[] cropped = contractFromSquare(rot, width, height, sqSize);
                    float[] translated = translate(cropped, width, height, my, mx, fillMode);

                    ImageProcessor op = createProcessor(translated, width, height, bitDepth);
                    resultStack.addSlice(null, op);
                }
            }

            float[] exReg = extendToSquare(curPixels, width, height, sqSize, fillMode);
            float[] rotReg = rotate(exReg, sqSize, sqSize, bestTheta, fillMode);
            float[] croppedReg = contractFromSquare(rotReg, width, height, sqSize);
            float[] translatedReg = translate(croppedReg, width, height, my, mx, fillMode);
            correctedRef[t - 1] = translatedReg;
        }

        IJ.showProgress(1.0);
        IJ.showStatus("CCN Registration done.");

        ImagePlus result = new ImagePlus("Registered_" + imp.getTitle(), resultStack);
        result.setDimensions(nChannels, nSlices, nFrames);

        result.copyScale(imp);
        IJ.log("Output container bit depth: " + result.getBitDepth() + " (Display set to " + displayBitDepth + "-bit)");

        double dMin = 0;
        double dMax = (1 << displayBitDepth) - 1;
        if (displayBitDepth >= 32) {
            dMin = imp.getDisplayRangeMin();
            dMax = imp.getDisplayRangeMax();
        }

        if (imp.isComposite() && nChannels > 1) {
            CompositeImage ci = (CompositeImage) imp;
            CompositeImage co;
            if (result instanceof CompositeImage) {
                co = (CompositeImage) result;
            } else {
                co = new CompositeImage(result, ci.getMode());
            }
            co.setMode(ci.getMode());
            for (int c = 1; c <= nChannels; c++) {
                ci.setC(c);
                co.setC(c);
                co.setChannelLut(ci.getChannelLut());
                co.setDisplayRange(dMin, dMax);
            }
            co.updateAndDraw();
            co.show();
        } else {
            result.setDisplayRange(dMin, dMax);
            if (imp.getProcessor().getColorModel() != null) {
                result.getProcessor().setColorModel(imp.getProcessor().getColorModel());
            }
            result.updateAndDraw();
            result.show();
        }

        IJ.log("Registration complete. Output: " + nChannels + "C x " + nSlices + "Z x " + nFrames + "T"
                + " (" + result.getBitDepth() + "-bit)");
    }

    // ================================================================== //
    // Core algorithm methods
    // ================================================================== //

    /**
     * Normalized cross-correlation (normxcorr2) — "full" mode via FFT.
     * Uses ImageJ's FHT (Fast Hartley Transform) for O(N² log N) performance.
     * Returns a (2H-1)*(2W-1) array of correlation values.
     */
    private double[] normxcorr2(float[] template, int tW, int tH,
            float[] image, int iW, int iH) {
        int outW = iW + tW - 1;
        int outH = iH + tH - 1;

        // FHT requires power-of-2 square dimensions
        int fftSize = nextPowerOf2(Math.max(outW, outH));

        // Compute means
        double tMean = mean(template);
        double iMean = mean(image);

        // Zero-mean template and image
        float[] tZm = new float[tW * tH];
        float[] imZm = new float[iW * iH];
        for (int i = 0; i < template.length; i++)
            tZm[i] = (float) (template[i] - tMean);
        for (int i = 0; i < image.length; i++)
            imZm[i] = (float) (image[i] - iMean);

        // Template energy
        double tEnergy = 0;
        for (float v : tZm)
            tEnergy += (double) v * v;

        // Cross-correlation via FFT: corr(t, im) = IFFT(conj(FFT(t)) * FFT(im))
        double[] crossCorr = fftCorrelate(tZm, tW, tH, imZm, iW, iH, fftSize);

        // Local energy: sum(im²) under template window, and sum(im) under template
        // window
        // Computed via FFT with a ones-kernel of template size
        float[] ones = new float[tW * tH];
        java.util.Arrays.fill(ones, 1.0f);
        float[] imSq = new float[iW * iH];
        for (int i = 0; i < imZm.length; i++)
            imSq[i] = imZm[i] * imZm[i];

        double[] localSumSq = fftCorrelate(ones, tW, tH, imSq, iW, iH, fftSize);
        double[] localSum = fftCorrelate(ones, tW, tH, imZm, iW, iH, fftSize);

        // Normalize
        int tSize = tW * tH;
        double[] result = new double[outW * outH];
        for (int oy = 0; oy < outH; oy++) {
            for (int ox = 0; ox < outW; ox++) {
                int outIdx = oy * outW + ox;
                int fftIdx = oy * fftSize + ox;
                double localEnergy = localSumSq[fftIdx]
                        - (localSum[fftIdx] * localSum[fftIdx]) / tSize;
                if (localEnergy < 0)
                    localEnergy = 0;
                double denom = Math.sqrt(localEnergy * tEnergy);
                result[outIdx] = (denom > 1e-10) ? crossCorr[fftIdx] / denom : 0;
            }
        }
        return result;
    }

    /**
     * FFT-based cross-correlation using ImageJ's FHT.
     * Pads both inputs into fftSize x fftSize, computes correlation.
     * Returns the full fftSize x fftSize result (caller extracts needed region).
     */
    private double[] fftCorrelate(float[] t, int tW, int tH,
            float[] im, int iW, int iH, int fftSize) {
        // Pad template into top-left of fftSize x fftSize
        FloatProcessor tFp = new FloatProcessor(fftSize, fftSize);
        for (int y = 0; y < tH; y++)
            for (int x = 0; x < tW; x++)
                tFp.setf(x, y, t[y * tW + x]);

        // Pad image into top-left
        FloatProcessor imFp = new FloatProcessor(fftSize, fftSize);
        for (int y = 0; y < iH; y++)
            for (int x = 0; x < iW; x++)
                imFp.setf(x, y, im[y * iW + x]);

        // Forward FHT
        FHT fhtT = new FHT(tFp);
        FHT fhtIm = new FHT(imFp);
        fhtT.transform();
        fhtIm.transform();

        // Correlation = conjugateMultiply (equivalent to conj(FFT(t)) * FFT(im))
        FHT corrFht = fhtT.conjugateMultiply(fhtIm);
        corrFht.inverseTransform();
        corrFht.swapQuadrants();

        // Extract result — swapQuadrants places the zero-lag at center,
        // so the "full" correlation output starts at (fftSize/2 - (tH-1), fftSize/2 -
        // (tW-1))
        int offY = fftSize / 2 - (tH - 1);
        int offX = fftSize / 2 - (tW - 1);
        float[] corrPixels = (float[]) corrFht.getPixels();

        double[] result = new double[fftSize * fftSize];
        for (int y = 0; y < fftSize; y++) {
            for (int x = 0; x < fftSize; x++) {
                int srcY = (y + offY + fftSize) % fftSize;
                int srcX = (x + offX + fftSize) % fftSize;
                result[y * fftSize + x] = corrPixels[srcY * fftSize + srcX];
            }
        }
        return result;
    }

    /** Return the smallest power of 2 >= n. */
    private int nextPowerOf2(int n) {
        int p = 1;
        while (p < n)
            p <<= 1;
        return p;
    }

    /**
     * Rotate image by theta radians using bicubic interpolation.
     * Pixels outside the original boundary are filled according to fillMode.
     */
    private float[] rotate(float[] img, int w, int h, double theta, int fillMode) {
        float[] result = new float[w * h];
        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);
        double cx = (w - 1) / 2.0;
        double cy = (h - 1) / 2.0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - cx;
                double dy = y - cy;
                // Inverse rotation
                double srcX = cosT * dx + sinT * dy + cx;
                double srcY = -sinT * dx + cosT * dy + cy;

                if (srcX >= 0 && srcX <= w - 1 && srcY >= 0 && srcY <= h - 1) {
                    result[y * w + x] = bicubicInterpolate(img, w, h, srcX, srcY);
                } else {
                    result[y * w + x] = 0; // border fill = 0
                }
            }
        }
        return result;
    }

    /** Bicubic interpolation at fractional position (fx, fy). */
    private float bicubicInterpolate(float[] img, int w, int h, double fx, double fy) {
        int ix = (int) Math.floor(fx);
        int iy = (int) Math.floor(fy);
        double dx = fx - ix;
        double dy = fy - iy;

        double sum = 0;
        for (int m = -1; m <= 2; m++) {
            for (int n = -1; n <= 2; n++) {
                int px = clamp(ix + n, 0, w - 1);
                int py = clamp(iy + m, 0, h - 1);
                double weight = cubicWeight(n - dx) * cubicWeight(m - dy);
                sum += img[py * w + px] * weight;
            }
        }
        return (float) sum;
    }

    /** Cubic interpolation kernel (Catmull-Rom). */
    private double cubicWeight(double x) {
        x = Math.abs(x);
        if (x <= 1)
            return 1.5 * x * x * x - 2.5 * x * x + 1;
        if (x < 2)
            return -0.5 * x * x * x + 2.5 * x * x - 4 * x + 2;
        return 0;
    }

    /**
     * Translate image by (dy, dx) pixels. Border filled with 0.
     */
    private float[] translate(float[] img, int w, int h, int dy, int dx, int fillMode) {
        float[] result = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcY = y - dy;
                int srcX = x - dx;
                if (srcY >= 0 && srcY < h && srcX >= 0 && srcX < w) {
                    result[y * w + x] = img[srcY * w + srcX];
                }
                // else remains 0
            }
        }
        return result;
    }

    /**
     * Extend a non-square image to square by zero-padding.
     */
    private float[] extendToSquare(float[] img, int w, int h, int sqSize, int fillMode) {
        if (w == h)
            return img.clone();
        float[] sq = new float[sqSize * sqSize];

        if (h > w) {
            // Pad left and right
            int pad = (h - w) / 2;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    sq[y * sqSize + (x + pad)] = img[y * w + x];
                }
            }
        } else {
            // Pad top and bottom
            int pad = (w - h) / 2;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    sq[(y + pad) * sqSize + x] = img[y * w + x];
                }
            }
        }
        return sq;
    }

    /**
     * Crop square image back to original dimensions.
     */
    private float[] contractFromSquare(float[] sq, int origW, int origH, int sqSize) {
        if (origW == origH)
            return sq.clone();
        float[] out = new float[origW * origH];

        if (origH > origW) {
            int pad = (origH - origW) / 2;
            for (int y = 0; y < origH; y++) {
                for (int x = 0; x < origW; x++) {
                    out[y * origW + x] = sq[y * sqSize + (x + pad)];
                }
            }
        } else {
            int pad = (origW - origH) / 2;
            for (int y = 0; y < origH; y++) {
                for (int x = 0; x < origW; x++) {
                    out[y * origW + x] = sq[(y + pad) * sqSize + x];
                }
            }
        }
        return out;
    }

    // ================================================================== //
    // Utility methods
    // ================================================================== //

    /** Extract a single frame (all slices) for a given channel as float arrays. */
    private float[][] getFrame(ImageStack stack, ImagePlus imp, int c, int t, int nSlices, boolean swappedAxes) {
        float[][] result = new float[nSlices][];
        for (int z = 1; z <= nSlices; z++) {
            int idx = getStackIdx(imp, c, z, t, swappedAxes);
            result[z - 1] = toFloat(stack.getProcessor(idx));
        }
        return result;
    }

    /**
     * Compute stack index, handling Z-stack-as-T-series axis swap.
     * When swappedAxes is true, the original image has slices but no frames,
     * so our "frame t" corresponds to the original "slice t".
     */
    private int getStackIdx(ImagePlus imp, int c, int z, int t, boolean swappedAxes) {
        if (swappedAxes) {
            // Our t (frame) is the original z (slice), original t is always 1
            return imp.getStackIndex(c, t, 1);
        }
        return imp.getStackIndex(c, z, t);
    }

    /** Convert any ImageProcessor to float array. */
    private float[] toFloat(ImageProcessor ip) {
        int n = ip.getWidth() * ip.getHeight();
        float[] f = new float[n];
        for (int i = 0; i < n; i++)
            f[i] = ip.getf(i);
        return f;
    }

    /** Create an ImageProcessor of the original bit depth from float data. */
    private ImageProcessor createProcessor(float[] data, int w, int h, int bitDepth) {
        switch (bitDepth) {
            case 8: {
                byte[] b = new byte[data.length];
                for (int i = 0; i < data.length; i++)
                    b[i] = (byte) clamp(Math.round(data[i]), 0, 255);
                return new ByteProcessor(w, h, b, null);
            }
            case 16: {
                short[] s = new short[data.length];
                for (int i = 0; i < data.length; i++)
                    s[i] = (short) clamp(Math.round(data[i]), 0, 65535);
                return new ShortProcessor(w, h, s, null);
            }
            case 32: {
                FloatProcessor fp = new FloatProcessor(w, h, data.clone());
                return fp;
            }
            default: {
                // Fallback to 16-bit
                short[] s = new short[data.length];
                for (int i = 0; i < data.length; i++)
                    s[i] = (short) clamp(Math.round(data[i]), 0, 65535);
                return new ShortProcessor(w, h, s, null);
            }
        }
    }

    private double mean(float[] a) {
        double s = 0;
        for (float v : a)
            s += v;
        return s / a.length;
    }

    private int clamp(long v, int min, int max) {
        return (int) Math.max(min, Math.min(max, v));
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
