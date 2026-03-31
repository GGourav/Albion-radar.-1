package com.albionradar

import android.content.Intent
import android.net.VpnService
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albionradar.data.EntityManager
import com.albionradar.data.EntityType
import com.albionradar.overlay.RadarOverlayService
import com.albionradar.ui.EntityAdapter
import com.albionradar.ui.RadarView
import com.albionradar.ui.SettingsActivity
import com.albionradar.vpn.AlbionVpnService
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val VPN_REQUEST_CODE = 1001
        private const val OVERLAY_REQUEST_CODE = 1002
    }

    private lateinit var radarView: RadarView
    private lateinit var btnStartVpn: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnSettings: Button
    private lateinit var tabLayout: TabLayout
    private lateinit var entityList: RecyclerView
    private lateinit var entityAdapter: EntityAdapter
    private lateinit var vpnStatusText: TextView
    private lateinit var entityCountText: TextView
    private lateinit var zoneNameText: TextView
    
    private var vpnRunning = false
    private var overlayRunning = false
    
    private val entityManager = EntityManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupListeners()
        observeEntities()
    }

    private fun initViews() {
        radarView = findViewById(R.id.radarView)
        btnStartVpn = findViewById(R.id.btnStartVpn)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnSettings = findViewById(R.id.btnSettings)
        tabLayout = findViewById(R.id.tabLayout)
        entityList = findViewById(R.id.entityList)
        vpnStatusText = findViewById(R.id.vpnStatus)
        entityCountText = findViewById(R.id.entityCount)
        zoneNameText = findViewById(R.id.zoneName)
        
        entityAdapter = EntityAdapter()
        entityList.layoutManager = LinearLayoutManager(this)
        entityList.adapter = entityAdapter
    }

    private fun setupListeners() {
        btnStartVpn.setOnClickListener {
            if (vpnRunning) {
                stopVpn()
            } else {
                startVpn()
            }
        }
        
        btnOverlay.setOnClickListener {
            if (overlayRunning) {
                hideOverlay()
            } else {
                showOverlay()
            }
        }
        
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val entityType = when (tab?.position) {
                    0 -> EntityType.RESOURCE
                    1 -> EntityType.MOB
                    2 -> EntityType.PLAYER
                    3 -> EntityType.DUNGEON
                    else -> EntityType.RESOURCE
                }
                updateEntityList(entityType)
                entityList.visibility = RecyclerView.VISIBLE
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeEntities() {
        lifecycleScope.launch {
            entityManager.entities.collect { entities ->
                radarView.updateEntities(entities)
                entityCountText.text = "Entities: ${entities.size}"
            }
        }
        
        lifecycleScope.launch {
            entityManager.zoneName.collect { zone ->
                zoneNameText.text = "Zone: $zone"
            }
        }
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null)
        }
    }

    private fun stopVpn() {
        val intent = Intent(this, AlbionVpnService::class.java).apply {
            action = AlbionVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        vpnRunning = false
        updateVpnStatus()
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_REQUEST_CODE)
        } else {
            startOverlayService()
        }
    }

    private fun hideOverlay() {
        val intent = Intent(this, RadarOverlayService::class.java).apply {
            action = RadarOverlayService.ACTION_HIDE
        }
        startService(intent)
        overlayRunning = false
        btnOverlay.setText(R.string.show_overlay)
    }

    private fun startOverlayService() {
        val intent = Intent(this, RadarOverlayService::class.java).apply {
            action = RadarOverlayService.ACTION_SHOW
        }
        startForegroundService(intent)
        overlayRunning = true
        btnOverlay.setText(R.string.hide_overlay)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            VPN_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    val intent = Intent(this, AlbionVpnService::class.java).apply {
                        action = AlbionVpnService.ACTION_CONNECT
                    }
                    startForegroundService(intent)
                    vpnRunning = true
                    updateVpnStatus()
                } else {
                    Toast.makeText(this, R.string.vpn_permission_required, Toast.LENGTH_SHORT).show()
                }
            }
            OVERLAY_REQUEST_CODE -> {
                if (resultCode == RESULT_OK || Settings.canDrawOverlays(this)) {
                    startOverlayService()
                } else {
                    Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateVpnStatus() {
        if (vpnRunning) {
            vpnStatusText.setText(R.string.running)
            vpnStatusText.setTextColor(getColor(R.color.player_friendly))
            btnStartVpn.setText(R.string.stop_vpn)
        } else {
            vpnStatusText.setText(R.string.stopped)
            vpnStatusText.setTextColor(getColor(R.color.player_hostile))
            btnStartVpn.setText(R.string.start_vpn)
        }
    }

    private fun updateEntityList(type: EntityType) {
        val entities = entityManager.getEntitiesByType(type)
        entityAdapter.submitList(entities)
    }

    override fun onResume() {
        super.onResume()
        updateVpnStatus()
    }
}
