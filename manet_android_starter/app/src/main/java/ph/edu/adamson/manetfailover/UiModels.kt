package ph.edu.adamson.manetfailover

data class CurrentNetworkUi(
    val title: String,
    val operatorName: String,
    val dbmText: String,
    val barsText: String,
    val statusText: String
)

data class UiCellItem(
    val operatorName: String,
    val radioType: String,
    val dbmText: String,
    val barsText: String,
    val statusText: String,
    val registrationText: String
)
