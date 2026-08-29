package com.example.safetrack

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvDescription = findViewById<TextView>(R.id.tvDescription)
        val btnGrant = findViewById<Button>(R.id.btnGrantPermissions)

        tvTitle.text = "Welcome to SafeTrack"
        tvDescription.text = "To ensure device safety, please grant the following permissions. You can manage these at any time in device settings."
        btnGrant.text = "Grant Permissions"

        btnGrant.setOnClickListener {
            startActivity(android.content.Intent(this, PermissionOnboardingActivity::class.java))
            finish()
        }
    }
}
