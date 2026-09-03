package com.example.safetrack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private var adapter: LogAdapter? = null
    // New: CellInfoManager instance
    private var cellInfoManager: CellInfoManager? = null
    private lateinit var tvLiveCellInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        tvLiveCellInfo = findViewById(R.id.tvLiveCellInfo)
        recyclerView = findViewById(R.id.rvLogs)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Register CellInfoManager if SDK >= 31
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            cellInfoManager = CellInfoManager(this)
            cellInfoManager?.setOnCellInfoUpdateListener { data ->
                updateCellUi(data)
            }
            cellInfoManager?.register()
        }

        loadLogs()
    }

    private fun updateCellUi(data: CellInfoData?) {
        runOnUiThread {
            if (data == null) {
                tvLiveCellInfo.text = "Cell Info: Unavailable"
                return@runOnUiThread
            }

            val sb = StringBuilder()
            sb.append("Network: ${data.networkType}\n")
            sb.append("MCC: ${data.mcc ?: "N/A"} | MNC: ${data.mnc ?: "N/A"}\n")
            sb.append("TAC: ${data.tac ?: "N/A"} | PCI: ${data.pci ?: "N/A"}\n")

            if (data.networkType == "5G NR") {
                sb.append("NR NCI: ${data.nci ?: "Unavailable"}\n")
                sb.append("NR ARFCN: ${data.nrArfcn ?: "N/A"}\n")
                sb.append("SS-RSRP: ${data.ssRsrp ?: "N/A"}\n")
            } else {
                sb.append("LTE CI/ECI: ${data.lteCi ?: "Unavailable"}\n")
                sb.append("EARFCN: ${data.earfcn ?: "N/A"}\n")
                sb.append("RSRP: ${data.rsrp ?: "N/A"}\n")
            }
            tvLiveCellInfo.text = sb.toString()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            cellInfoManager?.unregister()
        }
    }

    override fun onResume() {
        super.onResume()
        loadLogs()
    }

    private fun loadLogs() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@DashboardActivity)
            val logs = db.trackingDao().getRecentLogs()
            adapter = LogAdapter(logs)
            recyclerView.adapter = adapter
        }
    }

    private class LogAdapter(private val logs: List<TrackingData>) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

        class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
            val tvLocation: TextView = view.findViewById(R.id.tvLocation)
            val tvUsage: TextView = view.findViewById(R.id.tvUsage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val log = logs[position]
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            holder.tvTimestamp.text = sdf.format(Date(log.timestamp))

            if (log.latitude != null && log.longitude != null) {
                holder.tvLocation.text = "📍 Lat: ${log.latitude}, Lng: ${log.longitude}"
                holder.tvLocation.visibility = View.VISIBLE
            } else {
                holder.tvLocation.visibility = View.GONE
            }

            val durationMin = log.foregroundTimeMs / 60000
            holder.tvUsage.text = "📱 App: ${log.packageName} | Duration: ${durationMin}m"
        }

        override fun getItemCount() = logs.size
    }
}
