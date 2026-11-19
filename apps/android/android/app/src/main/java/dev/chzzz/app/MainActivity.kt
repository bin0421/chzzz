package dev.chzzz.app

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    lateinit var controller: AppController
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(ChzzzAppPlugin::class.java)
        super.onCreate(savedInstanceState)
        controller = AppController(this, bridge)
        controller.start()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = controller.back()
        })
    }

    override fun onStop() {
        controller.keepLoginCookie()
        super.onStop()
    }
}
