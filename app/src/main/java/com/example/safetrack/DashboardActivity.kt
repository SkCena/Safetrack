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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val recyclerView = findViewById<RecyclerView>(R.id.rvLogs)
        recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@DashboardActivity)
            val logs = db.trackingDao().getRecentLogs()
            recyclerView.adapter = LogAdapter(logs)
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
            holder.tvLocation.text = "Lat: ${log.latitude}, Lng: ${log.longitude}"
            holder.tvUsage.text = "Usage: ${log.packageName}"
        }

        override fun getItemCount() = logs.size
    }
}
