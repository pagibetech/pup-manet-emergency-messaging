import sys
import os

# Add rpi-gateway parent to sys.path so gateway_sim/ is importable
rpi_gateway_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if rpi_gateway_root not in sys.path:
    sys.path.insert(0, rpi_gateway_root)
