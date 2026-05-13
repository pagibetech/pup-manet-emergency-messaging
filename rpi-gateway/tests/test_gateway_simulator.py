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

    def test_router_link_ping_succeeds_between_gateways(self) -> None:
        sim = GatewaySimulator()

        ok = sim.ping_gateway("GWA", "GWB", now=0.0)
        snapshot = sim.snapshot_dict()

        self.assertTrue(ok)
        self.assertTrue(snapshot["router_link"]["last_ping_ok"])
        self.assertEqual(snapshot["router_link"]["last_ping_latency_ms"], 0)
        self.assertTrue(any("PING_OK GWA->GWB" in event for event in snapshot["events"]))

    def test_router_link_ping_reports_configured_latency(self) -> None:
        sim = GatewaySimulator()
        sim.set_router_link_latency(600, now=0.0)

        ok = sim.ping_gateway("GWA", "GWB", now=0.1)
        snapshot = sim.snapshot_dict()

        self.assertTrue(ok)
        self.assertEqual(snapshot["router_link"]["latency_ms"], 600)
        self.assertEqual(snapshot["router_link"]["last_ping_latency_ms"], 600)
        self.assertTrue(any("latency_ms=600" in event for event in snapshot["events"]))

    def test_router_link_ping_fails_when_link_is_offline(self) -> None:
        sim = GatewaySimulator()
        sim.set_router_link_available(False, now=1.0)

        ok = sim.ping_gateway("GWA", "GWB", now=1.1)

        self.assertFalse(ok)
        self.assertFalse(sim.snapshot_dict()["router_link"]["last_ping_ok"])

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

    def test_low_rssi_triggers_failover_after_ten_seconds(self) -> None:
        sim = GatewaySimulator()
        sim.observe_lora_rssi("GWA", -82.0, now=0.0)

        sim.tick(9.9)
        self.assertEqual(sim.gateways["GWA"].route_state, "DEGRADED")

        sim.tick(10.0)
        self.assertEqual(sim.gateways["GWA"].route_state, "FAILOVER_ACTIVE")
        self.assertEqual(sim.gateways["GWA"].failover_reason, "LOW_RSSI")

        result = sim.send_message("A1", "A2", "low rssi failover", now=10.1)
        self.assertTrue(result.delivered)
        self.assertEqual(result.packet.route_used, "GATEWAY_WIFI_ROUTER")

    def test_rssi_recovery_before_ten_seconds_prevents_failover(self) -> None:
        sim = GatewaySimulator()
        sim.observe_lora_rssi("GWA", -82.0, now=0.0)
        sim.observe_lora_rssi("GWA", -60.0, now=5.0)

        sim.tick(10.0)

        self.assertEqual(sim.gateways["GWA"].route_state, "RECOVERING")
        self.assertNotEqual(sim.gateways["GWA"].route_state, "FAILOVER_ACTIVE")

    def test_recovery_holds_gateway_route_until_stable_window_completes(self) -> None:
        sim = GatewaySimulator()
        sim.observe_lora_rssi("GWA", -82.0, now=0.0)
        sim.tick(10.0)
        self.assertEqual(sim.gateways["GWA"].route_state, "FAILOVER_ACTIVE")

        sim.observe_lora_rssi("GWA", -60.0, now=11.0)
        result_before = sim.send_message("A1", "A2", "still recovering", now=12.0)
        self.assertEqual(result_before.packet.route_used, "GATEWAY_WIFI_ROUTER")

        sim.tick(21.0)
        self.assertEqual(sim.gateways["GWA"].route_state, "PRIMARY_LORA")
        result_after = sim.send_message("A1", "A2", "recovered", now=21.1)
        self.assertEqual(result_after.packet.route_used, "LOCAL_LORA")

    def test_ack_timeout_triggers_failover_route(self) -> None:
        sim = GatewaySimulator()
        packet_result = sim.send_message("A1", "A2", "manual ack timeout seed", now=0.0)
        packet = packet_result.packet
        sim.delivered.remove(packet)
        sim.queue.append(packet)
        packet.status = "WAITING_ACK"
        packet.created_at = 0.0
        packet.route_used = "LOCAL_LORA"

        sim.tick(10.0)

        self.assertEqual(packet.status, "DELIVERED")
        self.assertEqual(packet.route_used, "GATEWAY_WIFI_ROUTER")
        self.assertEqual(sim.gateways["GWA"].route_state, "FAILOVER_ACTIVE")
        self.assertEqual(sim.gateways["GWA"].failover_reason, "ACK_TIMEOUT")

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
        self.assertEqual(result.packet.status, "BUFFERED_FOR_FORWARD")
        self.assertEqual(result.packet.route_used, "NO_GATEWAY_LINK")
        self.assertEqual(sim.snapshot_dict()["store_forward_depth"], 1)

    def test_store_forward_flushes_buffered_packets_in_order(self) -> None:
        sim = GatewaySimulator()
        sim.set_router_link_available(False, now=0.0)

        first = sim.send_message("A1", "B1", "first", now=1.0)
        second = sim.send_message("A2", "B2", "second", now=2.0)
        self.assertEqual(first.packet.status, "BUFFERED_FOR_FORWARD")
        self.assertEqual(second.packet.status, "BUFFERED_FOR_FORWARD")
        self.assertEqual(sim.snapshot_dict()["store_forward_depth"], 2)

        sim.set_router_link_available(True, now=3.0)

        self.assertEqual(sim.snapshot_dict()["store_forward_depth"], 0)
        self.assertEqual([packet.msg_id for packet in sim.delivered[-2:]], [first.packet.msg_id, second.packet.msg_id])
        self.assertTrue(all(packet.status == "DELIVERED" for packet in sim.delivered[-2:]))

    def test_router_link_demo_records_ping_and_delivery_results(self) -> None:
        sim = GatewaySimulator()

        snapshot = sim.run_router_link_demo()
        demo = snapshot["router_link_demo"]

        self.assertTrue(demo["initial_ping_ok"])
        self.assertEqual(demo["remote_delivery_status"], "DELIVERED")
        self.assertEqual(demo["remote_delivery_route"], "GATEWAY_WIFI_ROUTER")
        self.assertFalse(demo["link_down_ping_ok"])
        self.assertEqual(demo["link_down_delivery_status"], "DELIVERED")
        self.assertTrue(demo["recovered_ping_ok"])

    def test_latency_demo_records_500_to_700_ms_target(self) -> None:
        sim = GatewaySimulator()

        snapshot = sim.run_latency_demo(latency_ms=650)
        demo = snapshot["latency_demo"]

        self.assertTrue(demo["ping_ok"])
        self.assertEqual(demo["configured_latency_ms"], 650)
        self.assertEqual(demo["measured_ping_latency_ms"], 650)
        self.assertTrue(demo["within_500_700_ms_target"])
        self.assertEqual(demo["remote_delivery_status"], "DELIVERED")

    def test_failover_demo_records_state_transition_and_route(self) -> None:
        sim = GatewaySimulator()

        snapshot = sim.run_failover_demo()
        demo = snapshot["failover_demo"]

        self.assertEqual(demo["state_before_10s"], "DEGRADED")
        self.assertEqual(demo["state_after_10s"], "FAILOVER_ACTIVE")
        self.assertEqual(demo["failover_reason"], "LOW_RSSI")
        self.assertEqual(demo["message_route"], "GATEWAY_WIFI_ROUTER")
        self.assertEqual(demo["message_status"], "DELIVERED")

    def test_store_forward_demo_reports_order_preserved(self) -> None:
        sim = GatewaySimulator()

        snapshot = sim.run_store_forward_demo()
        demo = snapshot["store_forward_demo"]

        self.assertEqual(demo["depth_before_recovery"], 2)
        self.assertEqual(demo["statuses_before_recovery"], ["BUFFERED_FOR_FORWARD", "BUFFERED_FOR_FORWARD"])
        self.assertTrue(demo["order_preserved"])
        self.assertEqual(demo["depth_after_recovery"], 0)

    def test_recovery_demo_reports_return_to_primary_lora(self) -> None:
        sim = GatewaySimulator()

        snapshot = sim.run_recovery_demo()
        demo = snapshot["recovery_demo"]

        self.assertEqual(demo["failover_state"], "FAILOVER_ACTIVE")
        self.assertEqual(demo["route_during_failover"], "GATEWAY_WIFI_ROUTER")
        self.assertEqual(demo["state_before_stable_10s"], "RECOVERING")
        self.assertEqual(demo["state_after_stable_10s"], "PRIMARY_LORA")
        self.assertTrue(demo["completed_within_5s"])
        self.assertEqual(demo["route_after_recovery"], "LOCAL_LORA")

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
