import unittest

from gateway_sim import GatewaySimulator


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


if __name__ == "__main__":
    unittest.main()
