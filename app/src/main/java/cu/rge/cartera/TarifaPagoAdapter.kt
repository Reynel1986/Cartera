package cu.rge.cartera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cu.rge.cartera.data.model.TarifaPago

class TarifaPagoAdapter(
    private val tarifas: List<TarifaPago>,
    private val onEdit: (TarifaPago) -> Unit,
    private val onDelete: (TarifaPago) -> Unit
) : RecyclerView.Adapter<TarifaPagoAdapter.TarifaViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TarifaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tarifa_pago, parent, false)
        return TarifaViewHolder(view)
    }
    override fun onBindViewHolder(holder: TarifaViewHolder, position: Int) {
        val tarifa = tarifas[position]
        holder.textNombre.text = tarifa.nombre
        holder.textPorcentaje.text = "${tarifa.porcentaje}%"
        holder.textDescripcion.text = tarifa.descripcion
        holder.btnEdit.setOnClickListener { onEdit(tarifa) }
        holder.btnDelete.setOnClickListener { onDelete(tarifa) }
    }
    override fun getItemCount() = tarifas.size
    class TarifaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textNombre: TextView = view.findViewById(R.id.textNombreTarifa)
        val textPorcentaje: TextView = view.findViewById(R.id.textPorcentajeTarifa)
        val textDescripcion: TextView = view.findViewById(R.id.textDescripcionTarifa)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditTarifa)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteTarifa)
    }
} 