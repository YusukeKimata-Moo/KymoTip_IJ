import ij.IJ;
import ij.plugin.PlugIn;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.List;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

public class KymoTip_Centerline implements PlugIn {

    private JFrame frame;
    private JTextField txtInDir, txtOutDir;
    private JTextField txtO, txtM1, txtM2, txtMm, txtImgSize, txtScale;
    private JButton btnRun;

    private int o = 10;
    private int m1 = 10;
    private int m2 = 10;
    private int mm = 65;
    private int imgSize = 512;
    private double pixelPerMicron = 4.917;

    @Override
    public void run(String arg) {
        showDialog();
    }

    private void showDialog() {
        if (frame != null && frame.isVisible()) {
            frame.toFront();
            return;
        }
        frame = new JFrame("Centerline Extraction (Coordinate Input)");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Input Dir
        gbc.gridx = 0; gbc.gridy = row;
        frame.add(new JLabel("Input Contour Data Dir:"), gbc);
        JPanel inDirPanel = new JPanel(new BorderLayout(5, 0));
        txtInDir = new JTextField(20);
        JButton btnBrowseIn = new JButton("Browse...");
        btnBrowseIn.addActionListener(e -> browseDir(txtInDir));
        inDirPanel.add(txtInDir, BorderLayout.CENTER);
        inDirPanel.add(btnBrowseIn, BorderLayout.EAST);
        gbc.gridx = 1; gbc.gridy = row;
        frame.add(inDirPanel, gbc);
        row++;

        // Output Dir
        gbc.gridx = 0; gbc.gridy = row;
        frame.add(new JLabel("Output Dir:"), gbc);
        JPanel outDirPanel = new JPanel(new BorderLayout(5, 0));
        txtOutDir = new JTextField(20);
        JButton btnBrowseOut = new JButton("Browse...");
        btnBrowseOut.addActionListener(e -> browseDir(txtOutDir));
        outDirPanel.add(txtOutDir, BorderLayout.CENTER);
        outDirPanel.add(btnBrowseOut, BorderLayout.EAST);
        gbc.gridx = 1; gbc.gridy = row;
        frame.add(outDirPanel, gbc);
        row++;

        // Params
        addParamRow(frame, gbc, row++, "Extension average points (o):", txtO = new JTextField(String.valueOf(o), 5));
        addParamRow(frame, gbc, row++, "Exclude bottom points (m1):", txtM1 = new JTextField(String.valueOf(m1), 5));
        addParamRow(frame, gbc, row++, "Exclude tip points (m2):", txtM2 = new JTextField(String.valueOf(m2), 5));
        addParamRow(frame, gbc, row++, "Smoothing window (mm):", txtMm = new JTextField(String.valueOf(mm), 5));
        addParamRow(frame, gbc, row++, "Image size (px):", txtImgSize = new JTextField(String.valueOf(imgSize), 5));
        addParamRow(frame, gbc, row++, "Scale (pixel/\u00B5m):", txtScale = new JTextField(String.valueOf(pixelPerMicron), 5));

        // Run
        btnRun = new JButton("Run Processing");
        btnRun.addActionListener(e -> execute());
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        frame.add(btnRun, gbc);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void addParamRow(JFrame f, GridBagConstraints gbc, int row, String label, JTextField tf) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        f.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.gridy = row;
        f.add(tf, gbc);
    }

    private void browseDir(JTextField tf) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            tf.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void execute() {
        String inDirStr = txtInDir.getText();
        String outDirStr = txtOutDir.getText();
        if (inDirStr.isEmpty() || outDirStr.isEmpty()) {
            IJ.error("Please specify both input and output directories.");
            return;
        }
        try {
            o = Integer.parseInt(txtO.getText());
            m1 = Integer.parseInt(txtM1.getText());
            m2 = Integer.parseInt(txtM2.getText());
            mm = Integer.parseInt(txtMm.getText());
            imgSize = Integer.parseInt(txtImgSize.getText());
            pixelPerMicron = Double.parseDouble(txtScale.getText().trim());
        } catch (NumberFormatException e) {
            IJ.error("Invalid numeric parameter.");
            return;
        }

        new Thread(() -> processDirectory(new File(inDirStr), new File(outDirStr))).start();
    }

    private void processDirectory(File inDir, File outDir) {
        File[] files = inDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null || files.length == 0) {
            IJ.log("No .txt files found in input directory.");
            return;
        }
        Arrays.sort(files);

        File txtOutDir = new File(outDir, "centerline");
        File imgOutDir = new File(outDir, "centerline(Fig)");
        txtOutDir.mkdirs();
        imgOutDir.mkdirs();

        IJ.log("Found " + files.length + " contour files to process.");

        List<String[]> lengthRecords = new ArrayList<>();

        for (int fi = 0; fi < files.length; fi++) {
            File f = files[fi];
            try {
                IJ.log("Processing " + f.getName() + "...");
                List<Coordinate> contour = loadContour(f);
                if (contour.size() < 3) continue;

                List<Node> skeletonPath = extractCenterline(contour);
                if (skeletonPath.isEmpty()) {
                    IJ.log("No valid skeleton path found for " + f.getName());
                    continue;
                }

                // Adjust direction
                if (skeletonPath.get(0).y - skeletonPath.get(skeletonPath.size() - 1).y < 0) {
                    Collections.reverse(skeletonPath);
                }

                // Smooth
                List<Coordinate> smoothed = smoothCenterline(skeletonPath, m1, m2, mm);

                // Extend
                List<Coordinate> extended = extendCenterline(contour, smoothed, o);

                // Interpolate
                double avgDist = calculateAverageDistance(extended);
                List<Coordinate> complete = interpolateCenterline(extended, avgDist);

                // Calculate cell length (sum of inter-point distances, in microns)
                double lengthPx = 0;
                for (int i = 1; i < complete.size(); i++) {
                    double dx = complete.get(i).x - complete.get(i - 1).x;
                    double dy = complete.get(i).y - complete.get(i - 1).y;
                    lengthPx += Math.sqrt(dx * dx + dy * dy);
                }
                double lengthUm = lengthPx / pixelPerMicron;
                lengthRecords.add(new String[]{String.valueOf(fi + 1), String.format("%.6f", lengthUm)});

                // Save txt (pixel coordinates)
                String baseName = f.getName().replace(".txt", "");
                saveCenterline(complete, new File(txtOutDir, baseName + ".txt"));

                // Save png
                savePlot(contour, complete, new File(imgOutDir, baseName + ".png"));

            } catch (Exception e) {
                IJ.log("Error processing " + f.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Save cell length CSV
        try {
            File csvFile = new File(outDir, "cell_length.csv");
            try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile))) {
                pw.println("frame,length_um");
                for (String[] rec : lengthRecords) {
                    pw.println(rec[0] + "," + rec[1]);
                }
            }
            IJ.log("Saved: " + csvFile.getAbsolutePath());
        } catch (IOException e) {
            IJ.log("Error saving cell_length.csv: " + e.getMessage());
        }

        IJ.log("Processing completed.");
    }

    private List<Coordinate> loadContour(File f) throws IOException {
        List<Coordinate> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    list.add(new Coordinate(x, y));
                }
            }
        }
        return list;
    }

    static class Node {
        double x, y;
        List<Edge> edges = new ArrayList<>();
        public Node(double x, double y) { this.x = x; this.y = y; }
        public boolean equals(Object o) {
            if (!(o instanceof Node)) return false;
            Node n = (Node) o;
            return Math.abs(x - n.x) < 1e-5 && Math.abs(y - n.y) < 1e-5;
        }
        @Override
        public int hashCode() { return Objects.hash(Math.round(x*1000), Math.round(y*1000)); }
    }
    static class Edge {
        Node target; double weight;
        public Edge(Node t, double w) { target = t; weight = w; }
    }

    private Node getOrCreateNode(double x, double y, List<Node> nodes) {
        Node d = new Node(x, y);
        for (Node n : nodes) if (n.equals(d)) return n;
        nodes.add(d);
        return d;
    }
    private void addUniqueEdge(Node n1, Node n2, double dist) {
        for (Edge e : n1.edges) {
            if (e.target.equals(n2)) return;
        }
        n1.edges.add(new Edge(n2, dist));
    }

    private List<Node> extractCenterline(List<Coordinate> contourPts) {
        GeometryFactory gf = new GeometryFactory();
        Coordinate[] bnd = contourPts.toArray(new Coordinate[0]);
        // ensure closed
        if (!bnd[0].equals2D(bnd[bnd.length-1])) {
            Coordinate[] closed = new Coordinate[bnd.length+1];
            System.arraycopy(bnd, 0, closed, 0, bnd.length);
            closed[bnd.length] = bnd[0];
            bnd = closed;
        }
        Polygon contourPoly = gf.createPolygon(bnd);

        VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
        builder.setSites(contourPts);
        Geometry voronoiGeom = builder.getDiagram(gf);

        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < voronoiGeom.getNumGeometries(); i++) {
            Polygon cell = (Polygon) voronoiGeom.getGeometryN(i);
            Coordinate[] cellCoords = cell.getExteriorRing().getCoordinates();
            for (int j = 0; j < cellCoords.length - 1; j++) {
                Coordinate c1 = cellCoords[j];
                Coordinate c2 = cellCoords[j+1];
                Point p1 = gf.createPoint(c1);
                Point p2 = gf.createPoint(c2);
                
                // Voronoi vertices must be strictly inside the contour
                if (contourPoly.contains(p1) && contourPoly.contains(p2)) {
                    Node n1 = getOrCreateNode(c1.x, c1.y, nodes);
                    Node n2 = getOrCreateNode(c2.x, c2.y, nodes);
                    double d = c1.distance(c2);
                    addUniqueEdge(n1, n2, d);
                    addUniqueEdge(n2, n1, d);
                }
            }
        }

        List<Node> endpoints = new ArrayList<>();
        for (Node n : nodes) {
            if (n.edges.size() == 1) endpoints.add(n);
        }

        double maxLen = -1;
        List<Node> bestPath = new ArrayList<>();

        for (int i = 0; i < endpoints.size(); i++) {
            Node src = endpoints.get(i);
            Map<Node, Double> dist = new HashMap<>();
            Map<Node, Node> prev = new HashMap<>();
            PriorityQueue<Node> pq = new PriorityQueue<>(Comparator.comparingDouble(n -> dist.getOrDefault(n, Double.MAX_VALUE)));
            
            for (Node n : nodes) dist.put(n, Double.MAX_VALUE);
            dist.put(src, 0.0);
            pq.add(src);

            while (!pq.isEmpty()) {
                Node u = pq.poll();
                for (Edge e : u.edges) {
                    Node v = e.target;
                    double alt = dist.get(u) + e.weight;
                    if (alt < dist.get(v)) {
                        dist.put(v, alt);
                        prev.put(v, u);
                        pq.remove(v); pq.add(v);
                    }
                }
            }

            for (int j = i + 1; j < endpoints.size(); j++) {
                Node tgt = endpoints.get(j);
                if (dist.get(tgt) != Double.MAX_VALUE && dist.get(tgt) > maxLen) {
                    maxLen = dist.get(tgt);
                    bestPath = reconstructPath(prev, tgt);
                }
            }
        }
        return bestPath;
    }

    private List<Node> reconstructPath(Map<Node, Node> prev, Node tgt) {
        List<Node> path = new ArrayList<>();
        Node curr = tgt;
        while (curr != null) {
            path.add(curr);
            curr = prev.get(curr);
        }
        Collections.reverse(path);
        return path;
    }

    private List<Coordinate> smoothCenterline(List<Node> path, int m1, int m2, int mm) {
        if (path.size() < m1 + m2 + 1) {
            List<Coordinate> res = new ArrayList<>();
            for (Node n : path) res.add(new Coordinate(n.x, n.y));
            return res;
        }

        int startIdx = m1;
        int endIdx = path.size() - m2 + 1; // match Python path[m1:len(path)-m2+1]
        List<Node> sub = path.subList(startIdx, endIdx);

        // cumsum-based moving average (matches Python moving_avg)
        // output length = sub.size() - mm + 1
        int n = sub.size();
        double[] cumX = new double[n + 1];
        double[] cumY = new double[n + 1];
        for (int i = 0; i < n; i++) {
            cumX[i + 1] = cumX[i] + sub.get(i).x;
            cumY[i + 1] = cumY[i] + sub.get(i).y;
        }

        List<Coordinate> smoothed = new ArrayList<>();
        for (int i = 0; i <= n - mm; i++) {
            double sx = (cumX[i + mm] - cumX[i]) / mm;
            double sy = (cumY[i + mm] - cumY[i]) / mm;
            smoothed.add(new Coordinate(sx, sy));
        }
        return smoothed;
    }

    private List<Coordinate> extendCenterline(List<Coordinate> contour, List<Coordinate> smooth, int o) {
        if (smooth.size() < o + 1) return smooth;

        double v1x = 0, v1y = 0;
        double v2x = 0, v2y = 0;
        int n = smooth.size();

        for (int i = 0; i < o; i++) {
            Coordinate p1 = smooth.get(i);
            Coordinate p2 = smooth.get(i + 1);
            double d1 = p1.distance(p2);
            if (d1 > 0) {
                v1x -= (p2.x - p1.x) / d1 / o;
                v1y -= (p2.y - p1.y) / d1 / o;
            }

            Coordinate p3 = smooth.get(n - 2 - i);
            Coordinate p4 = smooth.get(n - 1 - i);
            double d2 = p3.distance(p4);
            if (d2 > 0) {
                v2x += (p4.x - p3.x) / d2 / o;
                v2y += (p4.y - p3.y) / d2 / o;
            }
        }

        Coordinate startNode = smooth.get(0);
        Coordinate endNode = smooth.get(n - 1);
        double maxL1 = -Double.MAX_VALUE, maxL2 = -Double.MAX_VALUE;
        Coordinate extStart = startNode, extEnd = endNode;

        for (Coordinate pt : contour) {
            double dS = pt.distance(startNode);
            if (dS > 0) {
                double l1 = (v1x * (pt.x - startNode.x)/dS) + (v1y * (pt.y - startNode.y)/dS);
                if (l1 > maxL1) { maxL1 = l1; extStart = pt; }
            }
            double dE = pt.distance(endNode);
            if (dE > 0) {
                double l2 = (v2x * (pt.x - endNode.x)/dE) + (v2y * (pt.y - endNode.y)/dE);
                if (l2 > maxL2) { maxL2 = l2; extEnd = pt; }
            }
        }

        List<Coordinate> ext = new ArrayList<>();
        ext.add(extStart);
        ext.addAll(smooth);
        ext.add(extEnd);
        return ext;
    }

    private double calculateAverageDistance(List<Coordinate> pts) {
        if (pts.size() < 2) return 1.0;
        double sum = 0;
        for (int i=0; i<pts.size()-1; i++) {
            sum += pts.get(i).distance(pts.get(i+1));
        }
        return sum / (pts.size() - 1);
    }

    private List<Coordinate> interpolateCenterline(List<Coordinate> pts, double avgDist) {
        if (pts.size() < 3) return pts;
        List<Coordinate> res = new ArrayList<>();
        
        Coordinate c0 = pts.get(0);
        Coordinate c1 = pts.get(1);
        double dStart = c0.distance(c1);
        int nStart = (int)(dStart / avgDist) - 1;
        
        res.add(c0);
        for (int i=1; i<=nStart; i++) {
            double rx = c0.x + (c1.x - c0.x) * i / (nStart + 1);
            double ry = c0.y + (c1.y - c0.y) * i / (nStart + 1);
            res.add(new Coordinate(rx, ry));
        }
        
        for (int i=1; i<pts.size()-1; i++) res.add(pts.get(i));
        
        Coordinate cM = pts.get(pts.size()-2);
        Coordinate cE = pts.get(pts.size()-1);
        double dEnd = cM.distance(cE);
        int nEnd = (int)(dEnd / avgDist) - 1;
        
        for (int i=1; i<=nEnd; i++) {
            double rx = cM.x + (cE.x - cM.x) * i / (nEnd + 1);
            double ry = cM.y + (cE.y - cM.y) * i / (nEnd + 1);
            res.add(new Coordinate(rx, ry));
        }
        res.add(cE);
        return res;
    }

    private void saveCenterline(List<Coordinate> path, File f) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            for (Coordinate c : path) pw.printf("%.6f %.6f\n", c.x, c.y);
        }
    }

    private void savePlot(List<Coordinate> contour, List<Coordinate> path, File f) throws IOException {
        int w = imgSize, h = imgSize;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        
        g.setStroke(new BasicStroke(1.0f));

        g.setColor(Color.RED);
        for (int i = 0; i < contour.size() - 1; i++) {
            Coordinate c1 = contour.get(i);
            Coordinate c2 = contour.get(i + 1);
            g.drawLine((int)c1.x, (int)c1.y, (int)c2.x, (int)c2.y);
        }

        g.setColor(Color.BLUE);
        for (int i = 0; i < path.size() - 1; i++) {
            Coordinate p1 = path.get(i);
            Coordinate p2 = path.get(i+1);
            g.drawLine((int)p1.x, (int)p1.y, (int)p2.x, (int)p2.y);
        }
        g.dispose();
        ImageIO.write(img, "png", f);
    }
}
