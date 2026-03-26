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

## Reference

If you use this plugin in your research, please cite the following paper:

> Kang, Z., Kimata, Y., Nonoyama, T., Ikeuchi, T., Kuchitsu, K., Tsugawa, S. and Ueda, M. (2026), KymoTip: high-throughput characterization of tip-growth dynamics in plant cells. _Plant J_, 125: e70691. https://doi.org/10.1111/tpj.70691

## License

This project is provided under the [MIT License](LICENSE).
