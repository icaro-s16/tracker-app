package com.tracker.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tracker.app.databinding.ActivityMainBinding
import com.tracker.app.service.LocationTrackingService
import com.tracker.app.util.DeviceIdHelper
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel


    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val phoneGranted = permissions[Manifest.permission.READ_PHONE_STATE] == true

        Log.d(TAG, "Fine: $fineGranted | Coarse: $coarseGranted | Phone: $phoneGranted")

        if (fineGranted || coarseGranted) {
            updateDeviceIdDisplay()
            requestBackgroundLocationIfNeeded()
        } else {
            Toast.makeText(
                this,
                "Permissão de localização é obrigatória para o rastreamento.",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d(TAG, "Permissão de localização em segundo plano concedida.")
            tryStartService()
        } else {
            Toast.makeText(
                this,
                "Sem permissão em segundo plano, o rastreamento para quando o app for minimizado.",
                Toast.LENGTH_LONG
            ).show()
            tryStartService()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(application)
        )[MainViewModel::class.java]

        setupUI()
        observeViewModel()
        checkAndRequestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
    }



    private fun setupUI() {
        updateDeviceIdDisplay()

        binding.btnSaveAndStart.setOnClickListener {
            val url = binding.etTargetUrl.text.toString().trim()

            if (url.isBlank()) {
                binding.tilTargetUrl.error = "Informe a URL do endpoint"
                return@setOnClickListener
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                binding.tilTargetUrl.error = "URL deve começar com http:// ou https://"
                return@setOnClickListener
            }

            binding.tilTargetUrl.error = null

            lifecycleScope.launch {
                viewModel.saveUrl(url)
                tryStartService()
                Toast.makeText(
                    this@MainActivity,
                    "URL salva. Rastreamento iniciado!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnStop.setOnClickListener {
            LocationTrackingService.stopService(this)
            binding.tvStatus.text = "Status: Parado"
            binding.btnStop.visibility = View.GONE
            binding.btnSaveAndStart.visibility = View.VISIBLE
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.savedUrl.collect { url ->
                if (!url.isNullOrBlank() && binding.etTargetUrl.text.isNullOrBlank()) {
                    binding.etTargetUrl.setText(url)
                    Log.d(TAG, "URL restaurada das preferências: $url")
                }
            }
        }

        lifecycleScope.launch {
            viewModel.pendingCount.collect { count ->
                if (count > 0) {
                    binding.tvQueueStatus.text = "Na fila offline: $count registro(s)"
                    binding.tvQueueStatus.visibility = View.VISIBLE
                } else {
                    binding.tvQueueStatus.visibility = View.GONE
                }
            }
        }
    }

    private fun updateDeviceIdDisplay() {
        val displayId = DeviceIdHelper.getDeviceIdForDisplay(this)
        binding.tvDeviceId.text = displayId
    }


    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        val missing = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            updateDeviceIdDisplay()
            requestBackgroundLocationIfNeeded()
        } else {
            locationPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            tryStartService()
            return
        }

        val hasBackground = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasBackground) {
            tryStartService()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Rastreamento em segundo plano")
            .setMessage(
                "Para rastrear sua localização mesmo com o app minimizado, " +
                        "selecione \"Permitir o tempo todo\" na próxima tela de permissões."
            )
            .setPositiveButton("Entendido") { _, _ ->
                backgroundLocationLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
            .setNegativeButton("Agora não") { _, _ ->
                tryStartService()
            }
            .show()
    }


    private fun tryStartService() {
        val url = binding.etTargetUrl.text.toString().trim()
        if (url.isNotBlank() && hasLocationPermission()) {
            LocationTrackingService.startService(this, url)
            binding.tvStatus.text = "Status: Rastreando..."
            binding.btnStop.visibility = View.VISIBLE
            binding.btnSaveAndStart.visibility = View.GONE
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}
