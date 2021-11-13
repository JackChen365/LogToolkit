package cz.android.logtoolkit

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cz.android.logtoolkit.worker.Logger

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.injectLoggerService(this)
    }
}