package pers.zhc.android.rime

import android.annotation.SuppressLint
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import pers.zhc.android.rime.ImeSettingsActivity.Companion.CONFIGS_FILE
import pers.zhc.android.rime.MyApplication.Companion.GSON
import pers.zhc.android.rime.databinding.ImeCandidateItemBinding
import pers.zhc.android.rime.databinding.ImeCandidatesViewBinding
import pers.zhc.android.rime.rime.*
import pers.zhc.android.rime.util.fromJsonOrNull
import pers.zhc.tools.utils.setLinearLayoutManager
import kotlin.concurrent.thread

class IME : InputMethodService() {
    private var candidatesViewBinding: ImeCandidatesViewBinding? = null
    private var ic: InputConnection? = null
    private var candidatesAdapter: CandidatesListAdapter? = null

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        ic = currentInputConnection
    }

    override fun onCreate() {
        super.onCreate()
        val themedContext = ContextThemeWrapper(this, R.style.Theme_Main)
        candidatesViewBinding = ImeCandidatesViewBinding.inflate(LayoutInflater.from(themedContext))
        candidatesAdapter = CandidatesListAdapter()
        setupSession()
    }

    private fun onKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown) {
            hideWindow()
            return true
        }

        val session = SESSION ?: return false
        val ic = ic ?: return false
        val candidatesViewBinding = candidatesViewBinding ?: return false

        val rimeKeyEvent = toRimeKey(event) ?: return false
        val keyStatus = session.processKey(rimeKeyEvent)
        if (keyStatus == KeyStatus.PASS) {
            return false
        }

        val context = session.getContext()
        if (context != null) {
            candidatesViewBinding.setPreedit(context.getPreedit() ?: "")
            val candidates = context.getCandidates()
            candidatesAdapter!!.update(candidates)
        }
        val commit = session.getCommit()
        if (commit != null) {
            ic.commit(commit)
        }

        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return onKey(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return onKey(event)
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onCreateCandidatesView(): View {
        setCandidatesViewShown(true)
        candidatesViewBinding!!.recyclerView.apply {
            adapter = candidatesAdapter!!
            setLinearLayoutManager()
        }
        return candidatesViewBinding!!.root
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!isFullscreenMode) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets
        }
    }

    companion object {
        private var SESSION: Session? = null

        private fun setupSession() {
            if (SESSION != null) {
                return
            }
            val configs = GSON.fromJsonOrNull(CONFIGS_FILE.readText(), RimeConfigs::class.java)
            val userDataDir = configs?.userDataDir ?: ""
            val sharedDataDir = configs?.sharedDataDir ?: ""
            val engine = Engine.create(userDataDir, sharedDataDir)
            thread {
                val result = engine.waitForDeployment()
                if (result == Engine.Companion.DeployStatus.SUCCESS) {
                    SESSION = engine.createSession()
                }
                engine.setNotificationHandler { messageType, messageValue ->
                    println(Pair(messageType, messageValue))
                }
            }
        }
    }
}

fun ImeCandidatesViewBinding.setPreedit(text: String) {
    this.preeditView.text = text
}

fun InputConnection.commit(text: String) {
    this.commitText(text, 1 /* a value > 0 */)
}

class CandidatesListAdapter : RecyclerView.Adapter<CandidatesListAdapter.MyViewHolder>() {
    private var candidates: Context.Candidates? = null

    class MyViewHolder(bindings: ImeCandidateItemBinding) : RecyclerView.ViewHolder(bindings.root) {
        val candidateTV = bindings.candidateView
        val selectLabelTV = bindings.selectLabelTv
        val rootRL = bindings.root
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val bindings = ImeCandidateItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyViewHolder(bindings)
    }

    override fun getItemCount(): Int {
        return (candidates ?: return 0).candidates.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // when candidates is null, `getItemCount()` gets 0; we assert it's not null here
        val candidates = candidates!!
        val (selectLabel, candidateText, comment) = candidates.candidates[position]
        holder.selectLabelTV.text = selectLabel ?: (position + 1).toString()
        var text = candidateText
        comment?.let { text += " $it" }
        holder.candidateTV.text = text
        if (position == candidates.selectedPos) {
            holder.rootRL.setBackgroundColor(Color.LTGRAY)
        } else {
            holder.rootRL.setBackgroundColor(Color.WHITE)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun update(candidates: Context.Candidates) {
        this.candidates = candidates
        notifyDataSetChanged()
    }
}
