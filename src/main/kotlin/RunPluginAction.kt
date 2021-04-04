import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor
import com.intellij.codeInsight.lookup.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.annotations.NotNull
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.progress.util.ReadTask
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.util.concurrency.NonUrgentExecutor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


class RunPluginAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        GlobalStorage.out_path = System.getenv()["OUT_PATH"]
        val project = e.getData(LangDataKeys.PROJECT)!!
        val editor = e.getData(LangDataKeys.EDITOR)!!
        println("Works")
        var openedPopup: ListPopup? = null


        ProgressManager.getInstance().run(object : Backgroundable(project, "Title") {
            fun showPopup(group: DefaultActionGroup) {
                ApplicationManager.getApplication().invokeAndWait {
                    openedPopup?.cancel()
                }
//                Thread.sleep(300)
                ApplicationManager.getApplication().invokeAndWait {
                    openedPopup = JBPopupFactory.getInstance().createActionGroupPopup(
                        "Choose Intention",
                        group,
                        SimpleDataContext.getProjectContext(project),
                        true,
                        null,
                        -1
                    )
                    openedPopup!!.showInBestPositionFor(editor)
                }
            }

            override fun run(progressIndicator: ProgressIndicator) {

                // start your process

                // Set the progress bar percentage and text
                progressIndicator.isIndeterminate = true
                progressIndicator.text = "Prediction started"


                // 50% done
                val handler = CurrentPositionHandler(e)
                val actionGroup = DefaultActionGroup()
//                val job = GlobalScope.launch {
////                    ActionGroupBuilder().build(handler, actionGroup)
//                    repeat(1000) {
//                        val a = 5 + 5
//                    }
//                    println("stop")
//                }
                val channel = LinkedBlockingQueue<AnAction>()
                val job = thread {  ActionGroupBuilder().build(handler, channel)  }
                val action = channel.take() // First action takes more time to load so we wait here
                actionGroup.addAction(action)
                while (true) {
                    Thread.sleep(200)
                    if (channel.isEmpty()) break
                    while (channel.isNotEmpty()) {
                        val action = channel.take()
                        actionGroup.addAction(action)
                    }
                    showPopup(actionGroup)
                    progressIndicator.text = "${actionGroup.childrenCount} actions processed"
                }
                job.join()
                showPopup(actionGroup)
            }
        })

    }


}