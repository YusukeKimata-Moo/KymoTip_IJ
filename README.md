# CCN Registration (Fiji Plugin)

A Fiji/ImageJ plugin for time-series image registration using Normalized Cross-Correlation (NCC).

This tool performs rotation and translation correction on each frame of a hyperstack (Multi-channel / Z-stack / Time-series). A distinct feature of this plugin is its **non-blocking GUI (operation panel)**, allowing users to modify image display settings, view output hyperstacks, and iteratively tweak registration parameters without closing the control panel.

## Features
- Rotation + Translation correction via Fast Hartley Transform (FHT) based Normalized Cross-Correlation.
- Supports multi-channel, Z-stack, and Time-series hyperstacks. (Single-frame multi-slice images are automatically treated as time-series).
- Independent Swing GUI (non-modal) for uninterrupted Fiji interaction.
- Configurable reference frames (First frame or Sliding window).
- Border filling options (Zero or Random noise) after transformation.
- Display bit-depth customization for viewing the result hyperstack.

## Installation
1. Go to the [Releases](../../releases) page.
2. Download the latest `CCN_Registration.jar`.
3. Place the downloaded `.jar` file into the `plugins` folder of your Fiji/ImageJ installation.
4. Restart Fiji. The plugin will appear in the `Plugins > CCN Registration` menu.

## Usage
1. Open your time-series or Z-stack image in Fiji.
2. Run `Plugins > CCN Registration`.
3. Adjust the parameters in the panel:
   - **Registration channel**: Select the channel to compute the structural alignment against. 
   - **Angle search range (deg)**: Define the min, max, and step for rotation correction.
   - **Reference mode**: Choose whether to align against the `First frame` or a `Sliding window` of previous frames.
   - **Border fill**: Area left blank after translation/rotation can be filled with `Zero` or `Random noise`.
   - **Display Bit Depth**: Configure the viewing LUT range scaling of the output.
4. Click **Run Registration**. The panel will remain open, allowing you to instantly tweak parameters and re-run if needed.

## Reference
If you use this plugin in your research, please cite the following paper:
> Kang, Z., Kimata, Y., Nonoyama, T., Ikeuchi, T., Kuchitsu, K., Tsugawa, S. and Ueda, M. (2026), KymoTip: high-throughput characterization of tip-growth dynamics in plant cells. *Plant J*, 125: e70691. https://doi.org/10.1111/tpj.70691

## License
This project is provided under the [MIT License](LICENSE).
