package ph.edu.adamson.manetfailover

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CellInfoAdapter : RecyclerView.Adapter<CellInfoAdapter.CellViewHolder>() {

    private val items = mutableListOf<UiCellItem>()

    fun submitList(newItems: List<UiCellItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cell_info, parent, false)
        return CellViewHolder(view)
    }

    override fun onBindViewHolder(holder: CellViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class CellViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvOperator: TextView = itemView.findViewById(R.id.tvOperator)
        private val tvRadioType: TextView = itemView.findViewById(R.id.tvRadioType)
        private val tvDbm: TextView = itemView.findViewById(R.id.tvDbm)
        private val tvBars: TextView = itemView.findViewById(R.id.tvBars)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvRegistration: TextView = itemView.findViewById(R.id.tvRegistration)

        fun bind(item: UiCellItem) {
            tvOperator.text = item.operatorName
            tvRadioType.text = item.radioType
            tvDbm.text = item.dbmText
            tvBars.text = item.barsText
            tvStatus.text = item.statusText
            tvRegistration.text = item.registrationText
        }
    }
}
