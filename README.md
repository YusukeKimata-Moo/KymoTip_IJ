# KymoTip_IJ

A Fiji/ImageJ plugin suite for quantitative analysis of time-lapse cell imaging. This is the ImageJ/Fiji implementation of the KymoTip toolbox.

## Installation

1. Go to the [Releases](../../releases) page.
2. Download the latest `KymoTip_IJ.jar`.
3. Place the downloaded `.jar` file into the `plugins` folder of your Fiji/ImageJ installation.
4. Restart Fiji. Commands will appear under the `Plugins > KymoTip_IJ` menu.

## Commands

### CCN Registration

Contour-based coordinate normalization (CCN) for time-series image registration.

This command performs rotation and translation correction on each frame of a hyperstack (Multi-channel / single Z-slice / Time-series). By selecting a specific channel (e.g., cell contour) as a reference, it accurately tracks and corrects sample drift and rotation over time across all channels.

#### Features

- Rotation + Translation correction via Fast Hartley Transform (FHT) based Normalized Cross-Correlation.
- Supports multi-channel, single Z-stack, and Time-series hyperstacks.
- Independent Swing GUI (non-modal) for uninterrupted Fiji interaction.
- Configurable reference frames (First frame or Sliding window).
- Border filling options (Zero or Random noise) after transformation.
- Display bit-depth customization for viewing the result hyperstack.

#### Usage

1. Open your image in Fiji.
2. Run `Plugins > KymoTip_IJ > CCN Registration`.
3. Adjust the parameters in the panel:
   - **Registration channel**: Select the channel to compute the structural alignment against.
   - **Angle search range (deg)**: Define the min, max, and step for rotation correction.
   - **Reference mode**: Choose whether to align against the `First frame` or a `Sliding window` of previous frames.
   - **Border fill**: Area left blank after translation/rotation can be filled with `Zero` or `Random noise`.
   - **Display Bit Depth**: Configure the viewing LUT range scaling of the output.
4. Click **Run Registration**. The panel will remain open, allowing you to instantly tweak parameters and re-run if needed.

### Trajectory

Extracts cell trajectories from binarized time-lapse images with smoothing options.

This command extracts the non-zero pixel coordinates from each frame, resorts them into a logical chain using a nearest-neighbor algorithm, and applies smoothing (LOESS or Moving Average).

#### Features

- Nearest-neighbor chain resort algorithm for logical point ordering.
- LOESS (Locally Estimated Scatterplot Smoothing) using Apache Commons Math.
- Cyclic Moving Average for periodic smoothing.
- Batch processing of time-lapse hyperstacks.
- Export results to space-separated text files.

#### Usage

1. Open a binarized 8-bit image stack (e.g. from `Process > Binary > Outline`).
2. Run `Plugins > KymoTip_IJ > Trajectory`.
3. Configure parameters:
   - **Linkage distance**: Maximum pixel distance to connect adjacent points in the nearest-neighbor chain.
   - **Smoothing method**: Choose between `LOESS` (robust local regression) or `Moving Average`.
   - **LOESS degree / fraction**: Tuning parameters for LOESS polynomial degree (robustness iterations) and bandwidth.
   - **Moving Avg window**: Window size for the cyclic moving average.
   - **Wraparound points**: Number of points to extend the periodic boundary for consistent start/end smoothing (LOESS only).
   - **Output directory**: Destination folder for the generated space-separated coordinate files (`.txt`).
4. Click **Run Trajectory**. Result files will be generated per frame in the selected directory.

### Centerline

Extracts cell centerlines from contour coordinate data using Voronoi-based skeletonization.

This command reads contour coordinate text files, computes the Voronoi diagram of the contour points, extracts the longest internal skeleton path, and refines it through smoothing, endpoint extension, and interpolation.

#### Features

- Voronoi-based skeleton extraction using JTS (Java Topology Suite).
- Longest-path detection via Dijkstra's algorithm on the internal Voronoi graph.
- Cumsum-based moving average smoothing for centerline refinement.
- Automatic endpoint extension toward the farthest contour points.
- Uniform-density interpolation along the extended centerline.
- Configurable output image size for visualization.
- Export centerline coordinates to text files and overlay plots to PNG images.

#### Usage

1. Prepare a directory of contour coordinate text files (e.g., output from the Trajectory command). Each file should contain whitespace-separated X Y coordinates, one point per line.
2. Run `Plugins > KymoTip_IJ > Centerline`.
3. Configure parameters:
   - **Input Contour Data Dir**: Directory containing the contour `.txt` files.
   - **Output Dir**: Destination folder. Subdirectories `centerline/` (coordinates) and `centerline(Fig)/` (plots) will be created automatically.
   - **Extension average points (o)**: Number of points used to compute the end slope for extension. Larger values produce straighter endpoints (default: 10).
   - **Exclude bottom points (m1)**: Points to trim from the bottom end before smoothing (default: 10).
   - **Exclude tip points (m2)**: Points to trim from the tip end before smoothing (default: 10).
   - **Smoothing window (mm)**: Window size for the moving average filter. Larger values produce smoother centerlines (default: 65).
   - **Image size (px)**: Width and height of the output PNG plots (default: 512).
4. Click **Run Processing**. Result files will be generated per frame in the output directory.

### Kymograph

Generates kymograph images from centerline data, cell masks, and fluorescence signal images.

This command samples pixel brightness along the centerline normal direction (masked to the cell interior) for each time frame, then assembles the profiles into a kymograph image where the X-axis represents time (frames) and the Y-axis represents position along the centerline.

#### Features

- Equal arc-length resampling of centerline points for uniform spatial sampling.
- Normal-direction brightness sampling with cell mask filtering (only pixels inside the cell are averaged).
- Configurable sampling half-width (W) for the normal direction scan.
- Single Channel mode: grayscale 32-bit float kymograph displayed in Fiji.
- Merge mode: RGB kymograph overlaying two channels (Magenta + Green) from a hyperstack.
- Automatic Y-axis calibration in micrometers using the user-specified pixel/um scale.
- Export of per-frame mean brightness data as text files.

#### Usage

1. Open your signal image (stack or hyperstack) and mask image (binary stack) in Fiji.
2. Prepare the centerline text files from the Centerline command output.
3. Run `Plugins > KymoTip_IJ > Kymograph`.
4. Configure parameters:
   - **Centerline Dir**: Directory containing centerline `.txt` files.
   - **Output Dir**: Destination folder. A `mean_brightness/` subdirectory and kymograph TIFF will be created.
   - **Signal Image**: Select the signal image (stack or hyperstack) from the dropdown.
   - **Mask Image**: Select the binary mask image stack from the dropdown.
   - **Mode**: `Single Channel` to process one channel, or `Merge` to overlay two channels.
   - **Channel / Ch1 / Ch2**: Channel number(s) to process (for hyperstacks).
   - **Sampling half-width W (px)**: Number of pixels to sample on each side of the centerline normal (default: 40).
   - **Scale (pixel/um)**: Pixel-to-micrometer conversion factor (default: 4.917).
5. Click **Run Processing**. The kymograph will be displayed in Fiji and saved as a TIFF file.

## Reference

If you use this plugin in your research, please cite the following paper:

> Kang, Z., Kimata, Y., Nonoyama, T., Ikeuchi, T., Kuchitsu, K., Tsugawa, S. and Ueda, M. (2026), KymoTip: high-throughput characterization of tip-growth dynamics in plant cells. _Plant J_, 125: e70691. https://doi.org/10.1111/tpj.70691

## License

This project is provided under the [MIT License](LICENSE).
