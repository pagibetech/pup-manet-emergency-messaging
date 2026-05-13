import unittest

from gateway_sim import GatewaySimulator
from gateway_sim.lora_spi import LoRaFrame, SimulatedLoRaSpiRadio


class GatewaySimulatorTest(unittest.TestCase):
    def test_topology_has_six_nodes_and_two_gateways(self) -> None:
        sim = GatewaySimulator()

        self.assertEqual(set(sim.nodes), {"A1", "A2", "A3", "B1", "B2", "B3"})
        self.assertEqual(set(sim.gateways), {"GWA", "GWB"})
        self.assertEqual(sim.gateways["GWA"].peer_gateway_id, "GWB")
        self.assertEqual(sim.gateways["GWB"].peer_gateway_id, "GWA")

    def test_local_delivery_uses_lora(self) -> None:
        sim = GatewaySimulator()

        result = sim.send_message("A1", "A2", "hello local", now=0.0)

        self.assertTrue(result.delivered)
        self.assertEqual(result.packet.route_used, "LOCAL_LORA")
        self.assertEqual(result.packet.path, ["A1", "GWA", "A2"])

    def test_remote_delivery_uses_gateway_wifi_router_link(self) -> None:
        sim = GatewaySimulator()

        result = sim.send_message("A1", "B2", "hello remote", now=1.0)

        self.assertTrue(result.delivered)
        self.assertEqual(result.packet.route_used, "GATEWAY_WIFI_ROUTER")
        self.assertEqual(result.packet.path, ["A1", "GWA", "WIFI_ROUTER_LINK", "GWB", "B2"])

    def test_failover_waits_ten_seconds_before_gateway_route(self) -> None:
        sim = GatewaySimulator()
        sim.set_lora_available("GWA", False, now=2.0)

        result = sim.send_message("A1", "A2", "needs failover", now=2.0)
        self.assertFalse(result.delivered)
        self.assertEqual(result.packet.status, "WAITING_FAILOVER")

        sim.tick(11.9)
        self.assertEqual(result.packet.status, "WAITING_FAILOVER")

        sim.tick(12.0)
        self.assertEqual(result.packet.status, "DELIVERED")
        self.assertEqual(result.packet.route_used, "GATEWAY_WIFI_ROUTER")

    def test_recovery_requires_ten_stable_seconds(self) -> None:
        sim = GatewaySimulator()
        sim.set_lora_available("GWA", False, now=0.0)
        self.assertEqual(sim.gateways["GWA"].route_state, "DEGRADED")

        sim.set_lora_available("GWA", True, now=5.0)
        sim.tick(14.9)
        self.assertEqual(sim.gateways["GWA"].route_state, "RECOVERING")

        sim.tick(15.0)
        self.assertEqual(sim.gateways["GWA"].route_state, "PRIMARY_LORA")

    def test_satellite_link_failure_marks_packet_failed(self) -> None:
        sim = GatewaySimulator()
        sim.set_satellite_link_available(False, now=0.0)

        result = sim.send_message("A1", "B1", "remote with link down", now=1.0)

        self.assertFalse(result.delivered)
        self.assertEqual(result.packet.status, "FAILED")
        self.assertEqual(result.packet.route_used, "NO_GATEWAY_LINK")

    def test_lora_spi_heartbeat_updates_gateway_snapshot(self) -> None:
        sim = GatewaySimulator()
        radio = SimulatedLoRaSpiRadio()
        radio.seed_heartbeat("A1", "GWA", now=3.0, rssi=-55.0, snr=9.7, uptime_s=42)

        received = sim.poll_lora_radio(radio, now=3.1)
        snapshot = sim.snapshot_dict()

        self.assertEqual(received, 1)
        self.assertEqual(snapshot["heartbeat_count"], 1)
        self.assertEqual(snapshot["heartbeats"]["A1"]["gateway_id"], "GWA")
        self.assertEqual(snapshot["heartbeats"]["A1"]["status"], "ONLINE")
        self.assertTrue(any("HEARTBEAT_RX node=A1 gateway=GWA" in event for event in snapshot["events"]))

    def test_lora_spi_heartbeat_rejects_wrong_gateway(self) -> None:
        sim = GatewaySimulator()

        accepted = sim.receive_lora_frame(LoRaFrame("HB1|A1|GWB|10", rssi=-60.0, snr=7.5, received_at=4.0))

        self.assertFalse(accepted)
        self.assertNotIn("A1", sim.snapshot_dict()["heartbeats"])


if __name__ == "__main__":
    unittest.main()
