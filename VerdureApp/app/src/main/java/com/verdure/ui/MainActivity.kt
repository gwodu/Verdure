package com.verdure.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.verdure.R
import com.verdure.core.GeminiNanoEngine
import com.verdure.core.VerdureAI
import com.verdure.tools.NotificationTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var geminiEngine: GeminiNanoEngine
    private lateinit var verdureAI: VerdureAI
    
    private lateinit var statusText: TextView
    private lateinit var responseText: TextView
    private lateinit var testButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        responseText = findViewById(R.id.responseText)
        testButton = findViewById(R.id.testButton)
        
        initializeAI()
        
        testButton.setOnClickListener {
            testNotificationFiltering()
        }
    }
    
    private fun initializeAI() {
        statusText.text = "Initializing Gemini Nano..."
        
        CoroutineScope(Dispatchers.Main).launch {
            geminiEngine = GeminiNanoEngine(applicationContext)
            
            val success = withContext(Dispatchers.IO) {
                geminiEngine.initialize()
            }
            
            if (success) {
                verdureAI = VerdureAI(geminiEngine)
                verdureAI.registerTool(NotificationTool(applicationContext, geminiEngine))
                
                statusText.text = "✅ Ready! Tap button to test."
                testButton.isEnabled = true
            } else {
                statusText.text = "❌ Failed to initialize. Check AICore settings."
            }
        }
    }
    
    private fun testNotificationFiltering() {
        responseText.text = "Processing..."
        
        CoroutineScope(Dispatchers.Main).launch {
            val response = withContext(Dispatchers.IO) {
                verdureAI.processRequest("What notifications are important?")
            }
            responseText.text = response
        }
    }
}
