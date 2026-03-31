package com.albionradar.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.albionradar.R
import com.albionradar.data.RadarSettings

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: RadarSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings)

        settings = RadarSettings.getInstance(this)
        setupSettings()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupSettings() {
        // Radar settings
        val zoomSeekBar = findViewById<SeekBar>(R.id.zoomSeekBar)
        zoomSeekBar.progress = (settings.zoomLevel * 50).toInt()
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settings.zoomLevel = progress / 50f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val showGridSwitch = findViewById<Switch>(R.id.showGridSwitch)
        showGridSwitch.isChecked = settings.showGrid
        showGridSwitch.setOnCheckedChangeListener { _, isChecked ->
            settings.showGrid = isChecked
        }

        val showLabelsSwitch = findViewById<Switch>(R.id.showLabelsSwitch)
        showLabelsSwitch.isChecked = settings.showLabels
        showLabelsSwitch.setOnCheckedChangeListener { _, isChecked ->
            settings.showLabels = isChecked
        }

        // Alert settings
        val alertSoundSwitch = findViewById<Switch>(R.id.alertSoundSwitch)
        alertSoundSwitch.isChecked = settings.alertSound
        alertSoundSwitch.setOnCheckedChangeListener { _, isChecked ->
            settings.alertSound = isChecked
        }

        val hostileAlertSwitch = findViewById<Switch>(R.id.hostileAlertSwitch)
        hostileAlertSwitch.isChecked = settings.hostileAlert
        hostileAlertSwitch.setOnCheckedChangeListener { _, isChecked ->
            settings.hostileAlert = isChecked
        }

        // Resource filters
        setupSwitch(R.id.showOreSwitch) { settings.showOre }
        setupSwitch(R.id.showWoodSwitch) { settings.showWood }
        setupSwitch(R.id.showRockSwitch) { settings.showRock }
        setupSwitch(R.id.showFiberSwitch) { settings.showFiber }
        setupSwitch(R.id.showHideSwitch) { settings.showHide }

        // Mob filters
        setupSwitch(R.id.showNormalMobsSwitch) { settings.showNormalMobs }
        setupSwitch(R.id.showBossSwitch) { settings.showBosses }
        setupSwitch(R.id.showVeteranSwitch) { settings.showVeterans }

        // Player filters
        setupSwitch(R.id.showPlayersSwitch) { settings.showPlayers }
        setupSwitch(R.id.hostileOnlySwitch) { settings.hostileOnly }

        // Other filters
        setupSwitch(R.id.showDungeonsSwitch) { settings.showDungeons }
        setupSwitch(R.id.showChestsSwitch) { settings.showChests }
        setupSwitch(R.id.showFishingSwitch) { settings.showFishing }
        setupSwitch(R.id.showMistSwitch) { settings.showMist }

        // Min tier
        val minTierSeekBar = findViewById<SeekBar>(R.id.minTierSeekBar)
        minTierSeekBar.progress = settings.minTier - 1
        minTierSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settings.minTier = progress + 1
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSwitch(
        switchId: Int,
        getter: () -> Boolean
    ) {
        val switch = findViewById<Switch>(switchId)
        switch.isChecked = getter()
    }
}
