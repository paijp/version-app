package com.example.versionapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.versionapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvVersion.text = "Version: ${BuildConfig.VERSION_NAME}"
        binding.tvVersionCode.text = "Version Code: ${BuildConfig.VERSION_CODE}"
        binding.tvAppId.text = "App ID: ${BuildConfig.APPLICATION_ID}"
    }
}
