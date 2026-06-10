# Open First - ESP32 Node Simulation

This package contains only the ESP32 PlatformIO simulation project.

No LoRa, Bluetooth, WiFi, GSM, or real hardware communication logic is included.

## How to Open and Run

1. Extract the `esp32-node-simulation` folder if it was provided as an archive.
2. Open the `esp32-node-simulation` folder in VS Code with the PlatformIO extension installed.
3. Connect the ESP32 board to your computer by USB.
4. Build the project:

   ```sh
   pio run
   ```

5. Upload the project to the ESP32:

   ```sh
   pio run --target upload
   ```

6. Open the Serial Monitor:

   ```sh
   pio device monitor -b 115200
   ```

Use the Serial Monitor to enter plain text, `send <DEST_NODE_ID> <message>`, or a JSON message as described in `README.md`.

