package pers.zhc.android.rime

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import pers.zhc.android.rime.databinding.ActivityMainBinding
import pers.zhc.android.rime.jni.Engine
import pers.zhc.android.rime.jni.KeyEvent
import pers.zhc.android.rime.util.ToastUtils
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    @Suppress("PrivatePropertyName")
    private val TAG: String = this.javaClass.name

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bindings = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bindings.root)

        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        bindings.testButton.setOnClickListener {
            val userDataDir = bindings.userDataDirEt.text.toString()
            val sharedDataDir = bindings.sharedDataDirEt.text.toString().ifEmpty { null }
            val engine = Engine.create(userDataDir, sharedDataDir)
            ToastUtils.show(this, "Deploying...")
            thread {
                val result = engine.waitForDeployment()
                if (!result) {
                    ToastUtils.show(this, "Deployment failed")
                } else {
                    ToastUtils.show(this, "Done")
                }

                val session = engine.createSession()
                val keyStatus = session.processKey(KeyEvent(103 /* g */, 0))
                println(keyStatus)

                val context = session.getContext()!!
                println(context)
                println(context.getCandidates())
                println(session.getCommit())
            }
        }
    }
}
