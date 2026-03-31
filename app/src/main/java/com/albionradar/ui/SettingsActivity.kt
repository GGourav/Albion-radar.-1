package com.albionradar.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Switch
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
        setupSwitch(R.id.showOreSwitch, settings::getShowOre) { settings.showOre = it }
        setupSwitch(R.id.showWoodSwitch, settings::getShowWood) { settings.showWood = it }
        setupSwitch(R.id.showRockSwitch, settings::getShowRock) { settings.showRock = it }
        setupSwitch(R.id.showFiberSwitch, settings::getShowFiber) { settings.showFiber = it }
        setupSwitch(R.id.showHideSwitch, settings::getShowHide) { settings.showHide = it }

        // Mob filters
        setupSwitch(R.id.showNormalMobsSwitch, settings::getShowNormalMobs) { settings.showNormalMobs = it }
        setupSwitch(R.id.showBossSwitch, settings::getShowBosses) { settings.showBosses = it }
        setupSwitch(R.id.showVeteranSwitch, settings::getShowVeterans) { settings.showVeterans = it }

        // Player filters
        setupSwitch(R.id.showPlayersSwitch, settings::getShowPlayers) { settings.showPlayers = it }
        setupSwitch(R.id.hostileOnlySwitch, settings::getHostileOnly) { settings.hostileOnly = it }

        // Other filters
        setupSwitch(R.id.showDungeonsSwitch, settings::getShowDungeons) { settings.showDungeons = it }
        setupSwitch(R.id.showChestsSwitch, settings::getShowChests) { settings.showChests = it }
        setupSwitch(R.id.showFishingSwitch, settings::getShowFishing) { settings.showFishing = it }
        setupSwitch(R.id.showMistSwitch, settings::getShowMist) { settings.showMist = it }

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
        getter: () -> Boolean,
        setter: (Boolean) -> Unit
    ) {
        val switch = findViewById<Switch>(switchId)
        switch.isChecked = getter()
        switch.setOnCheckedChangeListener { _, isChecked ->
            setter(isChecked)
        }
    }
}
