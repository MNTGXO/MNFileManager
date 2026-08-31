package com.example.mnfilemanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private var currentPath: String = ""

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = FileAdapter(
            onItemClick = { file ->
                if (file.isDirectory) {
                    navigateTo(file.absolutePath)
                } else {
                    openFile(file)
                }
            },
            onShortcutClick = { file ->
                ShortcutHelper.createShortcut(this, file)
            }
        )
        recyclerView.adapter = adapter

        // Start from external storage root or internal storage
        currentPath = Environment.getExternalStorageDirectory().absolutePath

        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                return
            }
        } else {
            val permissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            } else {
                loadFiles(currentPath)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadFiles(currentPath)
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload after permission grant if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                loadFiles(currentPath)
            }
        }
    }

    private fun loadFiles(path: String) {
        currentPath = path
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            Toast.makeText(this, "Invalid directory", Toast.LENGTH_SHORT).show()
            return
        }

        val files = dir.listFiles()?.toList() ?: emptyList()
        val items = files.map { FileItem(it) }.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
        adapter.submitList(items)
        supportActionBar?.title = path
    }

    private fun navigateTo(path: String) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            loadFiles(path)
        }
    }

    private fun openFile(file: File) {
        val uri = Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_VIEW)
        // Guess MIME type (simplified)
        val mime = when {
            file.extension == "txt" -> "text/plain"
            file.extension == "pdf" -> "application/pdf"
            file.extension == "jpg" || file.extension == "jpeg" -> "image/jpeg"
            file.extension == "png" -> "image/png"
            file.extension == "mp4" -> "video/mp4"
            file.extension == "mp3" -> "audio/mpeg"
            else -> "*/*"
        }
        intent.setDataAndType(uri, mime)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(this, "No app to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    // Called from shortcut to open a specific file
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            val filePath = it.getStringExtra("FILE_PATH")
            if (!filePath.isNullOrEmpty()) {
                val file = File(filePath)
                if (file.exists()) {
                    openFile(file)
                } else {
                    Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
