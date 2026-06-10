package ph.edu.adamson.manetfailover

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var ivLogo: ImageView
    private lateinit var tvActiveNetworkTitle: TextView
    private lateinit var tvActiveNetworkName: TextView
    private lateinit var tvActiveSignalDbm: TextView
    private lateinit var tvActiveSignalBars: TextView
    private lateinit var tvActiveSignalStatus: TextView
    private lateinit var tvAllSims: TextView
    private lateinit var tvDetectedHint: TextView
    private lateinit var btnRefresh: Button
    private lateinit var rvDetectedNetworks: RecyclerView

    private lateinit var adapter: CellInfoAdapter

    private val telephonyManager: TelephonyManager by lazy {
        getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private val subscriptionManager: SubscriptionManager by lazy {
        getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    }

    private val permissionRequestCode = 1001

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ivLogo = findViewById(R.id.ivLogo)
        tvActiveNetworkTitle = findViewById(R.id.tvActiveNetworkTitle)
        tvActiveNetworkName = findViewById(R.id.tvActiveNetworkName)
        tvActiveSignalDbm = findViewById(R.id.tvActiveSignalDbm)
        tvActiveSignalBars = findViewById(R.id.tvActiveSignalBars)
        tvActiveSignalStatus = findViewById(R.id.tvActiveSignalStatus)
        tvAllSims = findViewById(R.id.tvAllSims)
        tvDetectedHint = findViewById(R.id.tvDetectedHint)
        btnRefresh = findViewById(R.id.btnRefresh)
        rvDetectedNetworks = findViewById(R.id.rvDetectedNetworks)

        adapter = CellInfoAdapter()
        rvDetectedNetworks.layoutManager = LinearLayoutManager(this)
        rvDetectedNetworks.adapter = adapter

        btnRefresh.setOnClickListener {
            if (hasAllPermissions()) {
                refreshAllData()
            } else {
                requestNeededPermissions()
            }
        }

        if (hasTelephonyFeature()) {
            if (hasAllPermissions()) {
                refreshAllData()
            } else {
                requestNeededPermissions()
            }
        } else {
            showNoTelephonyMessage()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasTelephonyFeature() && hasAllPermissions()) {
            registerSignalListener()
            refreshAllData()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterSignalListener()
    }

    private fun hasTelephonyFeature(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    private fun showNoTelephonyMessage() {
        tvActiveNetworkTitle.text = getString(R.string.no_telephony_title)
        tvActiveNetworkName.text = getString(R.string.no_telephony_message)
        tvActiveSignalDbm.text = ""
        tvActiveSignalBars.text = ""
        tvActiveSignalStatus.text = ""
        tvAllSims.text = ""
        tvDetectedHint.text = getString(R.string.no_telephony_message)
        adapter.submitList(emptyList())
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestNeededPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, permissionRequestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                registerSignalListener()
                refreshAllData()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permission_required_message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun registerSignalListener() {
        unregisterSignalListener()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerTelephonyCallbackApi31()
        } else {
            registerPhoneStateListenerLegacy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallbackApi31() {
        val callback = object : TelephonyCallback(),
            TelephonyCallback.SignalStrengthsListener,
            TelephonyCallback.ServiceStateListener {

            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                refreshAllData()
            }

            override fun onServiceStateChanged(serviceState: android.telephony.ServiceState) {
                refreshAllData()
            }
        }

        telephonyCallback = callback
        telephonyManager.registerTelephonyCallback(mainExecutor, callback)
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListenerLegacy() {
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                super.onSignalStrengthsChanged(signalStrength)
                refreshAllData()
            }

            override fun onServiceStateChanged(serviceState: android.telephony.ServiceState?) {
                super.onServiceStateChanged(serviceState)
                refreshAllData()
            }
        }

        phoneStateListener = listener
        telephonyManager.listen(
            listener,
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_SERVICE_STATE
        )
    }

    private fun unregisterSignalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
            telephonyCallback = null
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
            phoneStateListener = null
        }
    }

    private fun refreshAllData() {
        updateCurrentNetworkCard()
        updateDetectedNetworksList()
    }

    private fun updateCurrentNetworkCard() {
        val subscriptions = getActiveSubscriptions()

        if (subscriptions.isEmpty()) {
            tvActiveNetworkTitle.text = getString(R.string.current_network_card_title)
            tvActiveNetworkName.text = getString(R.string.no_active_sim_detected)
            tvActiveSignalDbm.text = getString(R.string.signal_dbm_placeholder)
            tvActiveSignalBars.text = getString(R.string.signal_bars_placeholder)
            tvActiveSignalStatus.text = getString(R.string.status_unknown)
            tvAllSims.text = getString(R.string.insert_sim_message)
            return
        }

        val activeSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        val selectedSub = subscriptions.firstOrNull { it.subscriptionId == activeSubId } ?: subscriptions.first()
        val currentUi = buildCurrentNetworkUi(selectedSub)

        tvActiveNetworkTitle.text = currentUi.title
        tvActiveNetworkName.text = currentUi.operatorName
        tvActiveSignalDbm.text = currentUi.dbmText
        tvActiveSignalBars.text = currentUi.barsText
        tvActiveSignalStatus.text = currentUi.statusText
        tvAllSims.text = buildAllSimSummary(subscriptions)
    }

    private fun updateDetectedNetworksList() {
        if (!hasAllPermissions()) {
            tvDetectedHint.text = getString(R.string.permission_required_message)
            adapter.submitList(emptyList())
            return
        }

        val allCellInfo = try {
            telephonyManager.allCellInfo.orEmpty()
        } catch (sec: SecurityException) {
            emptyList()
        } catch (ex: Exception) {
            emptyList()
        }

        val items = allCellInfo.mapNotNull { mapCellInfoToUi(it) }
            .sortedWith(
                compareByDescending<UiCellItem> { it.registrationText.contains("Registered", ignoreCase = true) }
                    .thenByDescending { barsTextToLevel(it.barsText) }
            )

        if (items.isEmpty()) {
            tvDetectedHint.text = getString(R.string.detected_networks_hint_empty)
        } else {
            tvDetectedHint.text = getString(R.string.detected_networks_hint)
        }

        adapter.submitList(items)
    }

    private fun buildCurrentNetworkUi(subscriptionInfo: SubscriptionInfo): CurrentNetworkUi {
        val tmForSub = telephonyManager.createForSubscriptionId(subscriptionInfo.subscriptionId)
        val operatorName = getOperatorNameForSubscription(subscriptionInfo, tmForSub)
        val signalStrength = try {
            tmForSub.signalStrength
        } catch (_: SecurityException) {
            null
        }

        val level = signalStrength?.level ?: 0
        val dbm = extractDbmFromSignalStrength(signalStrength)

        return CurrentNetworkUi(
            title = getString(R.string.current_network_card_title),
            operatorName = operatorName,
            dbmText = formatDbm(dbm),
            barsText = buildBarsLabel(level),
            statusText = buildStatusLabel(level)
        )
    }

    private fun buildAllSimSummary(subscriptions: List<SubscriptionInfo>): String {
        return subscriptions.joinToString(separator = "\n\n") { sub ->
            val tmForSub = telephonyManager.createForSubscriptionId(sub.subscriptionId)
            val operatorName = getOperatorNameForSubscription(sub, tmForSub)
            val signalStrength = try {
                tmForSub.signalStrength
            } catch (_: SecurityException) {
                null
            }
            val level = signalStrength?.level ?: 0
            val dbm = extractDbmFromSignalStrength(signalStrength)

            "SIM ${sub.simSlotIndex + 1}\n" +
                "Network: $operatorName\n" +
                "Signal: ${formatDbm(dbm)}\n" +
                "Bars: ${buildBarsLabel(level)}\n" +
                "Status: ${buildStatusLabel(level)}"
        }
    }

    private fun getActiveSubscriptions(): List<SubscriptionInfo> {
        return try {
            subscriptionManager.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getOperatorNameForSubscription(
        subscriptionInfo: SubscriptionInfo,
        tmForSub: TelephonyManager
    ): String {
        val telephonyName = tmForSub.networkOperatorName?.trim().orEmpty()
        if (telephonyName.isNotEmpty()) return telephonyName

        val carrierName = subscriptionInfo.carrierName?.toString()?.trim().orEmpty()
        if (carrierName.isNotEmpty()) return carrierName

        return getString(R.string.unknown_network)
    }

    private fun extractDbmFromSignalStrength(signalStrength: SignalStrength?): Int? {
        if (signalStrength == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            signalStrength.cellSignalStrengths
                .map { it.dbm }
                .filter { it != Int.MAX_VALUE && it > -300 && it < 0 }
                .maxOrNull()
        } else {
            null
        }
    }

    private fun mapCellInfoToUi(cellInfo: CellInfo): UiCellItem? {
        return when (cellInfo) {
            is CellInfoLte -> mapLteCell(cellInfo)
            is CellInfoNr -> mapNrCell(cellInfo)
            is CellInfoGsm -> mapGsmCell(cellInfo)
            is CellInfoWcdma -> mapWcdmaCell(cellInfo)
            is CellInfoTdscdma -> mapTdscdmaCell(cellInfo)
            is CellInfoCdma -> mapCdmaCell(cellInfo)
            else -> null
        }
    }

    private fun mapLteCell(cellInfo: CellInfoLte): UiCellItem {
        val level = cellInfo.cellSignalStrength.level
        return UiCellItem(
            operatorName = getOperatorDisplayName(cellInfo.cellIdentity),
            radioType = "LTE",
            dbmText = formatDbm(cellInfo.cellSignalStrength.dbm),
            barsText = buildBarsLabel(level),
            statusText = buildStatusLabel(level),
            registrationText = if (cellInfo.isRegistered) "Registered cell" else "Neighbor cell"
        )
    }

    private fun mapNrCell(cellInfo: CellInfoNr): UiCellItem {
        val level = cellInfo.cellSignalStrength.level
        return UiCellItem(
            operatorName = getOperatorDisplayName(cellInfo.cellIdentity as CellIdentityNr),
            radioType = "5G NR",
            dbmText = formatDbm(cellInfo.cellSignalStrength.dbm),
            barsText = buildBarsLabel(level),
            statusText = buildStatusLabel(level),
            registrationText = if (cellInfo.isRegistered) "Registered cell" else "Neighbor cell"
        )
    }

    private fun mapGsmCell(cellInfo: CellInfoGsm): UiCellItem {
        val level = cellInfo.cellSignalStrength.level
        return UiCellItem(
            operatorName = getOperatorDisplayName(cellInfo.cellIdentity),
            radioType = "GSM",
            dbmText = formatDbm(cellInfo.cellSignalStrength.dbm),
            barsText = buildBarsLabel(level),
            statusText = buildStatusLabel(level),
            registrationText = if (cellInfo.isRegistered) "Registered cell" else "Neighbor cell"
        )
    }

    private fun mapWcdmaCell(cellInfo: CellInfoWcdma): UiCellItem {
        val level = cellInfo.cellSignalStrength.level
        return UiCellItem(
            operatorName = getOperatorDisplayName(cellInfo.cellIdentity),
            radioType = "WCDMA",
            dbmText = formatDbm(cellInfo.cellSignalStrength.dbm),
            barsText = buildBarsLabel(level),
            statusText = buildStatusLabel(level),
            registrationText = if (cellInfo.isRegistered) "Registered cell" else "Neighbor cell"
        )
    }

    private fun mapTdscdmaCell(cellInfo: CellInfoTdscdma): UiCellItem {
        val level = cellInfo.cellSignalStrength.level
        return UiCellItem(
            operatorName = getOperatorDisplayName(cellInfo.cellIdentity),
            radioType = "TD-SCDMA",
            dbmText = formatDbm(cellInfo.cellSignalStrength.dbm),
            barsText = buildBarsLabel(level),
            statusText = buildStatusLabel(level),
            registrationText = if (cellInfo.isRegistered) "Registered cell" else "Neighbor cell"
        )
    }

    private fun mapCdmaCell(cellInfo: CellInfoCdma): UiCellItem {
        val level = cellInfo.cellSignalStrength.level
        return UiCellItem(
            operatorName = getOperatorDisplayName(cellInfo.cellIdentity),
            radioType = "CDMA",
            dbmText = formatDbm(cellInfo.cellSignalStrength.dbm),
            barsText = buildBarsLabel(level),
            statusText = buildStatusLabel(level),
            registrationText = if (cellInfo.isRegistered) "Registered cell" else "Neighbor cell"
        )
    }

    private fun getOperatorDisplayName(identity: CellIdentityLte): String {
        return identity.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }
            ?: buildMccMnc(identity.mccString, identity.mncString)
            ?: getString(R.string.unknown_network)
    }

    private fun getOperatorDisplayName(identity: CellIdentityNr): String {
        return identity.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }
            ?: buildMccMnc(identity.mccString, identity.mncString)
            ?: getString(R.string.unknown_network)
    }

    private fun getOperatorDisplayName(identity: CellIdentityGsm): String {
        return identity.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }
            ?: buildMccMnc(identity.mccString, identity.mncString)
            ?: getString(R.string.unknown_network)
    }

    private fun getOperatorDisplayName(identity: CellIdentityWcdma): String {
        return identity.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }
            ?: buildMccMnc(identity.mccString, identity.mncString)
            ?: getString(R.string.unknown_network)
    }

    private fun getOperatorDisplayName(identity: CellIdentityTdscdma): String {
        return identity.operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }
            ?: buildMccMnc(identity.mccString, identity.mncString)
            ?: getString(R.string.unknown_network)
    }

    private fun getOperatorDisplayName(identity: CellIdentityCdma): String {
        return getString(R.string.unknown_network)
    }

    private fun buildMccMnc(mcc: String?, mnc: String?): String? {
        val safeMcc = mcc?.takeIf { it.isNotBlank() }
        val safeMnc = mnc?.takeIf { it.isNotBlank() }
        return if (safeMcc != null && safeMnc != null) {
            "$safeMcc-$safeMnc"
        } else {
            null
        }
    }

    private fun formatDbm(dbm: Int?): String {
        return if (dbm == null || dbm == Int.MAX_VALUE) {
            getString(R.string.signal_dbm_placeholder)
        } else {
            "$dbm dBm"
        }
    }

    private fun buildBarsLabel(level: Int): String {
        val clamped = level.coerceIn(0, 4)
        val filled = "■".repeat(clamped)
        val empty = "□".repeat(4 - clamped)
        return "$filled$empty ($clamped/4)"
    }

    private fun buildStatusLabel(level: Int): String {
        return when (level.coerceIn(0, 4)) {
            0, 1 -> getString(R.string.status_weak)
            2 -> getString(R.string.status_fair)
            3, 4 -> getString(R.string.status_strong)
            else -> getString(R.string.status_unknown)
        }
    }

    private fun barsTextToLevel(barsText: String): Int {
        val regex = Regex("\\((\\d)/4\\)")
        val match = regex.find(barsText)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
}
